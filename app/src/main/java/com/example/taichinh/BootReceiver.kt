package com.example.taichinh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Khởi động lại dịch vụ hiển thị sau khi máy khởi động lại (nếu người dùng đang bật). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && SummarySettings.isEnabled(context)) {
            SummaryService.start(context)
        }
    }
}
