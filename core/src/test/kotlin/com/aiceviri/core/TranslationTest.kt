package com.aiceviri.core

import com.aiceviri.core.model.ConversationHistory
import com.aiceviri.core.model.LanguagePair
import com.aiceviri.core.model.Languages
import com.aiceviri.core.model.Speaker
import com.aiceviri.core.model.Tone
import com.aiceviri.core.model.TranslationParseException
import com.aiceviri.core.pipeline.TranslationPrompt
import com.aiceviri.core.pipeline.TranslationRequest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationTest {

    private val tr = Languages.TURKISH
    private val en = Languages.ENGLISH
    private val de = Languages.byCode("de")!!
    private val pair = LanguagePair(tr, en)

    @Test
    fun languageLookupHandlesCodesLocalesAndNames() {
        assertEquals(tr, Languages.byCode("TR"))
        assertEquals(tr, Languages.byCode("tr-TR"))
        assertEquals(en, Languages.byCode("en_US"))
        assertEquals(de, Languages.resolve("German"))
        assertNull(Languages.resolve("klingon"))
        assertNull(Languages.byCode(""))
        assertEquals(Languages.all.size, Languages.all.map { it.code }.toSet().size, "kodlar benzersiz olmalı")
    }

    @Test
    fun languagePairDirections() {
        assertEquals(en, pair.otherOf(Speaker.A))
        assertEquals(tr, pair.otherOf(Speaker.B))
        assertEquals(Speaker.B, pair.speakerFor(en))
        assertNull(pair.speakerFor(de))
        assertEquals(LanguagePair(en, tr), pair.swapped())
        assertFailsWith<IllegalArgumentException> { LanguagePair(tr, tr) }
    }

    @Test
    fun systemPromptMentionsBothLanguagesToneAndDomain() {
        val p = TranslationPrompt.systemPrompt(TranslationRequest("x", null, pair, Tone.FORMAL, domain = "otel resepsiyonu"))
        assertTrue("Türkçe [tr]" in p && "English [en]" in p)
        assertTrue("Detect which one" in p)
        assertTrue(Tone.FORMAL.promptHint in p)
        assertTrue("otel resepsiyonu" in p)
        assertTrue("JSON" in p)
    }

    @Test
    fun systemPromptWithKnownSourceStatesDirection() {
        val p = TranslationPrompt.systemPrompt(TranslationRequest("x", en, pair))
        assertTrue("The text is in English [en]. Translate it into Türkçe [tr]." in p, p)
        assertFalse("Detect which one" in p)
    }

    @Test
    fun userPromptIncludesContext() {
        val h = ConversationHistory()
        h.add(Speaker.A, tr, en, "Odam hazır mı?", "Is my room ready?")
        val u = TranslationPrompt.userPrompt(TranslationRequest("  Ne zaman?  ", null, pair, context = h.recent(5)))
        assertTrue("A: Odam hazır mı? => Is my room ready?" in u)
        assertTrue(u.endsWith("Ne zaman?"))
        assertEquals("Selam", TranslationPrompt.userPrompt(TranslationRequest("Selam", null, pair)))
    }

    @Test
    fun parsesJsonResponseAndDetectsDirection() {
        val req = TranslationRequest("How much is it?", null, pair)
        val r = TranslationPrompt.parseResponse("""{"src":"en","text":"Bu ne kadar?"}""", req)
        assertEquals(en, r.source); assertEquals(tr, r.target); assertEquals("Bu ne kadar?", r.text)
    }

    @Test
    fun parsesFencedJsonAndPlainText() {
        val req = TranslationRequest("Merhaba", tr, pair)
        assertEquals("Hello", TranslationPrompt.parseResponse("```json\n{\"src\":\"tr\",\"text\":\"Hello\"}\n```", req).text)
        val plain = TranslationPrompt.parseResponse("Hello there", req)
        assertEquals("Hello there", plain.text); assertEquals(en, plain.target)
    }

    @Test
    fun knownSourceWinsOverModelGuess() {
        val req = TranslationRequest("Merhaba", tr, pair)
        val r = TranslationPrompt.parseResponse("""{"src":"en","text":"Hello"}""", req)
        assertEquals(tr, r.source); assertEquals(en, r.target)
    }

    @Test
    fun unknownDetectedLanguageFallsBackToA() {
        val r = TranslationPrompt.parseResponse("""{"src":"de","text":"Hello"}""", TranslationRequest("Hallo", null, pair))
        assertEquals(tr, r.source); assertEquals(en, r.target)
    }

    @Test
    fun emptyTranslationIsRejected() {
        assertFailsWith<TranslationParseException> {
            TranslationPrompt.parseResponse("""{"src":"tr","text":"  "}""", TranslationRequest("a", null, pair))
        }
    }

    @Test
    fun historyKeepsLimitAndRecentOrder() {
        val h = ConversationHistory(maxTurns = 3)
        repeat(5) { h.add(Speaker.A, tr, en, "c$it", "t$it") }
        assertEquals(listOf("c2", "c3", "c4"), h.all.map { it.original })
        assertEquals(listOf("c3", "c4"), h.recent(2).map { it.original })
        assertEquals(emptyList(), h.recent(0))
        assertTrue("c4" in h.transcript())
    }
}
