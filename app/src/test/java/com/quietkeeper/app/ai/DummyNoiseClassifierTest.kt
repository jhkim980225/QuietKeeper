package com.quietkeeper.app.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DummyNoiseClassifierTest {
    private val c = DummyNoiseClassifier()

    @Test fun deterministic_sameInput_sameTag() = runTest {
        val a = c.classify("/x/event_5.wav", 70f); val b = c.classify("/x/event_5.wav", 70f)
        assertEquals(a.tag, b.tag); assertEquals(a.confidence, b.confidence, 1e-6f)
    }

    @Test fun confidence_in_range() = runTest {
        val r = c.classify("/x/event_1.wav", 95f)
        assertTrue(r.confidence in 0.6f..0.95f)
    }

    @Test fun returns_a_known_tag() = runTest {
        val known = setOf("발걸음", "뛰는 소리", "가구 끄는 소리", "망치질", "문 여닫음", "물건 낙하")
        assertTrue(c.classify("/x/event_9.wav", 60f).tag in known)
    }
}
