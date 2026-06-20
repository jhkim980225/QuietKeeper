package com.quietkeeper.app.data
import androidx.room.*

@Dao
interface NoiseEventDao {
    @Insert suspend fun insert(e: NoiseEvent): Long
    @Query("SELECT * FROM noise_events ORDER BY timestamp DESC") suspend fun getAll(): List<NoiseEvent>
    @Update suspend fun update(e: NoiseEvent)
    @Query("SELECT * FROM noise_events WHERE id = :id") suspend fun getById(id: Long): NoiseEvent?
    @Delete suspend fun delete(e: NoiseEvent)
}
