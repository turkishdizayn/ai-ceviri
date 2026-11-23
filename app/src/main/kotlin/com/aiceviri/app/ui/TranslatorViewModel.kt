package com.aiceviri.app.ui

import android.app.Application
import android.media.AudioDeviceInfo
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiceviri.app.AiCeviriApp
import com.aiceviri.app.audio.DeviceTextToSpeech
import com.aiceviri.app.audio.HeadsetButton
import com.aiceviri.app.audio.HeadsetInfo
import com.aiceviri.app.audio.MicRecorder
import com.aiceviri.app.audio.SpeechPlayer
import com.aiceviri.app.audio.SwitchingTextToSpeech
import com.aiceviri.app.data.AppSettings
import com.aiceviri.app.data.ConversationStore
import com.aiceviri.app.photo.PhotoTextRecognizer
import com.aiceviri.core.audio.AudioClip
import com.aiceviri.core.audio.AudioMath
import com.aiceviri.core.audio.UtteranceSegmenter
import com.aiceviri.core.model.ConversationRecord
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
import java.util.UUID

/** Ekran görüntülerindeki uygulamanın modlarının karşılıkları. */
enum class Mode(val title: String, val icon: String, val short: String, val description: String) {
    FACE_TO_FACE(
        "Yüz Yüze", "🗣️", "Telefon ortada, sırayla konuşun",
        "Telefon ortada. Her kişi kendi butonuna basıp konuşur, çeviri hoparlörden okunur.",
    ),
    HEADSET(
        "Kulaklıkla Çeviri", "🎧", "Karşınızdaki kulağınıza çevrilir",
        "Siz kulaklık takın. Karşınızdakinin sözleri kulağınıza, sizin sözleriniz telefon hoparlöründen ona çevrilir. Dil otomatik algılanır.",
    ),
    LISTEN(
        "Dinle ve Çevir", "👂", "Rehber, toplantı, ders",
        "Ortamı sürekli dinler, altyazı olarak gösterir ve isterseniz kulaklığınıza okur.",
    ),
}

enum class Screen { HOME, CONVERSATION, SETTINGS, PHOTO, RECORDS, RECORD_DETAIL }

enum class Status(val label: String) {
    IDLE("Hazır"),
    LISTENING("Dinliyor…"),
    TRANSCRIBING("Anlaşılıyor…"),
    TRANSLATING("Çevriliyor…"),
    SPEAKING("Okunuyor…"),
}

