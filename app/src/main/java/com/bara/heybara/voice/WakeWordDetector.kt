package com.bara.heybara.voice

interface WakeWordDetector {
    fun start(onDetected: () -> Unit)
    fun stop()
    fun release()
}
