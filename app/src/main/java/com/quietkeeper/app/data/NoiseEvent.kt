package com.quietkeeper.app.data
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "noise_events")
data class NoiseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val peakDb: Float,
    val leq: Float,
    val wavPath: String,
    val moved: Boolean,
    val tag: String?,
    val note: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
)
