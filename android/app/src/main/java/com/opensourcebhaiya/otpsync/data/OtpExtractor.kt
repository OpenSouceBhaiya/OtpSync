package com.opensourcebhaiya.otpsync.data

import android.content.Context

data class ExtractionResult(
    val code: String,
    val expiresAt: Long?
)

object OtpExtractor {

    // Comprehensive multi-lingual genuine trigger phrases that confirm this message actually contains an OTP
    private val genuineOtpKeywords = listOf(
        // English
        "otp", "aotp", "passcode", "use otp", "enter otp", "the otp", "secret otp",
        "verification code", "one-time code", "one time code", "one-time password", "one time password",
        "security code", "access code", "login code", "sign-in code", "sign in code", 
        "auth code", "authentication code", "confirmation code", "secret code",
        "single-use code", "single use code", "otp is", "aotp is", "otp for", "aotp for",
        "otp:", "otp -", "aotp:", "otp code", "your otp", "your otp is", "your code", "code is", "code:",
        "use this code", "enter this code", "enter the code", "use the code", "code to sign in",
        "code to log in", "code to verify", "code valid", "valid code", "your code is", "your code:", "pin is",
        "your pin", "secret pin", "temporary password", "delivery otp", "pickup otp",
        "verification pin", "security pin", "login otp", "signin otp", "sign-in otp", "verification otp",
        "security otp", "bank otp", "transaction otp",
        "login pin", "auth pin", "passcode is", "one-time pin", "one time pin", "temporary pin",
        "2fa code", "2-factor code", "two-factor", "two factor", "mfa code", "multi-factor",
        "here is your code", "here is your otp", "uber code", "uber otp",
        // Spanish (Español)
        "código de verificación", "codigo de verificacion", "código de seguridad", "codigo de seguridad",
        "código de confirmación", "codigo de confirmacion", "código de acceso", "codigo de acceso",
        "código de entrega", "codigo de entrega", "código de recogida", "codigo de recogida",
        "clave de seguridad", "clave de confirmación", "clave de verificacion", "clave de verificación",
        "clave dinámica", "clave dinamica", "clave de firma",
        "contraseña de un solo uso", "su código es", "tu código es", "su codigo es", "tu codigo es",
        "su código de", "tu código de", "código otp", "codigo otp", "use el código",
        // French (Français)
        "code de vérification", "code de verification", "code de sécurité", "code de securite",
        "code de confirmation", "code de connexion", "code d'accès", "code d'activation",
        "code d'autorisation", "code de validation", "code de retrait", "code de livraison",
        "mot de passe à usage unique", "mot de passe a usage unique", "mot de passe temporaire",
        "votre code est", "votre code de", "code d'authentification", "utilisez le code",
        // German (Deutsch)
        "verifizierungscode", "sicherheitscode", "bestätigungscode", "bestatigungscode",
        "aktivierungscode", "freigabecode", "abholcode", "liefercode", "buchungscode",
        "einmalpasswort", "einmalkennwort", "ihr code lautet", "ihr sicherheitscode", "ihr bestätigungscode",
        "ihr einmalcode", "sicherheitsschlüssel",
        // Portuguese (Português)
        "código de verificação", "codigo de verificacao", "código de segurança", "codigo de seguranca",
        "código de confirmação", "codigo de confirmacao", "senha de uso único", "senha de uso unico",
        "chave de segurança", "chave de seguranca", "código de entrega", "código de levantamento", "código de recolha",
        "seu código é", "seu codigo e", "código de acesso", "use a senha de uso único", "use o código",
        // Italian (Italiano)
        "codice di verifica", "codice di sicurezza", "codice di conferma", "codice di accesso",
        "codice di autorizzazione", "codice di consegna", "codice di ritiro", "codice dispositivo",
        "il tuo codice è", "il tuo codice e", "il tuo codice:", "password monouso",
        // Russian (Русский)
        "код подтверждения", "проверочный код", "код безопасности", "одноразовый пароль",
        "код для подтверждения", "код получения", "код выдачи", "код для входа",
        "пароль для входа", "ваш код:", "ваш проверочный код", "код авторизации",
        // Arabic (العربية)
        "رمز التحقق", "رمز التأكيد", "رمز الأمان", "كلمة مرور لمرة واحدة",
        "رمز الدخول", "رمزك هو", "كود التأكيد", "كود التحقق", "كود الأمان", "كود الدخول",
        // Chinese (中文)
        "验证码", "驗證碼", "确认码", "確認碼", "安全码", "安全碼",
        "动态口令", "动态密码", "動態密碼", "您的验证码", "取件码", "提货码", "取件验证码", "核验验证码",
        // Japanese (日本語)
        "認証コード", "確認コード", "セキュリティコード", "ワンタイムパスワード",
        "認証番号", "コードは", "ガードコード", "Steam ガード", "配達コード", "本人確認コード",
        // Korean (한국어)
        "인증번호", "인증코드", "보안코드", "확인코드", "승인번호", "일회용 비밀번호", "일회용 인증번호",
        // Hindi & Hinglish (हिंदी)
        "ओटीपी", "सत्यापन कोड", "सुरक्षा कोड", "गुप्त कोड", "प्रमाणीकरण कोड", "पासकोड", "aapka otp", "apna otp",
        "otp hai", "otp darj", "वन टाइम पासवर्ड", "वन-टाइम पासवर्ड", "डिलीवरी ओटीपी", "सत्यापन पिन", "डिलीवरी पिन", "गुप्त ओटीपी", "कोड:", "कोड ", "تصدیقی کوڈ", "او ٹی پی",
        // Bengali (বাংলা)
        "যাচাইকরণ কোড", "ওয়ান-টাইম পাসওয়ার্ড", "ওটিপি", "নিরাপত্তা কোড", "লগইন কোড",
        "আপনার কোড হল", "লগইন ওটিপি", "ভেরিফিকেশন পিন", "ডেলিভারি ওটিপি", "যাচাইকরণ পিন", "রাইড শুরুর পিন",
        // Turkish (Türkçe)
        "doğrulama kodu", "dogrulama kodu", "güvenlik kodu", "guvenlik kodu",
        "onay kodu", "onay kodun", "onay kodunuz", "doğrulama kodun", "güvenlik kodun", "tek kullanımlık şifre", "tek kullanimlik sifre",
        // Vietnamese (Tiếng Việt)
        "mã xác minh", "ma xac minh", "mã xác thực", "mã xác nhận",
        "mã bảo mật", "ma bao mat", "mã otp",
        // Indonesian / Malay (Bahasa)
        "kode verifikasi", "kode keamanan", "kode konfirmasi", "kata sandi sekali pakai",
        "kode otp anda adalah", "kode keamanan anda adalah", "kod pengesahan", "kod keselamatan",
        // Marathi (मराठी)
        "तुमचा ओटीपी", "सत्यापन कोड", "सुरक्षा कोड", "लॉगिन कोड", "ओटीपी आहे", "एकवेळचा पासवर्ड",
        "डिलिव्हरी पिन", "डिलिव्हरी ओटीपी", "पडताळणी कोड", "पडताळणी ओटीपी",
        // Telugu (తెలుగు)
        "ధృవీకరణ కోడ్", "ధ్రువీకరణ కోడ్", "రహస్య కోడ్", "వన్-టైమ్ పాస్‌వర్డ్", "మీ ఓటీపీ", "లాగిన్ కోడ్",
        "డెలివరీ పిన్", "భద్రతా కోడ్", "రహస్య ఓటీపీ",
        // Tamil (தமிழ்)
        "சரிபார்ப்புக் குறியீடு", "பாதுகாப்புக் குறியீடு", "ஒருமுறை கடவுச்சொல்", "உங்கள் ஓடிபி", "உள்நுழைவு குறியீடு",
        "சரிபார்ப்பு பின்", "டெலிவரி பின்", "ரகசிய குறியீடு",
        // Gujarati (ગુજરાતી)
        "ચકાસણી કોડ", "સુરક્ષા કોડ", "વન-ટાઇમ પાસવર્ડ", "તમારો ઓટીપી", "લોગિન કોડ",
        "વેરિફિકેશન પિન", "ડિલિવરી પિન", "સિક્રેટ કોડ"
    )

