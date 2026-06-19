package com.noisemeter.app.data
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [NoiseEvent::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noiseEventDao(): NoiseEventDao
}
