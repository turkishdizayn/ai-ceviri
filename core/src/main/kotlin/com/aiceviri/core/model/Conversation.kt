package com.aiceviri.core.model

/** Konuşmadaki iki taraf. A genelde uygulama sahibi (ör. Türkçe), B karşı taraf. */
enum class Speaker { A, B }

/** Çeviri üslubu. LLM'e verilen talimatı değiştirir. */
enum class Tone(val promptHint: String) {
    NATURAL("natural, everyday spoken language, as a fluent native speaker would say it"),
    FORMAL("polite and formal register suitable for business meetings"),
    CASUAL("relaxed, friendly and informal register"),
}

data class LanguagePair(val a: Language, val b: Language) {
    init {
        require(a.code != b.code) { "İki dil aynı olamaz: ${a.code}" }
    }

    fun languageOf(speaker: Speaker): Language = if (speaker == Speaker.A) a else b
    fun otherOf(speaker: Speaker): Language = if (speaker == Speaker.A) b else a

    /** Tespit edilen dile göre konuşan tarafı bulur; çiftte yoksa null. */
    fun speakerFor(language: Language?): Speaker? = when (language?.code) {
        a.code -> Speaker.A
        b.code -> Speaker.B
        else -> null
    }

    fun swapped(): LanguagePair = LanguagePair(b, a)
}

data class Turn(
    val id: Long,
    val speaker: Speaker,
    val source: Language,
    val target: Language,
    val original: String,
    val translated: String,
    val timestampMs: Long,
)

/**
 * Konuşma geçmişi. Son birkaç tur, çevirinin bağlama uygun ve tutarlı
 * olması için LLM'e bağlam olarak verilir.
 */
class ConversationHistory(private val maxTurns: Int = 200) {
    private val turns = ArrayList<Turn>()
    private var nextId = 1L

    val all: List<Turn> get() = synchronized(turns) { turns.toList() }

    fun add(
        speaker: Speaker,
        source: Language,
        target: Language,
        original: String,
        translated: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): Turn = synchronized(turns) {
        val turn = Turn(nextId++, speaker, source, target, original, translated, timestampMs)
        turns.add(turn)
        while (turns.size > maxTurns) turns.removeAt(0)
        turn
    }

    /** LLM'e gönderilecek son [count] tur. */
    fun recent(count: Int): List<Turn> = synchronized(turns) {
        if (count <= 0) emptyList() else turns.takeLast(count)
    }

    fun clear() = synchronized(turns) { turns.clear() }

    /** Paylaşmak / kaydetmek için düz metin döküm. */
    fun transcript(): String = all.joinToString("\n\n") { t ->
        "[${t.speaker}] ${t.source.code}: ${t.original}\n    → ${t.target.code}: ${t.translated}"
    }
}
