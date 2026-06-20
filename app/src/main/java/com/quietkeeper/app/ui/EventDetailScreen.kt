package com.quietkeeper.app.ui

import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.quietkeeper.app.R
import com.quietkeeper.app.billing.PlayQuota
import com.quietkeeper.app.data.NoiseEvent
import kotlinx.coroutines.launch
import java.io.File
import com.quietkeeper.app.ui.theme.BgLight
import com.quietkeeper.app.ui.theme.EvDb
import com.quietkeeper.app.ui.theme.GlassCard
import com.quietkeeper.app.ui.theme.Primary
import com.quietkeeper.app.ui.theme.ScreenScaffold
import com.quietkeeper.app.ui.theme.TextPrimary
import com.quietkeeper.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LightBlue = Color(0xFFCFE0F7)
private val WaveBars = listOf(5, 8, 12, 6, 14, 9, 4, 11, 16, 7, 10, 13, 6, 15, 8, 5, 12, 9, 14, 6, 10, 7, 13, 5)

@Composable
fun EventDetailScreen(
    event: NoiseEvent,
    onBack: () -> Unit,
    onSaveNote: (tag: String?, note: String?) -> Unit,
    onDelete: () -> Unit,
    isPro: Boolean,
    onNeedUpgrade: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }

    // Free users see remaining plays today; bumped after each consume so the
    // hint counts down. Pro users bypass the quota entirely.
    var quotaTick by remember { mutableStateOf(0) }
    val remaining by produceState(initialValue = PlayQuota.FREE_DAILY_LIMIT, isPro, quotaTick) {
        value = if (isPro) PlayQuota.FREE_DAILY_LIMIT
        else PlayQuota.remainingToday(context, PlayQuota.todayString())
    }

    fun startPlayback() {
        runCatching {
            player.reset()
            player.setDataSource(event.wavPath)
            player.prepare()
            player.start()
            isPlaying = true
        }
    }

    DisposableEffect(Unit) {
        player.setOnCompletionListener { isPlaying = false }
        onDispose { runCatching { player.release() } }
    }

    var tagAdding by remember { mutableStateOf(false) }
    var tag by remember { mutableStateOf(event.tag ?: "") }
    var note by remember { mutableStateOf(event.note ?: "") }

    ScreenScaffold {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // Top back bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "←",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(end = 12.dp),
                )
                Text(
                    stringResource(R.string.detail_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
            }
            Spacer(Modifier.height(16.dp))

            // Big metric block
            Text(
                "${event.peakDb.toInt()} " + stringResource(R.string.dba) + " · " + stringResource(R.string.lmax),
                style = MaterialTheme.typography.displayMedium,
                color = EvDb,
            )
            val date = remember(event.timestamp) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))
            }
            Text(
                "$date · " + stringResource(R.string.leq) + " ${event.leq.toInt()} dB",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            if (event.moved) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .background(Color(0xFFFDECEC), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFF2B8B8), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.detail_moved_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFC0392B),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Player card
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val playedBars = if (isPlaying) WaveBars.size else WaveBars.size / 3
                    WaveBars.forEachIndexed { i, h ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height((h * 3).dp)
                                .background(
                                    if (i < playedBars) Primary else LightBlue,
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Primary, CircleShape)
                            .clickable {
                                if (isPlaying) {
                                    runCatching { player.pause() }
                                    isPlaying = false
                                } else if (isPro) {
                                    startPlayback()
                                } else {
                                    scope.launch {
                                        val ok = PlayQuota.tryConsume(context, PlayQuota.todayString())
                                        quotaTick++ // refresh the remaining-plays hint
                                        if (ok) {
                                            startPlayback()
                                        } else {
                                            onNeedUpgrade()
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isPlaying) "⏸" else "▶",
                            color = Color.White,
                            fontSize = 20.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(LightBlue, RoundedCornerShape(2.dp)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(if (isPlaying) 0.6f else 0.0f)
                                    .height(4.dp)
                                    .background(Primary, RoundedCornerShape(2.dp)),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (isPro) {
                                stringResource(R.string.detail_unlimited_play)
                            } else {
                                val used = PlayQuota.FREE_DAILY_LIMIT - remaining
                                stringResource(
                                    R.string.detail_plays_today,
                                    used,
                                    PlayQuota.FREE_DAILY_LIMIT,
                                )
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Map placeholder
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.detail_location),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(Color(0xFFE3E8F0), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.detail_map_pending),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Tag section
            Text(
                stringResource(R.string.detail_tag_section),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tag.isNotBlank()) {
                    Chip(text = tag, filled = true)
                } else {
                    Chip(
                        text = stringResource(R.string.detail_add_tag),
                        filled = false,
                        modifier = Modifier.clickable { tagAdding = true },
                    )
                }
                // AI suggestion placeholder (disabled)
                Chip(text = stringResource(R.string.detail_ai_suggest), filled = false, faded = true)
            }
            if (tagAdding) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.detail_add_tag)) },
                )
            }
            Spacer(Modifier.height(16.dp))

            // Note section
            Text(
                stringResource(R.string.detail_note_section),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                label = { Text(stringResource(R.string.detail_note_hint)) },
            )
            Spacer(Modifier.height(16.dp))

            // Save button
            Button(
                onClick = { onSaveNote(tag.ifBlank { null }, note.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text(stringResource(R.string.detail_save))
            }
            Spacer(Modifier.height(12.dp))

            // Bottom row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (!isPro) {
                            onNeedUpgrade()
                        } else {
                            runCatching {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    File(event.wavPath),
                                )
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/wav"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(send, context.getString(R.string.detail_export)),
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (isPro) stringResource(R.string.detail_export)
                        else stringResource(R.string.detail_export_pro),
                    )
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD9534F)),
                ) {
                    Text(stringResource(R.string.detail_delete))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Chip(
    text: String,
    filled: Boolean,
    faded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bg = if (filled) Primary.copy(alpha = 0.12f) else BgLight
    val fg = when {
        faded -> TextSecondary
        filled -> EvDb
        else -> TextPrimary
    }
    Box(
        modifier
            .background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, if (filled) Primary.copy(alpha = 0.4f) else Color(0xFFD5DEEC), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
