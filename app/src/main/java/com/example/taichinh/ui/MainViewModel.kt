package com.example.taichinh.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taichinh.SummaryService
import com.example.taichinh.SummaryWidget
import com.example.taichinh.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class BalanceRow(val label: String, val thu: Long, val chi: Long) {
    val balance get() = thu - chi
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()

    val chiCats = dao.categories("CHI").stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val thuCats = dao.categories("THU").stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addCategory(name: String, type: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insertCategory(Category(name = name.trim(), type = type, sortOrder = dao.nextOrder()))
        }
    }

    fun archiveCategory(id: Long) {
        viewModelScope.launch { dao.archiveCategory(id) }
    }

    suspend fun addTransaction(cat: Category, amount: Long, ts: Long, note: String) {
        dao.insertTx(FinTransaction(categoryId = cat.id, amount = amount, timestamp = ts, note = note.trim()))
        SummaryWidget.refreshAll(getApplication())
        SummaryService.refresh()
    }

    /** Báo cáo 1: tổng thu/chi/số dư theo tuần hoặc tháng, mới nhất ở trên. */
    suspend fun balance(range: DateRange, weekly: Boolean): List<BalanceRow> {
        val rows = dao.rowsInRange(range.fromMillis(), range.toMillis())
        val zone = ZoneId.systemDefault()
        val groups = rows.groupBy { r ->
            val d = Instant.ofEpochMilli(r.timestamp).atZone(zone).toLocalDate()
            if (weekly) d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else d.withDayOfMonth(1)
        }
        val f = DateTimeFormatter.ofPattern("dd/MM")
        return groups.entries.sortedByDescending { it.key.toEpochDay() }.map { (start, list) ->
            val label = if (weekly) "${start.format(f)} – ${start.plusDays(6).format(f)}"
            else "Tháng ${start.monthValue}/${start.year}"
            BalanceRow(
                label,
                list.filter { it.type == "THU" }.sumOf { it.amount },
                list.filter { it.type == "CHI" }.sumOf { it.amount }
            )
        }
    }

    /** Dùng cho báo cáo 2 (sắp theo số lần) và báo cáo 3 (sắp theo số tiền). */
    suspend fun categorySums(range: DateRange): List<CategorySum> =
        dao.categorySums(range.fromMillis(), range.toMillis())

    /** Ghi file text 3 cột (phân tách bằng TAB): TÊN HÀNH ĐỘNG / BIẾN ĐỘNG / NGÀY GIỜ. */
    suspend fun writeBackup(uri: Uri, range: DateRange, type: String): Int = withContext(Dispatchers.IO) {
        val rows = dao.backupRows(range.fromMillis(), range.toMillis(), type)
        val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val zone = ZoneId.systemDefault()
        val sb = StringBuilder("\uFEFF") // BOM để Excel/Notepad đọc đúng tiếng Việt
        sb.append("TÊN HÀNH ĐỘNG\tBIẾN ĐỘNG\tNGÀY GIỜ\n")
        rows.forEach { r ->
            val name = (if (r.note.isBlank()) r.name else "${r.name} - ${r.note}")
                .replace('\t', ' ').replace('\n', ' ')
            val delta = (if (r.type == "THU") "+" else "-") + r.amount
            val time = Instant.ofEpochMilli(r.timestamp).atZone(zone).format(fmt)
            sb.append(name).append('\t').append(delta).append('\t').append(time).append('\n')
        }
        val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            ?: error("Không mở được file")
        out.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
        rows.size
    }
}
