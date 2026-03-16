package com.bara.heybara.domain.session

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import com.bara.heybara.domain.voice.*

class VoiceSessionTest {

    private lateinit var mockRecognizer: SpeechRecognizer
    private lateinit var mockTts: TtsEngine
    private lateinit var mockBeep: BeepPlayer
    private lateinit var session: VoiceSession

    @Before
    fun setup() {
        mockRecognizer = mock()
        mockTts = mock()
        mockBeep = mock()
        session = VoiceSession(mockRecognizer, mockTts, mockBeep)
    }

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
        // beep은 최초 1회만 호출
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
    fun `onSpeechRecognized ignored when in PROCESSING`() {
        session.onWakeWordDetected()
        session.onSpeechRecognized("첫 번째")
        assertEquals(SessionState.PROCESSING, session.currentState)
        session.onSpeechRecognized("두 번째")
        // PROCESSING 상태에서 무시되므로 첫 번째 텍스트 유지
        assertEquals("첫 번째", session.lastRecognizedText)
    }
}
