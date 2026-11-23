package com.aiceviri.app.data

import android.content.Context
import com.aiceviri.core.model.Language
import com.aiceviri.core.model.LanguagePair
import com.aiceviri.core.model.Languages
import com.aiceviri.core.model.Tone
import com.aiceviri.core.openai.OpenAiConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = OpenAiConfig.DEFAULT_BASE_URL,
    val sttModel: String = "gpt-4o-transcribe",
    val translateModel: String = "gpt-4o-mini",
    val ttsModel: String = "gpt-4o-mini-tts",
    val ttsVoice: String = "alloy",
    val languageA: Language = Languages.TURKISH,
    val languageB: Language = Languages.ENGLISH,
    val tone: Tone = Tone.NATURAL,
    val domain: String = "",
    /** Kulaklık bağlıysa onun mikrofonunu kullan (Bluetooth SCO). Kapalıysa telefon mikrofonu. */
    val useHeadsetMic: Boolean = false,
    /** Çevirileri sesli oku. */
    val speakTranslations: Boolean = true,
) {
    val pair: LanguagePair
        get() = if (languageA.code != languageB.code) LanguagePair(languageA, languageB)
        else LanguagePair(languageA, if (languageA == Languages.ENGLISH) Languages.TURKISH else Languages.ENGLISH)

    fun toOpenAiConfig() = OpenAiConfig(
        apiKey = apiKey,
        baseUrl = baseUrl.ifBlank { OpenAiConfig.DEFAULT_BASE_URL },
        sttModel = sttModel.ifBlank { "gpt-4o-transcribe" },
        translateModel = translateModel.ifBlank { "gpt-4o-mini" },
        ttsModel = ttsModel.ifBlank { "gpt-4o-mini-tts" },
        ttsVoice = ttsVoice.ifBlank { "alloy" },
    )
}

/**
 * Ayarları SharedPreferences'ta saklar.
 *
 * NOT (üretim): API anahtarını uygulama içinde tutmak prototip içindir.
 * Mağazaya çıkarken anahtar kendi sunucunuzda (proxy) durmalı; uygulama
 * kullanıcı oturumu ile sizin sunucunuza bağlanmalıdır. Bkz. docs/MIMARI.md
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ai_ceviri_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    val settings: StateFlow<AppSettings> = state.asStateFlow()
    val current: AppSettings get() = state.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        save(next)
        state.value = next
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            apiKey = prefs.getString("apiKey", d.apiKey) ?: d.apiKey,
            baseUrl = prefs.getString("baseUrl", d.baseUrl) ?: d.baseUrl,
            sttModel = prefs.getString("sttModel", d.sttModel) ?: d.sttModel,
            translateModel = prefs.getString("translateModel", d.translateModel) ?: d.translateModel,
            ttsModel = prefs.getString("ttsModel", d.ttsModel) ?: d.ttsModel,
            ttsVoice = prefs.getString("ttsVoice", d.ttsVoice) ?: d.ttsVoice,
            languageA = Languages.byCode(prefs.getString("langA", null)) ?: d.languageA,
            languageB = Languages.byCode(prefs.getString("langB", null)) ?: d.languageB,
            tone = runCatching { Tone.valueOf(prefs.getString("tone", d.tone.name)!!) }.getOrDefault(d.tone),
            domain = prefs.getString("domain", d.domain) ?: d.domain,
            useHeadsetMic = prefs.getBoolean("useHeadsetMic", d.useHeadsetMic),
            speakTranslations = prefs.getBoolean("speak", d.speakTranslations),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString("apiKey", s.apiKey)
            .putString("baseUrl", s.baseUrl)
            .putString("sttModel", s.sttModel)
            .putString("translateModel", s.translateModel)
            .putString("ttsModel", s.ttsModel)
            .putString("ttsVoice", s.ttsVoice)
            .putString("langA", s.languageA.code)
            .putString("langB", s.languageB.code)
            .putString("tone", s.tone.name)
            .putString("domain", s.domain)
            .putBoolean("useHeadsetMic", s.useHeadsetMic)
            .putBoolean("speak", s.speakTranslations)
            .apply()
    }
}
