package com.bara.heybara.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bara.heybara.data.agent.KoogAgentEngine
import com.bara.heybara.data.settings.SecurePreferences
import com.bara.heybara.domain.agent.AgentEngine
import com.bara.heybara.domain.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey

    private var agentEngine: AgentEngine? = null

    fun updateState(state: SessionState) {
        _sessionState.value = state
    }

    fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    fun updatePartialText(text: String) {
        _sttPartialText.value = text
    }

    fun updateApiKeyStatus(hasKey: Boolean) {
        _hasApiKey.value = hasKey
    }

    // 텍스트 입력 시 AgentEngine 초기화 + 처리
    fun initAgent(context: Context) {
        if (agentEngine != null) return
        val apiKey = SecurePreferences(context).getGeminiApiKey() ?: return
        agentEngine = KoogAgentEngine(apiKey)
    }

    fun sendMessage(text: String, context: Context) {
        if (text.isBlank()) return
        initAgent(context)

        val now = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date())
        addMessage(ChatMessage(text, isUser = true, timestamp = now))
        updateState(SessionState.PROCESSING)

        viewModelScope.launch {
            try {
                val response = agentEngine?.process(text)
                if (response != null) {
                    addMessage(ChatMessage(response.text, isUser = false, timestamp = now))
                    // 액션이 있으면 로그로 표시
                    response.action?.let {
                        addMessage(ChatMessage("[액션: $it]", isUser = false, timestamp = now))
                    }
                } else {
                    addMessage(ChatMessage("AgentEngine이 초기화되지 않았습니다", isUser = false, timestamp = now))
                }
            } catch (e: Exception) {
                addMessage(ChatMessage("오류: ${e.message}", isUser = false, timestamp = now))
            } finally {
                updateState(SessionState.IDLE)
            }
        }
    }

    override fun onCleared() {
        agentEngine?.release()
        super.onCleared()
    }
}
