package com.aiceviri.core.openai

import com.aiceviri.core.audio.AudioClip
import com.aiceviri.core.audio.WavEncoder
import com.aiceviri.core.model.ApiHttpException
import com.aiceviri.core.model.ApiKeyMissingException
import com.aiceviri.core.model.Language
import com.aiceviri.core.model.Languages
import com.aiceviri.core.model.Tone
import com.aiceviri.core.model.TranslationParseException
import com.aiceviri.core.pipeline.SpeechToText
import com.aiceviri.core.pipeline.SynthesizedAudio
import com.aiceviri.core.pipeline.TextToSpeech
import com.aiceviri.core.pipeline.Transcript
import com.aiceviri.core.pipeline.TranslationPrompt
import com.aiceviri.core.pipeline.TranslationRequest
import com.aiceviri.core.pipeline.TranslationResult
import com.aiceviri.core.pipeline.Translator
import com.aiceviri.core.util.Json
import com.aiceviri.core.util.JsonException
import com.aiceviri.core.util.path

/**
 * OpenAI uyumlu API ayarları. baseUrl değiştirilerek OpenAI uyumlu başka
 * servisler (Azure OpenAI proxy'si, Groq, OpenRouter, kendi sunucunuz vb.) kullanılabilir.
 * Model adları Ayarlar ekranından değiştirilebilir.
 */
data class OpenAiConfig(
    val apiKey: String,
    val baseUrl: String = DEFAULT_BASE_URL,
    val sttModel: String = "gpt-4o-transcribe",
    val translateModel: String = "gpt-4o-mini",
    val ttsModel: String = "gpt-4o-mini-tts",
    val ttsVoice: String = "alloy",
    /** Bazı akıl yürütme modelleri temperature kabul etmez; null ise gönderilmez. */
    val temperature: Double? = 0.3,
) {
    val normalizedBaseUrl: String get() = baseUrl.trim().trimEnd('/')

    fun authHeaders(): Map<String, String> {
        if (apiKey.isBlank()) throw ApiKeyMissingException()
        return mapOf("Authorization" to "Bearer ${apiKey.trim()}")
    }

    /** "instructions" (ses tonu talimatı) alanını destekleyen TTS modeli mi? */
    val ttsSupportsInstructions: Boolean get() = ttsModel.startsWith("gpt-")

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }
}

private fun HttpResponse.requireOk(): HttpResponse {
    if (!isSuccessful) throw ApiHttpException(code, bodyText())
    return this
}

class OpenAiSpeechToText(
    private val config: () -> OpenAiConfig,
    private val http: HttpTransport,
) : SpeechToText {

    override suspend fun transcribe(clip: AudioClip, hint: Language?): Transcript {
        val cfg = config()
        val body = MultipartBuilder()
            .field("model", cfg.sttModel)
            .field("response_format", "json")
            .apply { if (hint != null) field("language", hint.code) }
            .file("file", "speech.wav", "audio/wav", WavEncoder.encode(clip))
        val response = http.post(
            url = "${cfg.normalizedBaseUrl}/audio/transcriptions",
            headers = cfg.authHeaders(),
            body = body.build(),
            contentType = body.contentType,
        ).requireOk()
        return parseTranscription(response.bodyText(), hint)
    }

    companion object {
        fun parseTranscription(json: String, hint: Language?): Transcript {
            val obj = try {
                Json.parseObject(json)
            } catch (e: JsonException) {
                // response_format=text dönen servisler için
                return Transcript(json.trim(), hint)
            }
            val text = obj["text"] as? String ?: ""
            val detected = Languages.resolve(obj["language"] as? String)
            return Transcript(text.trim(), detected ?: hint)
        }
    }
}

class OpenAiTranslator(
    private val config: () -> OpenAiConfig,
    private val http: HttpTransport,
) : Translator {

    override suspend fun translate(request: TranslationRequest): TranslationResult {
        val cfg = config()
        val payload = buildRequestBody(cfg, request)
        val response = http.post(
            url = "${cfg.normalizedBaseUrl}/chat/completions",
            headers = cfg.authHeaders(),
            body = payload.toByteArray(Charsets.UTF_8),
            contentType = "application/json; charset=utf-8",
        ).requireOk()
        val content = extractContent(response.bodyText())
        return TranslationPrompt.parseResponse(content, request)
    }

    companion object {
        fun buildRequestBody(cfg: OpenAiConfig, request: TranslationRequest): String {
            val body = linkedMapOf<String, Any?>(
                "model" to cfg.translateModel,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to TranslationPrompt.systemPrompt(request)),
                    mapOf("role" to "user", "content" to TranslationPrompt.userPrompt(request)),
                ),
                "response_format" to mapOf("type" to "json_object"),
            )
            cfg.temperature?.let { body["temperature"] = it }
            return Json.stringify(body)
        }

        fun extractContent(json: String): String {
            val obj = try {
                Json.parse(json)
            } catch (e: JsonException) {
                throw TranslationParseException("Geçersiz JSON cevabı")
            }
            return obj.path("choices", 0, "message", "content") as? String
                ?: throw TranslationParseException("Cevapta içerik yok")
        }
    }
}

class OpenAiTextToSpeech(
    private val config: () -> OpenAiConfig,
    private val http: HttpTransport,
) : TextToSpeech {

    override suspend fun synthesize(text: String, language: Language, tone: Tone): SynthesizedAudio {
        val cfg = config()
        val response = http.post(
            url = "${cfg.normalizedBaseUrl}/audio/speech",
            headers = cfg.authHeaders(),
            body = buildRequestBody(cfg, text, language, tone).toByteArray(Charsets.UTF_8),
            contentType = "application/json; charset=utf-8",
        ).requireOk()
        return SynthesizedAudio(response.body, "audio/mpeg")
    }

    companion object {
        fun buildRequestBody(cfg: OpenAiConfig, text: String, language: Language, tone: Tone): String {
            val body = linkedMapOf<String, Any?>(
                "model" to cfg.ttsModel,
                "voice" to cfg.ttsVoice,
                "input" to text,
                "response_format" to "mp3",
            )
            if (cfg.ttsSupportsInstructions) {
                body["instructions"] = "Speak in ${language.nativeName} with a native accent. " +
                    "Sound like a real person in a live conversation: warm, clear, natural pacing. Register: ${tone.promptHint}."
            }
            return Json.stringify(body)
        }
    }
}
