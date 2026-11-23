package com.aiceviri.core.model

/**
 * Desteklenen bir dil.
 *
 * @param code ISO 639-1 kodu (STT ve LLM'e gönderilen kod).
 * @param nameTr Türkçe görünen ad.
 * @param nativeName Dilin kendi dilindeki adı.
 * @param locale TTS / sistem için BCP-47 etiketi.
 */
data class Language(
    val code: String,
    val nameTr: String,
    val nativeName: String,
    val locale: String,
) {
    override fun toString(): String = "$nameTr ($nativeName)"
}

object Languages {
    val TURKISH = Language("tr", "Türkçe", "Türkçe", "tr-TR")
    val ENGLISH = Language("en", "İngilizce", "English", "en-US")

    val all: List<Language> = listOf(
        TURKISH,
        ENGLISH,
        Language("de", "Almanca", "Deutsch", "de-DE"),
        Language("fr", "Fransızca", "Français", "fr-FR"),
        Language("es", "İspanyolca", "Español", "es-ES"),
        Language("it", "İtalyanca", "Italiano", "it-IT"),
        Language("pt", "Portekizce", "Português", "pt-PT"),
        Language("nl", "Felemenkçe", "Nederlands", "nl-NL"),
        Language("ru", "Rusça", "Русский", "ru-RU"),
        Language("uk", "Ukraynaca", "Українська", "uk-UA"),
        Language("pl", "Lehçe", "Polski", "pl-PL"),
        Language("ar", "Arapça", "العربية", "ar-SA"),
        Language("fa", "Farsça", "فارسی", "fa-IR"),
        Language("az", "Azerbaycan Türkçesi", "Azərbaycanca", "az-AZ"),
        Language("kk", "Kazakça", "Қазақша", "kk-KZ"),
        Language("uz", "Özbekçe", "Oʻzbekcha", "uz-UZ"),
        Language("ka", "Gürcüce", "ქართული", "ka-GE"),
        Language("el", "Yunanca", "Ελληνικά", "el-GR"),
        Language("bg", "Bulgarca", "Български", "bg-BG"),
        Language("ro", "Rumence", "Română", "ro-RO"),
        Language("sr", "Sırpça", "Српски", "sr-RS"),
        Language("sq", "Arnavutça", "Shqip", "sq-AL"),
        Language("zh", "Çince", "中文", "zh-CN"),
        Language("ja", "Japonca", "日本語", "ja-JP"),
        Language("ko", "Korece", "한국어", "ko-KR"),
        Language("hi", "Hintçe", "हिन्दी", "hi-IN"),
        Language("ur", "Urduca", "اردو", "ur-PK"),
        Language("id", "Endonezce", "Bahasa Indonesia", "id-ID"),
        Language("th", "Tayca", "ไทย", "th-TH"),
        Language("vi", "Vietnamca", "Tiếng Việt", "vi-VN"),
        Language("sv", "İsveççe", "Svenska", "sv-SE"),
        Language("he", "İbranice", "עברית", "he-IL"),
    )

    fun byCode(code: String?): Language? {
        if (code.isNullOrBlank()) return null
        val normalized = code.trim().lowercase().substringBefore('-').substringBefore('_')
        return all.firstOrNull { it.code == normalized }
    }

    /** STT servislerinin döndürebildiği İngilizce dil adlarını ("turkish") koda çevirir. */
    private val englishNames = mapOf(
        "turkish" to "tr", "english" to "en", "german" to "de", "french" to "fr",
        "spanish" to "es", "italian" to "it", "portuguese" to "pt", "dutch" to "nl",
        "russian" to "ru", "ukrainian" to "uk", "polish" to "pl", "arabic" to "ar",
        "persian" to "fa", "azerbaijani" to "az", "kazakh" to "kk", "uzbek" to "uz",
        "georgian" to "ka", "greek" to "el", "bulgarian" to "bg", "romanian" to "ro",
        "serbian" to "sr", "albanian" to "sq", "chinese" to "zh", "japanese" to "ja",
        "korean" to "ko", "hindi" to "hi", "urdu" to "ur", "indonesian" to "id",
        "thai" to "th", "vietnamese" to "vi", "swedish" to "sv", "hebrew" to "he",
    )

    fun resolve(codeOrName: String?): Language? {
        if (codeOrName.isNullOrBlank()) return null
        byCode(codeOrName)?.let { return it }
        return byCode(englishNames[codeOrName.trim().lowercase()])
    }
}
