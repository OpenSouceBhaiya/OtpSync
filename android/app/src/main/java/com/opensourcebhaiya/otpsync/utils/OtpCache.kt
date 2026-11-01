package com.opensourcebhaiya.otpsync.utils

data class CachedOtpRecord(
    val code: String,
    val sender: String,
    val sources: MutableSet<String>,
    var initialTimestamp: Long,
    var lastTimestamp: Long
)

enum class OtpArrivalStatus {
    NEW,                   // Brand new OTP
    SIMULTANEOUS_BURST,    // Received from an additional source/channel within retention window
    SAME_SMS_DUPLICATE,    // Duplicate notification from an already-handled channel (suppress)
    RESENT_SAME_CODE       // Exact same code re-sent after cooldown (> 45s) from same channel
}

object OtpCache {
    private val recentOtps = mutableListOf<CachedOtpRecord>()
    private const val MAX_RETENTION_MS = 600_000L // 10-minute maximum retention window. After 10 mins, resets to fresh normal OTP
    private const val SAME_CHANNEL_DEBOUNCE_MS = 45_000L // 45s debounce window for identical channel re-triggers

    fun resolveChannel(sourcePackage: String, accountName: String): String {
        val pkg = sourcePackage.lowercase()
        return when {
            pkg.contains("truecaller") -> "Truecaller"
            pkg.contains("whatsapp") -> "WhatsApp"
            pkg.contains("telegram") -> "Telegram"
            pkg.contains("com.google.android.gm") || accountName.contains("gmail", ignoreCase = true) -> "Gmail"
            pkg.contains("messaging") || pkg.contains("mms") || pkg.contains(".sms") || pkg.contains("telephony") -> "SMS"
            pkg.contains("outlook") -> "Outlook"
            accountName.isNotBlank() && !accountName.equals("SMS", ignoreCase = true) -> accountName
            else -> "SMS"
        }
    }

    @Synchronized
    fun recordSmsArrival(code: String, sender: String): OtpArrivalStatus {
        val sanitized = code.replace("\\s".toRegex(), "")
        if (sanitized.isBlank()) return OtpArrivalStatus.NEW
        val now = System.currentTimeMillis()
        recentOtps.removeAll { now - it.lastTimestamp > MAX_RETENTION_MS }

        val existing = recentOtps.find { it.code == sanitized }
        return if (existing == null) {
            recentOtps.add(CachedOtpRecord(
                code = sanitized,
                sender = sender,
                sources = mutableSetOf("SMS"),
                initialTimestamp = now,
                lastTimestamp = now
            ))
            OtpArrivalStatus.NEW
        } else {
            if ("SMS" !in existing.sources) {
                existing.sources.add("SMS")
                existing.lastTimestamp = now
                OtpArrivalStatus.SIMULTANEOUS_BURST
            } else {
                existing.lastTimestamp = now
                OtpArrivalStatus.RESENT_SAME_CODE
            }
        }
    }

    @Synchronized
    fun checkNotificationArrival(code: String, sender: String, sourcePackage: String, accountName: String): OtpArrivalStatus {
        val sanitized = code.replace("\\s".toRegex(), "")
        if (sanitized.isBlank()) return OtpArrivalStatus.NEW
        val now = System.currentTimeMillis()
        recentOtps.removeAll { now - it.lastTimestamp > MAX_RETENTION_MS }

        val channel = resolveChannel(sourcePackage, accountName)
        val existing = recentOtps.find { it.code == sanitized }

        if (existing == null) {
            recentOtps.add(CachedOtpRecord(
                code = sanitized,
                sender = sender,
                sources = mutableSetOf(channel),
                initialTimestamp = now,
                lastTimestamp = now
            ))
            return OtpArrivalStatus.NEW
        }

        val elapsed = now - existing.lastTimestamp

        // 1. If this channel is already in the sources list for this OTP:
        if (channel in existing.sources) {
            return if (elapsed < SAME_CHANNEL_DEBOUNCE_MS) {
                OtpArrivalStatus.SAME_SMS_DUPLICATE
            } else {
                existing.lastTimestamp = now
                OtpArrivalStatus.RESENT_SAME_CODE
            }
        }

        // 2. Additional source channel arrived with the same OTP (e.g. came on SMS, now arrives on Truecaller or Gmail)
        existing.sources.add(channel)
        existing.lastTimestamp = now
        return OtpArrivalStatus.SIMULTANEOUS_BURST
    }

    @Synchronized
    fun getSourcesForCode(code: String): Set<String> {
        val sanitized = code.replace("\\s".toRegex(), "")
        return recentOtps.find { it.code == sanitized }?.sources?.toSet() ?: emptySet()
    }

    @Synchronized
    fun checkStatus(code: String, sender: String, source: String): OtpArrivalStatus {
        val sanitizedCode = code.replace("\\s".toRegex(), "")
        if (sanitizedCode.isBlank()) return OtpArrivalStatus.NEW
        val now = System.currentTimeMillis()

        recentOtps.removeAll { now - it.lastTimestamp > MAX_RETENTION_MS }

        val existing = recentOtps.find { it.code == sanitizedCode }
        if (existing != null) {
            val elapsed = now - existing.lastTimestamp
            return if (elapsed <= SAME_CHANNEL_DEBOUNCE_MS) {
                OtpArrivalStatus.SIMULTANEOUS_BURST
            } else {
                existing.lastTimestamp = now
                OtpArrivalStatus.RESENT_SAME_CODE
            }
        }

        recentOtps.add(CachedOtpRecord(
            code = sanitizedCode,
            sender = sender,
            sources = mutableSetOf(source.takeIf { it.isNotBlank() } ?: "SMS"),
            initialTimestamp = now,
            lastTimestamp = now
        ))
        return OtpArrivalStatus.NEW
    }

    @Synchronized
    fun isDuplicate(code: String, sender: String, source: String): Boolean {
        return checkStatus(code, sender, source) != OtpArrivalStatus.NEW
    }

    @Synchronized
    fun shouldCopy(code: String): Boolean {
        val sanitized = code.replace("\\s".toRegex(), "")
        val existing = recentOtps.find { it.code == sanitized } ?: return true
        val now = System.currentTimeMillis()
        // Do not re-copy if already copied within the 10-minute window unless explicitly resent after cooldown
        return (now - existing.initialTimestamp) > MAX_RETENTION_MS
    }

    @Synchronized
    fun clearForTesting() {
        recentOtps.clear()
    }
}
