package com.quietkeeper.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietkeeper.app.R
import com.quietkeeper.app.audio.MeasurementService
import com.quietkeeper.app.ui.theme.Accent
import com.quietkeeper.app.ui.theme.EvDb
import com.quietkeeper.app.ui.theme.GlassBorder
import com.quietkeeper.app.ui.theme.GlassCard
import com.quietkeeper.app.ui.theme.Primary
import com.quietkeeper.app.ui.theme.ScreenScaffold
import com.quietkeeper.app.ui.theme.TextPrimary
import com.quietkeeper.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val THRESHOLD_DB = 55

@Composable
fun MeasureScreen(
    running: Boolean = false,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowEvents: () -> Unit,
) {
    val m by MeasurementService.metrics.collectAsStateWithLifecycle()
    val summary by MeasurementService.summary.collectAsStateWithLifecycle()

    // Tick elapsed time while running.
    var elapsedMs by remember { mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(running) {
        if (running) {
            while (true) {
                elapsedMs = MeasurementService.summary.value.durationMs
                delay(250)
            }
        } else {
            elapsedMs = 0L
        }
    }

    ScreenScaffold(title = stringResource(R.string.measure_title)) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(8.dp))
            Gauge(currentDb = m.current)
            Spacer(Modifier.height(24.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MetricCell(stringResource(R.string.leq), m.leq.toInt())
                    MetricCell(stringResource(R.string.lmax), m.lmax.toInt())
                    MetricCell(stringResource(R.string.threshold), THRESHOLD_DB)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (running) {
                Pill(text = stringResource(R.string.measuring_pill, formatElapsed(elapsedMs)))
                Spacer(Modifier.height(12.dp))
            }
            Text(
                stringResource(R.string.session_events, summary.eventCount),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(24.dp))
            if (running) {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.measure_stop))
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.prep_start))
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onShowEvents, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.view_events))
            }
        }
    }
}

@Composable
private fun Gauge(currentDb: Float) {
    val clamped = currentDb.coerceIn(30f, 90f)
    val fraction = (clamped - 30f) / 60f
    val sweep = 270f * fraction
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Background ring (270° arc, opening at the bottom).
            drawArc(
                color = GlassBorder,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Foreground proportional arc.
            drawArc(
                color = Primary,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${currentDb.roundToInt()}",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
            )
            Text(
                stringResource(R.string.dba),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Accent.copy(alpha = 0.18f))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = EvDb)
    }
}

@Composable
private fun MetricCell(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Text("$value", style = MaterialTheme.typography.headlineMedium, color = EvDb)
    }
}

internal fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val mm = totalSec / 60
    val ss = totalSec % 60
    return "%02d:%02d".format(mm, ss)
}
