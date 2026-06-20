package com.quietkeeper.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.quietkeeper.app.audio.MeasurementService
import com.quietkeeper.app.ui.AppNav
import com.quietkeeper.app.ui.theme.QuietKeeperTheme

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
            QuietKeeperTheme {
                Surface {
                    AppNav(
                        onStartService = { startMeasurement() },
                        onStopService = {
                            stopService(Intent(this@MainActivity, MeasurementService::class.java))
                        },
                    )
                }
            }
        }
    }

    private fun startMeasurement() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startForegroundService(Intent(this, MeasurementService::class.java))
        } else {
            perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }
    }
}
