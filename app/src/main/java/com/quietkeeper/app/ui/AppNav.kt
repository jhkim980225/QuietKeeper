package com.quietkeeper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.quietkeeper.app.data.AppDatabase
import com.quietkeeper.app.data.NoiseEvent
import com.quietkeeper.app.ui.theme.ScreenScaffold
import com.quietkeeper.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppNav(onStartService: () -> Unit, onStopService: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("language") {
            LanguagePlaceholder(onStart = { nav.navigate("home") })
        }
        composable("home") {
            HomeScreen(
                onStart = onStartService,
                onStop = onStopService,
                onShowEvents = { nav.navigate("events") },
            )
        }
        composable("events") {
            val context = LocalContext.current
            var events by remember { mutableStateOf(emptyList<NoiseEvent>()) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                events = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(context).noiseEventDao().getAll()
                }
            }
            EventListScreen(events = events, onBack = { nav.popBackStack() })
        }
        composable("prep") { PlaceholderScreen("Prepare") }
        composable("save") { PlaceholderScreen("Save") }
        composable("detail/{eventId}") { PlaceholderScreen("Detail") }
        composable("paywall") { PlaceholderScreen("Paywall") }
    }
}

@Composable
private fun HomeScreen(onStart: () -> Unit, onStop: () -> Unit, onShowEvents: () -> Unit) {
    // Observe running state so the home screen recomposes with service lifecycle.
    val running by com.quietkeeper.app.audio.MeasurementService.running
        .collectAsStateWithLifecycle()
    MeasureScreen(
        running = running,
        onStart = onStart,
        onStop = onStop,
        onShowEvents = onShowEvents,
    )
}

@Composable
private fun LanguagePlaceholder(onStart: () -> Unit) {
    ScreenScaffold {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "언어 선택 (구현 예정)",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Button(onClick = onStart) { Text("시작") }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    ScreenScaffold {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "$name (TODO)",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        }
    }
}
