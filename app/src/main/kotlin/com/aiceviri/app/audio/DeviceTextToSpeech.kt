package com.aiceviri.app.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.UtteranceProgressListener
import com.aiceviri.core.model.Language
import com.aiceviri.core.model.Tone
import com.aiceviri.core.pipeline.SynthesizedAudio
import com.aiceviri.core.pipeline.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * Telefonun kendi seslendirme motoru (Google TTS, Samsung TTS vb.).
 * Ücretsizdir, internet gerektirmez, Türkçe dahil çoğu dili destekler.
 * Sesi WAV dosyasına üretir; böylece kulaklık/hoparlör yönlendirmesi
 * bulut seslendirmesiyle aynı şekilde çalışır.
 */
class DeviceTextToSpeech(private val context: Context) : TextToSpeech {

    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val tts: AndroidTts = AndroidTts(context.applicationContext) { status ->
        ready.complete(status == AndroidTts.SUCCESS)
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.complete(true) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { pending.remove(it)?.complete(false) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { pending.remove(it)?.complete(false) }
            }
        })
    }

    override suspend fun synthesize(text: String, language: Language, tone: Tone): SynthesizedAudio {
        if (!ready.await()) throw IllegalStateException("Telefonun seslendirme motoru başlatılamadı.")
        val result = tts.setLanguage(Locale.forLanguageTag(language.locale))
        if (result == AndroidTts.LANG_MISSING_DATA || result == AndroidTts.LANG_NOT_SUPPORTED) {
            throw IllegalStateException(
                "Telefonun seslendirme motoru ${language.nameTr} desteklemiyor. " +
                    "Ayarlar > Metin okuma çıkışı bölümünden dil paketini indirin."
            )
        }
        tts.setSpeechRate(1.0f)
        tts.setPitch(1.0f)

        val id = UUID.randomUUID().toString()
        val file = File(context.cacheDir, "device-tts-$id.wav")
        val done = CompletableDeferred<Boolean>()
        pending[id] = done
        val queued = tts.synthesizeToFile(text, Bundle(), file, id)
        if (queued != AndroidTts.SUCCESS) {
            pending.remove(id)
            throw IllegalStateException("Seslendirme başlatılamadı.")
        }
        val ok = done.await()
        return withContext(Dispatchers.IO) {
            try {
                if (!ok || !file.exists()) throw IllegalStateException("Seslendirme başarısız oldu.")
                SynthesizedAudio(file.readBytes(), "audio/wav")
            } finally {
                file.delete()
            }
        }
    }

    fun release() {
        tts.shutdown()
    }
}

/** Ayara göre bulut seslendirmesi ile telefon seslendirmesi arasında seçim yapar. */
class SwitchingTextToSpeech(
    private val useDevice: () -> Boolean,
    private val cloud: TextToSpeech,
    private val device: TextToSpeech,
) : TextToSpeech {
    override suspend fun synthesize(text: String, language: Language, tone: Tone): SynthesizedAudio =
        if (useDevice()) device.synthesize(text, language, tone) else cloud.synthesize(text, language, tone)
}
