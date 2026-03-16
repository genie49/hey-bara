package com.bara.heybara.session

import com.bara.heybara.voice.*

class VoiceSession(
    private val recognizer: SpeechRecognizer,
    private val tts: TtsEngine,
    private val beep: BeepPlayer
) {
    var currentState: SessionState = SessionState.IDLE
        private set

    var lastRecognizedText: String = ""
        private set

    var onStateChanged: ((SessionState) -> Unit)? = null

    fun onWakeWordDetected() {
        if (currentState != SessionState.IDLE) return
        transitionTo(SessionState.LISTENING)
        beep.playBeep {
            recognizer.start(
                onPartialResult = { /* update UI */ },
                onFinalResult = { text -> onSpeechRecognized(text) },
                onError = { endSession() }
            )
        }
    }

    fun onSpeechRecognized(text: String) {
        if (currentState != SessionState.LISTENING) return
        lastRecognizedText = text
        recognizer.stop()
        transitionTo(SessionState.PROCESSING)
    }

    fun endSession() {
        recognizer.release()
        transitionTo(SessionState.IDLE)
        lastRecognizedText = ""
    }

    fun speakAndEnd(text: String) {
        tts.speak(text) { endSession() }
    }

    private fun transitionTo(state: SessionState) {
        currentState = state
        onStateChanged?.invoke(state)
    }
}
