package com.aiceviri.app

import android.app.Application
import com.aiceviri.app.audio.AudioRouter
import com.aiceviri.app.data.SettingsRepository
import com.aiceviri.app.net.OkHttpTransport

/** Basit bağımlılık kabı (DI kütüphanesi olmadan). */
class AiCeviriApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var router: AudioRouter
        private set
    val http by lazy { OkHttpTransport() }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        router = AudioRouter(this)
    }
}
