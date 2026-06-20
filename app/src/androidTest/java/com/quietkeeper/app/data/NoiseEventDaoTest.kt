package com.quietkeeper.app.data
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoiseEventDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: NoiseEventDao
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.noiseEventDao()
    }
    @After fun close() = db.close()

    @Test fun insertAndReadBack() = runBlocking {
        dao.insert(NoiseEvent(timestamp = 1000L, peakDb = 71f, leq = 63f,
            wavPath = "/p/e.wav", moved = false, tag = null, note = null))
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(71f, all[0].peakDb)
    }

    @Test fun updateTagAndNote() = runBlocking {
        val id = dao.insert(NoiseEvent(timestamp = 2000L, peakDb = 60f, leq = 50f,
            wavPath = "/p/e2.wav", moved = true, tag = null, note = null))
        val e = dao.getAll().first { it.id == id }
        dao.update(e.copy(tag = "발걸음", note = "밤 9시"))
        val updated = dao.getAll().first { it.id == id }
        assertEquals("발걸음", updated.tag)
        assertEquals("밤 9시", updated.note)
    }

    @Test fun orderedByTimestampDesc() = runBlocking {
        dao.insert(NoiseEvent(timestamp = 100L, peakDb = 1f, leq = 1f, wavPath = "a", moved = false, tag = null, note = null))
        dao.insert(NoiseEvent(timestamp = 300L, peakDb = 3f, leq = 3f, wavPath = "c", moved = false, tag = null, note = null))
        dao.insert(NoiseEvent(timestamp = 200L, peakDb = 2f, leq = 2f, wavPath = "b", moved = false, tag = null, note = null))
        val all = dao.getAll()
        assertEquals(300L, all[0].timestamp)
        assertEquals(100L, all[2].timestamp)
    }
}
