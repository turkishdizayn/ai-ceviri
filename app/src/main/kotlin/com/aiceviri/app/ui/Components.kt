package com.aiceviri.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiceviri.core.model.Language
import com.aiceviri.core.model.Languages
import com.aiceviri.core.model.Speaker
import com.aiceviri.core.model.Turn

@Composable
fun Header(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("‹ Geri", fontSize = 16.sp) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun LanguagePicker(
    selected: Language,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.nameTr, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Languages.all.forEach { lang ->
                DropdownMenuItem(
                    text = { Text("${lang.nameTr} · ${lang.nativeName}") },
                    onClick = {
                        onSelect(lang)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Tamam") }
        }
    }
}

@Composable
fun LevelMeter(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { if (active) level else 0f },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
    )
}

/** Konuşma balonu: orijinal metin küçük, çeviri büyük. */
@Composable
fun TurnBubble(turn: Turn) {
    val isA = turn.speaker == Speaker.A
    val bg = if (isA) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isA) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(12.dp),
        ) {
            Text(
                "${turn.source.nameTr} → ${turn.target.nameTr}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(turn.original, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Spacer(Modifier.height(4.dp))
            Text(turn.translated, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
    }
}

/** Yüz Yüze modunda bir kişinin göreceği satır: kendi sözü orijinal, karşısındakinin sözü çeviri. */
@Composable
fun ViewerLine(turn: Turn, viewer: Speaker) {
    val own = turn.speaker == viewer
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
        Text(
            if (own) turn.original else turn.translated,
            style = if (own) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
            color = if (own) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (own) Color.Transparent else MaterialTheme.colorScheme.surface)
                .padding(10.dp),
        )
    }
}

@Composable
fun BigRoundButton(
    label: String,
    active: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFFDC2626) else color,
            contentColor = Color.White,
        ),
        modifier = modifier.size(104.dp),
        contentPadding = PaddingValues(8.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 3)
    }
}
