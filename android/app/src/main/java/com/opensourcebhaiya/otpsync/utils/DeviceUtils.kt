package com.opensourcebhaiya.otpsync.utils

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

object DeviceUtils {
    /**
     * Checks if the phone is actively in use by the user (screen ON and device UNLOCKED).
     * When true, an incoming OTP is likely intended for the mobile app currently in use,
     * rather than an external PC (unless an explicit PC domain/intent matches).
     */
    fun isPhoneCurrentlyInUse(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isScreenOn = powerManager?.isInteractive ?: false
            val isLocked = keyguardManager?.isKeyguardLocked ?: false
            isScreenOn && !isLocked
        } catch (_: Exception) {
            false
        }
    }
}
