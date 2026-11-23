package com.aiceviri.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aiceviri.app.data.AppSettings
import java.io.File

@Composable
fun PhotoScreen(state: UiState, settings: AppSettings, vm: TranslatorViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val photo = state.photo
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) vm.translatePhoto(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.translatePhoto(uri)
    }

    Column(Modifier.fillMaxSize()) {
        Header("Foto Çeviri", onBack = vm::back)
        state.error?.let { ErrorBanner(it, vm::dismissError) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Tabela, menü, etiket veya belgenin fotoğrafını çekin; yazı okunup ${settings.languageA.nameTr} ⇄ ${settings.languageB.nameTr} arasında çevrilir.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                        val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        pendingUri = uri
                        takePicture.launch(uri)
                    },
                    enabled = !photo.busy,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("📷  Fotoğraf çek") }
                OutlinedButton(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !photo.busy,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("🖼️  Galeriden seç") }
            }
            Text(
                "Yazı tanıma telefonda yapılır ve Latin alfabeli dilleri (Türkçe, İngilizce, Almanca, Fransızca, İspanyolca…) okur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            if (photo.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(photo.stage)
                }
            }

            photo.turn?.let { turn ->
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${turn.source.nameTr} → ${turn.target.nameTr}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        SelectionContainer {
                            Text(turn.translated, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { vm.replay(turn) }) { Text("🔊 Oku") }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(turn.translated)) }) { Text("Kopyala") }
                            TextButton(onClick = { shareText(context, turn.translated) }) { Text("Paylaş") }
                        }
                    }
                }
            }

            photo.recognized?.let { text ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Fotoğraftaki yazı", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}
