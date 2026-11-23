package com.aiceviri.core.audio

import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/** Mono, 16-bit PCM ses parçası. */
class AudioClip(val samples: ShortArray, val sampleRate: Int) {
    val durationMs: Long get() = samples.size * 1000L / sampleRate
}

object WavEncoder {
    /** 16-bit mono PCM'i WAV (RIFF) baytlarına çevirir. STT servislerine böyle gönderilir. */
    fun encode(clip: AudioClip): ByteArray {
        val dataSize = clip.samples.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        fun writeInt(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF); out.write(v shr 16 and 0xFF); out.write(v shr 24 and 0xFF) }
        fun writeShort(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF) }
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeInt(36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeInt(16)              // fmt chunk size
        writeShort(1)             // PCM
        writeShort(1)             // mono
        writeInt(clip.sampleRate)
        writeInt(clip.sampleRate * 2) // byte rate
        writeShort(2)             // block align
        writeShort(16)            // bits per sample
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeInt(dataSize)
        for (s in clip.samples) writeShort(s.toInt())
        return out.toByteArray()
    }
}

object AudioMath {
    /** 0..1 arası RMS seviyesi. */
    fun rms(frame: ShortArray, length: Int = frame.size): Double {
        if (length == 0) return 0.0
        var sum = 0.0
        for (i in 0 until length) {
            val v = frame[i] / 32768.0
            sum += v * v
        }
        return sqrt(sum / length)
    }
}

/**
 * Enerji tabanlı konuşma algılayıcı (VAD) ve cümle bölücü.
 *
 * Mikrofondan gelen kareleri (ör. 20 ms) alır; konuşma başlayınca biriktirir,
 * [silenceMs] kadar sessizlik olunca bir [AudioClip] olarak teslim eder.
 * Ortam gürültüsüne göre eşik kendini ayarlar (adaptif gürültü tabanı), böylece
 * hem sessiz odada hem kalabalık ortamda farklı kulaklık mikrofonlarıyla çalışır.
 */
class UtteranceSegmenter(
    private val sampleRate: Int,
    private val silenceMs: Int = 700,
    private val minSpeechMs: Int = 300,
    private val maxUtteranceMs: Int = 15_000,
    private val preRollMs: Int = 200,
    private val minThreshold: Double = 0.012,
    private val noiseMultiplier: Double = 3.0,
) {
    private val buffer = ArrayList<Short>()
    private val preRoll = ArrayDeque<ShortArray>()
    private var preRollSamples = 0
    private var inSpeech = false
    private var silentSamples = 0
    private var speechSamples = 0
    private var noiseFloor = minThreshold / noiseMultiplier

    /** Şu anki algılama eşiği (UI'da seviye göstergesi için). */
    val threshold: Double get() = maxOf(minThreshold, noiseFloor * noiseMultiplier)
    val isSpeaking: Boolean get() = inSpeech

    /**
     * Bir kare işler. Bir cümle tamamlandıysa onu döndürür, yoksa null.
     */
    fun accept(frame: ShortArray, length: Int = frame.size): AudioClip? {
        val level = AudioMath.rms(frame, length)
        val voiced = level >= threshold
        val copy = frame.copyOf(length)
        // Gürültü tabanı takibi: seviye düşünce hızla iner, yükselince yavaşça çıkar
        // (konuşma sırasında daha da yavaş, ~7 sn). Böylece sabit uğultu (fan, klima,
        // araç) birkaç saniye sonra konuşma sayılmaz; kelime aralarındaki kısa
        // sessizlikler ise tabanı tekrar aşağı çeker.
        val rise = if (inSpeech) 0.003 else 0.01
        noiseFloor = if (level < noiseFloor) noiseFloor * 0.8 + level * 0.2 else noiseFloor * (1 - rise) + level * rise

        if (!inSpeech) {
            if (voiced) {
                inSpeech = true
                silentSamples = 0
                speechSamples = length
                for (f in preRoll) for (s in f) buffer.add(s)
                preRoll.clear(); preRollSamples = 0
                for (s in copy) buffer.add(s)
            } else {
                preRoll.addLast(copy); preRollSamples += length
                val maxPre = sampleRate * preRollMs / 1000
                while (preRollSamples > maxPre && preRoll.isNotEmpty()) {
                    preRollSamples -= preRoll.removeFirst().size
                }
            }
            return null
        }

        for (s in copy) buffer.add(s)
        if (voiced) {
            silentSamples = 0
            speechSamples += length
        } else {
            silentSamples += length
        }

        val totalMs = buffer.size * 1000L / sampleRate
        val silentMs = silentSamples * 1000L / sampleRate
        if (silentMs >= silenceMs || totalMs >= maxUtteranceMs) {
            return finish()
        }
        return null
    }

    /** Kullanıcı "durdur"a bastığında eldeki konuşmayı hemen teslim eder. */
    fun flush(): AudioClip? = if (inSpeech) finish() else null

    fun reset() {
        buffer.clear(); preRoll.clear(); preRollSamples = 0
        inSpeech = false; silentSamples = 0; speechSamples = 0
    }

    private fun finish(): AudioClip? {
        val speechMs = speechSamples * 1000L / sampleRate
        val samples = ShortArray(buffer.size) { buffer[it] }
        reset()
        return if (speechMs >= minSpeechMs) AudioClip(samples, sampleRate) else null
    }
}
