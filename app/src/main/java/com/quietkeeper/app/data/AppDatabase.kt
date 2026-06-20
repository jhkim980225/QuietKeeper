package com.quietkeeper.app.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoiseEvent::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noiseEventDao(): NoiseEventDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v1 -> v2: add nullable GPS columns. Real ALTER TABLE so existing event
        // data survives the upgrade (no destructive fallback).
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noise_events ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE noise_events ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE noise_events ADD COLUMN address TEXT")
            }
        }

        // v2 -> v3: add nullable integrityHash column (SHA-256 tamper-evidence).
        // Real ALTER TABLE so existing event data survives the upgrade (no destructive fallback).
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE noise_events ADD COLUMN integrityHash TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "noise.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
