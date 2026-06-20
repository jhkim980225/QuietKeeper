package com.quietkeeper.app.ai

object Ai {
    @Volatile
    var classifier: NoiseClassifier = DummyNoiseClassifier()
}
