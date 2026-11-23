package com.aiceviri.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aiceviri.app.data.AppSettings
import com.aiceviri.app.data.ServicePreset
import com.aiceviri.core.model.Tone

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header("Ayarlar", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Hazır ayar")
            ServicePreset.entries.forEach { preset ->
                OutlinedButton(onClick = { onChange(preset.apply) }, modifier = Modifier.fillMaxWidth()) {
                    Text(preset.title)
                }
            }
            Text(
                "Hazır ayar servis adresini ve modelleri doldurur; API anahtarını o servisten alıp aşağıya girin.",
                style = MaterialTheme.typography.bodySmall,
            )

            Section("Yapay zekâ servisi")
            OutlinedTextField(
                value = settings.apiKey,
                onValueChange = { v -> onChange { it.copy(apiKey = v.trim()) } },
                label = { Text("API anahtarı") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.baseUrl,
                onValueChange = { v -> onChange { it.copy(baseUrl = v.trim()) } },
                label = { Text("Servis adresi (OpenAI uyumlu)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Varsayılan OpenAI'dır. OpenAI uyumlu başka bir servis veya kendi sunucunuz (önerilen) da girilebilir.",
                style = MaterialTheme.typography.bodySmall,
            )

            Section("Modeller")
            ModelField("Konuşma → metin modeli", settings.sttModel) { v -> onChange { it.copy(sttModel = v) } }
            ModelField("Çeviri modeli", settings.translateModel) { v -> onChange { it.copy(translateModel = v) } }
            ModelField("Seslendirme modeli", settings.ttsModel) { v -> onChange { it.copy(ttsModel = v) } }
            Text(
                if (settings.useDeviceTts) "Seslendirme: telefonun kendi sesi (ücretsiz, internetsiz)."
                else "Seslendirme: bulut modeli. Telefonun ücretsiz sesini kullanmak için boş bırakın.",
                style = MaterialTheme.typography.bodySmall,
            )
            ModelField("Ses (voice)", settings.ttsVoice) { v -> onChange { it.copy(ttsVoice = v) } }

            Section("Çeviri üslubu")
            Tone.entries.forEach { tone ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings.tone == tone, onClick = { onChange { it.copy(tone = tone) } }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.tone == tone, onClick = { onChange { it.copy(tone = tone) } })
                    Text(
                        when (tone) {
                            Tone.NATURAL -> "Doğal (günlük konuşma)"
                            Tone.FORMAL -> "Resmi (iş görüşmesi)"
                            Tone.CASUAL -> "Samimi (arkadaş arası)"
                        }
                    )
                }
            }
            OutlinedTextField(
                value = settings.domain,
                onValueChange = { v -> onChange { it.copy(domain = v) } },
                label = { Text("Konu / alan (isteğe bağlı)") },
                placeholder = { Text("ör. otel resepsiyonu, tıp, inşaat, e-ticaret") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Section("Ses")
            SwitchRow(
                title = "Çevirileri sesli oku",
                checked = settings.speakTranslations,
                onChecked = { v -> onChange { it.copy(speakTranslations = v) } },
            )
            SwitchRow(
                title = "Kulaklık mikrofonunu kullan",
                subtitle = "Bluetooth kulaklığın mikrofonu (SCO). Kapalıyken telefon mikrofonu kullanılır; Bluetooth mikrofonları dar bantlı olduğu için telefon mikrofonu genelde daha doğru sonuç verir.",
                checked = settings.useHeadsetMic,
                onChecked = { v -> onChange { it.copy(useHeadsetMic = v) } },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ModelField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.trim()) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
