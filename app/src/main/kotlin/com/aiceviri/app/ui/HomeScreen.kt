package com.aiceviri.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiceviri.app.audio.HeadsetInfo
import com.aiceviri.app.data.AppSettings
import com.aiceviri.app.ui.theme.Navy
import com.aiceviri.app.ui.theme.Orange
import com.aiceviri.app.ui.theme.Sky
import com.aiceviri.core.model.Language

private val Violet = Color(0xFF8B5CF6)
private val Emerald = Color(0xFF10B981)
private val Slate = Color(0xFF64748B)

@Composable
fun HomeScreen(
    settings: AppSettings,
    headset: HeadsetInfo?,
    micGranted: Boolean,
    onRequestPermission: () -> Unit,
    onOpen: (Mode) -> Unit,
    onPhoto: () -> Unit,
    onRecords: () -> Unit,
    onSettings: () -> Unit,
    onLanguageA: (Language) -> Unit,
    onLanguageB: (Language) -> Unit,
    onSwap: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ---- Üst bölüm: başlık + kulaklık + diller ----
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(Navy)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI Çeviri", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Her kulaklıkla, doğal çeviri", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
                TextButton(onClick = onSettings) { Text("⚙️", fontSize = 22.sp) }
            }
            Spacer(Modifier.height(12.dp))
            HeadsetChip(headset)
            Spacer(Modifier.height(16.dp))
            Card(shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Siz", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                        LanguagePicker(settings.languageA, onLanguageA)
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .padding(top = 16.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Sky.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(onClick = onSwap, contentPadding = PaddingValues(0.dp)) { Text("⇄", fontSize = 20.sp) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Karşı taraf", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                        LanguagePicker(settings.languageB, onLanguageB)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!micGranted) {
                Notice("Mikrofon izni gerekli", "Konuşmaları çevirebilmek için mikrofon izni verin.", "İzin ver", onRequestPermission)
            }
            if (settings.apiKey.isBlank()) {
                Notice("Kurulum", "Ayarlar'da hazır ayarı seçip API anahtarınızı girin (Groq ücretsizdir).", "Ayarlara git", onSettings)
            }

            Text("AI Çeviri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val modeColors = mapOf(Mode.FACE_TO_FACE to Sky, Mode.HEADSET to Orange, Mode.LISTEN to Violet)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureTile(Mode.FACE_TO_FACE.icon, Mode.FACE_TO_FACE.title, Mode.FACE_TO_FACE.short, modeColors.getValue(Mode.FACE_TO_FACE), { onOpen(Mode.FACE_TO_FACE) }, Modifier.weight(1f))
                FeatureTile(Mode.HEADSET.icon, "Kulaklıkla", Mode.HEADSET.short, modeColors.getValue(Mode.HEADSET), { onOpen(Mode.HEADSET) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureTile(Mode.LISTEN.icon, Mode.LISTEN.title, Mode.LISTEN.short, modeColors.getValue(Mode.LISTEN), { onOpen(Mode.LISTEN) }, Modifier.weight(1f))
                FeatureTile("📷", "Foto Çeviri", "Tabela, menü, belge", Emerald, onPhoto, Modifier.weight(1f))
            }

            Text("Diğer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureTile("🗂️", "Kayıtlar", "Geçmiş konuşmalar", Slate, onRecords, Modifier.weight(1f))
                FeatureTile("⚙️", "Ayarlar", "Servis, ses, üslup", Slate, onSettings, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeadsetChip(headset: HeadsetInfo?) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (headset != null) Color(0xFF22C55E) else Color.White.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (headset != null) {
                "🎧 ${headset.name} · " + (if (headset.isBluetooth) "Bluetooth" else "Kablolu") +
                    (if (headset.hasMic) " · mikrofonlu" else "")
            } else {
                "Kulaklık bağlı değil · telefonla da çalışır"
            },
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun Notice(title: String, text: String, action: String, onAction: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Navy)
                Text(text, style = MaterialTheme.typography.bodySmall, color = Navy)
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
