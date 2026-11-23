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
        current = player
        fun finish() {
            runCatching { player.release() }
            if (current === player) current = null
            if (cont.isActive) cont.resume(Unit)
        }
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
            player.setOnCompletionListener { finish() }
            player.setOnErrorListener { _, _, _ -> finish(); true }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            finish()
        }
        cont.invokeOnCancellation {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    fun stop() {
        current?.let { runCatching { it.stop() } }
    }
}
