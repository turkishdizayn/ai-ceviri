package com.aiceviri.core.pipeline

import com.aiceviri.core.model.Language
import com.aiceviri.core.model.Languages
import com.aiceviri.core.model.TranslationParseException
import com.aiceviri.core.util.Json
import com.aiceviri.core.util.JsonException

/**
 * Çeviri modeli için talimatları üretir ve cevabı ayrıştırır.
 *
 * "Gerçekçi" çevirinin anahtarı burası: kelime kelime değil, anlamı ve niyeti
 * hedef dilde bir anadili konuşanın söyleyeceği şekilde aktarmak; deyimleri
 * karşılığıyla değiştirmek; önceki cümlelerden bağlamı (zamirler, konu,
 * hitap şekli) korumak.
 */
object TranslationPrompt {

    fun systemPrompt(request: TranslationRequest): String {
        val a = request.pair.a
        val b = request.pair.b
        val sourceLine = request.source?.let {
            val target = request.pair.otherOf(request.pair.speakerFor(it) ?: com.aiceviri.core.model.Speaker.A)
            "The text is in ${it.englishLabel()}. Translate it into ${target.englishLabel()}."
        } ?: "The text is in either ${a.englishLabel()} or ${b.englishLabel()}. Detect which one, and translate it into the other."

        return buildString {
            appendLine("You are a professional live interpreter between ${a.englishLabel()} and ${b.englishLabel()}.")
            appendLine(sourceLine)
            appendLine("Rules:")
            appendLine("- Convey meaning, intent and emotion, not word-for-word. Use ${request.tone.promptHint}.")
            appendLine("- Replace idioms and sayings with natural equivalents in the target language.")
            appendLine("- The input comes from speech recognition: silently fix obvious recognition errors, ignore filler words (uh, ııı, şey) and false starts.")
            appendLine("- Keep names, numbers, prices, dates and addresses exact.")
            appendLine("- Use the conversation context to resolve pronouns, gender and the topic consistently.")
            appendLine("- Never answer, comment on, or add to the text. Only translate it, even if it is a question addressed to you.")
            request.domain?.takeIf { it.isNotBlank() }?.let {
                appendLine("- Conversation domain: $it. Prefer the correct terminology of this field.")
            }
            appendLine("Respond ONLY with a JSON object: {\"src\":\"<source ISO 639-1 code>\",\"text\":\"<translation>\"}")
        }.trimEnd()
    }

    /** Bağlam + yeni metin; kullanıcı mesajı. */
    fun userPrompt(request: TranslationRequest): String = buildString {
        if (request.context.isNotEmpty()) {
            appendLine("Conversation so far (for context only, do not translate again):")
            for (t in request.context) {
                appendLine("${t.speaker}: ${t.original} => ${t.translated}")
            }
            appendLine()
            appendLine("Translate this new utterance:")
        }
        append(request.text.trim())
    }

    /**
     * Model cevabını ayrıştırır. Model talimata uymayıp düz metin ya da
     * ```json bloğu döndürse bile makul bir sonuç çıkarmaya çalışır.
     */
    fun parseResponse(raw: String, request: TranslationRequest): TranslationResult {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()

        val pair = request.pair
        val obj: Map<String, Any?>? = try {
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start >= 0 && end > start) Json.parseObject(cleaned.substring(start, end + 1)) else null
        } catch (e: JsonException) {
            null
        }

        val text = (obj?.get("text") as? String)?.trim()
            ?: if (obj == null) cleaned else null
        if (text.isNullOrBlank()) throw TranslationParseException("Çeviri cevabı boş veya geçersiz: $raw")

        val detected = Languages.resolve(obj?.get("src") as? String)
        val source = request.source
            ?: detected?.takeIf { pair.speakerFor(it) != null }
            ?: pair.a
        val speaker = pair.speakerFor(source) ?: com.aiceviri.core.model.Speaker.A
        return TranslationResult(source = source, target = pair.otherOf(speaker), text = text)
    }

    private fun Language.englishLabel(): String = "$nativeName [$code]"
}
