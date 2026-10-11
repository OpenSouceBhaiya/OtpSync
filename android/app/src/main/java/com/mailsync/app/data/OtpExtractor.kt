package com.mailsync.app.data

import android.content.Context

data class ExtractionResult(
    val code: String,
    val expiresAt: Long?
)

object OtpExtractor {

    // Genuine trigger phrases that confirm this message actually contains an OTP/verification code
    private val genuineOtpKeywords = listOf(
        "verification code", "one-time code", "one time code", "one-time password", "one time password",
        "security code", "access code", "login code", "sign-in code", "sign in code", 
        "auth code", "authentication code", "confirmation code", "secret code", "passcode",
        "otp is", "aotp is", "otp for", "aotp for", "otp:", "otp -", "aotp:", "otp code", "your otp",
        "use this code", "enter this code", "enter the code", "use the code", "code to sign in",
        "code to log in", "code to verify", "code is", "your code is", "your code:", "pin is",
        "your pin", "secret pin", "temporary password"
    )

    // Secondary keywords that require proximity to verification/auth context
    private val proximityKeywords = listOf(
        "otp", "aotp", "code", "pin", "passcode"
    )

    // Footer boundaries for deprioritization
    private val footerBoundaries = listOf(
        "terms of", "privacy policy", "unsubscribe", "this message was sent to", 
        "the team", "all rights reserved", "©", "copyright", "thanks,", "thank you,", "regards,", "sincerely,"
    )

