package com.aiceviri.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiceviri.core.model.ConversationRecord
import com.aiceviri.core.model.ConversationHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.forLanguageTag("tr-TR"))

private fun modeLabel(mode: String): String = when (mode) {
    "PHOTO" -> "📷 Foto Çeviri"
    else -> Mode.entries.firstOrNull { it.name == mode }?.let { "${it.icon} ${it.title}" } ?: "Konuşma"
}

@Composable
fun RecordsScreen(state: UiState, vm: TranslatorViewModel) {
    Column(Modifier.fillMaxSize()) {
        Header("Kayıtlar", onBack = vm::back)
        if (state.records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Henüz kayıt yok.\nYaptığınız her konuşma ve foto çevirisi burada otomatik saklanır.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.records, key = { it.id }) { record ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { vm.openRecord(record) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(modeLabel(record.mode), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Text(dateFormat.format(Date(record.startedAtMs)), style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(record.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 2)
                        Text(
                            "${record.languages} · ${record.turns.size} çeviri",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordDetailScreen(record: ConversationRecord, vm: TranslatorViewModel) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Header(modeLabel(record.mode), onBack = vm::back) {
            HeaderAction("Paylaş") {
                val history = ConversationHistory()
                record.turns.forEach { history.add(it.speaker, it.source, it.target, it.original, it.translated, it.timestampMs) }
                shareText(context, dateFormat.format(Date(record.startedAtMs)) + "\n\n" + history.transcript())
            }
            HeaderAction("Sil") { confirmDelete = true }
        }
        Text(
            dateFormat.format(Date(record.startedAtMs)) + " · " + record.languages,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(record.turns, key = { it.id }) { TurnBubble(it, onReplay = vm::replay) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Kayıt silinsin mi?") },
            text = { Text("Bu konuşma kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteRecord(record)
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}
