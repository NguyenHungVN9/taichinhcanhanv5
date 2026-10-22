package com.example.taichinh

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Dịch vụ chạy thường trực khi người dùng bật "Hiển thị tổng quan":
 *  - Thông báo thường trực (hiện cả trên màn hình khóa khi màn hình đang sáng).
 *  - Bảng nổi trên màn hình chính (chỉ hiện khi đang ở launcher, đã mở khóa).
 */
class SummaryService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var overlay: View? = null
    private var summary: Summary? = null
    private var homes: Set<String> = emptySet()
    private var lastPkg: String? = null
    private var lastEventTime = 0L
    private var lastDataLoad = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        val n = buildNotification(null)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        homes = packageManager.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }.toSet()
        refreshNow()
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        scope.cancel()
        super.onDestroy()
    }

    // ───────────── vòng lặp kiểm tra ─────────────

    private val tick = object : Runnable {
        override fun run() {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isInteractive) {
                pollForeground()
                updateOverlay()
                if (SystemClock.elapsedRealtime() - lastDataLoad > 30_000) refreshNow()
                handler.postDelayed(this, 1500)
            } else {
                hideOverlay()
                handler.postDelayed(this, 5000)
            }
        }
    }

    fun refreshNow() {
        lastDataLoad = SystemClock.elapsedRealtime()
        scope.launch {
            val s = withContext(Dispatchers.IO) { SummaryLoader.load(applicationContext) }
            summary = s
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(s))
            overlay?.let { bind(it, s) }
        }
    }

    /** Ghi nhớ app nào vừa được đưa lên trước (cần quyền "Truy cập sử dụng"). */
    private fun pollForeground() {
        if (!SummarySettings.hasUsageAccess(this)) return
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val from = if (lastEventTime == 0L) now - 30 * 60_000 else lastEventTime + 1
        val events = usm.queryEvents(from, now) ?: return
        val e = UsageEvents.Event()
        val fg = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED
        else UsageEvents.Event.MOVE_TO_FOREGROUND
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == fg) {
                lastPkg = e.packageName
                lastEventTime = e.timeStamp
            }
        }
    }

    private fun updateOverlay() {
        val km = getSystemService(KeyguardManager::class.java)
        val onHome = !km.isKeyguardLocked && lastPkg != null && lastPkg in homes
        if (onHome && Settings.canDrawOverlays(this)) showOverlay() else hideOverlay()
    }

    // ───────────── bảng nổi ─────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bind(v: View, s: Summary) {
        v.findViewById<TextView>(R.id.tv_balance).apply { text = s.balanceText; setTextColor(s.balanceColor) }
        v.findViewById<TextView>(R.id.tv_thu).text = s.thuText
        v.findViewById<TextView>(R.id.tv_chi).text = s.chiText
        v.findViewById<TextView>(R.id.tv_top).text = s.topText
    }

    private fun showOverlay() {
        if (overlay != null) return
        val v = LayoutInflater.from(this).inflate(R.layout.widget_summary, null)
        summary?.let { s -> bind(v, s) }
        val lp = WindowManager.LayoutParams(
            dp(210), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = SummarySettings.overlayX(this, dp(16))
        lp.y = SummarySettings.overlayY(this, dp(90))

        // Chạm = mở app, kéo = di chuyển bảng (vị trí được nhớ lại)
        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 12 || abs(dy) > 12) moved = true
                        if (moved) {
                            lp.x = sx + dx; lp.y = sy + dy
                            try { wm?.updateViewLayout(view, lp) } catch (_: Exception) {}
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) SummarySettings.saveOverlayPos(this@SummaryService, lp.x, lp.y)
                        else startActivity(
                            Intent(this@SummaryService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                return true
            }
        })
        try {
            wm?.addView(v, lp)
            overlay = v
        } catch (_: Exception) {
            overlay = null
        }
    }

    private fun hideOverlay() {
        overlay?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        overlay = null
    }

    // ───────────── thông báo thường trực ─────────────

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "Tổng quan tài chính", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Hiển thị số dư thu chi và mục chi nhiều nhất"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(s: Summary?): Notification {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)
        if (s == null) {
            b.setContentTitle("Đang tải số liệu…")
        } else {
            b.setContentTitle(s.balanceText)
                .setContentText("${s.thuText} · ${s.chiText} · ${s.topText}")
                .setStyle(Notification.BigTextStyle().bigText("${s.thuText} · ${s.chiText}\n${s.topText}"))
        }
        return b.build()
    }

    companion object {
        private const val CHANNEL = "summary"
        private const val NOTIF_ID = 1001
        @Volatile private var instance: SummaryService? = null

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, SummaryService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, SummaryService::class.java))
        }

        /** Gọi sau khi dữ liệu thay đổi. */
        fun refresh() {
            instance?.refreshNow()
        }
    }
}
