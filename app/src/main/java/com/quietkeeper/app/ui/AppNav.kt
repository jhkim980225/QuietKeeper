package com.quietkeeper.app.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quietkeeper.app.audio.MeasurementService
import com.quietkeeper.app.data.AppDatabase
import com.quietkeeper.app.billing.BillingManager
import com.quietkeeper.app.billing.ProStatus
import com.quietkeeper.app.data.NoiseEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AppNav(onStartService: () -> Unit, onStopService: () -> Unit) {
    val nav = rememberNavController()
    // First launch (no locale chosen yet) → language screen; otherwise straight to home.
    val startDest = if (AppCompatDelegate.getApplicationLocales().isEmpty) "language" else "home"
    NavHost(navController = nav, startDestination = startDest) {
        composable("language") {
            LanguageScreen(onChosen = { tag ->
                // Persists the choice and recreates the activity in the new locale.
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                // Fallback nav in case no recreate happens (e.g. same-locale selection).
                nav.navigate("home") { popUpTo("language") { inclusive = true } }
            })
        }
        composable("home") {
            HomeScreen(
                onStart = onStartService,
                onStop = {
                    SessionStore.last = MeasurementService.summary.value
                    onStopService()
                    nav.navigate("save")
                },
                onShowEvents = { nav.navigate("events") },
                onShowPaywall = { nav.navigate("paywall") },
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
            EventListScreen(
                events = events,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate("detail/$id") },
            )
        }
        composable("save") {
            SaveScreen(
                summary = SessionStore.last,
                onSave = { _, _ ->
                    // Persisting the memo onto the session is Phase-later; the
                    // over-threshold events are already saved by the engine.
                    nav.popBackStack("home", false)
                },
                onDiscard = { nav.popBackStack("home", false) },
            )
        }
        composable(
            "detail/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val context = LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val id = backStackEntry.arguments?.getString("eventId")?.toLongOrNull()
            var event by remember { mutableStateOf<NoiseEvent?>(null) }
            androidx.compose.runtime.LaunchedEffect(id) {
                if (id != null) {
                    event = withContext(Dispatchers.IO) {
                        AppDatabase.getInstance(context).noiseEventDao().getById(id)
                    }
                }
            }
            val isPro by ProStatus.isPro.collectAsState()
            event?.let { ev ->
                EventDetailScreen(
                    event = ev,
                    isPro = isPro,
                    onNeedUpgrade = { nav.navigate("paywall") },
                    onBack = { nav.popBackStack() },
                    onSaveNote = { tag, note ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabase.getInstance(context).noiseEventDao()
                                    .update(ev.copy(tag = tag, note = note))
                            }
                            nav.popBackStack()
                        }
                    },
                    onDelete = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabase.getInstance(context).noiseEventDao().delete(ev)
                                runCatching { File(ev.wavPath).delete() }
                            }
                            nav.popBackStack()
                        }
                    },
                )
            }
        }
        composable("paywall") {
            val activity = LocalContext.current as? Activity
            val isPro by ProStatus.isPro.collectAsState()
            PaywallScreen(
                onBack = { nav.popBackStack() },
                onSubscribe = { activity?.let { BillingManager.launchPurchase(it) } },
                isPro = isPro,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowEvents: () -> Unit,
    onShowPaywall: () -> Unit,
) {
    // Observe running state so the home screen recomposes with service lifecycle.
    // Idle → Prep (status checks + start); Measuring → live gauge.
    val running by com.quietkeeper.app.audio.MeasurementService.running
        .collectAsStateWithLifecycle()
    if (running) {
        MeasureScreen(
            running = true,
            onStart = onStart,
            onStop = onStop,
            onShowEvents = onShowEvents,
            onShowPaywall = onShowPaywall,
        )
    } else {
        PrepScreen(
            onStart = onStart,
            onShowEvents = onShowEvents,
            onShowPaywall = onShowPaywall,
        )
    }
}
