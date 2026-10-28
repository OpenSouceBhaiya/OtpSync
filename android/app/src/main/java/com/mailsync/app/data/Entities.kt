package com.mailsync.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "otps")
data class OtpEntity(
    @PrimaryKey val id: String,
    val code: String,
    val sender: String,
    val subject: String,
    val account: String,
    var receivedAt: Long,
    val expiresAt: Long? = null,
    val isUsed: Boolean = false,
    val sourcePackage: String? = null,  // e.g. "com.whatsapp", "com.android.mms", "com.google.android.gm"
    var sentToDevices: String? = null
)

fun formatCompactSource(account: String): String {
    val tagRegex = Regex("(?i)(\\[|\\()\\s*(backend|instant)\\s*(\\]|\\))")
    val clean = account.replace(tagRegex, "").trim()
    if (clean.isBlank()) return ""

    if (clean.contains("+")) {
        val parts = clean.split("+").map { it.trim() }
        val shortParts = parts.map { part ->
            when {
                part.contains("gmail", ignoreCase = true) -> "Gmail"
                part.contains("sms", ignoreCase = true) -> "SMS"
                part.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                part.contains("telegram", ignoreCase = true) -> "Telegram"
                part.contains("@") -> "Gmail"
                else -> part.take(12)
            }
        }.distinct()
        return shortParts.joinToString(" + ")
    }

    return when {
        clean.contains("gmail", ignoreCase = true) || clean.contains("@") -> "Gmail"
        clean.contains("sms", ignoreCase = true) -> "SMS"
        clean.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
        clean.contains("telegram", ignoreCase = true) -> "Telegram"
        else -> clean.take(16)
    }
}


