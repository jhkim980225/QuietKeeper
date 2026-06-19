package com.noisemeter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noisemeter.app.audio.MeasurementService

@Composable
fun MeasureScreen(onStart: () -> Unit, onStop: () -> Unit, onShowEvents: () -> Unit) {
    val m by MeasurementService.metrics.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("${m[0].toInt()}", fontSize = 72.sp)
        Text("dB(A)")
        Spacer(Modifier.height(16.dp))
        Row {
            Text("Leq ${m[1].toInt()}    ")
            Text("Lmax ${m[2].toInt()}")
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStart) { Text("측정 시작") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStop) { Text("측정 정지") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onShowEvents) { Text("이벤트 보기") }
    }
}
