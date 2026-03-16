package com.bara.heybara.domain.session

import com.bara.heybara.domain.voice.*

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
    // STT 최종 결과가 나왔을 때 호출되는 콜백
    var onSpeechResult: ((String) -> Unit)? = null

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
        onSpeechResult?.invoke(text)
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
