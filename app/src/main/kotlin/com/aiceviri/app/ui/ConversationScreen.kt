package com.aiceviri.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiceviri.app.audio.HeadsetInfo
import com.aiceviri.app.data.AppSettings
import com.aiceviri.app.ui.theme.Orange
import com.aiceviri.app.ui.theme.Sky
import com.aiceviri.core.model.Speaker
import com.aiceviri.core.model.Turn

@Composable
fun ConversationScreen(
    state: UiState,
    settings: AppSettings,
    headset: HeadsetInfo?,
    vm: TranslatorViewModel,
) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize()) {
        Header(state.mode.title, onBack = vm::back) {
            TextButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, vm.transcript())
                    }
                    context.startActivity(Intent.createChooser(send, "Konuşmayı paylaş"))
                },
                enabled = state.turns.isNotEmpty(),
            ) { Text("Paylaş") }
            TextButton(onClick = vm::clearConversation, enabled = state.turns.isNotEmpty()) { Text("Temizle") }
        }
        state.error?.let { ErrorBanner(it, vm::dismissError) }

        when (state.mode) {
            Mode.FACE_TO_FACE -> FaceToFace(state, settings, vm)
            Mode.HEADSET, Mode.LISTEN -> Continuous(state, settings, headset, vm)
        }
    }
}

@Composable
private fun StatusLine(state: UiState, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.status.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (state.listening) Text(state.micSource, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        LevelMeter(state.level, state.listening)
        state.pendingText?.let {
            Spacer(Modifier.height(4.dp))
            Text("“$it”", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ------------------------------------------------------------
// Yüz Yüze: ekran ikiye bölünür, üst yarı karşıdaki kişi için ters çevrilir.
// ------------------------------------------------------------

@Composable
private fun ColumnScope.FaceToFace(state: UiState, settings: AppSettings, vm: TranslatorViewModel) {
    val pair = settings.pair
    // Üst yarı: B (karşı taraf), 180° döndürülmüş
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .rotate(180f)
            .background(Orange.copy(alpha = 0.06f)),
    ) {
        PersonPane(
            turns = state.turns,
            viewer = Speaker.B,
            buttonLabel = "${pair.b.nativeName}\n🎤",
            active = state.listening && state.activeSpeaker == Speaker.B,
            color = Orange,
            onTalk = { vm.talk(Speaker.B) },
        )
    }
    HorizontalDivider()
    StatusLine(state)
    HorizontalDivider()
    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(Sky.copy(alpha = 0.06f)),
    ) {
        PersonPane(
            turns = state.turns,
            viewer = Speaker.A,
            buttonLabel = "${pair.a.nativeName}\n🎤",
            active = state.listening && state.activeSpeaker == Speaker.A,
            color = Sky,
            onTalk = { vm.talk(Speaker.A) },
        )
    }
    TextInput(onSend = vm::sendText, hint = "${pair.a.nameTr} yazın…")
}

@Composable
private fun PersonPane(
    turns: List<Turn>,
    viewer: Speaker,
    buttonLabel: String,
    active: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onTalk: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }
    Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.Bottom) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(turns, key = { it.id }) { ViewerLine(it, viewer) }
        }
        Spacer(Modifier.width(8.dp))
        BigRoundButton(
            label = if (active) "Bitir" else buttonLabel,
            active = active,
            color = color,
            onClick = onTalk,
        )
    }
}

// ------------------------------------------------------------
// Kulaklıkla Çeviri / Dinle ve Çevir: sürekli dinleme, otomatik dil algılama
// ------------------------------------------------------------

@Composable
private fun ColumnScope.Continuous(
    state: UiState,
    settings: AppSettings,
    headset: HeadsetInfo?,
    vm: TranslatorViewModel,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.turns.size) {
        if (state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.size - 1)
    }

    if (headset == null && state.mode == Mode.HEADSET) {
        Text(
            "Kulaklık bağlı değil: çeviriler telefon hoparlöründen okunacak.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    StatusLine(state)
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (state.turns.isEmpty()) {
            item {
                Text(
                    "${settings.pair.a.nameTr} ⇄ ${settings.pair.b.nativeName}\nKonuşmaya başlayın; dil otomatik algılanır.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(state.turns, key = { it.id }) { TurnBubble(it) }
    }
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BigRoundButton(
            label = if (state.listening) "Durdur" else "Başlat",
            active = state.listening,
            color = Sky,
            onClick = vm::toggleListening,
        )
    }
    TextInput(onSend = vm::sendText, hint = "Yazarak çevir…")
}

@Composable
private fun TextInput(onSend: (String) -> Unit, hint: String) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(hint) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                onSend(text)
                text = ""
            },
            enabled = text.isNotBlank(),
        ) { Text("Çevir") }
    }
}
