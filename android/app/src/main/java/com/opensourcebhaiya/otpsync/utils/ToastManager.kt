package com.opensourcebhaiya.otpsync.utils

import android.content.Context
import android.widget.Toast

object ToastManager {
    private var currentToast: Toast? = null

    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        // Cancel the current toast if it exists to prevent queueing
        currentToast?.cancel()
        
        // Create and show the new toast immediately
        val newToast = Toast.makeText(context.applicationContext, message, duration)
        newToast.show()
        currentToast = newToast
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
