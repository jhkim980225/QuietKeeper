package com.quietkeeper.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietkeeper.app.R
import com.quietkeeper.app.audio.MeasurementService
import com.quietkeeper.app.ui.theme.EvDb
import com.quietkeeper.app.ui.theme.GlassCard
import com.quietkeeper.app.ui.theme.ScreenScaffold
import com.quietkeeper.app.ui.theme.TextPrimary
import com.quietkeeper.app.ui.theme.TextSecondary

@Composable
fun MeasureScreen(
    running: Boolean = false,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowEvents: () -> Unit,
) {
    val m by MeasurementService.metrics.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.measure_title)) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${m.current.toInt()}",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.dba),
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary,
            )
            Spacer(Modifier.height(24.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MetricCell(stringResource(R.string.leq), m.leq.toInt())
                    MetricCell(stringResource(R.string.lmax), m.lmax.toInt())
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onStart,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.start))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onStop,
                enabled = running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.stop))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onShowEvents, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.view_events))
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Text(
            "$value",
            style = MaterialTheme.typography.displayMedium,
            color = EvDb,
        )
    }
}
