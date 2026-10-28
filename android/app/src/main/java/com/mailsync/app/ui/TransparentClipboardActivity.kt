package com.mailsync.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

class TransparentClipboardActivity : Activity() {

    private var otpCode: String? = null
    private var isDuplicate = false
    private var hasCopied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // Ensure activity can wake up and copy even when locked/screen off
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        otpCode = intent.getStringExtra("EXTRA_OTP_CODE")
        isDuplicate = intent.getBooleanExtra("EXTRA_IS_DUPLICATE", false)

        if (savedInstanceState != null) {
            hasCopied = savedInstanceState.getBoolean("hasCopied", false)
            isDuplicate = savedInstanceState.getBoolean("isDuplicate", false)
        }
        
        if (hasCopied) {
            safeFinish()
        }
    }

    override fun onResume() {
        super.onResume()
        executeCopy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("hasCopied", hasCopied)
        outState.putBoolean("isDuplicate", isDuplicate)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            executeCopy()
        }
    }

    private fun executeCopy() {
        if (hasCopied) return
        val code = otpCode ?: return
        hasCopied = true

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val currentClip = try {
                clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            } catch (e: Exception) { null }

            val alreadyOnClipboard = (currentClip == code)

            // Only invoke setPrimaryClip if the code isn't already on the clipboard,
            // preventing Android 13+ OS from displaying a secondary 'Copied' overlay over our toast
            if (!alreadyOnClipboard) {
                val clip = ClipData.newPlainText("OTP", code)
                clipboard.setPrimaryClip(clip)
            }
            Log.d("TransparentClipboard", "Copied OTP status: $code (isDuplicate: $isDuplicate, alreadyOnClipboard: $alreadyOnClipboard)")

            if (isDuplicate) {
                com.mailsync.app.utils.ToastManager.show(this, "Same OTP already copied: $code", android.widget.Toast.LENGTH_SHORT)
            } else if (!alreadyOnClipboard && Build.VERSION.SDK_INT <= 32) {
                com.mailsync.app.utils.ToastManager.show(this, "MailSync: OTP Copied ✔️", android.widget.Toast.LENGTH_SHORT)
            }

            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            Log.e("TransparentClipboard", "Failed to copy to clipboard", e)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            safeFinish()
        }, 200)
    }

    private fun safeFinish() {
        if (!isFinishing) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
