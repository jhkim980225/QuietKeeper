package com.quietkeeper.app.cloud
import com.quietkeeper.app.data.NoiseEvent
interface CloudSync {
    /** Upload event metadata (+ integrity hash). Returns true on success. Never throws. */
    suspend fun syncEvent(event: NoiseEvent, integrityHash: String): Boolean
    /** Whether a backend is actually configured (false for the local dummy). */
    val isConfigured: Boolean
}