    private fun safeLog(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            println("$tag: $msg")
        }
    }

    fun extractOtp(subject: String?, bodyText: String?, bodyHtml: String?, receivedTimeMs: Long): ExtractionResult? =
        extractOtp(null, subject, bodyText, bodyHtml, receivedTimeMs)

    fun extractOtp(context: Context?, subject: String?, bodyText: String?, bodyHtml: String?, receivedTimeMs: Long): ExtractionResult? {
        val rawText = if (!bodyText.isNullOrBlank()) bodyText.trim() else stripHtml(bodyHtml ?: "")
        val truncatedBody = rawText.take(50000)

        // Try extracting from subject first
        var result: ExtractionResult? = null
        if (subject != null) {
            result = extractFromText(context, subject, receivedTimeMs)
        }
        
        // If not found in subject, try body
        if (result == null) {
            result = extractFromText(context, truncatedBody, receivedTimeMs)
        }
        
        return result
    }

    private fun stripHtml(html: String): String {
        var text = html.replace(Regex("(?is)<style.*?>.*?</style>"), " ")
        text = text.replace(Regex("(?is)<script.*?>.*?</script>"), " ")
        text = text.replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF]"), "")
        text = text.replace(Regex("(?i)</?(p|div|br|tr|ul|li|h[1-6])[^>]*>"), "\n")
        text = text.replace(Regex("<[^>]*>"), " ")
        text = text.replace(Regex("[ \\t]+"), " ")
        text = text.replace(Regex("\\n\\s*\\n"), "\n\n")
        return text.trim()
    }
    
    private fun normalizeText(text: String): String {
        var normalized = text
        // Replace smart quotes and dashes with standard ASCII
        normalized = normalized.replace("–", "-").replace("—", "-")
        normalized = normalized.replace("“", "\"").replace("”", "\"")
        normalized = normalized.replace("‘", "'").replace("’", "'")

        // Collapse digit sequences separated by spaces or dashes: e.g. "511 570" -> "511570", "482-913" -> "482913"
        var prev = ""
        while (normalized != prev) {
            prev = normalized
            normalized = normalized.replace(Regex("(?<=\\d)[ -]+(?=\\d)"), "")
        }
        
        // Strip invisible unicode characters
        normalized = normalized.replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF]"), "")
        return normalized
    }

    private fun isLikelyYear(candidate: String, text: String, startPos: Int): Boolean {
        if (candidate.length != 4 || !candidate.all { it.isDigit() }) return false
        val yearVal = candidate.toIntOrNull() ?: return false
        if (yearVal !in 1900..2099) return false

        // Check surrounding 40 characters for date/copyright markers
        val windowStart = maxOf(0, startPos - 40)
        val windowEnd = minOf(text.length, startPos + candidate.length + 40)
        val window = text.substring(windowStart, windowEnd).lowercase()

        if (window.contains("©") || window.contains("copyright") || window.contains("all rights") ||
            window.contains("since") || window.contains("year") || window.contains("valid till") ||
            window.contains("expiry") || window.contains("expires")) {
            return true
        }

        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        if (months.any { window.contains(it) }) {
            return true
        }

        // Preceded or followed by / or - (e.g. 15/10/2026 or 2026-10-15)
        if (startPos > 0 && (text[startPos - 1] == '/' || text[startPos - 1] == '-')) return true
        val afterPos = startPos + candidate.length
        if (afterPos < text.length && (text[afterPos] == '/' || text[afterPos] == '-')) return true

        // If it's a current or recent year (2015..2035) and NOT preceded directly by explicit "otp is" or "code is":
        if (yearVal in 2015..2035) {
            val prefix15 = text.substring(maxOf(0, startPos - 15), startPos).lowercase()
            val hasExplicitAssignment = prefix15.contains("otp is") || prefix15.contains("otp:") || 
                                       prefix15.contains("code is") || prefix15.contains("code:") ||
                                       prefix15.contains("pin is") || prefix15.contains("pin:")
            if (!hasExplicitAssignment) return true
        }

        return false
    }

    private fun isMaskedCardOrAccount(text: String, startPos: Int): Boolean {
        val prefix = text.substring(maxOf(0, startPos - 30), startPos).lowercase()
        // Matches "ending 4821", "ending in 4821", "card ending 4821", "card ending with 4821", "a/c ending 4821", "**4821", "xx4821"
        return Regex("(?:ending(?:\\s+in|\\s+with)?|card|a/c|ac|account)\\s*[:#]?\\s*[*xX]*\\s*$").containsMatchIn(prefix) ||
               Regex("[*xX]{2,}\\s*$").containsMatchIn(prefix)
    }

    private fun isAmountOrCurrency(text: String, startPos: Int, length: Int): Boolean {
        if (startPos > 0) {
            val prevChar = text[startPos - 1]
            if (prevChar == '₹' || prevChar == '$' || prevChar == '€' || prevChar == '£' || prevChar == '.') return true
        }
        val prefix = text.substring(maxOf(0, startPos - 15), startPos).lowercase()
        if (Regex("(?:rs\\.?|inr|usd|eur|gbp|amount|amt)\\s*[:=]?\\s*$").containsMatchIn(prefix)) return true

        val afterPos = startPos + length
        if (afterPos < text.length) {
            val suffix = text.substring(afterPos, minOf(text.length, afterPos + 10)).lowercase()
            if (suffix.startsWith(".00") || suffix.startsWith("/-") || Regex("^\\s*(?:rs|inr|usd|eur)").containsMatchIn(suffix)) return true
        }
        return false
    }

    private fun isIdentifierOrReference(text: String, startPos: Int): Boolean {
        val prefix = text.substring(maxOf(0, startPos - 25), startPos).lowercase()
        return Regex("(?:ref(?:erence)?|order|txn|ticket|case|awb|tracking|invoice|statement)\\s*(?:no\\.?|id|#|:|-|\\s)\\s*$").containsMatchIn(prefix)
    }

    private fun isPincode(text: String, startPos: Int, candidate: String): Boolean {
        if (candidate.length != 6 || !candidate.all { it.isDigit() }) return false
        val prefix = text.substring(maxOf(0, startPos - 25), startPos).lowercase()
        return Regex("(?:pin(?:\\s*code)?|postal(?:\\s*code)?|zip(?:\\s*code)?)\\s*[:=]?\\s*$").containsMatchIn(prefix)
    }

    private fun isPhoneNumber(text: String, startPos: Int, candidate: String): Boolean {
        val window = text.substring(maxOf(0, startPos - 6), minOf(text.length, startPos + candidate.length + 6))
        val consecutiveDigits = window.count { it.isDigit() }
        return consecutiveDigits >= 10
    }

    private fun isValidCandidate(candidate: String, text: String, startPos: Int): Boolean {
        val letterCount = candidate.count { it.isLetter() }
        val digitCount = candidate.count { it.isDigit() }

        // Pure letters: MUST be uppercase, >= 4 chars, and NOT a common English dictionary word
        if (digitCount == 0) {
            if (!candidate.all { it.isUpperCase() } || candidate.length < 4) return false
            val commonWords = setOf(
                "WITH", "FROM", "YOUR", "THIS", "THAT", "HAVE", "WILL", "CALL", "CODE",
                "USER", "HERE", "ONCE", "SIGN", "TRUE", "FREE", "MORE", "DATE", "TIME",
                "NAME", "TEAM", "HELP", "SEND", "DEAR", "STOP", "VALID", "SHARE", "ENTER",
                "ACCESS", "PLEASE", "VERIFY", "LOGIN", "ACCOUNT", "NUMBER", "SECRET"
            )
            if (commonWords.contains(candidate.uppercase())) return false
        }

        // Mixed letters: Require at least 2 digits to prevent tracking strings
        if (letterCount > 0 && digitCount < 2) return false

        if (isLikelyYear(candidate, text, startPos)) return false
        if (isMaskedCardOrAccount(text, startPos)) return false
        if (isAmountOrCurrency(text, startPos, candidate.length)) return false
        if (isIdentifierOrReference(text, startPos)) return false
        if (isPincode(text, startPos, candidate)) return false
        if (isPhoneNumber(text, startPos, candidate)) return false
        return true
    }

    private fun isAfterFooter(text: String, position: Int): Boolean {
        val beforeText = text.substring(0, position).lowercase()
        if (beforeText.contains("----") || beforeText.contains("____")) return true
        return footerBoundaries.any { beforeText.contains(it) }
    }

    private fun extractFromText(context: Context?, text: String, receivedTimeMs: Long): ExtractionResult? {
        val normalized = normalizeText(text)
        
        com.mailsync.app.utils.FileLogger.log(context, "[OtpExtractor] --- EVALUATING TEXT ---")
        com.mailsync.app.utils.FileLogger.log(context, "[OtpExtractor] Normalized Text: $normalized")

        // ══════════════════════════════════════════════════════════════════════════════
        // PHASE 1: HIGH-CONFIDENCE ANCHOR MATCHING (Instant, 100% precision)
        // ══════════════════════════════════════════════════════════════════════════════
        val anchorPatterns = listOf(
            // "Your OTP is 482913", "verification code: 482913", "auth code: A3F92K", "pin is 8492"
            Regex("(?i)\\b(?:one[- ]time\\s+(?:password|code)|verification\\s+code|security\\s+code|login\\s+code|sign[- ]in\\s+code|access\\s+code|auth(?:entication)?\\s+code|confirmation\\s+code|secret\\s+code|passcode|a?otp|pin(?:\\s+code)?)\\s*(?:is|:|–|-|=|#)\\s*([0-9]{4,8}|[A-Z0-9]{4,8})\\b"),

            // "OTP for transaction of ... is 839201", "OTP for login ... is 591823"
            Regex("(?i)\\b(?:a?otp|code|pin|password)\\s+(?:for|to)\\s+[^.\n\r]{1,100}?\\b(?:is|:|=)\\s*([0-9]{4,8})\\b"),

            // "OTP 482913", "Code 482913" (space separator strictly for pure digits)
            Regex("(?i)\\b(?:one[- ]time\\s+(?:password|code)|verification\\s+code|security\\s+code|login\\s+code|sign[- ]in\\s+code|access\\s+code|auth(?:entication)?\\s+code|confirmation\\s+code|secret\\s+code|passcode|a?otp|pin(?:\\s+code)?)\\s+([0-9]{4,8})\\b"),

            // "Code: 482913", "Code is 482913"
            Regex("(?i)\\b(?:code|pin)\\s*(?:is|:|–|-|=|#)\\s*([0-9]{4,8}|[A-Z0-9]{4,8})\\b"),
            
            // "482913 is your OTP", "482913 is your verification code", "4829 is your Uber code"
            Regex("(?i)\\b([0-9]{4,8}|[A-Z0-9]{4,8})\\s+(?:is\\s+(?:your\\s+)?(?:one[- ]time\\s+(?:password|code)|verification\\s+code|security\\s+code|login\\s+code|sign[- ]in\\s+code|confirmation\\s+code|passcode|a?otp|secret\\s+code|(?:[a-zA-Z0-9]+\\s+)?code|pin))\\b"),
            
            // "Use 482913 to verify", "Use 482913 as your code", "Enter 482913 to complete"
            Regex("(?i)\\b(?:use|enter|input)\\s+([0-9]{4,8}|[A-Z0-9]{4,8})\\s+(?:(?:it\\s+)?(?:to|for|on|as)\\s+(?:verify|sign\\s*in|log\\s*in|complete|authenticate|your\\s+(?:otp|code|pin)))\\b"),
            
            // Google format: "G-123456" or "G - 123456"
            Regex("(?i)\\b[Gg]\\s*-\\s*([0-9]{6})\\b"),
            
            // "OTP: 123456", "PIN: 1234", "OTP - 123456"
            Regex("(?i)\\b(?:a?otp|pin)\\s*[:#-–]\\s*([0-9]{4,8})\\b")
        )

        for (pattern in anchorPatterns) {
            val match = pattern.find(normalized)
            if (match != null) {
                val candidate = match.groupValues[1]
                val startPos = match.groups[1]?.range?.first ?: match.range.first
                if (isValidCandidate(candidate, normalized, startPos)) {
                    com.mailsync.app.utils.FileLogger.log(context, "[OtpExtractor] Phase 1 Anchor Matched: $candidate")
                    safeLog("OtpExtractor", "Phase 1 Anchor Matched: $candidate")
                    return ExtractionResult(candidate, extractExpiry(normalized, receivedTimeMs))
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════════════
        // PHASE 2: CONTEXTUAL SCORED EVALUATION (For multi-line, formatted emails)
        // ══════════════════════════════════════════════════════════════════════════════
        // Strict gate: If the message contains ZERO genuine OTP keywords, return null immediately.
        // This stops non-OTP marketing emails (like Amazon, Flipkart, WhatsApp newsletters) from false-extracting!
        val hasGenuineKeyword = genuineOtpKeywords.any { 
            Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) 
        }

        val hasContextualProximity = proximityKeywords.any { kw ->
            val match = Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).find(normalized)
            if (match != null) {
                val kwPos = match.range.first
                val contextWindow = normalized.substring(maxOf(0, kwPos - 50), minOf(normalized.length, kwPos + 50)).lowercase()
                listOf("verify", "verification", "sign", "login", "auth", "confirm", "secret", "access", "protect", "valid").any { 
                    contextWindow.contains(it) 
                }
            } else false
        }

        if (!hasGenuineKeyword && !hasContextualProximity) {
            safeLog("OtpExtractor", "No genuine OTP trigger phrases found. Message is not an OTP.")
            com.mailsync.app.utils.FileLogger.log(context, "[OtpExtractor] Rejected: Message contains no genuine OTP trigger.")
            return null
        }

        val numberRegex = Regex("\\b([0-9]{4,8}|[A-Z0-9]{4,8})\\b")
        val matches = numberRegex.findAll(normalized).toList()

        var bestMatch: String? = null
        var highestScore = Int.MIN_VALUE

        for (match in matches) {
            val candidate = match.groupValues[1]
            val startPos = match.range.first

            val digitCount = candidate.count { it.isDigit() }
            val letterCount = candidate.count { it.isLetter() }

            if (!isValidCandidate(candidate, normalized, startPos)) {
                com.mailsync.app.utils.FileLogger.log(context, "[OtpExtractor] Candidate $candidate rejected by safety validators.")
                continue
            }

            // Pure letters candidate check: Must be fully uppercase and length >= 5
            if (digitCount == 0) {
                if (!candidate.all { it.isUpperCase() } || candidate.length < 5) {
                    continue
                }
            }

            // Mixed letters candidate check: Must contain at least 2 digits to avoid tracking IDs
            if (letterCount > 0 && digitCount < 2) {
                continue
            }

            var score = 0

            // 1. Length scoring
            when (candidate.length) {
                6 -> score += 120
                4 -> score += 70
                8 -> score += 60
                5 -> score += 50
                7 -> score += 40
                else -> score += 20
            }

            // 2. Pure digits bonus
            if (letterCount == 0) {
                score += 60
            } else {
                score += 30
            }

            // 3. Proximity to genuine OTP keyword
            var minDistance = Int.MAX_VALUE
            for (kw in genuineOtpKeywords) {
                val kwMatch = Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).find(normalized)
                if (kwMatch != null) {
                    val dist = kotlin.math.abs(startPos - kwMatch.range.first)
                    if (dist < minDistance) minDistance = dist
                }
            }

            when {
                minDistance <= 25 -> score += 200
                minDistance <= 60 -> score += 120
                minDistance <= 120 -> score += 50
                minDistance > 200 -> score -= 150
            }

            // 4. Isolated line bonus: Code appearing on its own line in multi-line emails
            val beforeCandidate = normalized.substring(maxOf(0, startPos - 2), startPos)
            val afterCandidate = normalized.substring(startPos + candidate.length, minOf(normalized.length, startPos + candidate.length + 2))
            if ((beforeCandidate.contains("\n") || startPos == 0) && (afterCandidate.contains("\n") || afterCandidate.isBlank())) {
                score += 80
                com.mailsync.app.utils.FileLogger.log(context, "[OtpExtractor]   -> +80 (Isolated line bonus)")
            }

            // 5. Footer penalty
            if (isAfterFooter(normalized, startPos)) {
                score -= 300
            }

            com.mailsync.app.utils.FileLogger.log(context, "[OtpExtractor] Candidate $candidate pos $startPos => Total Score: $score")

            if (score > highestScore) {
                highestScore = score
                bestMatch = candidate
            }
        }

        // Must achieve at least a score of 100 to be declared an OTP
        if (bestMatch != null && highestScore >= 100) {
            safeLog("OtpExtractor", "Scored Engine Selected: $bestMatch with score $highestScore")
            com.mailsync.app.utils.FileLogger.log(context, "[OtpExtractor] Final Decision: Extracted $bestMatch with score $highestScore")
            return ExtractionResult(bestMatch, extractExpiry(normalized, receivedTimeMs))
        }

        return null
    }

    private fun extractExpiry(text: String, receivedTimeMs: Long): Long? {
        val patterns = listOf(
            Regex("(?i)expir(?:es?|ed|ing)\\s+in\\s+(\\d+)\\s*(second|sec|s|minute|min|m|hour|hr|h)s?"),
            Regex("(?i)valid\\s+for\\s+(\\d+)\\s*(second|sec|s|minute|min|m|hour|hr|h)s?"),
            Regex("(?i)(?:use|enter|verify|redeem)\\s+(?:it\\s+)?within\\s+(\\d+)\\s*(second|sec|s|minute|min|m|hour|hr|h)s?"),
            Regex("(?i)(?:otp|code|pin)\\s+(?:is\\s+)?valid\\s+(\\d+)\\s*(second|sec|s|minute|min|m|hour|hr|h)s?"),
            Regex("(?i)(\\d+)[- ]*(second|sec|s|minute|min|m|hour|hr|h)s?\\s+(?:otp|code|pin|password)"),
            Regex("(?i)(?:expires?\\s+at|valid\\s+(?:till|until))\\s+(\\d{1,2}):(\\d{2})"),
            Regex("(?i)(?:this\\s+)?(?:code|otp|pin)\\s+(?:will\\s+)?(?:expire|expir)\\s+in\\s+(\\d+)\\s*(second|sec|s|minute|min|m|hour|hr|h)s?"),
            Regex("(?i)valid\\s+for\\s+(\\d+)\\s*(second|sec|s|minute|min|m|hour|hr|h)s?\\.?"),
            Regex("(?i)\\b(\\d+)\\s*(mins?|secs?|hours?)\\b")
        )
        
        val absolutePattern = Regex("(?i)(?:expires?\\s+at|valid\\s+(?:till|until))\\s+(\\d{1,2}):(\\d{2})")
        absolutePattern.find(text)?.let { match ->
            try {
                val hr = match.groupValues[1].toInt()
                val min = match.groupValues[2].toInt()
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, hr)
                calendar.set(java.util.Calendar.MINUTE, min)
                calendar.set(java.util.Calendar.SECOND, 0)
                var expMs = calendar.timeInMillis
                
                if (expMs < receivedTimeMs) {
                    expMs += 24 * 60 * 60 * 1000L
                }
                
                val diffMs = expMs - receivedTimeMs
                if (diffMs in 0L..86400000L) {
                    return if (diffMs > 15 * 60 * 1000L) receivedTimeMs + 15 * 60 * 1000L else expMs
                }
            } catch (_: Exception) { }
        }
        
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val amount = match.groupValues[1].toLongOrNull() ?: continue
            val unit = match.groupValues[2].lowercase()
            
            val multiplier = when {
                unit.startsWith("sec") || unit == "s" -> 1000L
                unit.startsWith("hour") || unit.startsWith("hr") || unit == "h" -> 60 * 60 * 1000L
                else -> 60 * 1000L
            }
            
            var diffMs = amount * multiplier
            if (diffMs > 15 * 60 * 1000L) {
                diffMs = 15 * 60 * 1000L
            }
            
            return receivedTimeMs + diffMs
        }
        return null
    }
}
