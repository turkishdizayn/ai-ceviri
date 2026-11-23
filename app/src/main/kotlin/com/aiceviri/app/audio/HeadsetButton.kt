package com.aiceviri.app.audio

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent

/**
 * Kulaklığın oynat/duraklat tuşunu (veya tek dokunuşunu) yakalar.
 * Her marka kulaklık bu standart medya tuşu sinyalini gönderir; uygulamada
 * "dinlemeyi başlat/durdur" olarak kullanılır.
 */
class HeadsetButton(context: Context, private val onPress: () -> Unit) {

    private val session = MediaSession(context, "AiCeviriHeadset").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                @Suppress("DEPRECATION")
                val event: KeyEvent? = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                if (event?.action == KeyEvent.ACTION_UP) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_HEADSETHOOK,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            onPress()
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent)
            }
        })
        setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .setState(PlaybackState.STATE_PAUSED, 0, 1f)
                .build()
        )
    }

    fun setActive(active: Boolean) {
        session.isActive = active
    }

    fun release() {
        session.isActive = false
        session.release()
    }
}
