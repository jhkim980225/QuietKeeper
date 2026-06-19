package com.noisemeter.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import com.noisemeter.app.audio.MeasurementService
import com.noisemeter.app.data.AppDatabase
import com.noisemeter.app.data.NoiseEvent
import com.noisemeter.app.ui.EventListScreen
import com.noisemeter.app.ui.MeasureScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val perms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }
        setContent {
            MaterialTheme {
                Surface {
                    var showEvents by remember { mutableStateOf(false) }
                    var events by remember { mutableStateOf(emptyList<NoiseEvent>()) }
                    if (showEvents) {
                        LaunchedEffect(Unit) {
                            events = withContext(Dispatchers.IO) {
                                AppDatabase.getInstance(applicationContext).noiseEventDao().getAll()
                            }
                        }
                        EventListScreen(events = events, onBack = { showEvents = false })
                    } else {
                        MeasureScreen(
                            onStart = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    this@MainActivity, Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                startForegroundService(Intent(this@MainActivity, MeasurementService::class.java))
                            } else {
                                perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
                            }
                        },
                            onStop = { stopService(Intent(this@MainActivity, MeasurementService::class.java)) },
                            onShowEvents = { showEvents = true }
                        )
                    }
                }
            }
        }
    }
}
