package com.aiceviri.app.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bağlı kulaklığın özeti (UI'da gösterilir). */
data class HeadsetInfo(
    val name: String,
    val isBluetooth: Boolean,
    val hasMic: Boolean,
)

/**
 * Markadan bağımsız kulaklık yönetimi.
 *
 * Hiçbir üreticiye özel protokol kullanılmaz; yalnızca Android'in standart ses
 * API'leri (HFP/SCO mikrofon, A2DP/BLE Audio çıkış, kablolu/USB kulaklık) kullanılır.
 * Bu sayede AirPods, Galaxy Buds, JBL, Xiaomi, Sony, Monoise P-Q16 ve isimsiz
 * TWS kulaklıkların tamamıyla aynı şekilde çalışır.
 */
class AudioRouter(context: Context) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _headset = MutableStateFlow(findHeadset())
    val headset: StateFlow<HeadsetInfo?> = _headset.asStateFlow()

    private var headsetMicActive = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    init {
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun refresh() {
        _headset.value = findHeadset()
    }

    private fun findHeadset(): HeadsetInfo? {
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val out = outputs.firstOrNull { it.type in HEADSET_OUTPUT_TYPES } ?: return null
        val isBt = out.type in BLUETOOTH_TYPES
        val hasMic = inputs.any { it.type in HEADSET_INPUT_TYPES }
        val name = out.productName?.toString()?.takeIf { it.isNotBlank() } ?: if (isBt) "Bluetooth kulaklık" else "Kulaklık"
        return HeadsetInfo(name, isBt, hasMic)
    }

    /** Kulaklığa çıkış cihazı (çeviriyi kulaklık takan kişiye çalmak için). */
    fun headsetOutput(): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in HEADSET_OUTPUT_TYPES }

    /** Telefonun kendi hoparlörü (çeviriyi karşıdaki kişiye çalmak için). */
    fun phoneSpeaker(): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    fun phoneMic(): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

    fun headsetMic(): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type in HEADSET_INPUT_TYPES }

    val isHeadsetMicActive: Boolean get() = headsetMicActive

    /**
     * Bluetooth kulaklık mikrofonunu (SCO / LE Audio) açar.
     * @return başarılıysa true; kulaklık yoksa veya açılamazsa false (telefon mikrofonu kullanılır).
     */
    @Suppress("DEPRECATION")
    suspend fun startHeadsetMic(): Boolean {
        if (headsetMicActive) return true
        val wired = am.getDevices(AudioManager.GET_DEVICES_INPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (wired) {
            headsetMicActive = true // kablolu kulaklık mikrofonu ek işlem gerektirmez
            return true
        }
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = am.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (device != null && am.setCommunicationDevice(device)) {
                headsetMicActive = true
                return true
            }
        } else {
            if (!am.isBluetoothScoAvailableOffCall) {
                am.mode = AudioManager.MODE_NORMAL
                return false
            }
            am.startBluetoothSco()
            am.isBluetoothScoOn = true
            // SCO bağlantısının kurulmasını bekle (genelde < 1.5 sn)
            repeat(30) {
                if (am.isBluetoothScoOn) {
                    headsetMicActive = true
                    return true
                }
                delay(100)
            }
        }
        stopHeadsetMic()
        return false
    }

    @Suppress("DEPRECATION")
    fun stopHeadsetMic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.clearCommunicationDevice()
        } else {
            am.isBluetoothScoOn = false
            am.stopBluetoothSco()
        }
        am.mode = AudioManager.MODE_NORMAL
        headsetMicActive = false
    }

    fun release() {
        am.unregisterAudioDeviceCallback(callback)
        if (headsetMicActive) stopHeadsetMic()
    }

    companion object {
        private val BLUETOOTH_TYPES: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        private val HEADSET_OUTPUT_TYPES: Set<Int> = BLUETOOTH_TYPES + setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        private val HEADSET_INPUT_TYPES: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}
