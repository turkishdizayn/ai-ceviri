package com.aiceviri.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiceviri.app.audio.HeadsetInfo
import com.aiceviri.app.data.AppSettings
import com.aiceviri.core.model.Language

@Composable
fun HomeScreen(
    settings: AppSettings,
    headset: HeadsetInfo?,
    micGranted: Boolean,
    onRequestPermission: () -> Unit,
    onOpen: (Mode) -> Unit,
    onSettings: () -> Unit,
    onLanguageA: (Language) -> Unit,
    onLanguageB: (Language) -> Unit,
    onSwap: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header("AI Çeviri") {
            TextButton(onClick = onSettings) { Text("Ayarlar") }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!micGranted) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Mikrofon izni gerekli", fontWeight = FontWeight.SemiBold)
                        Text("Konuşmaları çevirebilmek için mikrofon izni verin.")
                        TextButton(onClick = onRequestPermission) { Text("İzin ver") }
                    }
                }
            }
            if (settings.apiKey.isBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Kurulum", fontWeight = FontWeight.SemiBold)
                        Text("Çeviri servisini kullanmak için Ayarlar'dan API anahtarını girin.")
                        TextButton(onClick = onSettings) { Text("Ayarlara git") }
                    }
                }
            }

            HeadsetCard(headset)

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Diller", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Siz", style = MaterialTheme.typography.labelMedium)
                            LanguagePicker(settings.languageA, onLanguageA)
                        }
                        TextButton(onClick = onSwap, modifier = Modifier.padding(top = 16.dp)) { Text("⇄") }
                        Column(Modifier.weight(1f)) {
                            Text("Karşı taraf", style = MaterialTheme.typography.labelMedium)
                            LanguagePicker(settings.languageB, onLanguageB)
                        }
                    }
                }
            }

            Text("AI Çeviri", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            Mode.entries.forEach { mode ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(mode) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(mode.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(mode.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadsetCard(headset: HeadsetInfo?) {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (headset != null) "🎧" else "📱", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column {
                if (headset != null) {
                    Text(headset.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(if (headset.isBluetooth) "Bluetooth" else "Kablolu")
                            append(if (headset.hasMic) " · mikrofonlu" else " · mikrofon yok")
                            append(" · bağlı")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("Kulaklık bağlı değil", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Herhangi bir marka Bluetooth veya kablolu kulaklık bağlayabilirsiniz. Kulaklıksız da çalışır.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
