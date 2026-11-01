package com.opensourcebhaiya.otpsync.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ToastManager {
    private var currentToast: Toast? = null
    private var lastMessage: String = ""
    private var lastToastTime: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val now = System.currentTimeMillis()
        if (message == lastMessage && (now - lastToastTime) < 2000L) {
            return // Debounce exact duplicate messages within 2 seconds
        }
        lastMessage = message
        lastToastTime = now

        mainHandler.post {
            try {
                // Cancel current toast to avoid delayed stacking/queueing
                currentToast?.cancel()
                val newToast = Toast.makeText(context.applicationContext, message, duration)
                newToast.show()
                currentToast = newToast
            } catch (_: Exception) {}
        }
    }

    /**
     * Call when copying content to clipboard. On Android 13+ (API 33+), the OS
     * automatically displays a native clipboard popup, so showing a toast creates
     * duplicate notifications. On older versions (< API 33), a clean toast is shown.
     */
    fun showCopyToast(context: Context, message: String = "Copied") {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            show(context, message, Toast.LENGTH_SHORT)
        }
    }
}
