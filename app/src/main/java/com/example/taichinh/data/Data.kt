package com.example.taichinh.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** type: "CHI" hoặc "THU". archived=true nghĩa là đã "xóa" khỏi màn hình nhưng vẫn giữ lịch sử cho báo cáo. */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("categoryId"), Index("timestamp")]
)
data class FinTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val amount: Long,      // luôn dương, dấu do loại (THU/CHI) quyết định
    val timestamp: Long,   // epoch millis
    val note: String = ""
)

data class RangeRow(val timestamp: Long, val type: String, val amount: Long)
data class CategorySum(val categoryId: Long, val name: String, val type: String, val total: Long, val cnt: Int)
data class TxRow(val name: String, val type: String, val amount: Long, val timestamp: Long, val note: String)

@Dao
interface AppDao {
    @Query("SELECT * FROM categories WHERE archived = 0 AND type = :type ORDER BY sortOrder, id")
    fun categories(type: String): Flow<List<Category>>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM categories")
    suspend fun nextOrder(): Int

    @Insert suspend fun insertCategory(c: Category): Long

    @Query("UPDATE categories SET archived = 1 WHERE id = :id")
    suspend fun archiveCategory(id: Long)

    @Insert suspend fun insertTx(t: FinTransaction)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun txCount(): Int

    @Query(
        "SELECT t.timestamp AS timestamp, c.type AS type, t.amount AS amount " +
        "FROM transactions t JOIN categories c ON c.id = t.categoryId " +
        "WHERE t.timestamp BETWEEN :from AND :to"
    )
    suspend fun rowsInRange(from: Long, to: Long): List<RangeRow>

    @Query(
        "SELECT c.id AS categoryId, c.name AS name, c.type AS type, SUM(t.amount) AS total, COUNT(*) AS cnt " +
        "FROM transactions t JOIN categories c ON c.id = t.categoryId " +
        "WHERE t.timestamp BETWEEN :from AND :to GROUP BY c.id"
    )
    suspend fun categorySums(from: Long, to: Long): List<CategorySum>

    @Query(
        "SELECT c.name AS name, c.type AS type, t.amount AS amount, t.timestamp AS timestamp, t.note AS note " +
        "FROM transactions t JOIN categories c ON c.id = t.categoryId " +
        "WHERE t.timestamp BETWEEN :from AND :to AND (:type = 'ALL' OR c.type = :type) " +
        "ORDER BY c.type, c.name, t.timestamp"
    )
    suspend fun backupRows(from: Long, to: Long, type: String): List<TxRow>
}

@Database(entities = [Category::class, FinTransaction::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: build(ctx.applicationContext).also { inst = it }
        }

        private fun build(ctx: Context) =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "taichinh.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Các mục chi tiêu mặc định theo bản vẽ
                        listOf("Giáo dục", "Nhiên liệu xe", "Bảo dưỡng xe", "Ăn vặt", "Bữa ăn chính", "Nhu yếu phẩm")
                            .forEachIndexed { i, n ->
                                db.execSQL(
                                    "INSERT INTO categories(name, type, sortOrder, archived) VALUES(?, 'CHI', ?, 0)",
                                    arrayOf<Any>(n, i)
                                )
                            }
                    }
                })
                .build()
    }
}
