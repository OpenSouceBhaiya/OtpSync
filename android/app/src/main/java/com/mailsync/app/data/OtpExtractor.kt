package com.mailsync.app.data

import android.content.Context

data class ExtractionResult(
    val code: String,
    val expiresAt: Long?
)

object OtpExtractor {

    // Comprehensive multi-lingual genuine trigger phrases that confirm this message actually contains an OTP
    private val genuineOtpKeywords = listOf(
        // English
        "verification code", "one-time code", "one time code", "one-time password", "one time password",
        "security code", "access code", "login code", "sign-in code", "sign in code", 
        "auth code", "authentication code", "confirmation code", "secret code", "passcode",
        "single-use code", "single use code", "otp is", "aotp is", "otp for", "aotp for",
        "otp:", "otp -", "aotp:", "otp code", "your otp", "your code", "code is",
        "use this code", "enter this code", "enter the code", "use the code", "code to sign in",
        "code to log in", "code to verify", "your code is", "your code:", "pin is",
        "your pin", "secret pin", "temporary password", "delivery otp", "pickup otp",
        // Spanish (Español)
        "código de verificación", "codigo de verificacion", "código de seguridad", "codigo de seguridad",
        "código de confirmación", "codigo de confirmacion", "código de acceso", "codigo de acceso",
        "clave de seguridad", "clave de confirmación", "clave de verificacion", "clave de verificación",
        "contraseña de un solo uso", "su código es", "tu código es", "su codigo es", "tu codigo es",
        "su código de", "tu código de", "código otp", "codigo otp",
        // French (Français)
        "code de vérification", "code de verification", "code de sécurité", "code de securite",
        "code de confirmation", "code de connexion", "code d'accès", "code d'activation",
        "mot de passe à usage unique", "mot de passe a usage unique", "mot de passe temporaire",
        "votre code est", "votre code de", "code d'authentification",
        // German (Deutsch)
        "verifizierungscode", "sicherheitscode", "bestätigungscode", "bestatigungscode",
        "aktivierungscode", "einmalpasswort", "einmalkennwort", "ihr code lautet",
        "ihr sicherheitscode", "ihr bestätigungscode", "sicherheitsschlüssel",
        // Portuguese (Português)
        "código de verificação", "codigo de verificacao", "código de segurança", "codigo de seguranca",
        "código de confirmação", "codigo de confirmacao", "senha de uso único", "senha de uso unico",
        "seu código é", "seu codigo e", "código de acesso",
        // Italian (Italiano)
        "codice di verifica", "codice di sicurezza", "codice di conferma", "codice di accesso",
        "il tuo codice è", "il tuo codice e", "il tuo codice:", "password monouso",
        // Russian (Русский)
        "код подтверждения", "проверочный код", "код безопасности", "одноразовый пароль",
        "пароль для входа", "ваш код:", "ваш проверочный код", "код авторизации",
        // Arabic (العربية)
        "رمز التحقق", "رمز التأكيد", "رمز الأمان", "كلمة مرور لمرة واحدة",
        "رمز الدخول", "رمزك هو",
        // Chinese (中文)
        "验证码", "驗證碼", "确认码", "確認碼", "安全码", "安全碼",
        "动态口令", "动态密码", "動態密碼", "您的验证码",
        // Japanese (日本語)
        "認証コード", "確認コード", "セキュリティコード", "ワンタイムパスワード",
        "認証番号", "コードは",
        // Korean (한국어)
        "인증번호", "인증코드", "보안코드", "확인코드",
        // Hindi & Hinglish (हिंदी)
        "ओटीपी", "सत्यापन कोड", "सुरक्षा कोड", "aapka otp", "apna otp",
        "otp hai", "otp darj", "تصدیقی کوڈ", "او ٹی پی",
        // Turkish (Türkçe)
        "doğrulama kodu", "dogrulama kodu", "güvenlik kodu", "guvenlik kodu",
        "onay kodu", "tek kullanımlık şifre", "tek kullanimlik sifre",
        // Vietnamese (Tiếng Việt)
        "mã xác minh", "ma xac minh", "mã xác thực", "mã xác nhận",
        "mã bảo mật", "ma bao mat", "mã otp",
        // Indonesian / Malay (Bahasa)
        "kode verifikasi", "kode keamanan", "kode konfirmasi", "kata sandi sekali pakai",
        "kod pengesahan", "kod keselamatan"
    )

