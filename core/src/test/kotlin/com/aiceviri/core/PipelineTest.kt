package com.aiceviri.core

import com.aiceviri.core.audio.AudioClip
import com.aiceviri.core.model.ApiHttpException
import com.aiceviri.core.model.ApiKeyMissingException
import com.aiceviri.core.model.Language
import com.aiceviri.core.model.LanguagePair
import com.aiceviri.core.model.Languages
import com.aiceviri.core.model.Speaker
import com.aiceviri.core.model.Tone
import com.aiceviri.core.model.userMessage
import com.aiceviri.core.openai.HttpResponse
import com.aiceviri.core.openai.HttpTransport
import com.aiceviri.core.openai.MultipartBuilder
import com.aiceviri.core.openai.OpenAiConfig
import com.aiceviri.core.openai.OpenAiSpeechToText
import com.aiceviri.core.openai.OpenAiTextToSpeech
import com.aiceviri.core.openai.OpenAiTranslator
import com.aiceviri.core.pipeline.AudioOutput
import com.aiceviri.core.pipeline.PipelineConfig
import com.aiceviri.core.pipeline.PipelineEvent
import com.aiceviri.core.pipeline.SpeechToText
import com.aiceviri.core.pipeline.SynthesizedAudio
import com.aiceviri.core.pipeline.TextToSpeech
import com.aiceviri.core.pipeline.Transcript
import com.aiceviri.core.pipeline.TranslationPipeline
import com.aiceviri.core.pipeline.TranslationRequest
import com.aiceviri.core.pipeline.TranslationResult
import com.aiceviri.core.pipeline.Translator
import com.aiceviri.core.util.Json
import com.aiceviri.core.util.path
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipelineTest {

    private val tr = Languages.TURKISH
    private val en = Languages.ENGLISH
    private val pair = LanguagePair(tr, en)
    private val clip = AudioClip(ShortArray(16_000), 16_000)

    private class FakeStt(var text: String, var lang: Language? = null) : SpeechToText {
        var lastHint: Language? = null
        override suspend fun transcribe(clip: AudioClip, hint: Language?): Transcript {
            lastHint = hint
            return Transcript(text, lang)
        }
    }

    private class FakeTranslator(val detect: Language) : Translator {
        val requests = mutableListOf<TranslationRequest>()
        override suspend fun translate(request: TranslationRequest): TranslationResult {
            requests += request
            val src = request.source ?: detect
            val target = request.pair.otherOf(request.pair.speakerFor(src)!!)
            return TranslationResult(src, target, "[${target.code}] ${request.text}")
        }
    }

    private class FakeTts : TextToSpeech {
        override suspend fun synthesize(text: String, language: Language, tone: Tone) =
            SynthesizedAudio(text.toByteArray(), "audio/mpeg")
    }

    @Test
    fun fullFlowWithKnownSpeaker() = runBlocking<Unit> {
        val stt = FakeStt("Merhaba, nasılsınız?")
        val translator = FakeTranslator(detect = en)
        val played = mutableListOf<Pair<String, Speaker>>()
        val pipeline = TranslationPipeline(stt, translator, FakeTts(), AudioOutput { a, s -> played += String(a.bytes) to s })
        val events = mutableListOf<PipelineEvent>()

        val turn = pipeline.process(clip, Speaker.A, PipelineConfig(pair)) { events += it }

        assertNotNull(turn)
        assertEquals(tr, stt.lastHint)
        assertEquals("[en] Merhaba, nasılsınız?", turn.translated)
        assertEquals(listOf("[en] Merhaba, nasılsınız?" to Speaker.B), played) // karşı tarafa çalındı
        assertIs<PipelineEvent.Transcribing>(events[0])
        assertIs<PipelineEvent.Transcribed>(events[1])
        assertIs<PipelineEvent.Translated>(events[2])
        assertIs<PipelineEvent.Speaking>(events[3])
        assertIs<PipelineEvent.Idle>(events[4])
    }

    @Test
    fun autoModeUsesDetectedDirectionAndBuildsContext() = runBlocking<Unit> {
        val stt = FakeStt("Where is the station?")
        val translator = FakeTranslator(detect = en)
        val pipeline = TranslationPipeline(stt, translator, null, null)
        val cfg = PipelineConfig(pair, contextTurns = 1)

        val t1 = pipeline.process(clip, null, cfg) {}
        assertEquals(Speaker.B, t1!!.speaker)
        assertNull(stt.lastHint)

        stt.text = "Second"; stt.lang = tr
        val t2 = pipeline.process(clip, null, cfg) {}
        assertEquals(Speaker.A, t2!!.speaker) // STT dili çiftteyse kaynak o kabul edilir
        assertEquals(tr, translator.requests[1].source)
        assertEquals(listOf(t1), translator.requests[1].context)
        assertEquals(2, pipeline.history.all.size)
    }

    @Test
    fun noiseAndEmptyTranscriptsAreSkipped() = runBlocking<Unit> {
        val stt = FakeStt("  ")
        val translator = FakeTranslator(en)
        val pipeline = TranslationPipeline(stt, translator, null, null)
        assertNull(pipeline.process(clip, Speaker.A, PipelineConfig(pair)) {})
        stt.text = "Altyazı M.K."
        assertNull(pipeline.process(clip, Speaker.A, PipelineConfig(pair)) {})
        stt.text = "..."
        assertNull(pipeline.process(clip, Speaker.A, PipelineConfig(pair)) {})
        assertTrue(translator.requests.isEmpty())
        assertTrue(!TranslationPipeline.isNoise("Thank you."))
    }

    @Test
    fun errorsAreReportedNotThrown() = runBlocking<Unit> {
        val failing = object : SpeechToText {
            override suspend fun transcribe(clip: AudioClip, hint: Language?): Transcript = throw ApiHttpException(401, "bad key")
        }
        val pipeline = TranslationPipeline(failing, FakeTranslator(en), null, null)
        var failure: Throwable? = null
        assertNull(pipeline.process(clip, Speaker.A, PipelineConfig(pair)) { if (it is PipelineEvent.Failed) failure = it.error })
        assertEquals("API anahtarı geçersiz (401).", failure!!.userMessage())
    }

    @Test
    fun httpErrorMessageShowsStepAndServiceDetail() = runBlocking<Unit> {
        val body = """{"error":{"message":"The model `llama-3.1-8b-instant` does not exist or you do not have access to it.","type":"invalid_request_error","code":"model_not_found"}}"""
        val http = RecordingHttp(ArrayDeque(listOf(HttpResponse(404, body.toByteArray()))))
        val e = assertFailsWith<ApiHttpException> {
            OpenAiTranslator({ OpenAiConfig("k") }, http).translate(TranslationRequest("a", null, pair))
        }
        val msg = e.userMessage()
        assertTrue(msg.startsWith("Çeviri: Model veya servis adresi bulunamadı (404)."), msg)
        assertTrue("llama-3.1-8b-instant" in msg, msg)
    }

    @Test
    fun textInputSkipsStt() = runBlocking<Unit> {
        val pipeline = TranslationPipeline(FakeStt("unused"), FakeTranslator(en), null, null)
        val turn = pipeline.processText("Hesap lütfen", Speaker.A, PipelineConfig(pair, speak = false)) {}
        assertEquals("[en] Hesap lütfen", turn!!.translated)
    }

    // ---- OpenAI protokol katmanı (sahte HTTP ile) ----

    private class RecordingHttp(val responses: ArrayDeque<HttpResponse>) : HttpTransport {
        data class Call(val url: String, val headers: Map<String, String>, val body: ByteArray, val contentType: String)
        val calls = mutableListOf<Call>()
        override suspend fun post(url: String, headers: Map<String, String>, body: ByteArray, contentType: String): HttpResponse {
            calls += Call(url, headers, body, contentType)
            return responses.removeFirst()
        }
    }

    private fun ok(json: String) = HttpResponse(200, json.toByteArray())

    @Test
    fun openAiEndToEndWithFakeHttp() = runBlocking<Unit> {
        val http = RecordingHttp(ArrayDeque(listOf(
            ok("""{"text":"Merhaba"}"""),
            ok("""{"choices":[{"message":{"role":"assistant","content":"{\"src\":\"tr\",\"text\":\"Hello\"}"}}]}"""),
            HttpResponse(200, byteArrayOf(1, 2, 3)),
        )))
        val cfg = { OpenAiConfig(apiKey = "sk-test", baseUrl = "https://example.com/v1/") }
        var played: SynthesizedAudio? = null
        val pipeline = TranslationPipeline(
            OpenAiSpeechToText(cfg, http), OpenAiTranslator(cfg, http), OpenAiTextToSpeech(cfg, http),
            AudioOutput { a, _ -> played = a },
        )
        val turn = pipeline.process(clip, Speaker.A, PipelineConfig(pair)) {}

        assertEquals("Hello", turn!!.translated)
        assertEquals(listOf(
            "https://example.com/v1/audio/transcriptions",
            "https://example.com/v1/chat/completions",
            "https://example.com/v1/audio/speech",
        ), http.calls.map { it.url })
        assertTrue(http.calls.all { it.headers["Authorization"] == "Bearer sk-test" })

        val multipart = String(http.calls[0].body, Charsets.ISO_8859_1)
        assertTrue("name=\"language\"\r\n\r\ntr\r\n" in multipart)
        assertTrue("filename=\"speech.wav\"" in multipart && "RIFF" in multipart)
        assertTrue(http.calls[0].contentType.startsWith("multipart/form-data; boundary="))

        val chat = Json.parse(String(http.calls[1].body))
        assertEquals("gpt-4o-mini", chat.path("model"))
        assertEquals("json_object", chat.path("response_format", "type"))
        assertEquals("Merhaba", chat.path("messages", 1, "content"))

        val speech = Json.parse(String(http.calls[2].body))
        assertEquals("Hello", speech.path("input"))
        assertTrue((speech.path("instructions") as String).contains("English"))
        assertEquals(listOf<Byte>(1, 2, 3), played!!.bytes.toList())
    }

    @Test
    fun ttsInstructionsOnlyForSupportedModelsAndTemperatureOptional() {
        val cfg = OpenAiConfig("k", ttsModel = "tts-1", temperature = null)
        val tts = Json.parse(OpenAiTextToSpeech.buildRequestBody(cfg, "hi", en, Tone.NATURAL))
        assertNull(tts.path("instructions"))
        val chat = Json.parse(OpenAiTranslator.buildRequestBody(cfg, TranslationRequest("a", null, pair)))
        assertNull(chat.path("temperature"))
    }

    @Test
    fun transcriptionParsingHandlesLanguageAndPlainText() {
        assertEquals(en, OpenAiSpeechToText.parseTranscription("""{"text":" Hi ","language":"english"}""", null).language)
        assertEquals("Hi", OpenAiSpeechToText.parseTranscription("""{"text":" Hi "}""", tr).text)
        assertEquals(tr, OpenAiSpeechToText.parseTranscription("düz metin", tr).language)
    }

    @Test
    fun httpErrorsAndMissingKey() = runBlocking<Unit> {
        val http = RecordingHttp(ArrayDeque(listOf(HttpResponse(429, "rate".toByteArray()))))
        val e = assertFailsWith<ApiHttpException> {
            OpenAiTranslator({ OpenAiConfig("k") }, http).translate(TranslationRequest("a", null, pair))
        }
        assertEquals(429, e.code)
        assertFailsWith<ApiKeyMissingException> {
            OpenAiTranslator({ OpenAiConfig("  ") }, http).translate(TranslationRequest("a", null, pair))
        }
        Unit
    }

    @Test
    fun multipartIsWellFormed() {
        val b = MultipartBuilder(boundary = "XYZ").field("model", "m").file("file", "a.wav", "audio/wav", byteArrayOf(65, 66))
        val body = String(b.build())
        assertEquals("--XYZ\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nm\r\n" +
            "--XYZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\nContent-Type: audio/wav\r\n\r\nAB\r\n--XYZ--\r\n", body)
    }
}
