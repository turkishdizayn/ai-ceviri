package com.aiceviri.app.ui

import android.app.Application
import android.media.AudioDeviceInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiceviri.app.AiCeviriApp
import com.aiceviri.app.audio.HeadsetButton
import com.aiceviri.app.audio.HeadsetInfo
import com.aiceviri.app.audio.MicRecorder
import com.aiceviri.app.audio.SpeechPlayer
import com.aiceviri.app.audio.DeviceTextToSpeech
import com.aiceviri.app.audio.SwitchingTextToSpeech
import com.aiceviri.app.data.AppSettings
import com.aiceviri.core.audio.AudioClip
import com.aiceviri.core.audio.AudioMath
import com.aiceviri.core.audio.UtteranceSegmenter
import com.aiceviri.core.model.Speaker
import com.aiceviri.core.model.Turn
import com.aiceviri.core.model.userMessage
import com.aiceviri.core.openai.OpenAiSpeechToText
import com.aiceviri.core.openai.OpenAiTextToSpeech
import com.aiceviri.core.openai.OpenAiTranslator
import com.aiceviri.core.pipeline.PipelineConfig
import com.aiceviri.core.pipeline.PipelineEvent
import com.aiceviri.core.pipeline.TranslationPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ekran görüntülerindeki uygulamanın modlarının karşılıkları. */
enum class Mode(val title: String, val description: String) {
    FACE_TO_FACE(
        "Yüz Yüze",
        "Telefon ortada. Her kişi kendi butonuna basıp konuşur, çeviri hoparlörden okunur.",
    ),
    HEADSET(
        "Kulaklıkla Çeviri",
        "Siz kulaklık takın. Karşınızdakinin sözleri kulağınıza, sizin sözleriniz telefon hoparlöründen ona çevrilir. Dil otomatik algılanır.",
    ),
    LISTEN(
        "Dinle ve Çevir",
        "Rehber, toplantı, ders, anons… Ortamı sürekli dinler, altyazı olarak gösterir ve isterseniz kulaklığınıza okur.",
    ),
}

enum class Screen { HOME, CONVERSATION, SETTINGS }

enum class Status(val label: String) {
    IDLE("Hazır"),
    LISTENING("Dinliyor…"),
    TRANSCRIBING("Anlaşılıyor…"),
    TRANSLATING("Çevriliyor…"),
    SPEAKING("Okunuyor…"),
}

