package com.quietkeeper.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.quietkeeper.app.R
import com.quietkeeper.app.data.AppDatabase
import com.quietkeeper.app.data.NoiseEvent
import com.quietkeeper.app.location.LocationProvider
import com.quietkeeper.app.sensor.MovementDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MeasurementService : Service(), AudioEngine.Listener {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val engine = AudioEngine(this)
    private lateinit var db: AppDatabase
    private var isRunning = false
    private var sessionStart = 0L
    private var eventCount = 0

    // Captured once per session (best-effort). Stamped onto each saved event.
    @Volatile private var sessionFix: LocationProvider.Fix? = null

    data class SessionSummary(
        val durationMs: Long = 0,
        val eventCount: Int = 0,
        val maxLmax: Float = -120f,
        val avgLeq: Float = -120f,
    )

    companion object {
        private val _metrics = MutableStateFlow(Metrics())
        val metrics: StateFlow<Metrics> = _metrics
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        private val _summary = MutableStateFlow(SessionSummary())
        val summary: StateFlow<SessionSummary> = _summary
        private const val CHANNEL_ID = "measurement"
        private const val NOTIF_ID = 1
        // TODO: replace with real external-mic calibration offset (placeholder).
        private const val CALIBRATION_OFFSET = 94.0f
        private const val THRESHOLD_DB = 55.0f
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (isRunning) return START_STICKY
        isRunning = true
        val outDir = getExternalFilesDir("events")?.absolutePath
            ?: filesDir.resolve("events").also { it.mkdirs() }.absolutePath
        MovementDetector.reset()
        MovementDetector.start(this)
        engine.start(outDir, CALIBRATION_OFFSET, THRESHOLD_DB)
        sessionStart = System.currentTimeMillis()
        eventCount = 0
        _running.value = true
        // Best-effort, non-blocking location capture; measurement proceeds regardless.
        sessionFix = null
        scope.launch { sessionFix = LocationProvider.current(applicationContext) }
        scope.launch {
            while (isActive) {
                val p = engine.poll()
                _metrics.value = Metrics(p[0], p[1], p[2])
                _summary.value = SessionSummary(
                    System.currentTimeMillis() - sessionStart, eventCount, p[2], p[1]
                )
                delay(125)
            }
        }
        return START_STICKY
    }

    override fun onEvent(wavPath: String, peakDb: Float, leq: Float) {
        // Called on the engine worker thread (JNI-attached). Persist metadata off the main thread.
        eventCount++
        scope.launch {
            withContext(NonCancellable) {
                val id = db.noiseEventDao().insert(
                    NoiseEvent(
                        timestamp = System.currentTimeMillis(),
                        peakDb = peakDb, leq = leq, wavPath = wavPath,
                        moved = MovementDetector.movedFlag, tag = null, note = null,
                        latitude = sessionFix?.lat,
                        longitude = sessionFix?.lng,
                        address = sessionFix?.address,
                    )
                )
                // Auto-tag: ask the (dummy) classifier for a suggested noise type and
                // store it only if the user hasn't already set a tag. Behind the
                // Ai.classifier seam so a real model swaps in without touching this.
                val ai = com.quietkeeper.app.ai.Ai.classifier.classify(wavPath, peakDb)
                val saved = db.noiseEventDao().getById(id)
                if (saved != null && saved.tag.isNullOrBlank()) {
                    db.noiseEventDao().update(saved.copy(tag = ai.tag))
                }
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        _metrics.value = Metrics()
        _running.value = false
        scope.cancel()
        engine.stop()
        MovementDetector.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_measuring_title))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notif,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        )
    }
}
