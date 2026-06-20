package com.quietkeeper.app.ai

data class AiResult(val tag: String, val confidence: Float)

interface NoiseClassifier {
    /** Called after a recording is saved. Returns a suggested noise-type tag. */
    suspend fun classify(wavPath: String, peakDb: Float): AiResult
}