data class UiState(
    val screen: Screen = Screen.HOME,
    val mode: Mode = Mode.FACE_TO_FACE,
    val listening: Boolean = false,
    val activeSpeaker: Speaker? = null,
    val status: Status = Status.IDLE,
    val pendingText: String? = null,
    val turns: List<Turn> = emptyList(),
    val level: Float = 0f,
    val micSource: String = "",
    val error: String? = null,
)

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AiCeviriApp
    private val settingsRepo = app.settings
    private val router = app.router
    private val recorder = MicRecorder()

    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val headset: StateFlow<HeadsetInfo?> = router.headset

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val player = SpeechPlayer(
        context = application,
        routeFor = { listener -> outputFor(listener) },
        voiceCall = { router.isHeadsetMicActive },
    )

    private val deviceTts = DeviceTextToSpeech(application)

    private val pipeline = TranslationPipeline(
        stt = OpenAiSpeechToText({ settingsRepo.current.toOpenAiConfig() }, app.http),
        translator = OpenAiTranslator({ settingsRepo.current.toOpenAiConfig() }, app.http),
        tts = SwitchingTextToSpeech(
            useDevice = { settingsRepo.current.useDeviceTts },
            cloud = OpenAiTextToSpeech({ settingsRepo.current.toOpenAiConfig() }, app.http),
            device = deviceTts,
        ),
        output = player,
    )

    private val headsetButton = HeadsetButton(application) { onHeadsetButton() }

    private var captureJob: Job? = null
    private var segmenter: UtteranceSegmenter? = null

    // ---------- Navigasyon ----------

    fun open(mode: Mode) {
        stopListening()
        _state.update { it.copy(screen = Screen.CONVERSATION, mode = mode, error = null) }
        headsetButton.setActive(true)
        if (mode != Mode.FACE_TO_FACE) startListening(null)
    }

    fun openSettings() {
        stopListening()
        _state.update { it.copy(screen = Screen.SETTINGS) }
    }

    fun back() {
        stopListening()
        player.stop()
        headsetButton.setActive(false)
        _state.update { it.copy(screen = Screen.HOME, error = null) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsRepo.update(transform)

    fun swapLanguages() = settingsRepo.update { it.copy(languageA = it.languageB, languageB = it.languageA) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun clearConversation() {
        pipeline.history.clear()
        _state.update { it.copy(turns = emptyList(), pendingText = null) }
    }

    fun transcript(): String = pipeline.history.transcript()

    // ---------- Dinleme ----------

    /** Yüz Yüze modunda: ilgili kişinin butonu. Basılıyken tekrar basılırsa cümleyi bitirir. */
    fun talk(speaker: Speaker) {
        val s = _state.value
        if (s.listening && s.activeSpeaker == speaker) {
            finishUtterance()
        } else {
            startListening(speaker, singleUtterance = true)
        }
    }

    fun toggleListening() {
        if (_state.value.listening) stopListening() else startListening(null)
    }

    private fun onHeadsetButton() {
        when (_state.value.mode) {
            Mode.FACE_TO_FACE -> talk(Speaker.A)
            else -> toggleListening()
        }
    }

    private fun startListening(speaker: Speaker?, singleUtterance: Boolean = false) {
        if (settingsRepo.current.apiKey.isBlank()) {
            _state.update { it.copy(error = "Önce Ayarlar'dan API anahtarınızı girin.") }
            return
        }
        val previous = captureJob
        val seg = UtteranceSegmenter(recorder.sampleRate)
        segmenter = seg
        captureJob = viewModelScope.launch {
            // Önceki oturum (mikrofon/SCO) tamamen kapanmadan yenisini açma
            previous?.cancelAndJoin()
            val wantHeadsetMic = settingsRepo.current.useHeadsetMic && router.headset.value?.hasMic == true
            val usingHeadsetMic = wantHeadsetMic && router.startHeadsetMic()
            val device = if (usingHeadsetMic) router.headsetMic() else router.phoneMic()
            val source = if (usingHeadsetMic) "Kulaklık mikrofonu" else "Telefon mikrofonu"
            _state.update {
                it.copy(listening = true, activeSpeaker = speaker, status = Status.LISTENING, micSource = source, error = null)
            }
            var done = false
            try {
                recorder.frames(device).takeWhile { !done }.collect { frame ->
                    if (isPlayingOnSpeaker()) {
                        // Hoparlörden okunan çeviriyi tekrar duyup çevirmesin
                        seg.reset()
                        return@collect
                    }
                    val level = (AudioMath.rms(frame) * 8).coerceAtMost(1.0).toFloat()
                    _state.update { it.copy(level = level) }
                    val clip = seg.accept(frame)
                    if (clip != null) {
                        handleClip(clip, speaker)
                        if (singleUtterance) done = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                if (usingHeadsetMic) router.stopHeadsetMic()
                _state.update {
                    it.copy(
                        listening = false,
                        activeSpeaker = null,
                        level = 0f,
                        status = if (it.status == Status.LISTENING) Status.IDLE else it.status,
                    )
                }
            }
        }
    }

    /** Konuşmayı hemen bitir ve eldeki cümleyi çevir. */
    private fun finishUtterance() {
        val speaker = _state.value.activeSpeaker
        segmenter?.flush()?.let { handleClip(it, speaker) }
        stopListening()
    }

    fun stopListening() {
        captureJob?.cancel()
        captureJob = null
        segmenter = null
    }

    private fun handleClip(clip: AudioClip, speaker: Speaker?) {
        viewModelScope.launch {
            pipeline.process(clip, speaker, pipelineConfig(), ::onEvent)
        }
    }

    fun sendText(text: String) {
        val speaker = if (_state.value.mode == Mode.FACE_TO_FACE) Speaker.A else null
        viewModelScope.launch {
            _state.update { it.copy(status = Status.TRANSLATING, pendingText = text) }
            pipeline.processText(text, speaker, pipelineConfig(), ::onEvent)
        }
    }

    private fun pipelineConfig(): PipelineConfig {
        val s = settingsRepo.current
        return PipelineConfig(
            pair = s.pair,
            tone = s.tone,
            domain = s.domain.ifBlank { null },
            speak = s.speakTranslations,
        )
    }

    private fun onEvent(event: PipelineEvent) {
        when (event) {
            is PipelineEvent.Transcribing -> _state.update { it.copy(status = Status.TRANSCRIBING) }
            is PipelineEvent.Transcribed -> _state.update { it.copy(status = Status.TRANSLATING, pendingText = event.text) }
            is PipelineEvent.Translated -> _state.update {
                it.copy(turns = pipeline.history.all, pendingText = null)
            }
            is PipelineEvent.Speaking -> _state.update { it.copy(status = Status.SPEAKING) }
            is PipelineEvent.Idle -> _state.update {
                it.copy(status = if (it.listening) Status.LISTENING else Status.IDLE, pendingText = null)
            }
            is PipelineEvent.Failed -> _state.update {
                it.copy(
                    error = event.error.userMessage(),
                    status = if (it.listening) Status.LISTENING else Status.IDLE,
                    pendingText = null,
                )
            }
        }
    }

    // ---------- Ses yönlendirme ----------

    /**
     * Çevirinin hangi cihazdan çalınacağı.
     * A = uygulama sahibi (kulaklık takan), B = karşıdaki kişi.
     */
    private fun outputFor(listener: Speaker): AudioDeviceInfo? = when (_state.value.mode) {
        Mode.FACE_TO_FACE -> router.phoneSpeaker()
        Mode.HEADSET -> if (listener == Speaker.A) router.headsetOutput() else router.phoneSpeaker()
        Mode.LISTEN -> router.headsetOutput()
    }

    private fun isPlayingOnSpeaker(): Boolean {
        if (!player.playing.value) return false
        // Kulaklıktan çalarken mikrofon (telefon) sesi duymaz; susturmaya gerek yok.
        val mode = _state.value.mode
        return mode == Mode.FACE_TO_FACE || mode == Mode.HEADSET || router.headsetOutput() == null
    }

    override fun onCleared() {
        stopListening()
        headsetButton.release()
        deviceTts.release()
        super.onCleared()
    }
}
