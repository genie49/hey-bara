package com.bara.heybara.domain.voice

interface TtsEngine {
    fun speak(text: String, onDone: () -> Unit = {})
    fun stop()
    fun release()
}
