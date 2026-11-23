package com.aiceviri.core

import com.aiceviri.core.model.ConversationHistory
import com.aiceviri.core.model.ConversationRecord
import com.aiceviri.core.model.Languages
import com.aiceviri.core.model.Speaker
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordTest {

    @Test
    fun roundTripsThroughJson() {
        val h = ConversationHistory()
        h.add(Speaker.A, Languages.TURKISH, Languages.ENGLISH, "Merhaba \"dostum\"\nnasılsın?", "Hello \"friend\"\nhow are you?", 1000)
        h.add(Speaker.B, Languages.ENGLISH, Languages.TURKISH, "Fine, thanks", "İyiyim, teşekkürler", 2000)
        val rec = ConversationRecord("abc", "HEADSET", 999, h.all)
        val back = ConversationRecord.fromJson(rec.toJson())
        assertEquals(rec, back)
        assertEquals("Türkçe ⇄ İngilizce", back!!.languages)
        assertEquals("Merhaba \"dostum\"\nnasılsın?", back.title)
    }

    @Test
    fun corruptOrEmptyRecordsAreHandled() {
        assertNull(ConversationRecord.fromJson("{bozuk"))
        assertNull(ConversationRecord.fromJson("""{"id":"x"}"""))
        assertEquals("Boş konuşma", ConversationRecord("x", "", 0, emptyList()).title)
        val long = "a".repeat(100)
        val h = ConversationHistory().apply { add(Speaker.A, Languages.TURKISH, Languages.ENGLISH, long, "b") }
        assertEquals(58, ConversationRecord("x", "", 0, h.all).title.length)
    }
}
