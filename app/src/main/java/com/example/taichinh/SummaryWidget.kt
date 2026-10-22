package com.example.taichinh

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Widget 2x1 ô (~1/16 màn hình): số dư thu chi tháng này + mục chi nhiều lần nhất. */
class SummaryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = build(context)
                appWidgetIds.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Gọi sau mỗi lần thêm giao dịch để widget cập nhật ngay. */
        fun refreshAll(context: Context) {
            val app = context.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, SummaryWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val views = build(app)
                ids.forEach { mgr.updateAppWidget(it, views) }
            }
        }

        private suspend fun build(ctx: Context): RemoteViews {
            val s = SummaryLoader.load(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_summary)
            v.setTextViewText(R.id.tv_balance, s.balanceText)
            v.setTextColor(R.id.tv_balance, s.balanceColor)
            v.setTextViewText(R.id.tv_thu, s.thuText)
            v.setTextViewText(R.id.tv_chi, s.chiText)
            v.setTextViewText(R.id.tv_top, s.topText)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}
