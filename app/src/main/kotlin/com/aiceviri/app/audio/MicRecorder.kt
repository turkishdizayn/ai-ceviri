package com.aiceviri.app.audio

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
 *
 * - Telefon mikrofonu: VOICE_RECOGNITION kaynağı (konuşma tanıma için ayarlı, sesi
 *   kısan telefon-görüşmesi işlemesi yok). Desteklenmezse MIC'e düşer.
 * - Kulaklık (Bluetooth SCO) mikrofonu: VOICE_COMMUNICATION kaynağı (SCO yalnızca
 *   bu kaynakla gelir) + gürültü bastırma.
 */
class MicRecorder(val sampleRate: Int = 16_000) {

    val frameSize: Int = sampleRate / 50

    /** RECORD_AUDIO izni çağıran tarafından kontrol edilir. */
    @SuppressLint("MissingPermission")
    fun frames(preferredDevice: AudioDeviceInfo?, headsetMic: Boolean): Flow<ShortArray> = flow {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(minBuf, frameSize * 2 * 10)
        val sources = if (headsetMic) {
            listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC)
        } else {
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        }
        var record: AudioRecord? = null
        for (source in sources) {
            val r = runCatching {
                AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            }.getOrNull()
            if (r != null && r.state == AudioRecord.STATE_INITIALIZED) {
                record = r
                break
            }
            r?.release()
        }
        val rec = record ?: throw IllegalStateException("Mikrofon açılamadı. Başka bir uygulama mikrofonu kullanıyor olabilir.")
        preferredDevice?.let { rec.setPreferredDevice(it) }
        val effects = buildList<AudioEffect> {
            if (headsetMic && NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(rec.audioSessionId)?.let { it.enabled = true; add(it) }
            }
        }
        try {
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Mikrofon kayda başlamadı. Başka bir uygulama mikrofonu kullanıyor olabilir.")
            }
            val buffer = ShortArray(frameSize)
            while (currentCoroutineContext().isActive) {
                var read = 0
                while (read < frameSize) {
                    val n = rec.read(buffer, read, frameSize - read)
                    if (n < 0) throw IllegalStateException("Mikrofon okuma hatası ($n)")
                    read += n
                }
                emit(buffer.copyOf())
            }
        } finally {
            runCatching { rec.stop() }
            effects.forEach { runCatching { it.release() } }
            rec.release()
        }
    }.flowOn(Dispatchers.IO)
}
