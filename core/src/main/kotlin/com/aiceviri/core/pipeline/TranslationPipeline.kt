package com.aiceviri.core.pipeline

import com.aiceviri.core.audio.AudioClip
import com.aiceviri.core.model.ConversationHistory
import com.aiceviri.core.model.Language
import com.aiceviri.core.model.LanguagePair
import com.aiceviri.core.model.Speaker
import com.aiceviri.core.model.Tone
import com.aiceviri.core.model.Turn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Hattın UI'a bildirdiği olaylar. */
sealed interface PipelineEvent {
    data class Transcribing(val speaker: Speaker?) : PipelineEvent
    data class Transcribed(val speaker: Speaker?, val text: String) : PipelineEvent
    data class Translated(val turn: Turn) : PipelineEvent
    data class Speaking(val turn: Turn) : PipelineEvent
    data object Idle : PipelineEvent
    /** Ses metne çevrildi ama boş/gürültü çıktı; çeviri yapılmadı. */
    data class Discarded(val text: String) : PipelineEvent
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
 * Hız için iki ayrı sıra vardır:
 *  - Tanıma + çeviri sırası: cümleler sırayla çevrilir, bağlam doğru kalır.
 *  - Okuma sırası: bir çeviri okunurken bir sonraki cümle tanınıp çevrilebilir;
 *    seslendirme de okuma sırasını beklemeden önceden hazırlanır.
 */
class TranslationPipeline(
    private val stt: SpeechToText,
    private val translator: Translator,
    private val tts: TextToSpeech?,
    private val output: AudioOutput?,
    val history: ConversationHistory = ConversationHistory(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val translateMutex = Mutex()
    private val speakMutex = Mutex()

    suspend fun process(
        clip: AudioClip,
        speaker: Speaker?,
        config: PipelineConfig,
        onEvent: (PipelineEvent) -> Unit,
    ): Turn? {
        val turn = guarded(onEvent) {
            translateMutex.withLock {
                onEvent(PipelineEvent.Transcribing(speaker))
                val hint = speaker?.let { config.pair.languageOf(it) }
                val transcript = stt.transcribe(clip, hint, sttPrompt(hint, config))
                val text = transcript.text.trim()
                if (text.isEmpty() || isNoise(text)) {
                    onEvent(PipelineEvent.Discarded(text))
                    null
                } else {
                    onEvent(PipelineEvent.Transcribed(speaker, text))
                    translateInternal(text, speaker, transcript, config).also { onEvent(PipelineEvent.Translated(it)) }
                }
            }
        }
        if (turn != null) speak(turn, config, onEvent)
        onEvent(PipelineEvent.Idle)
        return turn
    }

    /** Yazarak çeviri (klavye girişi, foto metni) için: STT adımı atlanır. */
    suspend fun processText(
        text: String,
        speaker: Speaker?,
        config: PipelineConfig,
        onEvent: (PipelineEvent) -> Unit,
    ): Turn? {
        if (text.isBlank()) return null
        val turn = guarded(onEvent) {
            translateMutex.withLock {
                translateInternal(text.trim(), speaker, null, config).also { onEvent(PipelineEvent.Translated(it)) }
            }
        }
        if (turn != null) speak(turn, config, onEvent)
        onEvent(PipelineEvent.Idle)
        return turn
    }

    /** Bir çeviriyi (yeniden) seslendirir. "Tekrar oku" butonu da bunu kullanır. */
    suspend fun speak(turn: Turn, config: PipelineConfig, onEvent: (PipelineEvent) -> Unit, force: Boolean = false) {
        if ((!config.speak && !force) || tts == null || output == null) return
        guarded(onEvent) {
            coroutineScope {
                // Seslendirmeyi hemen başlat; önceki çeviri okunurken hazır olsun
                val audio = async { tts.synthesize(turn.translated, turn.target, config.tone) }
                speakMutex.withLock {
                    val ready = audio.await()
                    onEvent(PipelineEvent.Speaking(turn))
                    // Çeviri, konuşanın KARŞISINDAKİ kişiye çalınır.
                    val listener = if (turn.speaker == Speaker.A) Speaker.B else Speaker.A
                    output.play(ready, listener)
                }
            }
        }
    }

    private suspend fun <T> guarded(onEvent: (PipelineEvent) -> Unit, block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onEvent(PipelineEvent.Failed(e))
            null
        }

    /**
     * Konuşma tanımaya ipucu: aynı dildeki son cümleler ve konu.
     * Yalnızca dil biliniyorsa verilir (aksi halde ipucu dili yanlış algılatabilir).
     */
    private fun sttPrompt(hint: Language?, config: PipelineConfig): String? {
        if (hint == null) return null
        val recent = history.recent(config.contextTurns)
            .filter { it.source.code == hint.code }
            .takeLast(2)
            .joinToString(" ") { it.original }
        return buildPromptText(config.domain, recent)
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

    companion object {
        /**
         * STT modellerinin sessizlik/gürültüde uydurduğu bilinen kalıplar.
         * Bunlar çevrilmez.
         */
        private val hallucinations = setOf(
            "altyazı m.k.", "altyazı m.k", "thanks for watching!", "thanks for watching", "thank you for watching.",
            "thank you for watching!", "abone olmayı unutmayın", "abone olmayı unutmayın.",
            "izlediğiniz için teşekkürler.", "izlediğiniz için teşekkür ederim.",
            "subtitles by the amara.org community", "please subscribe.", "like and subscribe.",
            "untertitel im auftrag des zdf, 2017", "sous-titres réalisés para la communauté d'amara.org",
            "продолжение следует...", "ご視聴ありがとうございました",
        )

        fun isNoise(text: String): Boolean {
            val t = text.trim().lowercase()
            if (t.length < 2) return true
            if (t.all { !it.isLetterOrDigit() }) return true
            if (t in hallucinations) return true
            // Aynı kelimenin anlamsız tekrarı ("ve ve ve ve ve…") tipik STT uydurmasıdır
            val words = t.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.size >= 6 && words.toSet().size <= 2) return true
            return false
        }

        fun buildPromptText(domain: String?, recent: String): String? {
            val parts = listOfNotNull(domain?.takeIf { it.isNotBlank() }, recent.takeIf { it.isNotBlank() })
            if (parts.isEmpty()) return null
            return parts.joinToString(". ").takeLast(400)
        }
    }
}
