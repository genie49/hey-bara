package com.bara.heybara.ui

import androidx.lifecycle.ViewModel
import com.bara.heybara.domain.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: String
)

class MainViewModel : ViewModel() {
    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _sttPartialText = MutableStateFlow("")
    val sttPartialText: StateFlow<String> = _sttPartialText

    fun updateState(state: SessionState) {
        _sessionState.value = state
    }

    fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    fun updatePartialText(text: String) {
        _sttPartialText.value = text
    }
}
