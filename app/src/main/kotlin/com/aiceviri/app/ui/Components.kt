package com.aiceviri.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiceviri.app.ui.theme.Navy
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
            .background(Navy)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("‹ Geri", fontSize = 16.sp, color = Color.White) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            actions()
        }
    }
}

@Composable
fun HeaderAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.35f)),
    ) { Text(label) }
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
            Text(selected.nameTr, maxLines = 1, fontWeight = FontWeight.SemiBold)
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

/** Konuşma balonu: orijinal metin küçük, çeviri büyük; altında Oku / Kopyala. */
@Composable
fun TurnBubble(turn: Turn, onReplay: ((Turn) -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    val isA = turn.speaker == Speaker.A
    val bg = if (isA) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isA) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(18.dp))
                .background(bg)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
        ) {
            Text(
                "${turn.source.nameTr} → ${turn.target.nameTr}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Text(turn.original, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            Text(turn.translated, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onReplay != null) {
                    TextButton(onClick = { onReplay(turn) }) { Text("🔊 Oku") }
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(turn.translated)) }) { Text("Kopyala") }
            }
        }
    }
}

/** Yüz Yüze modunda bir kişinin göreceği satır: kendi sözü orijinal, karşısındakinin sözü çeviri. */
@Composable
fun ViewerLine(turn: Turn, viewer: Speaker, onReplay: (Turn) -> Unit) {
    val own = turn.speaker == viewer
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
        Text(
            if (own) turn.original else turn.translated,
            style = if (own) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
            color = if (own) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (own) Color.Transparent else MaterialTheme.colorScheme.surface)
                .clickable(enabled = !own) { onReplay(turn) }
                .padding(10.dp),
        )
    }
}

/** Büyük yuvarlak konuşma butonu; dinlerken nabız gibi atar. */
@Composable
fun BigRoundButton(
    label: String,
    active: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    level: Float = 0f,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    val ring = if (active) pulse + level * 0.25f else 1f
    Box(modifier.size(124.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .size(104.dp)
                    .scale(ring)
                    .clip(CircleShape)
                    .background(Color(0xFFDC2626).copy(alpha = 0.25f)),
            )
        }
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) Color(0xFFDC2626) else color,
                contentColor = Color.White,
            ),
            modifier = Modifier.size(104.dp),
            contentPadding = PaddingValues(8.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 3)
        }
    }
}

/** Ana ekrandaki mod kutucuğu. */
@Composable
fun FeatureTile(
    icon: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Text(icon, fontSize = 24.sp) }
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 2,
            )
        }
    }
}
