package com.noisemeter.app.data
import androidx.room.*

@Dao
interface NoiseEventDao {
    @Insert suspend fun insert(e: NoiseEvent): Long
    @Query("SELECT * FROM noise_events ORDER BY timestamp DESC") suspend fun getAll(): List<NoiseEvent>
    @Update suspend fun update(e: NoiseEvent)
}
