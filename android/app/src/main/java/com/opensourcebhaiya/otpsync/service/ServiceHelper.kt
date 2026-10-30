package com.opensourcebhaiya.otpsync.service

import android.content.Context
import android.content.Intent
import android.os.Build

object ServiceHelper {
    fun startForegroundService(context: Context) {
        // Rebind NotificationListenerService in case Android OS disconnected it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val componentName = android.content.ComponentName(context, OtpNotificationListenerService::class.java)
                android.service.notification.NotificationListenerService.requestRebind(componentName)
            } catch (e: Exception) {
                android.util.Log.w("ServiceHelper", "requestRebind failed: ${e.message}")
            }
        }
    }

    fun stopForegroundService(context: Context) {
        // No-op.
    }
}
