package com.quietkeeper.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietkeeper.app.data.NoiseEvent

@Composable
fun EventListScreen(events: List<NoiseEvent>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("← 측정 화면") }
        Spacer(Modifier.height(8.dp))
        if (events.isEmpty()) {
            Text("저장된 이벤트가 없습니다.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(events) { e ->
                    ListItem(
                        headlineContent = { Text("${e.peakDb.toInt()} dB(A)  ·  Leq ${e.leq.toInt()}") },
                        supportingContent = {
                            Text((if (e.moved) "📍이동됨 · " else "") + e.wavPath.substringAfterLast('/'))
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
