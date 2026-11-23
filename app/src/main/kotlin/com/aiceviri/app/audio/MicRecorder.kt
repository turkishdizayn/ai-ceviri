package com.aiceviri.app.audio

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Mikrofondan 16 kHz mono PCM kareleri (20 ms) üretir.
 * VOICE_COMMUNICATION kaynağı; Bluetooth SCO açıksa kulaklık mikrofonunu,
 * değilse telefon mikrofonunu kullanır ve donanım yankı/gürültü bastırmayı açar.
 */
class MicRecorder(val sampleRate: Int = 16_000) {

    val frameSize: Int = sampleRate / 50

    /** RECORD_AUDIO izni çağıran tarafından kontrol edilir. */
    @SuppressLint("MissingPermission")
    fun frames(preferredDevice: AudioDeviceInfo?): Flow<ShortArray> = flow {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, frameSize * 2 * 10),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Mikrofon açılamadı")
        }
        preferredDevice?.let { record.setPreferredDevice(it) }
        val effects = buildList<AudioEffect> {
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.let { it.enabled = true; add(it) }
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.let { it.enabled = true; add(it) }
        }
        try {
            record.startRecording()
            val buffer = ShortArray(frameSize)
            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < frameSize) {
                    val n = record.read(buffer, read, frameSize - read)
                    if (n < 0) throw IllegalStateException("Mikrofon okuma hatası: $n")
                    read += n
                }
                emit(buffer.copyOf())
            }
        } finally {
            runCatching { record.stop() }
            effects.forEach { runCatching { it.release() } }
            record.release()
        }
    }.flowOn(Dispatchers.IO)
}
