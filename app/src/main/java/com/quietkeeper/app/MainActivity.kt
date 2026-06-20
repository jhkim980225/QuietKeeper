package com.quietkeeper.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.MobileAds
import com.quietkeeper.app.audio.MeasurementService
import com.quietkeeper.app.billing.BillingManager
import com.quietkeeper.app.billing.ProStatus
import com.quietkeeper.app.ui.AppNav
import com.quietkeeper.app.ui.theme.QuietKeeperTheme

class MainActivity : AppCompatActivity() {
    private val perms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }
        // Monetization wiring: ads SDK, Pro source-of-truth, billing connection.
        MobileAds.initialize(this) {}
        ProStatus.init(applicationContext)
        BillingManager.onProductUnavailable = {
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.paywall_product_unavailable),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
        BillingManager.connect(applicationContext)
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
