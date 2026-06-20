package com.quietkeeper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quietkeeper.app.R
import com.quietkeeper.app.ui.theme.GlassCard
import com.quietkeeper.app.ui.theme.ScreenScaffold
import com.quietkeeper.app.ui.theme.TextPrimary
import com.quietkeeper.app.ui.theme.TextSecondary

@Composable
fun PrepScreen(
    onStart: () -> Unit,
    onShowEvents: () -> Unit = {},
    onShowPaywall: () -> Unit = {},
) {
    ScreenScaffold(title = stringResource(R.string.prep_title)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onShowEvents) {
                    Text(stringResource(R.string.view_events))
                }
                TextButton(onClick = onShowPaywall) {
                    Text(
                        stringResource(R.string.pro_entry),
                        color = com.quietkeeper.app.ui.theme.Primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StatusRow("🎙", stringResource(R.string.prep_mic))
            Spacer(Modifier.height(12.dp))
            StatusRow("🔧", stringResource(R.string.prep_calibration))
            Spacer(Modifier.height(12.dp))
            StatusRow("📐", stringResource(R.string.prep_level))
            Spacer(Modifier.height(20.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.prep_threshold_label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                    Text(
                        stringResource(R.string.prep_threshold_value),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.prep_start))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.prep_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatusRow(icon: String, label: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.titleLarge)
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                "${stringResource(R.string.prep_ok)} ✓",
                style = MaterialTheme.typography.titleMedium,
                color = com.quietkeeper.app.ui.theme.Primary,
            )
        }
    }
}
