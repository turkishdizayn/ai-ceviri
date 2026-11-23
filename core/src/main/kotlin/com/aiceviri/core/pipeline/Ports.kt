package com.aiceviri.core.pipeline

import com.aiceviri.core.audio.AudioClip
import com.aiceviri.core.model.Language
import com.aiceviri.core.model.LanguagePair
import com.aiceviri.core.model.Tone
import com.aiceviri.core.model.Turn

/*
 * Servis sağlayıcıdan bağımsız arayüzler. OpenAI uyumlu uygulama openai/ paketinde.
 * Başka bir sağlayıcı (Google, Azure, yerel model vb.) eklemek için bu arayüzleri
 * uygulamak yeterli; uygulamanın geri kalanı değişmez.
 */

data class Transcript(val text: String, val language: Language? = null)

interface SpeechToText {
    /** @param hint Konuşulan dil biliniyorsa doğruluğu artırmak için verilir. */
    suspend fun transcribe(clip: AudioClip, hint: Language?): Transcript
}

data class TranslationRequest(
    val text: String,
    /** Kaynak dil biliniyorsa; null ise çeviri modeli çiftten hangisi olduğunu tespit eder. */
    val source: Language?,
    val pair: LanguagePair,
    val tone: Tone = Tone.NATURAL,
    val context: List<Turn> = emptyList(),
    /** Ör. "tıp", "otel resepsiyonu" — terim seçimini iyileştirir. */
    val domain: String? = null,
)

data class TranslationResult(val source: Language, val target: Language, val text: String)

interface Translator {
    suspend fun translate(request: TranslationRequest): TranslationResult
}

data class SynthesizedAudio(val bytes: ByteArray, val mimeType: String)

interface TextToSpeech {
    suspend fun synthesize(text: String, language: Language, tone: Tone): SynthesizedAudio
}

/** Sentezlenen sesi çalan taraf (Android'de kulaklığa / hoparlöre yönlendirilir). */
fun interface AudioOutput {
    suspend fun play(audio: SynthesizedAudio, target: com.aiceviri.core.model.Speaker)
}