    // Secondary keywords that require proximity to verification/auth context
    private val proximityKeywords = listOf(
        "otp", "aotp", "code", "pin", "passcode", "código", "codigo", "clave", "schlüssel", "пароль"
    )

    // Footer boundaries for deprioritization
    private val footerBoundaries = listOf(
        "terms of", "privacy policy", "unsubscribe", "this message was sent to", 
        "the team", "all rights reserved", "©", "copyright", "thanks,", "thank you,", "regards,", "sincerely,",
        "todos los derechos", "tous droits réservés", "alle rechte vorbehalten"
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

        // Convert full-width numbers (often seen in CJK notifications: ０-９) to standard ASCII
        val fullWidthMap = mapOf(
            '０' to '0', '１' to '1', '２' to '2', '３' to '3', '４' to '4',
            '５' to '5', '６' to '6', '７' to '7', '８' to '8', '９' to '9'
        )
        val sb = StringBuilder()
        for (ch in normalized) {
            sb.append(fullWidthMap[ch] ?: ch)
        }
        normalized = sb.toString()

        // Replace smart quotes and dashes with standard ASCII
        normalized = normalized.replace("–", "-").replace("—", "-")
        normalized = normalized.replace("“", "\"").replace("”", "\"")
        normalized = normalized.replace("‘", "'").replace("’", "'")

        // Collapse digit sequences separated by spaces or dashes: e.g. "511 570" -> "511570", "482-913" -> "482913", "849-201-92" -> "84920192"
        var prev = ""
        while (normalized != prev) {
            prev = normalized
            normalized = normalized.replace(Regex("(?<=\\d)[ -]+(?=\\d)"), "")
        }
        
        // Strip invisible unicode characters
        normalized = normalized.replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF]"), "")
        // Normalize quotes and brackets wrapping codes
        normalized = normalized.replace(Regex("(?<=[^\\w]|^)[\"'\\[\\]()<>]+(?=\\d)"), " ")
        normalized = normalized.replace(Regex("(?<=\\d)[\"'\\[\\]()<>]+(?=[^\\w]|$)"), " ")
        return normalized
    }

    private fun isLikelyYear(candidate: String, text: String, startPos: Int): Boolean {
        if (candidate.length != 4 || !candidate.all { it.isDigit() }) return false
        val yearVal = candidate.toIntOrNull() ?: return false
        if (yearVal !in 1900..2099) return false

        // Check surrounding 40 characters for date/copyright markers in multiple languages
        val windowStart = maxOf(0, startPos - 40)
        val windowEnd = minOf(text.length, startPos + candidate.length + 40)
        val window = text.substring(windowStart, windowEnd).lowercase()

        if (window.contains("©") || window.contains("copyright") || window.contains("all rights") ||
            window.contains("since") || window.contains("year") || window.contains("valid till") ||
            window.contains("expiry") || window.contains("expires") || window.contains("expira") ||
            window.contains("año") || window.contains("année") || window.contains("jahr") ||
            window.contains("anno") || window.contains("год") || window.contains("年")) {
            return true
        }

        val months = listOf(
            "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
            "ene", "abr", "ago", "dic", "mai", "août", "okt"
        )
        if (months.any { window.contains(it) }) {
            return true
        }

        // Preceded or followed by / or - (e.g. 15/10/2026 or 2026-10-15)
        if (startPos > 0 && (text[startPos - 1] == '/' || text[startPos - 1] == '-')) return true
        val afterPos = startPos + candidate.length
        if (afterPos < text.length && (text[afterPos] == '/' || text[afterPos] == '-')) return true

        // If it's a current or recent year (2015..2035) and NOT preceded directly by explicit "otp is" or "code is":
        if (yearVal in 2015..2035) {
            val prefix20 = text.substring(maxOf(0, startPos - 20), startPos).lowercase()
            val hasExplicitAssignment = prefix20.contains("otp is") || prefix20.contains("otp:") || 
                                       prefix20.contains("code is") || prefix20.contains("code:") ||
                                       prefix20.contains("pin is") || prefix20.contains("pin:") ||
                                       prefix20.contains("código es") || prefix20.contains("código:") ||
                                       prefix20.contains("code est") || prefix20.contains("code:")
            if (!hasExplicitAssignment) return true
        }

        return false
    }

    private fun isMaskedCardOrAccount(text: String, startPos: Int): Boolean {
        val prefix = text.substring(maxOf(0, startPos - 35), startPos).lowercase()
        // Matches "ending 4821", "terminada en 4821", "terminata con 4821", "card ending 4821", "a/c ending 4821", "**4821", "xx4821", "المنتهي برقم"
        return Regex("(?:ending(?:\\s+in|\\s+with)?|terminada\\s+en|terminata\\s+con|finissant\\s+par|endend\\s+auf|card|tarjeta|carte|karte|a/c|ac|account|cuenta|compte|konto|kart|المنتهي(?:\\s+برقم)?|खाता|कार्ड)\\s*[:#]?\\s*[*xX]*\\s*$").containsMatchIn(prefix) ||
               Regex("[*xX]{2,}\\s*$").containsMatchIn(prefix)
    }

    private fun isAmountOrCurrency(text: String, startPos: Int, length: Int): Boolean {
        if (startPos > 0) {
            val prevChar = text[startPos - 1]
            if (prevChar == '₹' || prevChar == '$' || prevChar == '€' || prevChar == '£' || prevChar == '¥' || prevChar == '.') return true
        }
        val prefix = text.substring(maxOf(0, startPos - 20), startPos).lowercase()
        if (Regex("(?:rs\\.?|inr|usd|eur|gbp|cad|aud|sgd|brl|rub|jpy|cny|amount|amt|importe|monto|montant|betrag|importo|сумма|金额|金額|tutar|jumlah|مبلغ|ر\\.س|रु|र)\\s*[:=]?\\s*$").containsMatchIn(prefix)) return true

        val afterPos = startPos + length
        if (afterPos < text.length) {
            val suffix = text.substring(afterPos, minOf(text.length, afterPos + 10)).lowercase()
            if (suffix.startsWith(".00") || suffix.startsWith("/-") || Regex("^\\s*(?:rs|inr|usd|eur|gbp|cad|aud|rub|元|ر\\.س)").containsMatchIn(suffix)) return true
        }
        return false
    }

    private fun isIdentifierOrReference(text: String, startPos: Int): Boolean {
        val prefix = text.substring(maxOf(0, startPos - 25), startPos).lowercase()
        return Regex("(?:ref(?:erence|erencia|érence)?|riferimento|order|pedido|commande|bestellung|ordine|заказ|订单|注文|sipariş|مرجع|رقم\\s+المرجع|संदर्भ|txn|ticket|case|awb|tracking|单号|快递单号|invoice|factura|rechnung|statement|beneficiary|destinatario|empfänger|bénéficiaire|store|tienda|magasin)\\s*(?:no\\.?|id|#|:|-|\\s)\\s*$").containsMatchIn(prefix)
    }

    private fun isPincode(text: String, startPos: Int, candidate: String): Boolean {
        if (candidate.length != 6 || !candidate.all { it.isDigit() }) return false
        val prefix = text.substring(maxOf(0, startPos - 25), startPos).lowercase()
        return Regex("(?:pin(?:\\s*code)?|postal(?:\\s*code)?|zip(?:\\s*code)?|código\\s*postal|code\\s*postal|plz)\\s*[:=]?\\s*$").containsMatchIn(prefix)
    }

    private fun isPhoneNumber(text: String, startPos: Int, candidate: String): Boolean {
        val prefix = text.substring(maxOf(0, startPos - 30), startPos).lowercase()
        if (Regex("(?:call|helpline|contact|tel|phone|support|teléfono|téléphone|telefon|toll\\s*free|helpdesk|संपर्क)\\s*[:=]?\\s*$").containsMatchIn(prefix)) return true

        val window = text.substring(maxOf(0, startPos - 4), minOf(text.length, startPos + candidate.length + 4))
        val digitsInWindow = window.count { it.isDigit() }
        if (digitsInWindow > candidate.length && digitsInWindow >= 10) return true

        return false
    }

    private fun isValidCandidate(candidate: String, text: String, startPos: Int): Boolean {
        // Supported OTP length range: 4 to 10 characters
        if (candidate.length !in 4..10) return false

        val letterCount = candidate.count { it.isLetter() }
        val digitCount = candidate.count { it.isDigit() }

        // Pure letters: MUST be uppercase, >= 4 chars, and NOT a common dictionary word
        if (digitCount == 0) {
            if (!candidate.all { it.isUpperCase() } || candidate.length < 4) return false
            val commonWords = setOf(
                "WITH", "FROM", "YOUR", "THIS", "THAT", "HAVE", "WILL", "CALL", "CODE",
                "USER", "HERE", "ONCE", "SIGN", "TRUE", "FREE", "MORE", "DATE", "TIME",
                "NAME", "TEAM", "HELP", "SEND", "DEAR", "STOP", "VALID", "SHARE", "ENTER",
                "ACCESS", "PLEASE", "VERIFY", "LOGIN", "ACCOUNT", "NUMBER", "SECRET",
                "ESTA", "ESTE", "PARA", "POUR", "AVEC", "VOUS", "NOUS", "BIEN", "DASS",
                "DICH", "BITTE", "KANN", "SEIN", "TODO", "NADA", "SOLO", "CLAVE"
            )
            if (commonWords.contains(candidate.uppercase())) return false
        }

        // Mixed letters: Require at least 2 digits unless it matches common 5-char token (like Steam Guard)
        if (letterCount > 0 && digitCount < 2 && candidate.length != 5) return false

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
            // 1. W3C WebOTP Standard format: "@example.com #123456" or "@domain.io #A3F92"
            Regex("(?i)@[a-zA-Z0-9.-]+\\s+#([0-9A-Za-z]{4,10})\\b"),

            // 2. Google and platform prefixes: "G-123456", "FB-12345", "TW-123456", "WL-123456"
            Regex("(?i)\\b(?:G|C|B|V|FB|TW|WL|GH|MS)\\s*-\\s*([0-9A-Za-z]{4,8})\\b"),

            // 3. English assignment: "Your OTP is 482913", "verification code: 482913", "auth code: A3F92K", "pin is 8492"
            Regex("(?i)\\b(?:one[- ]time\\s+(?:password|code)|verification\\s+code|security\\s+code|login\\s+code|sign[- ]in\\s+code|access\\s+code|auth(?:entication)?\\s+code|confirmation\\s+code|secret\\s+code|single[- ]use\\s+code|passcode|a?otp|pin(?:\\s+code)?)\\s*(?:is|:|–|-|=|#)\\s*([0-9A-Za-z]{4,10})\\b"),

            // 4. Spanish assignment: "Su código de verificación es 492018", "clave de seguridad: 849201"
            Regex("(?i)\\b(?:código|codigo|clave)\\s+(?:de\\s+(?:verificación|verificacion|seguridad|confirmación|confirmacion|acceso))?\\s*(?:es|:|–|-|=|#)\\s*([0-9A-Za-z]{4,10})\\b"),

            // 5. French assignment: "Votre code de confirmation est le 739102", "mot de passe temporaire: 482910"
            Regex("(?i)\\b(?:code\\s+de\\s+(?:vérification|verification|sécurité|securite|confirmation|connexion)|mot\\s+de\\s+passe(?:\\s+à\\s+usage\\s+unique)?)\\s*(?:est(?:\\s+le)?|:|–|-|=|#)\\s*([0-9A-Za-z]{4,10})\\b"),

            // 6. German assignment: "Ihr Bestätigungscode lautet 829104", "Sicherheitscode: 829104"
            Regex("(?i)\\b(?:(?:sicherheits|bestätigungs|verifizierungs)?code|einmalpasswort|einmalkennwort)\\s*(?:lautet|ist|:|–|-|=|#)\\s*([0-9A-Za-z]{4,10})\\b"),
            Regex("(?i)\\blautet\\s+(?:ihr\\s+)?(?:sicherheits|bestätigungs|verifizierungs)?(?:code|einmalpasswort|passwort)\\s*[:=—–-]?\\s*([0-9A-Za-z]{4,10})\\b"),

            // 7. Portuguese assignment: "Seu código de verificação é 618294"
            Regex("(?i)\\b(?:código|codigo)\\s+(?:de\\s+(?:verificação|verificacao|segurança|seguranca|confirmação|confirmacao))?\\s*(?:é|e|:|–|-|=|#)\\s*([0-9A-Za-z]{4,10})\\b"),

            // 8. Italian assignment: "Il tuo codice di verifica è 618294"
            Regex("(?i)\\b(?:codice\\s+di\\s+(?:verifica|sicurezza|conferma)|codice\\s+otp)\\s*(?:è|e|:|–|-|=|#)\\s*([0-9A-Za-z]{4,10})\\b"),

            // 9. Russian assignment: "Ваш проверочный код: 391820", "Код: 582914"
            Regex("(?iu)(?:код\\s+(?:подтверждения(?:\\s+операции)?|безопасности)|проверочный\\s+код|одноразовый\\s+пароль|пароль)\\s*(?:[:=—–-]|это)\\s*([0-9A-Za-z]{4,10})\\b"),
            Regex("(?iu)(?:ваш\\s+(?:проверочный\\s+)?код)\\s*[:=—–-]\\s*([0-9A-Za-z]{4,10})\\b"),
            Regex("(?iu)(?:^|\\s|[.,;])(?:код|пароль)\\s*[:=—–-]\\s*([0-9A-Za-z]{4,10})\\b"),
            Regex("(?i)(?:код\\s+(?:подтверждения|безопасности)|проверочный\\s+код|одноразовый\\s+пароль|пароль)\\s*(?:[:=—–-]|это)\\s*([0-9A-Za-z]{4,10})\\b"),
            Regex("(?i)(?:ваш\\s+(?:проверочный\\s+)?код)\\s*[:=—–-]\\s*([0-9A-Za-z]{4,10})\\b"),

            // 10. Chinese assignment: "您的动态验证码为 582910", "验证码：582910"
            Regex("(?:动态验证码|动态密码|动态口令|验证码|驗證碼|确认码|確認碼|安全码|动态码)(?:为|是|：|:|\\s)*([0-9A-Za-z]{4,10})\\b"),

            // 11. Japanese assignment: "認証コードは 928301 です"
            Regex("(?:認証コード|確認コード|セキュリティコード|ワンタイムパスワード)(?:は|：|:|\\s)*([0-9A-Za-z]{4,10})\\b"),

            // 12. Korean assignment: "인증번호: 829104", "인증번호는 829104 입니다"
            Regex("(?:인증번호|인증코드|보안코드)(?:는|：|:|\\s)*([0-9A-Za-z]{4,10})\\b"),

            // 13. Arabic assignment: "رمز التحقق الخاص بك هو 719283"
            Regex("(?:رمز\\s+(?:التحقق|الأمان|التأكيد))[^0-9\\n\\r]{0,40}?\\s*([0-9A-Za-z]{4,10})\\b"),

            // 14. Hindi / Hinglish assignment: "आपका लॉगिन ओटीपी 839201 है"
            Regex("(?i)(?:(?:aapka|apna|लॉगिन|login|आपका|अपना)\\s+)*(?:otp|ओटीपी|सत्यापन\\s+कोड|सुरक्षा\\s+कोड)\\s*(?:hai|है|:|–|-|=|#)?\\s*([0-9A-Za-z]{4,10})\\b"),

            // 15. Turkish assignment: "Doğrulama kodu: 829104"
            Regex("(?i)\\b(?:doğrulama|dogrulama|güvenlik|guvenlik|onay)\\s+kodu\\s*(?:[:=—–-]|dur|dir)?\\s*([0-9A-Za-z]{4,10})\\b"),

            // 16. Vietnamese assignment: "Mã xác thực: 829104"
            Regex("(?i)(?:mã\\s+(?:xác\\s+minh|xác\\s+thực|xác\\s+nhận|bảo\\s+mật|otp))\\s*(?:là|:|-|=)\\s*([0-9A-Za-z]{4,10})\\b"),

            // 17. Indonesian / Malay assignment: "Kode verifikasi adalah 829104"
            Regex("(?i)\\b(?:kode|kod)\\s+(?:verifikasi|keselamatan|pengesahan|konfirmasi)\\s*(?:adalah|:|-|=)\\s*([0-9A-Za-z]{4,10})\\b"),

            // 18. Bank SMS template (English, Spanish, French, German): "OTP for transaction of ... is 839201"
            Regex("(?i)\\b(?:a?otp|code|pin|password|clave)\\s+(?:for|to|para|pour|für|di)\\s+[^.\n\r]{1,120}?\\b(?:is|:|=|es|lautet|est|é)\\s*([0-9]{4,10})\\b"),

            // 19. Reverse phrasing: "482913 is your OTP", "4829 is your Uber code"
            Regex("(?i)\\b([0-9A-Za-z]{4,10})\\s+(?:is\\s+(?:your\\s+)?(?:one[- ]time\\s+(?:password|code)|verification\\s+code|security\\s+code|login\\s+code|sign[- ]in\\s+code|confirmation\\s+code|passcode|a?otp|secret\\s+code|(?:[a-zA-Z0-9]+\\s+)?code|pin))\\b"),
            Regex("(?i)\\b([0-9A-Za-z]{4,10})\\s+es\\s+(?:su|tu)\\s+(?:código|codigo)\\b"),
            Regex("(?i)\\b([0-9A-Za-z]{4,10})\\s+est\\s+votre\\s+code\\b"),
            Regex("(?i)\\b([0-9A-Za-z]{4,10})\\s+é\\s+o\\s+seu\\s+código\\b"),

            // 20. Action phrasing: "Use 482913 to verify", "Enter 482913 to complete", "Utilice 482913 para verificar"
            Regex("(?i)\\b(?:use|enter|input|utilice|ingrese|entrez|saisissez|geben\\s+sie)\\s+([0-9A-Za-z]{4,10})\\s+(?:(?:it\\s+)?(?:to|for|on|as|para|pour|um|zu)\\s+(?:verify|sign\\s*in|log\\s*in|complete|authenticate|verificar|valider|anmelden|bestätigen|your\\s+(?:otp|code|pin)))\\b"),

            // 21. Next-line isolated code after prompt
            Regex("(?im)^(?:verification\\s+code|security\\s+code|one[- ]time\\s+password|your\\s+code|código|code|passcode|código\\s+de\\s+verificación|code\\s+de\\s+vérification|sicherheitscode|ihr\\s+code)\\s*:\\s*\r?\n\\s*([0-9A-Za-z]{4,10})\\s*$"),

            // 22. "OTP 482913", "Code 482913" (space separator for pure digits)
            Regex("(?i)\\b(?:one[- ]time\\s+(?:password|code)|verification\\s+code|security\\s+code|login\\s+code|sign[- ]in\\s+code|access\\s+code|auth(?:entication)?\\s+code|confirmation\\s+code|secret\\s+code|passcode|a?otp|pin(?:\\s+code)?)\\s+([0-9]{4,10})\\b"),

            // 23. Direct labeled: "Code: 482913", "Code is 482913", "OTP: 123456"
            Regex("(?i)\\b(?:code|pin|a?otp|код)\\s*(?:is|:|–|-|=|#)\\s*([0-9A-Za-z]{4,10})\\b")
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
        val hasGenuineKeyword = genuineOtpKeywords.any { kw ->
            if (kw.any { ch -> ch.code > 127 }) {
                normalized.contains(kw, ignoreCase = true)
            } else {
                Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
            }
        }

        val hasContextualProximity = proximityKeywords.any { kw ->
            val match = Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).find(normalized)
            if (match != null) {
                val kwPos = match.range.first
                val contextWindow = normalized.substring(maxOf(0, kwPos - 50), minOf(normalized.length, kwPos + 50)).lowercase()
                listOf("verify", "verification", "sign", "login", "auth", "confirm", "secret", "access", "protect", "valid", "seguridad", "verificar", "confirmer").any { 
                    contextWindow.contains(it) 
                }
            } else false
        }

        if (!hasGenuineKeyword && !hasContextualProximity) {
            safeLog("OtpExtractor", "No genuine OTP trigger phrases found. Message is not an OTP.")
            com.mailsync.app.utils.FileLogger.log(context, "[OtpExtractor] Rejected: Message contains no genuine OTP trigger.")
            return null
        }

        val numberRegex = Regex("\\b([0-9]{4,10}|[A-Z0-9]{4,10})\\b")
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

            // Pure letters candidate check: Must be fully uppercase and length >= 4
            if (digitCount == 0) {
                if (!candidate.all { it.isUpperCase() } || candidate.length < 4) {
                    continue
                }
            }

            // Mixed letters candidate check: Must contain at least 2 digits unless it's a 5-char token
            if (letterCount > 0 && digitCount < 2 && candidate.length != 5) {
                continue
            }

            var score = 0

            // 1. Length scoring
            when (candidate.length) {
                6 -> score += 120 // Most common (Google, banks, 2FA)
                8 -> score += 80  // Banking, Apple, Epic Games
                4 -> score += 70  // Uber, WhatsApp, SMS
                5 -> score += 60  // Steam Guard, Telegram
                7 -> score += 60  // European banks
                else -> score += 40
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
                val kwPos = if (kw.any { ch -> ch.code > 127 }) {
                    normalized.indexOf(kw, ignoreCase = true)
                } else {
                    val kwMatch = Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).find(normalized)
                    kwMatch?.range?.first ?: -1
                }
                if (kwPos != -1) {
                    val dist = kotlin.math.abs(startPos - kwPos)
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
