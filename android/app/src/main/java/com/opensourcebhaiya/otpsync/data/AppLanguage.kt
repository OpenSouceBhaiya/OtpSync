package com.opensourcebhaiya.otpsync.data

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    EN("en", "English", "English"),
    HI("hi", "Hindi", "हिन्दी"),
    ES("es", "Spanish", "Español"),
    FR("fr", "French", "Français"),
    DE("de", "German", "Deutsch"),
    PT("pt", "Portuguese", "Português"),
    RU("ru", "Russian", "Русский"),
    JA("ja", "Japanese", "日本語"),
    ZH("zh", "Chinese", "简体中文"),
    AR("ar", "Arabic", "العربية"),
    IT("it", "Italian", "Italiano"),
    ID("id", "Indonesian", "Bahasa Indonesia"),
    TR("tr", "Turkish", "Türkçe"),
    KO("ko", "Korean", "한국어"),
    BN("bn", "Bengali", "বাংলা"),
    MR("mr", "Marathi", "मराठी"),
    TE("te", "Telugu", "తెలుగు"),
    TA("ta", "Tamil", "தமிழ்"),
    GU("gu", "Gujarati", "ગુજરાતી");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: EN
        }
    }
}
