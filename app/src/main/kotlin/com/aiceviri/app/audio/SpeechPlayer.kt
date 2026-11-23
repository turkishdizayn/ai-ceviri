package com.aiceviri.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.MediaPlayer
import android.os.Build
import com.aiceviri.core.model.Speaker
import com.aiceviri.core.pipeline.AudioOutput
import com.aiceviri.core.pipeline.SynthesizedAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Çeviri sesini doğru kişiye çalar.
 *
 * [routeFor] her dinleyici (A/B) için hangi çıkış cihazının kullanılacağını söyler:
 * ör. Kulaklık modunda A'ya (kulaklık takan) kulaklıktan, B'ye telefon hoparlöründen.
 * null dönerse sistemin varsayılan çıkışı kullanılır.
 */
class SpeechPlayer(
    private val context: Context,
    private val routeFor: (Speaker) -> AudioDeviceInfo?,
    private val voiceCall: () -> Boolean,
) : AudioOutput {

    private val _playing = MutableStateFlow(false)
    /** Çalarken mikrofon geçici olarak susturulur (kendi sesini tekrar çevirmesin). */
    val playing: StateFlow<Boolean> = _playing

    private var current: MediaPlayer? = null
    private var stopCurrent: (() -> Unit)? = null

    override suspend fun play(audio: SynthesizedAudio, target: Speaker) {
        val file = withContext(Dispatchers.IO) {
            File.createTempFile("tts", if (audio.mimeType == "audio/wav") ".wav" else ".mp3", context.cacheDir).apply { writeBytes(audio.bytes) }
        }
        try {
            _playing.value = true
            withContext(Dispatchers.Main) { playFile(file, routeFor(target)) }
        } finally {
            _playing.value = false
            file.delete()
        }
    }

    private suspend fun playFile(file: File, device: AudioDeviceInfo?) = suspendCancellableCoroutine<Unit> { cont ->
        val usage = if (voiceCall()) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
        val player = MediaPlayer()
        var finished = false
        fun finish(error: Throwable?) {
            if (finished) return
            finished = true
            runCatching { player.release() }
            if (current === player) {
                current = null
                stopCurrent = null
            }
            if (cont.isActive) {
                if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
            }
        }
        current = player
        // Durdurulduğunda bekleyen okuma da bitmiş sayılır (sıra kilitlenmez)
        val stopThis: () -> Unit = { runCatching { player.stop() }; finish(null) }
        stopCurrent = stopThis
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            if (device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                player.setPreferredDevice(device)
            }
            player.setOnCompletionListener { finish(null) }
            player.setOnErrorListener { _, what, extra ->
                finish(IllegalStateException("Ses çalınamadı (hata $what/$extra)."))
                true
            }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            finish(IllegalStateException("Ses çalınamadı: ${e.message}"))
        }
        cont.invokeOnCancellation {
            android.os.Handler(android.os.Looper.getMainLooper()).post { stopThis() }
        }
    }

    /** Çalan sesi durdurur. Ana iş parçacığından çağrılmalıdır. */
    fun stop() {
        stopCurrent?.invoke()
    }
}
