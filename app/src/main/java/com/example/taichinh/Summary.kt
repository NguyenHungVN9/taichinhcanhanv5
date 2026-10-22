package com.example.taichinh

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import com.example.taichinh.data.AppDatabase
import com.example.taichinh.data.CategorySum
import com.example.taichinh.ui.thisMonth
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/** Dữ liệu tóm tắt tháng hiện tại, dùng chung cho widget, thông báo và bảng nổi. */
data class Summary(val thu: Long, val chi: Long, val topName: String?, val topCount: Int) {
    val balance get() = thu - chi
    val balanceText get() = "Dư T${LocalDate.now().monthValue}: ${if (balance >= 0) "+" else ""}${short(balance)}"
    val thuText get() = "Thu ${short(thu)}"
    val chiText get() = "Chi ${short(chi)}"
    val topText get() = if (topName == null) "Chưa có khoản chi" else "Hay chi: $topName ×$topCount"
    val balanceColor get() = if (balance >= 0) 0xFF81C784.toInt() else 0xFFE57373.toInt()
}

object SummaryLoader {
    suspend fun load(ctx: Context): Summary {
        val range = thisMonth()
        val sums = AppDatabase.get(ctx).dao().categorySums(range.fromMillis(), range.toMillis())
        val thu = sums.filter { it.type == "THU" }.sumOf { it.total }
        val chi = sums.filter { it.type == "CHI" }.sumOf { it.total }
        val top = sums.filter { it.type == "CHI" }
            .maxWithOrNull(compareBy<CategorySum>({ it.cnt }, { it.total }))
        return Summary(thu, chi, top?.name, top?.cnt ?: 0)
    }
}

private fun one(d: Double) = "%.1f".format(Locale("vi", "VN"), d).removeSuffix(",0")

/** 1.250.000 → 1,3tr ; 45.000 → 45k */
fun short(v: Long): String {
    val a = abs(v)
    val s = when {
        a >= 1_000_000_000L -> one(a / 1e9) + "tỷ"
        a >= 1_000_000L -> one(a / 1e6) + "tr"
        a >= 1_000L -> one(a / 1e3) + "k"
        else -> a.toString()
    }
    return if (v < 0) "-$s" else s
}

object SummarySettings {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context) = p(ctx).getBoolean("show_summary", false)
    fun setEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("show_summary", v).apply()

    fun overlayX(ctx: Context, def: Int) = p(ctx).getInt("ox", def)
    fun overlayY(ctx: Context, def: Int) = p(ctx).getInt("oy", def)
    fun saveOverlayPos(ctx: Context, x: Int, y: Int) = p(ctx).edit().putInt("ox", x).putInt("oy", y).apply()

    @Suppress("DEPRECATION")
    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
