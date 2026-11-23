package com.aiceviri.core.model

import com.aiceviri.core.util.Json

/** Kaydedilmiş bir konuşma (Kayıtlar ekranı). */
data class ConversationRecord(
    val id: String,
    /** Hangi modda yapıldığı (ör. "FACE_TO_FACE", "PHOTO"). */
    val mode: String,
    val startedAtMs: Long,
    val turns: List<Turn>,
) {
    val title: String
        get() = turns.firstOrNull()?.original?.let { if (it.length > 60) it.take(57) + "…" else it } ?: "Boş konuşma"

    val languages: String
        get() = turns.flatMap { listOf(it.source, it.target) }.distinctBy { it.code }.joinToString(" ⇄ ") { it.nameTr }

    fun toJson(): String = Json.stringify(
        mapOf(
            "v" to 1,
            "id" to id,
            "mode" to mode,
            "startedAt" to startedAtMs,
            "turns" to turns.map {
                mapOf(
                    "id" to it.id,
                    "speaker" to it.speaker.name,
                    "src" to it.source.code,
                    "tgt" to it.target.code,
                    "original" to it.original,
                    "translated" to it.translated,
                    "ts" to it.timestampMs,
                )
            },
        )
    )

    companion object {
        /** Bozuk veya tanınmayan kayıtta null döner (uygulama çökmez). */
        fun fromJson(text: String): ConversationRecord? = runCatching {
            val o = Json.parseObject(text)
            val turns = (o["turns"] as List<*>).mapNotNull { raw ->
                val t = raw as? Map<*, *> ?: return@mapNotNull null
                val src = Languages.byCode(t["src"] as? String) ?: return@mapNotNull null
                val tgt = Languages.byCode(t["tgt"] as? String) ?: return@mapNotNull null
                Turn(
                    id = (t["id"] as Number).toLong(),
                    speaker = Speaker.valueOf(t["speaker"] as String),
                    source = src,
                    target = tgt,
                    original = t["original"] as String,
                    translated = t["translated"] as String,
                    timestampMs = (t["ts"] as Number).toLong(),
                )
            }
            ConversationRecord(
                id = o["id"] as String,
                mode = o["mode"] as? String ?: "",
                startedAtMs = (o["startedAt"] as Number).toLong(),
                turns = turns,
            )
        }.getOrNull()
    }
}
