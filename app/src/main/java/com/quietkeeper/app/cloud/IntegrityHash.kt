package com.quietkeeper.app.cloud
import java.security.MessageDigest
object IntegrityHash {
    /** SHA-256 hex of timestamp + deviceId + wavPath + peakDb — tamper-evidence for an event. */
    fun of(timestamp: Long, deviceId: String, wavPath: String, peakDb: Float): String {
        val input = "$timestamp|$deviceId|$wavPath|$peakDb"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
