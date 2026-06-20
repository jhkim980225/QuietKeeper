package com.quietkeeper.app.cloud
import android.util.Log
import com.quietkeeper.app.data.NoiseEvent
class LocalCloudSync : CloudSync {
    override val isConfigured = false
    override suspend fun syncEvent(event: NoiseEvent, integrityHash: String): Boolean {
        Log.d("CloudSync", "[dummy] would upload event ${event.id} hash=$integrityHash addr=${event.address}")
        return true
    }
}