    private val proximityKeywords = listOf(
        "otp", "aotp", "code", "pin", "passcode", "código", "codigo", "clave", "schlüssel", "пароль",
        "codice", "kodu", "kodunuz", "şifre", "şifreniz", "رمز", "كود", "코드", "번호", "コード", "குறியீடு", "కోడ్", "কোড", "કોડ",
        "flipkart", "amazon", "swiggy", "zomato", "meesho", "myntra", "snapdeal", "nykaa",
        "paytm", "phonepe", "gpay", "razorpay", "juspay", "hdfc", "sbi", "icici", "axis", "kotak",
        "uber", "ola", "rapido", "zippay", "blinkit", "bigbasket", "dunzo", "netflix", "microsoft",
        "apple", "google", "shein", "github", "gitlab", "discord", "steam", "epic", "paypal",
        "binance", "coinbase", "zerodha", "groww", "upstox", "zepto", "tataneu", "irctc",
        "instagram", "facebook", "twitter", "linkedin", "snapchat", "telegram"
    )

    // Footer boundaries for deprioritization
    private val footerBoundaries = listOf(
        "terms of", "privacy policy", "unsubscribe", "this message was sent to", 
        "the team", "all rights reserved", "©", "copyright", "thanks,", "thank you,", "regards,", "sincerely,",
        "todos los derechos", "tous droits réservés", "alle rechte vorbehalten"
    )

