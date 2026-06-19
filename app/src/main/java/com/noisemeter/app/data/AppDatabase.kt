package com.noisemeter.app.data
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [NoiseEvent::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noiseEventDao(): NoiseEventDao
}
