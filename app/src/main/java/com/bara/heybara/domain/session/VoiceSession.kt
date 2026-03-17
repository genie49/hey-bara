package com.bara.heybara.domain.session

import com.bara.heybara.domain.agent.*
import com.bara.heybara.domain.voice.*

class VoiceSession(
    private val recognizer: SpeechRecognizer,
    private val tts: TtsEngine,
    private val beep: BeepPlayer,
    private val agent: AgentEngine? = null
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
                onPartialResult = { /* UI 업데이트 */ },
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

    // AgentEngine으로 명령 처리 (Tool이 직접 확인/실행)
    suspend fun processWithAgent() {
        if (currentState != SessionState.PROCESSING) return
        if (agent == null) {
            speakAndEnd("${lastRecognizedText}라고 하셨나요?")
            return
        }
        try {
            val result = agent.process(lastRecognizedText)
            tts.speak(result) { endSession() }
        } catch (e: Exception) {
            tts.speak("이해하지 못했어요") { endSession() }
        }
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
