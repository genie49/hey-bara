package com.bara.heybara.session

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import com.bara.heybara.voice.*

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
}
