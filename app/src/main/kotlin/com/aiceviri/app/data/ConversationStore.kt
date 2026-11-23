package com.aiceviri.app.data

import android.content.Context
import com.aiceviri.core.model.ConversationRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Konuşmaları cihazda (uygulamanın özel klasöründe) JSON dosyaları olarak saklar. */
class ConversationStore(context: Context) {
    private val dir = File(context.filesDir, "conversations").apply { mkdirs() }

    suspend fun save(record: ConversationRecord) = withContext(Dispatchers.IO) {
        if (record.turns.isEmpty()) return@withContext
        val tmp = File(dir, "${record.id}.json.tmp")
        tmp.writeText(record.toJson())
        tmp.renameTo(File(dir, "${record.id}.json"))
    }

    suspend fun list(): List<ConversationRecord> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { runCatching { ConversationRecord.fromJson(it.readText()) }.getOrNull() }
            .sortedByDescending { it.startedAtMs }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
    }
}
