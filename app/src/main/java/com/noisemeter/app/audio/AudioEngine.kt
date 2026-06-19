package com.noisemeter.app.audio

class AudioEngine(private val listener: Listener) {
    interface Listener { fun onEvent(wavPath: String, peakDb: Float, leq: Float) }

    companion object { init { System.loadLibrary("audiocore") } }

    fun start(outDir: String, calibrationOffset: Float, thresholdDb: Float) =
        nativeStart(outDir, calibrationOffset, thresholdDb, listener)
    fun stop() = nativeStop()
    /** @return [db, leq, lmax] */
    fun poll(): FloatArray = nativePoll()

    private external fun nativeStart(outDir: String, offset: Float, threshold: Float, listener: Listener)
    private external fun nativeStop()
    private external fun nativePoll(): FloatArray
}
