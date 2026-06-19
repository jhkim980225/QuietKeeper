package com.noisemeter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Entry-point activity. Intentionally minimal for Task 1 (scaffold).
 * UI and the measurement engine are wired up in later tasks.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { }
    }
}
