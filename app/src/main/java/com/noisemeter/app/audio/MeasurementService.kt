package com.noisemeter.app.audio

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
import androidx.room.Room
import com.noisemeter.app.data.AppDatabase
import com.noisemeter.app.data.NoiseEvent
import com.noisemeter.app.sensor.MovementDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MeasurementService : Service(), AudioEngine.Listener {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val engine = AudioEngine(this)
    private lateinit var db: AppDatabase

    companion object {
        private val _metrics = MutableStateFlow(floatArrayOf(-120f, -120f, -120f)) // [db, leq, lmax]
        val metrics: StateFlow<FloatArray> = _metrics
        private const val CHANNEL_ID = "measurement"
        private const val NOTIF_ID = 1
        // TODO: replace with real external-mic calibration offset (placeholder).
        private const val CALIBRATION_OFFSET = 94.0f
        private const val THRESHOLD_DB = 55.0f
    }

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "noise.db").build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val outDir = getExternalFilesDir("events")!!.absolutePath
        MovementDetector.reset()
        MovementDetector.start(this)
        engine.start(outDir, CALIBRATION_OFFSET, THRESHOLD_DB)
        scope.launch {
            while (isActive) {
                _metrics.value = engine.poll()
                delay(125)
            }
        }
        return START_STICKY
    }

    override fun onEvent(wavPath: String, peakDb: Float, leq: Float) {
        // Called on the engine worker thread (JNI-attached). Persist metadata off the main thread.
        scope.launch {
            db.noiseEventDao().insert(
                NoiseEvent(
                    timestamp = System.currentTimeMillis(),
                    peakDb = peakDb,
                    leq = leq,
                    wavPath = wavPath,
                    moved = MovementDetector.movedFlag,
                    tag = null,
                    note = null,
                )
            )
        }
    }

    override fun onDestroy() {
        engine.stop()
        MovementDetector.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "측정", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("소음 측정 중")
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
