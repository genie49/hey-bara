package com.bara.heybara.voice

interface TtsEngine {
    fun speak(text: String, onDone: () -> Unit = {})
    fun stop()
    fun release()
}
