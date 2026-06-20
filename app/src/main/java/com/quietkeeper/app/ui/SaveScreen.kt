package com.quietkeeper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quietkeeper.app.R
import com.quietkeeper.app.audio.MeasurementService
import com.quietkeeper.app.ui.theme.EvDb
import com.quietkeeper.app.ui.theme.GlassCard
import com.quietkeeper.app.ui.theme.ScreenScaffold
import com.quietkeeper.app.ui.theme.TextPrimary
import com.quietkeeper.app.ui.theme.TextSecondary

@Composable
fun SaveScreen(
    summary: MeasurementService.SessionSummary,
    onSave: (memo: String, keepAudio: Boolean) -> Unit,
    onDiscard: () -> Unit,
) {
    var memo by remember { mutableStateOf("") }
    var keepAudio by remember { mutableStateOf(true) }

    ScreenScaffold(title = stringResource(R.string.save_title)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    stringResource(R.string.save_duration),
                    formatElapsed(summary.durationMs),
                    Modifier.weight(1f),
                )
                SummaryCard(
                    stringResource(R.string.save_events),
                    "${summary.eventCount}",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    stringResource(R.string.save_max_lmax),
                    "${summary.maxLmax.toInt()} dB",
                    Modifier.weight(1f),
                )
                SummaryCard(
                    stringResource(R.string.save_avg_leq),
                    "${summary.avgLeq.toInt()} dB",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text(stringResource(R.string.save_memo_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = keepAudio, onCheckedChange = { keepAudio = it })
                Text(
                    stringResource(R.string.save_keep_audio),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSave(memo, keepAudio) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save_button))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_discard))
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = EvDb)
    }
}