data class PhotoState(
    val busy: Boolean = false,
    val stage: String = "",
    val recognized: String? = null,
    val turn: Turn? = null,
)

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
    val photo: PhotoState = PhotoState(),
    val records: List<ConversationRecord> = emptyList(),
    val selectedRecord: ConversationRecord? = null,
    /** Ayarlar > Test sonuçları. */
    val testing: Boolean = false,
    val diagnostic: String? = null,
)

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AiCeviriApp
    private val settingsRepo = app.settings
    private val router = app.router
    private val recorder = MicRecorder()
    private val store = ConversationStore(application)
    private val ocr by lazy { PhotoTextRecognizer(application) }

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
    /** Bas-konuş (Yüz Yüze): butona basıldığından beri gelen tüm ses. */
    private var pttFrames: ArrayList<ShortArray>? = null

    // Kayıt oturumu
    private var sessionId: String = newSessionId()
    private var sessionStart: Long = System.currentTimeMillis()
    private var sessionMode: String = ""

    // ---------- Navigasyon ----------

    fun open(mode: Mode) {
        stopListening()
        startSession(mode.name)
        _state.update { it.copy(screen = Screen.CONVERSATION, mode = mode, error = null) }
        headsetButton.setActive(true)
        if (mode != Mode.FACE_TO_FACE) startListening(null)
    }

    fun openPhoto() {
        stopListening()
        startSession("PHOTO")
        _state.update { it.copy(screen = Screen.PHOTO, photo = PhotoState(), error = null) }
    }

    fun openSettings() {
        stopListening()
        _state.update { it.copy(screen = Screen.SETTINGS, diagnostic = null) }
    }

    fun openRecords() {
        stopListening()
        _state.update { it.copy(screen = Screen.RECORDS, selectedRecord = null) }
        viewModelScope.launch { _state.update { it.copy(records = store.list()) } }
    }

    fun openRecord(record: ConversationRecord) {
        _state.update { it.copy(screen = Screen.RECORD_DETAIL, selectedRecord = record) }
    }

    fun deleteRecord(record: ConversationRecord) {
        viewModelScope.launch {
            store.delete(record.id)
            _state.update { it.copy(records = store.list(), screen = Screen.RECORDS, selectedRecord = null) }
        }
    }

    fun back() {
        stopListening()
        player.stop()
        headsetButton.setActive(false)
        val target = if (_state.value.screen == Screen.RECORD_DETAIL) Screen.RECORDS else Screen.HOME
        _state.update { it.copy(screen = target, error = null) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsRepo.update(transform)

    fun swapLanguages() = settingsRepo.update { it.copy(languageA = it.languageB, languageB = it.languageA) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Ekranı temizler ve yeni bir kayıt başlatır (eski konuşma Kayıtlar'da kalır). */
    fun clearConversation() {
        startSession(sessionMode)
    }

    fun transcript(): String = pipeline.history.transcript()

    private fun startSession(mode: String) {
        pipeline.history.clear()
        sessionId = newSessionId()
        sessionStart = System.currentTimeMillis()
        sessionMode = mode
        _state.update { it.copy(turns = emptyList(), pendingText = null) }
    }

    private fun saveSession() {
        val record = ConversationRecord(sessionId, sessionMode, sessionStart, pipeline.history.all)
        viewModelScope.launch { runCatching { store.save(record) } }
    }

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
        // 600 ms sessizlik = cümle sonu (hız ile cümleyi bölmeme arasında denge)
        val seg = UtteranceSegmenter(recorder.sampleRate, silenceMs = 600, preRollMs = 300)
        segmenter = seg
        val ptt = if (singleUtterance) ArrayList<ShortArray>() else null
        pttFrames = ptt
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
            var frames = 0
            var peak = 0.0
            var warnedSilence = false
            try {
                recorder.frames(device, usingHeadsetMic).takeWhile { !done }.collect { frame ->
                    if (isPlayingOnSpeaker()) {
                        // Hoparlörden okunan çeviriyi tekrar duyup çevirmesin
                        seg.reset()
                        _state.update { it.copy(level = 0f) }
                        return@collect
                    }
                    val rms = AudioMath.rms(frame)
                    frames++
                    peak = maxOf(peak, rms)
                    _state.update { it.copy(level = levelFor(rms)) }
                    if (!warnedSilence && frames == 250 && peak < 0.002) {
                        // 5 sn boyunca neredeyse hiç ses yok: mikrofon gerçekten çalışmıyor
                        warnedSilence = true
                        _state.update {
                            it.copy(
                                error = "Mikrofondan ses gelmiyor. " + if (usingHeadsetMic) {
                                    "Ayarlar'dan \"Kulaklık mikrofonunu kullan\"ı kapatıp telefon mikrofonunu deneyin."
                                } else {
                                    "Başka bir uygulama mikrofonu kullanıyor olabilir; Ayarlar > Mikrofon testi ile kontrol edin."
                                },
                            )
                        }
                    }
                    if (ptt != null) {
                        ptt.add(frame)
                        if (ptt.size >= MAX_PTT_FRAMES) {
                            // 30 sn sınırı: eldekini gönder
                            done = true
                            pttFrames = null
                            handleClip(clipOf(ptt), speaker)
                            return@collect
                        }
                    }
                    val clip = seg.accept(frame)
                    if (clip != null) {
                        if (ptt != null) pttFrames = null
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
        val clip = segmenter?.flush()
            // Konuşma algılayıcı tetiklenmediyse bile (sessiz mikrofon, uzak konuşma)
            // butona basıldığından beri kaydedilen sesin tamamını gönder.
            ?: pttFrames?.takeIf { it.size >= MIN_PTT_FRAMES }?.let { clipOf(it) }
        pttFrames = null
        if (clip != null) {
            handleClip(clip, speaker)
        } else {
            _state.update { it.copy(pendingText = "Ses çok kısa, butona basıp konuşun, bitince tekrar basın.") }
        }
        stopListening()
    }

    private fun clipOf(frames: List<ShortArray>): AudioClip {
        val total = frames.sumOf { it.size }
        val out = ShortArray(total)
        var pos = 0
        for (f in frames) {
            f.copyInto(out, pos)
            pos += f.size
        }
        return AudioClip(out, recorder.sampleRate)
    }

    /** RMS'i göstergeye uygun 0..1 ölçeğe çevirir (sessiz konuşma da görünür olsun). */
    private fun levelFor(rms: Double): Float = (kotlin.math.sqrt(rms) * 2.5).coerceIn(0.0, 1.0).toFloat()

    fun stopListening() {
        captureJob?.cancel()
        captureJob = null
        segmenter = null
        pttFrames = null
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

    /** Bir çeviriyi tekrar seslendir (konuşmada, fotoğrafta veya kayıtlarda). */
    fun replay(turn: Turn) {
        viewModelScope.launch {
            player.stop()
            pipeline.speak(turn, pipelineConfig(), ::onEvent, force = true)
            onEvent(PipelineEvent.Idle)
        }
    }

    // ---------- Ayarlar > Testler ----------

    /** 3 sn kayıt alır, ses seviyesini ölçer ve konuşma tanımaya gönderir. */
    fun runMicTest() {
        if (_state.value.testing) return
        stopListening()
        viewModelScope.launch {
            _state.update { it.copy(testing = true, diagnostic = "🎤 3 saniye konuşun… (ör. \"Merhaba, bu bir testtir\")") }
            val lines = mutableListOf<String>()
            val wantHeadsetMic = settingsRepo.current.useHeadsetMic && router.headset.value?.hasMic == true
            val usingHeadsetMic = wantHeadsetMic && router.startHeadsetMic()
            try {
                val device = if (usingHeadsetMic) router.headsetMic() else router.phoneMic()
                val frames = ArrayList<ShortArray>()
                var peak = 0.0
                recorder.frames(device, usingHeadsetMic).takeWhile { frames.size < 150 }.collect { f ->
                    frames.add(f)
                    val rms = AudioMath.rms(f)
                    peak = maxOf(peak, rms)
                    _state.update { it.copy(level = levelFor(rms)) }
                }
                val clip = clipOf(frames)
                val avg = AudioMath.rms(clip.samples)
                lines += "Mikrofon: " + (if (usingHeadsetMic) "kulaklık (${router.headset.value?.name})" else "telefon")
                lines += "Ses seviyesi: en yüksek %.3f, ortalama %.4f".format(peak, avg) + when {
                    peak < 0.002 -> "  ❌ ses yok"
                    peak < 0.02 -> "  ⚠️ çok düşük"
                    else -> "  ✅"
                }
                if (settingsRepo.current.apiKey.isBlank()) {
                    lines += "Konuşma tanıma: API anahtarı girilmemiş ❌"
                } else {
                    _state.update { it.copy(diagnostic = (lines + "Konuşma tanıma deneniyor…").joinToString("\n")) }
                    val stt = OpenAiSpeechToText({ settingsRepo.current.toOpenAiConfig() }, app.http)
                    val text = runCatching { stt.transcribe(clip, settingsRepo.current.languageA, null).text }
                    lines += text.fold(
                        onSuccess = { if (it.isBlank()) "Konuşma tanıma: boş sonuç ⚠️" else "Konuşma tanıma ✅: \"$it\"" },
                        onFailure = { "Konuşma tanıma ❌: ${it.userMessage()}" },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lines += "Mikrofon açılamadı ❌: ${e.message}"
            } finally {
                if (usingHeadsetMic) router.stopHeadsetMic()
                _state.update { it.copy(testing = false, level = 0f, diagnostic = lines.joinToString("\n")) }
            }
        }
    }

    /** Örnek bir cümleyi çeviri sesiyle okur (kulaklık bağlıysa kulaklıktan). */
    fun runSpeakerTest() {
        if (_state.value.testing) return
        viewModelScope.launch {
            val s = settingsRepo.current
            val lang = s.languageA
            val sample = if (lang.code == "tr") "Merhaba! Bu bir ses testidir. Çeviriler bu sesle okunacak."
            else "Hello! This is a voice test. Translations will be read with this voice."
            val sampleLang = if (lang.code == "tr") lang else com.aiceviri.core.model.Languages.ENGLISH
            val engine = if (s.useDeviceTts) "telefonun sesi" else "bulut (${s.ttsModel})"
            _state.update { it.copy(testing = true, diagnostic = "🔊 Okunuyor… ($engine)") }
            var failure: Throwable? = null
            val turn = Turn(0, Speaker.B, sampleLang, sampleLang, sample, sample, System.currentTimeMillis())
            pipeline.speak(turn, pipelineConfig(), { ev -> if (ev is PipelineEvent.Failed) failure = ev.error }, force = true)
            val out = router.headsetOutput()?.let { "kulaklık" } ?: "telefon hoparlörü"
            _state.update {
                it.copy(
                    testing = false,
                    diagnostic = failure?.let { e -> "Ses ❌ ($engine): ${e.userMessage()}" }
                        ?: "Ses ✅ ($engine → $out). Duymadıysanız telefonun medya ses seviyesini kontrol edin.",
                )
            }
        }
    }

    // ---------- Foto Çeviri ----------

    fun translatePhoto(uri: Uri) {
        if (settingsRepo.current.apiKey.isBlank()) {
            _state.update { it.copy(error = "Önce Ayarlar'dan API anahtarınızı girin.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(photo = PhotoState(busy = true, stage = "Yazı okunuyor…"), error = null) }
            val text = try {
                ocr.recognize(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(photo = PhotoState(), error = "Fotoğraf okunamadı: ${e.message}") }
                return@launch
            }
            if (text.isBlank()) {
                _state.update {
                    it.copy(photo = PhotoState(), error = "Fotoğrafta okunabilir yazı bulunamadı. Daha yakından ve net çekmeyi deneyin.")
                }
                return@launch
            }
            _state.update { it.copy(photo = PhotoState(busy = true, stage = "Çevriliyor…", recognized = text)) }
            val cfg = pipelineConfig().copy(
                speak = false,
                domain = listOfNotNull(
                    "text photographed from a sign, menu, label or document (OCR); fix obvious OCR mistakes and keep line breaks",
                    settingsRepo.current.domain.ifBlank { null },
                ).joinToString("; "),
            )
            val turn = pipeline.processText(text, null, cfg, ::onEvent)
            _state.update { it.copy(photo = PhotoState(recognized = text, turn = turn)) }
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
            is PipelineEvent.Transcribing -> _state.update { it.copy(status = Status.TRANSCRIBING, pendingText = null) }
            is PipelineEvent.Transcribed -> _state.update { it.copy(status = Status.TRANSLATING, pendingText = event.text) }
            is PipelineEvent.Translated -> {
                _state.update { it.copy(turns = pipeline.history.all, pendingText = null) }
                saveSession()
            }
            is PipelineEvent.Speaking -> _state.update { it.copy(status = Status.SPEAKING) }
            is PipelineEvent.Discarded -> _state.update {
                it.copy(pendingText = "Konuşma anlaşılamadı, tekrar deneyin.")
            }
            is PipelineEvent.Idle -> _state.update {
                it.copy(
                    status = if (it.listening) Status.LISTENING else Status.IDLE,
                    // "anlaşılamadı" uyarısı ekranda kalsın, ara metinler temizlensin
                    pendingText = it.pendingText?.takeIf { t -> t.startsWith("Konuşma anlaşılamadı") || t.startsWith("Ses çok kısa") },
                )
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
    private fun outputFor(listener: Speaker): AudioDeviceInfo? {
        val s = _state.value
        if (s.screen != Screen.CONVERSATION) {
            // Foto / Kayıtlar: kulaklık varsa kulaklığa, yoksa varsayılan çıkışa
            return router.headsetOutput()
        }
        return when (s.mode) {
            Mode.FACE_TO_FACE -> router.phoneSpeaker()
            Mode.HEADSET -> if (listener == Speaker.A) router.headsetOutput() else router.phoneSpeaker()
            Mode.LISTEN -> router.headsetOutput()
        }
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

    private companion object {
        const val MIN_PTT_FRAMES = 25   // 0,5 sn
        const val MAX_PTT_FRAMES = 1500 // 30 sn
        fun newSessionId(): String = UUID.randomUUID().toString().take(12)
    }
}