    // ══════════════════════════════════════════════════════════════════════════════
    // PHASE 1: HIGH-CONFIDENCE ANCHOR MATCHING PATTERNS (Precompiled Singleton)
    // ══════════════════════════════════════════════════════════════════════════════
    private val anchorPatterns = listOf(
        // 1. W3C WebOTP Standard format: "@example.com #123456" or "@domain.io #A3F92"
        Regex("(?i)@[a-zA-Z0-9.-]+\\s+#([0-9A-Za-z]{4,10})\\b"),

        // 2. Google and platform prefixes: "G-123456", "FB-12345", "TW-123456", "WL-123456" (never preceded by #)
        Regex("(?i)(?<![#@\\w])(?:G|C|B|V|FB|TW|WL|GH|MS|VK|TG)[- ]([0-9]{4,8})\\b"),

        // 3. Steam Guard specifically: 5 uppercase letters/digits
        Regex("(?i)\\b(?:steam\\s+guard(?:\\s+(?:code|код|коડ|код|코드|コード|કોડ))?)\\s*(?:is|:|：|=|\u2013|-|\\s)\\s*([0-9A-Z]{5})\\b"),

        // 4. Universal platform/service + OTP/code: "WhatsApp kodunuz: 829104", "Steam Guard 코드: 4LM7P", "Uber code: 4918"
        Regex("(?i)(?:(?:whatsapp|telegram|uber|rapido|netflix|apple|google|microsoft|steam\\s*guard|dana|korail|turkcell|vodafone|blutv|novo\\s*banco|t-bank|т-банк|тинькофф|ozon|spotify|zomato|swiggy|flipkart|amazon|meesho|tata\\s*neu|irctc|당근|코레일|ডিজிட்டল|ડિજિટલ|дигитал|টেলিগ্রাম|ടെலிகிராம்|టెలిగ్రామ్)\\s+(?:[\\p{L}\\s]{0,35})?\\s*(?:code|otp|pin|passcode|kod(?:u|unuz)?|şifre(?:niz)?|codice|código|codigo|clave|senha|رمز|كود|код|пароль|कोड|কোড|కోడ్|குறியீடு|코드|コード|કોડ|પિન|વાપરો|ఉపయోగించండి|பયன்படுத்தவும்)[^0-9\\n\\r]{0,20}?\\s*[:=–\\-#।]?\\s*(\\d[0-9A-Za-z]{3,9})\\b)"),

        // 4b. Universal code keyword + platform: "Kode WhatsApp: 829104", "Codice WhatsApp: 829104", "Código de WhatsApp: 829104", "Código do WhatsApp: 829104", "টেলিগ্রাম কোড: 49201"
        Regex("(?i)(?:(?:code|otp|pin|passcode|kod(?:u|unuz)?|kode|şifre(?:niz)?|codice|código|codigo|clave|senha|mot\\s+de\\s+passe|رمز|كود|[Кк]од|[Пп]ароль|код|пароль|কোড|కోడ్|குறியீடு|코드|コード|કોડ)\\s+(?:(?:de|do|da|di|für|d'|para|для|for)\\s+|(?:de\\s+[\\p{L}]+\\s+))*(?:whatsapp|telegram|uber|rapido|netflix|apple|google|microsoft|steam|dana|spotify|zomato|swiggy|flipkart|amazon|ozon|টেলিগ্রাম|ടെலிகிராம்|టెలిగ్రామ్)[^0-9\\n\\r]{0,25}?\\s*[:=–\\-#।]?\\s*(\\d[0-9A-Za-z]{3,9})(?![0-9A-Za-z]))"),

        // 4c. Digits followed by use/action in Indian & Asian & Turkish languages:
        // "849201 কোডটি ব্যবহার করুন", "829104 கோடைப் பயன்படுத்தவும்", "582914 ஐப் பயன்படுத்தவும்", "582914 కోడ్ ఉపయోగించండి", "918204 kodunu kullanın", "391820 을(를) 사용하세요"
        Regex("(?i)(?<![\\p{L}\\p{Nd}])([0-9A-Za-z]{4,10})\\s*(?:[^\\d\\n\\r]{0,30})?\\s*(?:பயன்படுத்தவும்|ఉపయోగించండి|વાપરો|વાપરા|ব্যবহার\\s+করুন|사용하세요|kodunu\\s+kullan(?:ın)?|kodunu\\s+kuryeye)(?![\\p{L}\\p{Nd}])"),

        // 4d. Direct action verbs (multilingual): "Utilisez 918204 pour...", "Gunakan 918204 untuk...", "Используйте 618294 для..."
        Regex("(?i)(?<![\\p{L}\\p{Nd}])(?:use|enter|input|utilisez|utilice|utilize|ingrese|entrez|geben\\s+sie|verwenden\\s+sie|use\\s+el|usa|gunakan|используйте|[Ии]спользуйте)\\s+(?:le\\s+code\\s+de\\s+\\w+|el\\s+código\\s+de\\s+\\w+|a\\s+senha\\s+de\\s+\\w+|код\\s+проверки|[Кк]од\\s+проверки|código\\s+de\\s+validação)?\\s*([0-9A-Za-z]{4,8})\\s+(?:as|to|for|para|pour|um|zu|on|untuk|pour|para|sich|в|входа|войти|для)(?![\\p{L}\\p{Nd}])"),
        Regex("(?i)(?<![\\p{L}\\p{Nd}])(?:use|enter|input|utilisez|utilice|utilize|ingrese|entrez|geben\\s+sie|verwenden\\s+sie|use\\s+el|usa|gunakan|используйте|[Ии]спользуйте)\\s+(?:le\\s+code\\s+de\\s+\\w+|el\\s+código\\s+de\\s+\\w+|a\\s+senha\\s+de\\s+\\w+|код\\s+проверки|[Кк]од\\s+проверки|código\\s+de\\s+validação)?\\s*([0-9A-Za-z]{4,8})(?![0-9A-Za-z])"),

        // 4e. Direct action/use phrasing without 'to/for': "use one time password 7918", "enter one-time password 7918", "use code 7918", "your one-time password 7918", "One Time Password (OTP) 859878"
        // Catches SHEIN, Uber, banks worldwide in English, Spanish, French, Portuguese, etc.
        Regex("(?i)\\b(?:use|enter|input|your|the|su|tu|votre|seu|usa|use\\s+el|utilice|ingrese|entrez|gunakan)?\\s*(?:one[- ]time\\s*(?:password|passcode|code)|verification\\s*(?:code|otp|pin)|security\\s*(?:code|otp|pin)|login\\s*(?:code|otp|pin)|access\\s*(?:code|otp|pin)|auth\\s*(?:code|otp|pin)|a?otp|passcode|pin|chave\\s+de\\s+segurança|clave\\s+de\\s+seguridad|código\\s+de\\s+seguridad|código\\s+de\\s+segurança|senha\\s+de\\s+uso\\s+único|contraseña\\s+de\\s+un\\s+solo\\s+uso|kata\\s+sandi\\s+sekali\\s+pakai)(?:\\s*\\([a-zA-Z0-9 ]+\\))?\\s*(?:is|of|as|to|é|es|est)?\\s*[:=–\\-#]?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 5. Reverse phrasing: "4829 is your Uber code", "256587 is your LOGIN OTP for Flipkart", "918201 is your verification code", "582910 è il tuo codice di autorizzazione", "582914 adalah kode OTP"
        Regex("(?i)\\b([0-9A-Za-z]{4,10})\\s+(?:is|es|est|é|è|lautet|hai|adalah)\\s+(?:your|su|votre|dein|il\\s+tuo|la\\s+tua|il|la|aapka|apka|the|an?|kode)?\\s*(?:[\\w-]+\\s+){0,4}(?:otp|code|pin|passcode|one[- ]time\\s*(?:password|passcode|code)|verification\\s*(?:code|otp|pin)|security\\s*(?:code|otp|pin)|login\\s*(?:code|otp|pin)|access\\s*(?:code|otp|pin)|auth(?:entication|orization|orizzazione)?\\s*(?:code|otp|pin)|confirmation\\s*(?:code|pin)|autorizzazione|kode|verifikasi)\\b"),

        // 5b. Simpler reverse: "256587 is your OTP" / "123456 is the OTP" / "4829 is your Uber code"
        Regex("(?i)\\b([0-9A-Za-z]{4,10})\\s+is\\s+(?:your|the|an?)?\\s*(?:[A-Za-z0-9_-]+\\s+)?(?:a?otp|one[- ]time(?:\\s+password|\\s+code)?|verification(?:\\s+code)?|security(?:\\s+code)?|login(?:\\s+otp)?|access(?:\\s+code)?|code|password|passcode|pin)\\b"),

        // 6. Multi-line isolated code on its own line after a label or "code below"
        Regex("(?im)(?:verification\\s+code|security\\s+code|one[- ]time\\s+(?:password|code)|your\\s+code|código|code|passcode)[^\\n\\r]*?(?:below|is|:)?\\s*$\\s*^([0-9A-Za-z]{4,10})\\s*$"),

        // 7. Bank SMS, GST & service template with context before "is/are/:"
        Regex("(?i)\\b(?:one[- ]time\\s+(?:password|passcode|code|pin)|verification\\s+(?:code|pin|token|otp)|security\\s+(?:code|pin|token|otp)|auth(?:entication|orization)?\\s+(?:code|pin|token|otp)|confirm(?:ation)?\\s+(?:code|pin|token|otp)|login\\s+(?:code|pin|token|otp)|sign[- ]in\\s+(?:code|pin|token|otp)|access\\s+(?:code|pin|token|otp)|delivery\\s+otp|pickup\\s+otp|secret\\s+(?:code|pin)|passcode|a?otp|[\\w-]+\\s+(?:otp|code)|pin)(?:[^.!\n\r]{0,80})(?<!\\d)(?:is|are|es|est|lautet|é|è|:|=)(?!\\d)\\s*[:=]?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 7b. Universal service/brand OTP template: "Your OTP for Internet Lifestyle Hub is 123456", "OTP to login to Portal is 849201"
        Regex("(?i)\\b(?:your|the)?\\s*(?:a?otp|code|pin|passcode|verification\\s+code)\\s+(?:for|to)\\s+[^.!,:\n\r]{1,50}?(?:\\s+is\\s*[:=]?|[:=])\\s*([0-9A-Za-z]{4,10})\\b"),

        // 8. Direct keyword + colon/dash/is + OTP: "Telegram code: 49201", "WhatsApp code: 829104", "Код: 582914", "رمز: 83910"
        Regex("(?i)(?:^|[^\\p{L}\\p{Nd}])(?:code|otp|pin|passcode|[Кк]од|[Пп]ароль|رمز|کد|कोड)\\s*(?:is\\s*:?|:|=)\\s*[:=]?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 9. English assignment: "Your OTP is 482913", "verification code: 482913", "pin is 8492"
        Regex("(?i)\\b(?:one[- ]time\\s+(?:password|code)|verification\\s+code|security\\s+code|login\\s+code|sign[- ]in\\s+code|access\\s+code|auth(?:entication)?\\s+code|confirmation\\s+code|secret\\s+code|single[- ]use\\s+code|passcode|a?otp|pin(?:\\s+code)?)\\s*(?:is|:|\u2013|-|=|#)\\s*([0-9A-Za-z]{4,10})\\b"),

        // 10. Hindi & Hinglish: "Aapka login OTP 849201 hai", "Apna OTP 392018", "गुप्त कोड 618294 है", "वन टाइम पासवर्ड 859878", "पिन 5928 है"
        Regex("(?i)(?:(?:aapka|apna|लॉगिन|login|आपका|अपना|खाते\\s+में\\s+लॉगिन\\s+करने\\s+के\\s+लिए)\\s+)*(?:otp|ओटीपी|सत्यापन\\s+код|सत्यापन\\s+कोड|सुरक्षा\\s+कोड|गुप्त\\s+कोड|वन[- ]?टाइम\\s+पासवर्ड|डिलीवरी\\s+ओटीपी|सत्यापन\\s+पिन|डिलीवरी\\s+पिन|गुप्त\\s+ओटीपी|प्रमाणीकरण\\s+कोड|पिन)\\s*(?:hai|है|:|=|-|\\s)?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 11. Bengali assignment
        Regex("(?i)(?:আপনার\\s+)?(?:যাচাইকরণ\\s+কোড|নিশ্চিতকরণ\\s+কোড|সুরক্ষা\\s+কোড|লগইন\\s+ওটিপি|ওটিপি|নিরাপত্তা\\s+কোড|ভেরিফিケーション\\s+পিন|লগইন\\s+পিন|ডেলিভারি\\s+ওটিপি|যাচাইকরণ\\s+পিন|ওয়ান[- ]?টাইম\\s+পাসওয়ার্ড|রাইড\\s+শুরুর\\s+পিন)\\s*(?:হল|হলো|:|—|-|=|\u0964|\\s)?\\s*([0-9A-Za-z]{4,10})\\b"),
        Regex("(?i)(?<![\\p{L}\\p{Nd}])([0-9A-Za-z]{4,10})\\s+(?:সুরক্ষা|যাচাইকরণ|লগইন|নিরাপত্তা)?\\s*কোড(?![\\p{L}\\p{Nd}])"),

        // 12. Spanish: "Su código de verificación es 492018", "clave de seguridad 582910", "Clave única 592810"
        Regex("(?i)\\b(?:código|codigo|clave)\\s+(?:única|unica|dinámica|dinamica|de\\s+(?:verificación|verificacion|seguridad|confirmación|confirmacion|acceso|entrega|recogida|firma))\\s*(?:es\\s*:?|:|=|\u2013|-|#|\\s)\\s*([0-9A-Za-z]{4,10})\\b"),
        Regex("(?i)\\b(?:código|codigo|clave)\\s*(?:es\\s*:?|:|=|\u2013|-|#)\\s*([0-9A-Za-z]{4,10})\\b"),

        // 13. French: "Votre code de confirmation est le 739102", "Votre code d'autorisation est 739104"
        Regex("(?i)\\b(?:code\\s+d'(?:autorisation|accès|activation)|code\\s+de\\s+(?:confirmation|vérification|sécurité|validation|retrait|livraison)|mot\\s+de\\s+passe(?:\\s+(?:à\\s+usage\\s+unique|temporaire))?)\\s*(?:est(?:\\s+le)?|est\\s*:?|:|=|-|#)\\s*([0-9A-Za-z]{4,10})\\b"),

        // 14. German: "Ihr Bestätigungscode lautet 829104", "Ihr VR-SecureGo Plus Freigabecode lautet 391028", "Ihr Buchungscode ist 849201"
        Regex("(?i)\\b(?:(?:freigabe|sicherheits|bestätigungs|verifizierungs|aktivierungs|abhol|liefer|buchungs)?code|einmalpasswort|einmalkennwort|einmalcode)(?:[^.!\n\r]{0,35})?\\s*(?:lautet|ist|:|=|-|#)\\s*[:=]?\\s*([0-9A-Za-z]{4,10})\\b"),
        Regex("(?i)\\blautet\\s+(?:ihr\\s+)?(?:sicherheits|bestätigungs|verifizierungs|freigabe)?(?:code|einmalpasswort|passwort)\\s*[:=—–-]?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 15. Portuguese: "Seu código de verificação é 618294", "Chave de segurança 582910", "código de validação é 839104"
        Regex("(?i)\\b(?:código|codigo|chave)\\s+(?:de\\s+(?:verificación|verificacao|segurança|seguranca|confirmação|confirmacao|acesso|validação|validacao|entrega|levantamento|recolha))\\s*(?:é\\s*:?|e|:|=|-|#|\\s)\\s*([0-9A-Za-z]{4,10})\\b"),
        Regex("(?i)\\b(?:código|codigo|chave)\\s*(?:é\\s*:?|:|=|-|#)\\s*([0-9A-Za-z]{4,10})\\b"),

        // 16. Italian: "Il tuo codice di verifica è 618294", "codice di autorizzazione"
        Regex("(?i)\\b(?:codice\\s+di\\s+(?:verifica|sicurezza|conferma|autorizzazione|accesso|consegna|ritiro)|codice\\s+dispositivo|codice\\s+otp)\\s*(?:è\\s*:?|e|:|=|-|#)\\s*([0-9A-Za-z]{4,10})\\b"),

        // 17. Russian: "Ваш проверочный код: 391820", "Код 492018 для подтверждения платежа"
        Regex("(?i)[Кк]од\\s+([0-9A-Za-z]{4,10})\\s+для\\s+подтверждения(?![\\p{L}\\p{Nd}])"),
        Regex("(?i)[Кк]од\\s+(?:для\\s+получения|выдачи)[^0-9\\n\\r]{0,30}?\\s*([0-9A-Za-z]{4,10})\\b"),
        Regex("(?i)[Кк]од\\s+получения\\s+заказа[^:]*:\\s*([0-9]{4,10})\\b"),
        Regex("(?i)(?:заказа|посылки)[^0-9\\n\\r]{0,40}?:\\s*([0-9A-Za-z]{4,10})\\b"),
        Regex("(?i)(?:[Вв]аш\\s+[Кк]од(?:\\s+для\\s+входа)?|[Кк]од\\s+(?:подтверждения(?:\\s+операции)?|безопасности|для\\s+входа|для\\s+подтверждения|получения|выдачи)|[Пп]роверочный\\s+[Кк]од|[Оо]дноразовый\\s+[Пп]ароль|[Пп]ароль)\\s*(?:[:=—–-]|это|для\\s+подтверждения|:)\\s*([0-9A-Za-z]{4,10})\\b"),
        Regex("(?i)(?:[Вв]аш\\s+(?:[Пп]роверочный\\s+)?[Кк]од)\\s*[:=—–-]\\s*([0-9A-Za-z]{4,10})\\b"),

        // 18. Chinese: "验证码：582910", "取件码是 839104"
        Regex("(?i)(?:动态验证码|动态密码|动态口令|验证码|驗證碼|确认码|確認碼|安全码|安全碼|动态码|取件码|提货码|取件验证码|身份验证码|核验验证码)(?:为|是|：|:|=|\\s)*([0-9A-Za-z]{4,10})\\b"),

        // 19. Japanese: "認証コードは 928301 です", "Steam ガードコード：8XM3K"
        Regex("(?i)(?:認証コード|確認コード|セキュリティコード|ワンタイムパスワード|ガードコード|Steam\\s*ガードコード|認証番号|配達コード|本人確認コード)(?:は|：|:|=|\\s)*([0-9A-Za-z]{4,10})\\b"),

        // 20. Korean: "인증번호: 829104", "일회용 비밀번호 859878", "인증번호 [739104]", "본인인증 번호는 591028 입니다"
        Regex("(?i)(?:\\[?\\s*(?:(?:본인)?인증\\s*번호(?:는)?|확인\\s*번호(?:는)?|확인\\s*코드(?:는)?|로그인\\s*인증\\s*코드(?:는)?|보안\\s*확인\\s*코드로|인증코드|보안코드|승인번호|일회용\\s*비밀번호|일회용\\s*인증번호)\\s*\\]?)[^0-9\\n\\r]{0,20}?\\s*\\[?([0-9A-Za-z]{4,10})\\]?\\b"),

        // 21. Arabic: "رمز التحقق: 849201", "كود التأكيد هو 849201", "رمز أوبر الخاص بك هو 4918"
        Regex("(?i)(?:رمز|كود)[^0-9\\n\\r]{0,40}?(?:هو|:|—|-|=)\\s*([0-9A-Za-z]{4,10})\\b"),
        Regex("(?i)(?:رمز|كود)\\s+(?:التحقق|الأمان|التأكيد|الدخول)[^0-9\\n\\r]{0,40}?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 22. Turkish: "Doğrulama kodunuz: 829104", "Doğrulama şifreniz 591028", "Aktivasyon kodunuz 849201", "şifreniz 618294"
        Regex("(?i)\\b(?:doğrulama|dogrulama|onay|aktivasyon|güvenlik|guvenlik|uygulaması)\\s+(?:kodu(?:nuz|n)?|şifre(?:niz)?)\\s*(?:[:=—–-]|dur|dir|\\s)?\\s*([0-9A-Za-z]{4,10})\\b"),
        Regex("(?i)\\b(?:şifreniz|kodunuz)\\s*[:=—–-]?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 23. Vietnamese: "Mã xác thực: 829104"
        Regex("(?i)(?:mã\\s+(?:xác\\s+minh|xác\\s+thực|xác\\s+nhận|bảo\\s+mật|otp))[^0-9\\n\\r]{0,30}?(?:là|:|-|=)\\s*([0-9A-Za-z]{4,10})\\b"),

        // 24. Indonesian / Malay: "Kode verifikasi Anda adalah 829104", "Kode OTP Anda adalah 829104"
        Regex("(?i)\\b(?:kode|kod)\\s+(?:verifikasi|keselamatan|pengesahan|konfirmasi|otp|keamanan)[^0-9\\n\\r]{0,35}?(?:adalah|:|-|=)\\s*([0-9A-Za-z]{4,10})\\b"),

        // 25. Marathi: "तुमचा लॉगिन ओटीपी 849201 आहे", "डिलिव्हरी पिन 8492 आहे"
        Regex("(?i)(?:तुमचा|आपला\\s+)?(?:लॉगिन\\s+)?(?:ओटीपी|सत्यापन\\s+код|सत्यापन\\s+कोड|सुरक्षा\\s+कोड|गुप्त\\s+कोड|पडताळणी\\s+कोड|पडताळणी\\s+ओटीपी|डिलिव्हरी\\s+पिन|डिलिव्हरी\\s+ओटीपी|एकवेळचा\\s+पासवर्ड)\\s*(?:आहे|वापरा|:|=|-|\\s)?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 26. Telugu: "మీ ధృవీకరణ కోడ్ 849201", "వన్-టైమ్ పాస్‌వర్డ్ 859878", "మీ ఓటీపీ 749102"
        Regex("(?i)(?:మీ\\s+)?(?:ధృవీకరణ\\s+కోడ్|ధ్రువీకరణ\\s+కోడ్|ఓటీపీ|భద్రతా\\s+కోడ్|రహస్య\\s+ఓటీపీ|వన్[- ]?టైమ్\\s+పాస్‌వర్డ్|ఒకసారి\\s+పాస్‌వర్డ్|డెలివరీ\\s+పిన్|ధృవీకరణ\\s+పిన్|పిన్)\\s*(?:అయింది|ఉపయోగించండి|:|=|-|\\s)?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 27. Tamil: "உங்கள் சரிபார்ப்புக் குறியீடு 849201", "சரிபார்ப்பு பின் 7391", "உங்கள் ஓடிபி 618294"
        Regex("(?i)(?:உங்கள்\\s+)?(?:சரிபார்ப்புக்\\s+குறியீடு|பாதுகாப்புக்\\s+குறியீடு|ஓடிபி|ரகசிய\\s+குறியீடு|ஒருமுறை\\s+கடவுச்சொல்|சரிபார்ப்பு\\s+பின்|டெலிவரி\\s+பின்|பின்|குறியீடு)\\s*(?:ஆகும்|பயன்படுத்தவும்|:|=|-|\\s)?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 28. Gujarati: "તમારો ચકાસણી કોડ 849201 છે", "વેરિફિકેશન કોડ 849201", "તમારો ઓટીપી 491028 છે"
        Regex("(?i)(?:તમારો\\s+)?(?:ચકાસણી\\s+કોડ|સુરક્ષા\\s+કોડ|વેરિફિકેશન\\s+કોડ|ઓટીપી|સિક્રેટ\\s+કોડ|વેરિફિકેશન\\s+પિન|ડિલિવરી\\s+પિન|વન[- ]?ટાઇમ\\s+પાસવર્ડ|કોડ)\\s*(?:છે|વાપરો|:|=|-|\\s)?\\s*([0-9A-Za-z]{4,10})\\b"),

        // 29. LAST RESORT: Any OTP/code/pin keyword directly before digits
        Regex("(?i)\\b(?:otp|a-?otp|verification|code|pin|passcode)\\b\\s*[:\\-=\u2013#]?\\s*\\b([0-9]{4,8})\\b")
    )

    private fun safeLog(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            println("$tag: $msg")
        }
    }

    fun extractOtp(text: String): ExtractionResult? =
        extractOtp(null, null, text, null, 0L)

    fun extractOtp(subject: String?, bodyText: String?, bodyHtml: String?, receivedTimeMs: Long): ExtractionResult? =
        extractOtp(null, subject, bodyText, bodyHtml, receivedTimeMs)

    fun extractOtp(context: Context?, subject: String?, bodyText: String?, bodyHtml: String?, receivedTimeMs: Long): ExtractionResult? {
        return try {
            val rawText = if (!bodyText.isNullOrBlank()) bodyText.trim() else stripHtml(bodyHtml ?: "")
            val truncatedBody = rawText.take(50000)

            // Try extracting from body first (where OTPs reside)
            var result: ExtractionResult? = null
            if (truncatedBody.isNotBlank()) {
                result = extractFromText(context, truncatedBody, receivedTimeMs)
            }
            
            // If not found in body, try subject
            if (result == null && !subject.isNullOrBlank()) {
                result = extractFromText(context, subject, receivedTimeMs)
            }

            // If not found separately, evaluate combined subject + body context
            if (result == null && !subject.isNullOrBlank() && truncatedBody.isNotBlank()) {
                result = extractFromText(context, "$subject\n$truncatedBody", receivedTimeMs)
            }
            
            result
        } catch (t: Throwable) {
            safeLog("OtpExtractor", "Error in extractOtp: ${t.message}")
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(context, "[OtpExtractor] Error during extraction: ${t.message}")
            null
        }
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

        // Convert non-ASCII digits to standard ASCII 0-9 using Unicode category check
        // This is the most robust approach - works for ALL scripts globally
        val sb0 = StringBuilder(normalized.length)
        for (ch in normalized) {
            val digitVal = Character.getNumericValue(ch)
            if (Character.getType(ch) == Character.DECIMAL_DIGIT_NUMBER.toInt() && digitVal in 0..9) {
                sb0.append('0' + digitVal)
            } else {
                sb0.append(ch)
            }
        }
        normalized = sb0.toString()

        // Replace smart quotes and dashes with standard ASCII
        normalized = normalized.replace("–", "-").replace("—", "-")
        normalized = normalized.replace("“", "\"").replace("”", "\"")
        normalized = normalized.replace("‘", "'").replace("’", "'")

        // Normalize all Unicode whitespace (e.g. non-breaking space \u00A0, narrow \u202F, ideographic \u3000) to standard ASCII space
        normalized = normalized.replace(Regex("[\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]"), " ")

        // Strip Android SMS Retriever prefixes like "[#]", "<#>" at start or before digits
        normalized = normalized.replace(Regex("^(?:\\[#\\]|<#>|#)\\s*"), "")
        normalized = normalized.replace(Regex("(?:\\[#\\]|<#>)\\s*(?=\\d)"), "")

        // Strip Google SMS Retriever 11-character hash tokens at the very end of messages (e.g. "281z5M+dN9a")
        normalized = normalized.replace(Regex("(?<=\\s)[a-zA-Z0-9+/]{11}\\b\\s*$"), "")

        // 2. Collapse isolated single digits with newlines e.g. "0\n0\n6\n8\n0\n2" -> "006802"
        normalized = normalized.replace(Regex("(?m)^(\\d)\\s*\\n\\s*(\\d)\\s*\\n\\s*(\\d)\\s*\\n\\s*(\\d)(?:\\s*\\n\\s*(\\d))?(?:\\s*\\n\\s*(\\d))?(?:\\s*\\n\\s*(\\d))?(?:\\s*\\n\\s*(\\d))?$")) { m ->
            m.groupValues.drop(1).filter { it.isNotEmpty() }.joinToString("")
        }

        // 3. Collapse isolated single digits with spaces e.g. "0 0 6 8 0 2" -> "006802", "0   0   6   8   0   2" -> "006802"
        normalized = normalized.replace(Regex("\\b(\\d)\\s+(\\d)\\s+(\\d)\\s+(\\d)(?:\\s+(\\d))?(?:\\s+(\\d))?(?:\\s+(\\d))?(?:\\s+(\\d))?\\b")) { m ->
            m.groupValues.drop(1).filter { it.isNotEmpty() }.joinToString("")
        }

        // 4. Collapse separated by dot/bullet/dash: "0-0-6-8-0-2" or "0·0·6·8·0·2"
        normalized = normalized.replace(Regex("\\b(\\d)\\s*[-·•]\\s*(\\d)\\s*[-·•]\\s*(\\d)\\s*[-·•]\\s*(\\d)(?:\\s*[-·•]\\s*(\\d))?(?:\\s*[-·•]\\s*(\\d))?(?:\\s*[-·•]\\s*(\\d))?(?:\\s*[-·•]\\s*(\\d))?\\b")) { m ->
            m.groupValues.drop(1).filter { it.isNotEmpty() }.joinToString("")
        }

        // 5. Collapse 2-4 digit chunks separated by hyphens or spaces up to 10 total digits: e.g. "849-201-92" -> "84920192", "829-104" -> "829104", "392 810" -> "392810"
        normalized = normalized.replace(Regex("\\b(\\d{2,4})[- ](\\d{2,4})(?:[- ](\\d{2,4}))?\\b")) { m ->
            val g1 = m.groupValues[1]
            val g2 = m.groupValues[2]
            val g3 = m.groupValues[3]
            // Don't collapse year ranges like 2026-27 (4 digits starting with 19/20 followed by 2 digits)
            if (g1.length == 4 && (g1.startsWith("19") || g1.startsWith("20")) && g2.length == 2 && g3.isEmpty()) {
                m.value
            } else {
                val joined = m.groupValues.drop(1).filter { it.isNotEmpty() }.joinToString("")
                if (joined.length in 4..10) joined else m.value
            }
        }

        // 6. Normalize brackets wrapping codes: e.g. "[492018]" -> " 492018 "
        normalized = normalized.replace(Regex("\\[(\\d{4,8})\\]"), " $1 ")
        normalized = normalized.replace(Regex("\\((\\d{4,8})\\)"), " $1 ")

        // 7. Strip invisible unicode characters
        normalized = normalized.replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF]"), "")

        // 8. Normalize quotes and brackets wrapping codes
        normalized = normalized.replace(Regex("(?<=[^\\w]|^)[\"'\\[\\]()<>]+(?=\\d)"), " ")
        normalized = normalized.replace(Regex("(?<=\\d)[\"'\\[\\]()<>]+(?=[^\\w]|$)"), " ")

        // 9. Mask universal toll-free and helpline numbers (e.g. 18001030, 18001234, 1800 1030, 1800-123-456, 1860-xxx)
        normalized = normalized.replace(Regex("(?i)\\b(?:1800|1860|1900|1901|1[- ]?800)[- ]*\\d{3,7}\\b"), "[HELPLINE]")

        // 9b. Mask numbers following contact/helpline/toll-free labels
        normalized = normalized.replace(Regex("(?i)\\b(contact(?:\\s+us)?(?:\\s+on|\\s+at)?|call(?:\\s+us)?(?:\\s+on|\\s+at)?|customer\\s+care(?:\\s*:)?|helpdesk(?:\\s*:)?|helpline(?:\\s*:)?|support(?:\\s*:)?|toll[- ]?free(?:\\s+(?:number|no\\.?)?)?(?:\\s*:)?|reach\\s+us(?:\\s+at)?|dial|fraud(?:\\s+activity)?(?:\\s*[,:]|\\s+please\\s+contact[:\\s]*))\\s*[:=]?\\s*(?:(?:number|no\\.?)\\s*[:=]?\\s*)?\\+?\\d{3,14}\\b")) { m ->
            "${m.groupValues[1]} [HELPLINE]"
        }
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
        // If candidate is preceded by explicit OTP keyword, it is definitely an OTP, not a masked card/account!
        if (Regex("(?i)(?:otp|code|pin|passcode|código|clave|код|ओटीपी|पिन)\\s*(?:is|:|=|-|है)?\\s*$").containsMatchIn(prefix)) {
            return false
        }
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
        val prefix = text.substring(maxOf(0, startPos - 35), startPos).lowercase()
        // If candidate is preceded by explicit OTP keyword, it is definitely an OTP!
        if (Regex("(?i)(?:otp|code|pin|passcode|código|clave|код|ओटीपी|पिन)\\s*(?:is|:|=|-|है)?\\s*$").containsMatchIn(prefix)) {
            return false
        }
        return Regex("(?:ref(?:erence|erencia|érence)?|riferimento|order|pedido|commande|bestellung|ordine|operaci[oó]n|vorgang|transacci[oó]n|transazione|transaction|operazione|заказ|订单|注文|sipariş|مرجع|رقم\\s+المرجع|संदर्भ|txn|ticket|case|awb|tracking|单号|快递单号|invoice|factura|rechnung|statement|beneficiary|destinatario|empfänger|bénéficiaire|store|tienda|magasin|arn|srn|urn|pran|uan|crn|cif|pan|tan|din|cin|gstin|pnr|ack|reg[- ]?\\d*|form[- ]?\\d*|acknowledg(?:e)?ment|application|registration|challan|complaint|notice|circular|appeal|assessment)\\s*(?:(?:no\\.?|num(?:ber)?|id|#|:|-|=)\\s*(?:[a-z]{1,4}[- ]?)?|\\s)\\s*$").containsMatchIn(prefix)
    }

    private fun isPincode(text: String, startPos: Int, candidate: String): Boolean {
        if (candidate.length !in 4..6 || !candidate.all { it.isDigit() }) return false
        val prefix = text.substring(maxOf(0, startPos - 25), startPos).lowercase()
        return Regex("(?:pin(?:\\s*code)?|postal(?:\\s*code)?|zip(?:\\s*code)?|código\\s*postal|code\\s*postal|plz)\\s*[:=]?\\s*$").containsMatchIn(prefix)
    }

    private fun isPhoneNumber(text: String, startPos: Int, candidate: String): Boolean {
        // Universal toll-free and helpline prefixes worldwide (India, US, UK, EU, etc.)
        val tollFreePrefixes = listOf(
            "1800", "1860", "1600", "0800", "0808", "0845", "0870"
        )
        if (tollFreePrefixes.any { candidate.startsWith(it) }) {
            return true
        }

        // Look back 80 characters for helpline/contact indicators
        val prefix80 = text.substring(maxOf(0, startPos - 80), startPos).lowercase()
        val helplineKeywordMatch = Regex("(?:\\b(?:call(?:\\s+us)?|helpline|contact(?:\\s+us)?|tel|phone|dial|support|toll[- ]?free|customer\\s+care|helpdesk|fraud|reach\\s+us|teléfono|téléphone|telefon|servizio\\s+clienti|kundenservice)\\b|संपर्क)").containsMatchIn(prefix80)
        if (helplineKeywordMatch) {
            // Unless there is an explicit OTP assignment intervening directly before candidate
            val explicitOtpBeforeCandidate = Regex("(?i)(?:otp|code|pin|passcode|código|clave|passwort|код|ओटीपी|पासवर्ड)\\s*(?:is|:|=|है)?\\s*$").containsMatchIn(prefix80)
            if (!explicitOtpBeforeCandidate) {
                return true
            }
        }

        // Check if candidate is embedded in or adjacent to a telephone/toll-free number
        val windowStart = maxOf(0, startPos - 25)
        val windowEnd = minOf(text.length, startPos + candidate.length + 25)
        val window = text.substring(windowStart, windowEnd)

        // Toll-free and phone patterns: 1800-xxx-xxxx, 1860-xxx-xxxx, 1-800-xxx-xxxx, (xxx) xxx-xxxx
        if (Regex("(?:1800|1860|1600|1900|1[- ]?800|\\+?\\d{1,3})[- ]+\\d{2,4}[- ]+\\d{3,4}").containsMatchIn(window)) return true
        if (Regex("(?:\\+?\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]\\d{3}[- ]\\d{4}").containsMatchIn(window)) return true
        if (Regex("\\d{3,4}[- ]+\\d{3,4}[- ]+\\d{3,4}").containsMatchIn(window)) return true

        // Toll-free numbers separated by slashes: e.g. 18001234 / 18002100 / 1800111101
        if (Regex("\\d{4,11}\\s*/\\s*\\d{4,11}").containsMatchIn(window)) return true

        // Directly attached by hyphen: e.g. "103-4786" where 103 is preceded by digits
        if (startPos > 0 && text[startPos - 1] == '-' && startPos > 1 && text[startPos - 2].isDigit()) {
            val digitsInWindow = window.count { it.isDigit() }
            if (digitsInWindow >= 7) return true
        }

        return false
    }

    private fun isPostTransactionReceipt(text: String): Boolean {
        val lower = text.lowercase()
        
        // Positive markers of completed post-transaction notifications / bank receipts
        val hasCompletedMarkers = listOf(
            "has been credited", "has been debited", "is credited to", "is debited from",
            "credited to your account", "debited from your account", "credited to a/c", "debited from a/c",
            "total available balance", "available balance", "avl bal", "clear balance",
            "transaction status: successful", "transaction status successful", "transaction successful",
            "transaction details are as follows", "fund transfer successful", "payment successful",
            "transfer successful", "payment received", "payment of inr", "money received",
            "votre compte a été crédité", "votre compte a été débité", "virement effectué",
            "ha sido acreditado", "ha sido debitado", "transferencia exitosa", "pago exitoso",
            "wurde gutgeschrieben", "wurde abgebucht", "überweisung erfolgreich",
            "stato transazione: eseguita", "bonifico effettuato",
            "खाते में जमा", "खाते से निकाले गए", "लेन-देन सफल", "सफलतापूर्वक ट्रांसफर"
        ).any { lower.contains(it) }

        if (!hasCompletedMarkers) return false

        // Check if there is an active OTP requirement to approve/authorize an action
        val hasPendingOtpRequirement = listOf(
            "use otp", "enter otp", "otp is", "your otp", "secret otp", "otp for",
            "otp to", "otp required", "share delivery otp", "código para autorizar",
            "pour autoriser", "pour valider", "um die transaktion zu bestätigen",
            "सत्यापित करने के लिए ओटीपी", "लेन-देन के लिए ओटीपी", "ओटीपी दर्ज करें"
        ).any { lower.contains(it) } || Regex("(?i)\\b(?:use|enter)?\\s*(?:an?\\s+)?otp\\s*[:=]?\\s*\\d{4,8}\\b").containsMatchIn(lower)

        return !hasPendingOtpRequirement
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
                "DICH", "BITTE", "KANN", "SEIN", "TODO", "NADA", "SOLO", "CLAVE",
                "NUNCA", "JAMAS", "TIDAK", "PERNAH", "JANGAN", "BUKAN", "BANCO", "AVISO", "ALERT", "ALERTA"
            )
            if (commonWords.contains(candidate.uppercase())) return false
        }

        // Mixed letters: Require at least 2 digits unless it matches common 5-char token (like Steam Guard)
        if (letterCount > 0 && digitCount < 2 && candidate.length != 5) return false

        // Filter out mixed-case strings (like SMS Retriever hashes: Df9YrqIZHWd, FA+9qP15k7j)
        // Authentic alphanumeric OTPs (e.g. Steam Guard 9RF2K, Microsoft) are always purely uppercase or digits!
        if (letterCount > 0 && candidate.any { it.isLowerCase() } && candidate.any { it.isUpperCase() }) return false

        if (isLikelyYear(candidate, text, startPos)) return false
        if (isMaskedCardOrAccount(text, startPos)) return false
        if (isAmountOrCurrency(text, startPos, candidate.length)) return false
        if (isIdentifierOrReference(text, startPos)) return false
        if (startPos > 0) {
            val prefix10 = text.substring(maxOf(0, startPos - 10), startPos)
            val isWebOtp = Regex("(?i)@[a-zA-Z0-9.-]+\\s+#$").containsMatchIn(text.substring(0, startPos))
            if (!isWebOtp && Regex("#[A-Za-z0-9_-]*$").containsMatchIn(prefix10)) return false
        }
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
        
        com.opensourcebhaiya.otpsync.utils.FileLogger.log(context, "[OtpExtractor] --- EVALUATING TEXT ---")
        com.opensourcebhaiya.otpsync.utils.FileLogger.log(context, "[OtpExtractor] Normalized Text: $normalized")

        if (isPostTransactionReceipt(normalized)) {
            safeLog("OtpExtractor", "Message is a completed post-transaction alert/receipt without pending OTP.")
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(context, "[OtpExtractor] Rejected: Message is a post-transaction receipt/alert without pending OTP.")
            return null
        }

        // ══════════════════════════════════════════════════════════════════════════════
        // PHASE 1: HIGH-CONFIDENCE ANCHOR MATCHING (Instant, 100% precision)
        // ══════════════════════════════════════════════════════════════════════════════
        for (pattern in anchorPatterns) {
            for (match in pattern.findAll(normalized)) {
                val candidate = match.groupValues[1]
                val startPos = match.groups[1]?.range?.first ?: match.range.first
                if (isValidCandidate(candidate, normalized, startPos)) {
                    com.opensourcebhaiya.otpsync.utils.FileLogger.log(context, "[OtpExtractor] Phase 1 Anchor Matched: $candidate")
                    safeLog("OtpExtractor", "Phase 1 Anchor Matched: $candidate")
                    return ExtractionResult(candidate, extractExpiry(normalized, receivedTimeMs))
                }
            }
        }
        // ══════════════════════════════════════════════════════════════════════════════
        // PHASE 2: CONTEXTUAL SCORED EVALUATION (For multi-line, formatted emails)
        // ══════════════════════════════════════════════════════════════════════════════
        // Strip informational banking security disclaimers / warnings where "OTP" is mentioned only as a warning advice
        // e.g. "Safe Banking Tip: Never share your OTP...", "Bank never asks for OTP...", "Beware of fraud, do not share OTP..."
        var evalText = normalized.replace(Regex("(?i)(?:safe\\s+banking\\s+tip|security\\s+tip|safety\\s+tip|security\\s+advice|important\\s+notice|caution|disclaimer|warning|aviso\\s+de\\s+seguridad|waspada\\s+penipuan)[^.\\n\\r]*(?:never\\s+share|do\\s+not\\s+share|don'?t\\s+share|never\\s+disclose|never\\s+ask|never\\s+call|ne\\s+partagez\\s+pas|no\\s+comparta|jamás\\s+comparta|teilen\\s+sie\\s+nicht|geben\\s+sie\\s+niemals|nunca\\s+le\\s+solicitará|tidak\\s+pernah\\s+meminta|कभी\\s+भी\\s+शेयर\\s+न\\s+करें|साझा\\s+न\\s+करें)[^.\\n\\r]*[.!]?"), " ")

        evalText = evalText.replace(Regex("(?i)\\b(?:bank\\s+(?:never|will\\s+never)\\s+asks?\\s+for|we\\s+never\\s+ask\\s+for|never\\s+share\\s+your\\s+(?:internet\\s+banking|credentials|bank\\s+details|password|pin|mpin|cvv|otp))[^.\\n\\r]*[.!]?"), " ")

        // Strict gate: If the message contains ZERO genuine OTP keywords, return null immediately.
        // This stops non-OTP marketing emails (like Amazon, Flipkart, WhatsApp newsletters) and bank transaction receipts from false-extracting!
        val hasGenuineKeyword = genuineOtpKeywords.any { kw ->
            if (kw.any { ch -> ch.code > 127 }) {
                evalText.contains(kw, ignoreCase = true)
            } else {
                Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(evalText)
            }
        }

        val hasContextualProximity = proximityKeywords.any { kw ->
            val match = Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).find(evalText)
            if (match != null) {
                val kwPos = match.range.first
                val contextWindow = evalText.substring(maxOf(0, kwPos - 50), minOf(evalText.length, kwPos + 50)).lowercase()
                listOf("verify", "verification", "sign", "login", "auth", "confirm", "secret", "access", "protect", "valid", "seguridad", "verificar", "confirmer", "share", "code", "pin", "passcode").any { 
                    contextWindow.contains(it) 
                }
            } else false
        }

        if (!hasGenuineKeyword && !hasContextualProximity) {
            safeLog("OtpExtractor", "No genuine OTP trigger phrases found. Message is not an OTP.")
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(context, "[OtpExtractor] Rejected: Message contains no genuine OTP trigger.")
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
                com.opensourcebhaiya.otpsync.utils.FileLogger.log(context, "[OtpExtractor] Candidate $candidate rejected by safety validators.")
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
                if (kw.any { ch -> ch.code > 127 }) {
                    var idx = normalized.indexOf(kw, 0, ignoreCase = true)
                    while (idx != -1) {
                        val dist = kotlin.math.abs(startPos - idx)
                        if (dist < minDistance) minDistance = dist
                        idx = normalized.indexOf(kw, idx + 1, ignoreCase = true)
                    }
                } else {
                    Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).findAll(normalized).forEach { m ->
                        val dist = kotlin.math.abs(startPos - m.range.first)
                        if (dist < minDistance) minDistance = dist
                    }
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
                com.opensourcebhaiya.otpsync.utils.FileLogger.log(context, "[OtpExtractor]   -> +80 (Isolated line bonus)")
            }

            // 5. Footer penalty
            if (isAfterFooter(normalized, startPos)) {
                score -= 300
            }

            com.opensourcebhaiya.otpsync.utils.FileLogger.log(context, "[OtpExtractor] Candidate $candidate pos $startPos => Total Score: $score")

            if (score > highestScore) {
                highestScore = score
                bestMatch = candidate
            }
        }

        // Must achieve threshold: for short messages (SMS, RCS, notifications <= 300 chars) threshold is 30 for numeric codes, for long emails 100
        val bestCandidateDigitCount = bestMatch?.count { it.isDigit() } ?: 0
        val minRequiredScore = if (normalized.length <= 300 && bestCandidateDigitCount > 0) 30 else 100
        if (bestMatch != null && highestScore >= minRequiredScore) {
            safeLog("OtpExtractor", "Scored Engine Selected: $bestMatch with score $highestScore (threshold $minRequiredScore)")
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(context, "[OtpExtractor] Final Decision: Extracted $bestMatch with score $highestScore")
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
