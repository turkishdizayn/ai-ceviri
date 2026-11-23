package com.aiceviri.core

import com.aiceviri.core.audio.AudioClip
import com.aiceviri.core.audio.AudioMath
import com.aiceviri.core.audio.UtteranceSegmenter
import com.aiceviri.core.audio.WavEncoder
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioTest {

    private val rate = 16_000
    private val frameSize = rate / 50 // 20 ms

    private fun silence() = ShortArray(frameSize)
    private fun noise(amplitude: Int, seed: Int) = ShortArray(frameSize) { ((it * 7919 + seed * 104729) % (2 * amplitude + 1) - amplitude).toShort() }
    private fun tone(amplitude: Double = 0.3, offset: Int = 0) =
        ShortArray(frameSize) { (sin(2 * PI * 220 * (it + offset) / rate) * amplitude * 32767).toInt().toShort() }

    @Test
    fun wavHeaderIsValid() {
        val clip = AudioClip(shortArrayOf(1, -1, 256, -32768), rate)
        val wav = WavEncoder.encode(clip)
        assertEquals(44 + 8, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        fun int32(at: Int) = (wav[at].toInt() and 0xFF) or (wav[at + 1].toInt() and 0xFF shl 8) or
            (wav[at + 2].toInt() and 0xFF shl 16) or (wav[at + 3].toInt() and 0xFF shl 24)
        assertEquals(36 + 8, int32(4))
        assertEquals(rate, int32(24))
        assertEquals(8, int32(40))
        // -32768 little endian = 0x00 0x80
        assertEquals(0x00.toByte(), wav[50]); assertEquals(0x80.toByte(), wav[51])
    }

    @Test
    fun rmsOfSilenceIsZeroAndToneIsPositive() {
        assertEquals(0.0, AudioMath.rms(silence()))
        assertTrue(AudioMath.rms(tone(0.3)) in 0.2..0.22) // 0.3/sqrt(2) ≈ 0.212
    }

    @Test
    fun segmenterEmitsOneUtteranceAfterSilence() {
        val seg = UtteranceSegmenter(rate, silenceMs = 400, minSpeechMs = 200)
        repeat(20) { assertNull(seg.accept(silence())) }
        repeat(50) { i -> assertNull(seg.accept(tone(offset = i * frameSize))) } // 1 s konuşma
        var clip: AudioClip? = null
        repeat(30) { if (clip == null) clip = seg.accept(silence()) }
        assertNotNull(clip)
        // ~1 s konuşma + 400 ms sessizlik + ön tampon
        assertTrue(clip!!.durationMs in 1300..1700, "süre: ${clip!!.durationMs}")
        assertTrue(!seg.isSpeaking)
    }

    @Test
    fun segmenterIgnoresShortClicks() {
        val seg = UtteranceSegmenter(rate, silenceMs = 300, minSpeechMs = 250)
        repeat(10) { seg.accept(silence()) }
        repeat(3) { seg.accept(tone()) } // 60 ms tık
        var clip: AudioClip? = null
        repeat(40) { clip = clip ?: seg.accept(silence()) }
        assertNull(clip)
    }

    @Test
    fun segmenterAdaptsToBackgroundNoise() {
        val seg = UtteranceSegmenter(rate, minSpeechMs = 300)
        val before = seg.threshold
        // 10 sn sürekli uğultu (≈0.02 RMS, sabit eşiğin üstünde)
        repeat(500) { i -> seg.accept(noise(1200, i)) }
        assertTrue(seg.threshold > before, "eşik gürültüye göre yükselmeli")
        assertTrue(!seg.isSpeaking, "uğultu bir süre sonra konuşma sayılmamalı")
        // Uğultu üstünde gerçek konuşma hâlâ algılanmalı
        var clip: AudioClip? = null
        repeat(40) { seg.accept(tone(offset = it * frameSize)) }
        repeat(100) { i -> clip = clip ?: seg.accept(noise(1200, i)) }
        assertNotNull(clip, "gürültü üstündeki konuşma algılanmalı")
    }

    @Test
    fun maxUtteranceLengthForcesSplit() {
        val seg = UtteranceSegmenter(rate, maxUtteranceMs = 2_000)
        var clip: AudioClip? = null
        var frames = 0
        while (clip == null && frames < 500) { clip = seg.accept(tone(offset = frames * frameSize)); frames++ }
        assertNotNull(clip)
        assertTrue(clip!!.durationMs in 1900..2200)
    }

    @Test
    fun flushReturnsPendingSpeech() {
        val seg = UtteranceSegmenter(rate, minSpeechMs = 100)
        repeat(20) { seg.accept(tone(offset = it * frameSize)) }
        assertNotNull(seg.flush())
        assertNull(seg.flush())
    }
}
