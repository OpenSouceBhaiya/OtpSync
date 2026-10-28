package com.mailsync.app.utils

data class CachedOtpRecord(
    val code: String,
    val sender: String,
    val source: String,
    var timestamp: Long
)

enum class OtpArrivalStatus {
    NEW,                   // Brand new OTP
    SIMULTANEOUS_BURST,    // Received within <= 45s (concurrent dual dispatch e.g. SMS + Gmail notification)
    SAME_SMS_DUPLICATE,    // Notification from SMS app for SMS already handled by SmsReceiver within 30s
    RESENT_SAME_CODE       // Exact same code resent (e.g. user requested resend)
}

object OtpCache {
    private val recentOtps = mutableListOf<CachedOtpRecord>()
    private const val DEDUP_WINDOW_MS = 120_000L // 2-minute debounce window for matching brands/senders
    private const val STRICT_IDENTICAL_WINDOW_MS = 120_000L // 2-minute window for exact same OTP code
    private const val SIMULTANEOUS_BURST_MS = 45_000L // 45s window: captures SMS-to-Notification delays, dual SMS+Email, and notification updates without re-copying

    @Synchronized
    fun recordSmsArrival(code: String, sender: String): OtpArrivalStatus {
        val sanitized = code.replace("\\s".toRegex(), "")
        if (sanitized.isBlank()) return OtpArrivalStatus.NEW
        val now = System.currentTimeMillis()
        recentOtps.removeAll { now - it.timestamp > 180_000L }

        val existing = recentOtps.find { it.code == sanitized }
        return if (existing == null) {
            recentOtps.add(CachedOtpRecord(sanitized, sender, "SMS", now))
            OtpArrivalStatus.NEW
        } else {
            existing.timestamp = now
            OtpArrivalStatus.RESENT_SAME_CODE
        }
    }

    @Synchronized
    fun checkNotificationArrival(code: String, sender: String, sourcePackage: String, accountName: String): OtpArrivalStatus {
        val sanitized = code.replace("\\s".toRegex(), "")
        if (sanitized.isBlank()) return OtpArrivalStatus.NEW
        val now = System.currentTimeMillis()
        recentOtps.removeAll { now - it.timestamp > 180_000L }

        val isSmsApp = sourcePackage.contains("messaging") || sourcePackage.contains("mms") || sourcePackage.contains(".sms")
        val existing = recentOtps.find { it.code == sanitized }

        if (existing == null) {
            recentOtps.add(CachedOtpRecord(sanitized, sender, accountName, now))
            return OtpArrivalStatus.NEW
        }

        val elapsed = now - existing.timestamp

        // 1. If this is an SMS notification (e.g. Google Messages) and we already handled this OTP via SMS within 30s:
        if (isSmsApp && existing.source.equals("SMS", ignoreCase = true) && elapsed < 30_000L) {
            return OtpArrivalStatus.SAME_SMS_DUPLICATE
        }

        // 2. Dual channel arrival (e.g. Gmail notification for OTP already received on SMS) within 45s:
        if (elapsed < SIMULTANEOUS_BURST_MS) {
            existing.timestamp = now
            return OtpArrivalStatus.SIMULTANEOUS_BURST
        }

        // 3. Re-sent code (> 45s)
        existing.timestamp = now
        return OtpArrivalStatus.RESENT_SAME_CODE
    }

    @Synchronized
    fun checkStatus(code: String, sender: String, source: String): OtpArrivalStatus {
        val sanitizedCode = code.replace("\\s".toRegex(), "")
        if (sanitizedCode.isBlank()) return OtpArrivalStatus.NEW
        val now = System.currentTimeMillis()

        // Purge expired records older than 3 minutes
        recentOtps.removeAll { now - it.timestamp > 180_000L }

        val senderTokens = extractSignificantTokens(sender)

        for (item in recentOtps) {
            if (item.code == sanitizedCode) {
                val elapsed = now - item.timestamp
                
                // 1. Exact same OTP code within 45 seconds is a concurrent dual arrival / system notification update (e.g. SMS -> Google Messages notification, SMS + Email)
                if (elapsed <= SIMULTANEOUS_BURST_MS) {
                    return OtpArrivalStatus.SIMULTANEOUS_BURST
                }
                
                // 2. Re-sent OTP: exact same code resent after 45s (e.g. user requested resend), or matching brand within 2 minutes
                val isMatchingSender = if (elapsed <= STRICT_IDENTICAL_WINDOW_MS) {
                    true
                } else if (elapsed <= DEDUP_WINDOW_MS) {
                    val existingTokens = extractSignificantTokens(item.sender)
                    val sharesBrand = senderTokens.any { it in existingTokens }
                    sharesBrand || item.sender.equals(sender, ignoreCase = true)
                } else false

                if (isMatchingSender) {
                    item.timestamp = now
                    return OtpArrivalStatus.RESENT_SAME_CODE
                }
            }
        }

        // New fresh OTP — record it
        recentOtps.add(CachedOtpRecord(sanitizedCode, sender, source, now))
        return OtpArrivalStatus.NEW
    }

    @Synchronized
    fun isDuplicate(code: String, sender: String, source: String): Boolean {
        return checkStatus(code, sender, source) != OtpArrivalStatus.NEW
    }

    @Synchronized
    fun shouldCopy(code: String): Boolean {
        return checkStatus(code, "", "") != OtpArrivalStatus.SIMULTANEOUS_BURST
    }

    @Synchronized
    fun clearForTesting() {
        recentOtps.clear()
    }

    private fun extractSignificantTokens(sender: String): Set<String> {
        if (sender.isBlank()) return emptySet()
        // Strip common 2-letter SMS telecom routing prefixes: e.g. "AX-SHEIN", "VM-HDFCBK", "JD-FLPKRT", "BZ-AMAZON"
        var clean = sender.lowercase()
        if (clean.length > 3 && (clean[2] == '-' || clean[2] == '_')) {
            clean = clean.substring(3)
        }
        val ignoreList = setOf("sms", "otp", "code", "mail", "gmail", "service", "noreply", "team", "account", "info", "support")
        return clean.split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in ignoreList }
            .toSet()
    }
}
