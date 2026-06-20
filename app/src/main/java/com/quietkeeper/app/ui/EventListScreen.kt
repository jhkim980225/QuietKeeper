package com.quietkeeper.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quietkeeper.app.R
import com.quietkeeper.app.data.NoiseEvent
import com.quietkeeper.app.ui.theme.EvDb
import com.quietkeeper.app.ui.theme.GlassCard
import com.quietkeeper.app.ui.theme.ScreenScaffold
import com.quietkeeper.app.ui.theme.TextPrimary
import com.quietkeeper.app.ui.theme.TextSecondary

@Composable
fun EventListScreen(events: List<NoiseEvent>, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    ScreenScaffold(title = stringResource(R.string.events_title)) {
        TextButton(onClick = onBack) { Text("← " + stringResource(R.string.back)) }
        Spacer(Modifier.height(8.dp))
        if (events.isEmpty()) {
            Text(
                stringResource(R.string.events_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(events) { e ->
                    GlassCard(Modifier.fillMaxWidth().clickable { onOpen(e.id) }) {
                        Text(
                            "${e.peakDb.toInt()} " + stringResource(R.string.dba),
                            style = MaterialTheme.typography.displayMedium,
                            color = EvDb,
                        )
                        Text(
                            stringResource(R.string.leq) + " ${e.leq.toInt()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                        )
                        Text(
                            (if (e.moved) "📍 · " else "") + e.wavPath.substringAfterLast('/'),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
