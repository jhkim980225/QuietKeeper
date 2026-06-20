package com.quietkeeper.app.cloud
import org.junit.Assert.*
import org.junit.Test
class IntegrityHashTest {
    @Test fun deterministic_and_64hex() {
        val a = IntegrityHash.of(1000L, "dev1", "/e/event_1.wav", 71f)
        val b = IntegrityHash.of(1000L, "dev1", "/e/event_1.wav", 71f)
        assertEquals(a, b)
        assertEquals(64, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
    }
    @Test fun differs_on_different_input() {
        val a = IntegrityHash.of(1000L, "dev1", "/e/event_1.wav", 71f)
        val c = IntegrityHash.of(1001L, "dev1", "/e/event_1.wav", 71f)
        assertNotEquals(a, c)
    }
}
