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
    // 액션 실행 콜백 (Phase 2: 로그, Phase 3: 실제 실행)
    var onActionExecute: ((AgentAction) -> Unit)? = null

    // CONFIRMING 상태에서 대기 중인 응답
    private var pendingResponse: AgentResponse? = null

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

    // Phase 2: AgentEngine으로 명령 처리
    suspend fun processWithAgent() {
        if (currentState != SessionState.PROCESSING) return
        if (agent == null) {
            // Phase 1 폴백: 에코백
            speakAndEnd("${lastRecognizedText}라고 하셨나요?")
            return
        }
        try {
            val response = agent.process(lastRecognizedText)
            pendingResponse = response
            if (response.requiresConfirmation) {
                transitionTo(SessionState.CONFIRMING)
                tts.speak(response.text) {}
            } else {
                tts.speak(response.text) { endSession() }
            }
        } catch (e: Exception) {
            tts.speak("이해하지 못했어요") { endSession() }
        }
    }

    // CONFIRMING 상태에서 확인
    fun confirmAction() {
        pendingResponse?.action?.let { action ->
            onActionExecute?.invoke(action)
        }
        pendingResponse = null
        endSession()
    }

    // CONFIRMING 상태에서 취소
    fun cancelAction() {
        pendingResponse = null
        tts.speak("취소할게요") { endSession() }
    }

    fun endSession() {
        recognizer.release()
        transitionTo(SessionState.IDLE)
        lastRecognizedText = ""
        pendingResponse = null
    }

    fun speakAndEnd(text: String) {
        tts.speak(text) { endSession() }
    }

    private fun transitionTo(state: SessionState) {
        currentState = state
        onStateChanged?.invoke(state)
    }
}
