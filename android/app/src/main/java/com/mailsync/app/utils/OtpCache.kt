package com.mailsync.app.utils

data class CachedOtpRecord(
    val code: String,
    val sender: String,
    val source: String,
    val timestamp: Long
)

object OtpCache {
    private val recentOtps = mutableListOf<CachedOtpRecord>()
    private const val DEDUP_WINDOW_MS = 120_000L // 2-minute debounce window for matching brands/senders
    private const val STRICT_IDENTICAL_WINDOW_MS = 60_000L // 60 seconds strict window for exact same OTP code

    @Synchronized
    fun isDuplicate(code: String, sender: String, source: String): Boolean {
        val sanitizedCode = code.replace("\\s".toRegex(), "")
        val now = System.currentTimeMillis()

        // Purge expired records older than 3 minutes
        recentOtps.removeAll { now - it.timestamp > 180_000L }

        val senderTokens = extractSignificantTokens(sender)

        for (item in recentOtps) {
            if (item.code == sanitizedCode) {
                val elapsed = now - item.timestamp
                
                // 1. Exact same OTP code within 60 seconds is always a duplicate (e.g., dual SMS + Email login dispatch)
                if (elapsed <= STRICT_IDENTICAL_WINDOW_MS) {
                    return true
                }
                
                // 2. Same OTP code within 2 minutes with matching brand/company/sender
                if (elapsed <= DEDUP_WINDOW_MS) {
                    val existingTokens = extractSignificantTokens(item.sender)
                    val sharesBrand = senderTokens.any { it in existingTokens }
                    if (sharesBrand || item.sender.equals(sender, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }

        // New fresh OTP — record it
        recentOtps.add(CachedOtpRecord(sanitizedCode, sender, source, now))
        return false
    }

    @Synchronized
    fun shouldCopy(code: String): Boolean {
        return !isDuplicate(code, "", "")
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
