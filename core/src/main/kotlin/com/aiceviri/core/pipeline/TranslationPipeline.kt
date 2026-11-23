package com.aiceviri.core.pipeline

import com.aiceviri.core.audio.AudioClip
import com.aiceviri.core.model.ConversationHistory
import com.aiceviri.core.model.LanguagePair
import com.aiceviri.core.model.Speaker
import com.aiceviri.core.model.Tone
import com.aiceviri.core.model.Turn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Hattın UI'a bildirdiği olaylar. */
sealed interface PipelineEvent {
    data class Transcribing(val speaker: Speaker?) : PipelineEvent
    data class Transcribed(val speaker: Speaker?, val text: String) : PipelineEvent
    data class Translated(val turn: Turn) : PipelineEvent
    data class Speaking(val turn: Turn) : PipelineEvent
    data object Idle : PipelineEvent
    data class Failed(val error: Throwable) : PipelineEvent
}

data class PipelineConfig(
    val pair: LanguagePair,
    val tone: Tone = Tone.NATURAL,
    val domain: String? = null,
    /** Çeviriyi sesli okut. "Dinle ve Çevir" modunda kapatılıp sadece altyazı gösterilebilir. */
    val speak: Boolean = true,
    /** LLM'e bağlam olarak verilecek önceki tur sayısı. */
    val contextTurns: Int = 6,
)

/**
 * Konuşma → metin → çeviri → ses hattı.
 *
 * Konuşan taraf biliniyorsa (ör. Yüz Yüze modunda kendi butonuna basan kişi)
 * [speaker] verilir; bilinmiyorsa (otomatik mod) dil, çeviri modeli tarafından
 * çiftteki iki dilden hangisi olduğu tespit edilerek yön belirlenir.
 *
 * Cümleler sırayla işlenir (Mutex), böylece çeviriler karışmaz ve bağlam doğru kalır.
 */
class TranslationPipeline(
    private val stt: SpeechToText,
    private val translator: Translator,
    private val tts: TextToSpeech?,
    private val output: AudioOutput?,
    val history: ConversationHistory = ConversationHistory(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun process(
        clip: AudioClip,
        speaker: Speaker?,
        config: PipelineConfig,
        onEvent: (PipelineEvent) -> Unit,
    ): Turn? = mutex.withLock {
        try {
            onEvent(PipelineEvent.Transcribing(speaker))
            val hint = speaker?.let { config.pair.languageOf(it) }
            val transcript = stt.transcribe(clip, hint)
            val text = transcript.text.trim()
            if (text.isEmpty() || isNoise(text)) {
                onEvent(PipelineEvent.Idle)
                return@withLock null
            }
            onEvent(PipelineEvent.Transcribed(speaker, text))
            val turn = translateInternal(text, speaker, transcript, config)
            onEvent(PipelineEvent.Translated(turn))
            speakIfEnabled(turn, config, onEvent)
            onEvent(PipelineEvent.Idle)
            turn
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onEvent(PipelineEvent.Failed(e))
            null
        }
    }

    /** Yazarak çeviri (klavye girişi) için: STT adımı atlanır. */
    suspend fun processText(
        text: String,
        speaker: Speaker?,
        config: PipelineConfig,
        onEvent: (PipelineEvent) -> Unit,
    ): Turn? = mutex.withLock {
        try {
            if (text.isBlank()) return@withLock null
            val turn = translateInternal(text.trim(), speaker, null, config)
            onEvent(PipelineEvent.Translated(turn))
            speakIfEnabled(turn, config, onEvent)
            onEvent(PipelineEvent.Idle)
            turn
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onEvent(PipelineEvent.Failed(e))
            null
        }
    }

    private suspend fun translateInternal(
        text: String,
        speaker: Speaker?,
        transcript: Transcript?,
        config: PipelineConfig,
    ): Turn {
        val pair = config.pair
        // Kaynak dil: konuşan biliniyorsa onun dili; değilse STT'nin tespit ettiği dil çiftteyse o.
        val knownSource = speaker?.let { pair.languageOf(it) }
            ?: transcript?.language?.takeIf { pair.speakerFor(it) != null }
        val result = translator.translate(
            TranslationRequest(
                text = text,
                source = knownSource,
                pair = pair,
                tone = config.tone,
                context = history.recent(config.contextTurns),
                domain = config.domain,
            )
        )
        val resolvedSpeaker = speaker ?: pair.speakerFor(result.source) ?: Speaker.A
        return history.add(
            speaker = resolvedSpeaker,
            source = result.source,
            target = result.target,
            original = text,
            translated = result.text,
            timestampMs = clock(),
        )
    }

    private suspend fun speakIfEnabled(turn: Turn, config: PipelineConfig, onEvent: (PipelineEvent) -> Unit) {
        if (!config.speak || tts == null || output == null) return
        val audio = tts.synthesize(turn.translated, turn.target, config.tone)
        onEvent(PipelineEvent.Speaking(turn))
        // Çeviri, konuşanın KARŞISINDAKİ kişiye çalınır.
        val listener = if (turn.speaker == Speaker.A) Speaker.B else Speaker.A
        output.play(audio, listener)
    }

    companion object {
        /**
         * STT modellerinin sessizlik/gürültüde uydurduğu bilinen kalıplar.
         * Bunlar çevrilmez.
         */
        private val hallucinations = setOf(
            "altyazı m.k.", "altyazı m.k", "thanks for watching!", "thank you for watching.",
            "abone olmayı unutmayın", "abone olmayı unutmayın.", "izlediğiniz için teşekkürler.",
            "subtitles by the amara.org community",
        )

        fun isNoise(text: String): Boolean {
            val t = text.trim().lowercase()
            if (t.length < 2) return true
            if (t.all { !it.isLetterOrDigit() }) return true
            return t in hallucinations
        }
    }
}
