package com.quietkeeper.app.ai

import kotlinx.coroutines.delay

class DummyNoiseClassifier : NoiseClassifier {
    private val tags = listOf("발걸음", "뛰는 소리", "가구 끄는 소리", "망치질", "문 여닫음", "물건 낙하")

    override suspend fun classify(wavPath: String, peakDb: Float): AiResult {
        delay(150) // simulate inference latency
        // deterministic pick from a stable hash of the filename
        val name = wavPath.substringAfterLast('/')
        val idx = (kotlin.math.abs(name.hashCode())) % tags.size
        // confidence loosely scaled by loudness (clamped 0.6..0.95), deterministic
        val conf = (0.6f + ((peakDb.coerceIn(40f, 90f) - 40f) / 50f) * 0.35f).coerceIn(0.6f, 0.95f)
        return AiResult(tags[idx], conf)
    }
}
