package com.bara.heybara.domain.session

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import com.bara.heybara.domain.agent.*
import com.bara.heybara.domain.voice.*
import kotlinx.coroutines.test.runTest

class VoiceSessionTest {

    private lateinit var mockRecognizer: SpeechRecognizer
    private lateinit var mockTts: TtsEngine
    private lateinit var mockBeep: BeepPlayer
    private lateinit var mockAgent: AgentEngine
    private lateinit var session: VoiceSession

    @Before
    fun setup() {
        mockRecognizer = mock()
        mockTts = mock()
        mockBeep = mock()
        mockAgent = mock()
        session = VoiceSession(mockRecognizer, mockTts, mockBeep, mockAgent)
    }

    // === Phase 1 기존 테스트 ===

    @Test
    fun `initial state is IDLE`() {
        assertEquals(SessionState.IDLE, session.currentState)
    }

    @Test
    fun `onWakeWordDetected transitions to LISTENING`() {
        session.onWakeWordDetected()
        assertEquals(SessionState.LISTENING, session.currentState)
    }

    @Test
    fun `onWakeWordDetected plays beep`() {
        session.onWakeWordDetected()
        verify(mockBeep).playBeep(any())
    }

    @Test
    fun `onSpeechRecognized transitions to PROCESSING`() {
        session.onWakeWordDetected()
        session.onSpeechRecognized("엄마한테 전화해")
        assertEquals(SessionState.PROCESSING, session.currentState)
    }

    @Test
    fun `onSpeechRecognized stores recognized text`() {
        session.onWakeWordDetected()
        session.onSpeechRecognized("테스트")
        assertEquals("테스트", session.lastRecognizedText)
    }

    @Test
    fun `endSession transitions back to IDLE`() {
        session.onWakeWordDetected()
        session.onSpeechRecognized("테스트")
        session.endSession()
        assertEquals(SessionState.IDLE, session.currentState)
    }

    @Test
    fun `endSession releases recognizer`() {
        session.onWakeWordDetected()
        session.endSession()
        verify(mockRecognizer).release()
    }

    @Test
    fun `cannot transition from IDLE to PROCESSING directly`() {
        session.onSpeechRecognized("test")
        assertEquals(SessionState.IDLE, session.currentState)
    }

    @Test
    fun `onWakeWordDetected ignored when already LISTENING`() {
        session.onWakeWordDetected()
        assertEquals(SessionState.LISTENING, session.currentState)
        session.onWakeWordDetected()
        verify(mockBeep, times(1)).playBeep(any())
    }

    @Test
    fun `endSession clears lastRecognizedText`() {
        session.onWakeWordDetected()
        session.onSpeechRecognized("테스트")
        assertEquals("테스트", session.lastRecognizedText)
        session.endSession()
        assertEquals("", session.lastRecognizedText)
    }

    @Test
    fun `speakAndEnd calls tts speak`() {
        session.onWakeWordDetected()
        session.speakAndEnd("안녕하세요")
        verify(mockTts).speak(eq("안녕하세요"), any())
    }

    @Test
    fun `onStateChanged callback is invoked on transition`() {
        val states = mutableListOf<SessionState>()
        session.onStateChanged = { states.add(it) }
        session.onWakeWordDetected()
        session.onSpeechRecognized("테스트")
        assertEquals(listOf(SessionState.LISTENING, SessionState.PROCESSING), states)
    }

    @Test
    fun `onSpeechResult callback is invoked with recognized text`() {
        var resultText = ""
        session.onSpeechResult = { resultText = it }
        session.onWakeWordDetected()
        session.onSpeechRecognized("엄마한테 전화해")
        assertEquals("엄마한테 전화해", resultText)
    }

    @Test
    fun `onSpeechResult callback not invoked from IDLE`() {
        var called = false
        session.onSpeechResult = { called = true }
        session.onSpeechRecognized("테스트")
        assertFalse(called)
    }

    @Test
    fun `onSpeechRecognized ignored when in PROCESSING`() {
        session.onWakeWordDetected()
        session.onSpeechRecognized("첫 번째")
        assertEquals(SessionState.PROCESSING, session.currentState)
        session.onSpeechRecognized("두 번째")
        assertEquals("첫 번째", session.lastRecognizedText)
    }

    // === Phase 2 AgentEngine 테스트 ===

    @Test
    fun `processWithAgent transitions to CONFIRMING when confirmation required`() = runTest {
        whenever(mockAgent.process("엄마한테 전화해")).thenReturn(
            AgentResponse("엄마한테 전화를 걸까요?", AgentAction.Call("엄마", null), true)
        )
        session.onWakeWordDetected()
        session.onSpeechRecognized("엄마한테 전화해")
        session.processWithAgent()
        assertEquals(SessionState.CONFIRMING, session.currentState)
    }

    @Test
    fun `processWithAgent speaks response when confirmation required`() = runTest {
        whenever(mockAgent.process("엄마한테 전화해")).thenReturn(
            AgentResponse("엄마한테 전화를 걸까요?", AgentAction.Call("엄마", null), true)
        )
        session.onWakeWordDetected()
        session.onSpeechRecognized("엄마한테 전화해")
        session.processWithAgent()
        verify(mockTts).speak(eq("엄마한테 전화를 걸까요?"), any())
    }

    @Test
    fun `processWithAgent speaks and ends when no confirmation needed`() = runTest {
        whenever(mockAgent.process("오늘 날씨")).thenReturn(
            AgentResponse("오늘 서울은 맑아요", null, false)
        )
        session.onWakeWordDetected()
        session.onSpeechRecognized("오늘 날씨")
        session.processWithAgent()
        verify(mockTts).speak(eq("오늘 서울은 맑아요"), any())
    }

    @Test
    fun `processWithAgent handles error and returns to IDLE`() = runTest {
        whenever(mockAgent.process(any())).thenThrow(RuntimeException("Network error"))
        session.onWakeWordDetected()
        session.onSpeechRecognized("테스트")
        session.processWithAgent()
        verify(mockTts).speak(eq("이해하지 못했어요"), any())
    }

    @Test
    fun `processWithAgent falls back to echo when no agent`() = runTest {
        val sessionNoAgent = VoiceSession(mockRecognizer, mockTts, mockBeep)
        sessionNoAgent.onWakeWordDetected()
        sessionNoAgent.onSpeechRecognized("테스트")
        sessionNoAgent.processWithAgent()
        verify(mockTts).speak(eq("테스트라고 하셨나요?"), any())
    }

    @Test
    fun `confirmAction invokes onActionExecute callback`() = runTest {
        var executedAction: AgentAction? = null
        session.onActionExecute = { executedAction = it }
        whenever(mockAgent.process("엄마한테 전화해")).thenReturn(
            AgentResponse("엄마한테 전화를 걸까요?", AgentAction.Call("엄마", null), true)
        )
        session.onWakeWordDetected()
        session.onSpeechRecognized("엄마한테 전화해")
        session.processWithAgent()
        session.confirmAction()
        assertEquals(AgentAction.Call("엄마", null), executedAction)
        assertEquals(SessionState.IDLE, session.currentState)
    }

    @Test
    fun `cancelAction speaks cancel message and returns to IDLE`() {
        session.onWakeWordDetected()
        session.onSpeechRecognized("테스트")
        session.cancelAction()
        verify(mockTts).speak(eq("취소할게요"), any())
    }

    @Test
    fun `processWithAgent ignored when not in PROCESSING`() = runTest {
        session.processWithAgent()
        verifyNoInteractions(mockAgent)
    }
}
