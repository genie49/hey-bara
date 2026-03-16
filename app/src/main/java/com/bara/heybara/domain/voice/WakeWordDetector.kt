package com.bara.heybara.domain.voice

interface WakeWordDetector {
    fun start(onDetected: () -> Unit)
    fun stop()
    fun release()
}
