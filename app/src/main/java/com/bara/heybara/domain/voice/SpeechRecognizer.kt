package com.bara.heybara.domain.voice

interface SpeechRecognizer {
    fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    )
    fun stop()
    fun release()
}
