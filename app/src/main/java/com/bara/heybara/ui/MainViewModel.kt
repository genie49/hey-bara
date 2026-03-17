package com.bara.heybara.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bara.heybara.data.action.DeviceContactResolver
import com.bara.heybara.data.agent.KoogAgentEngine
import com.bara.heybara.data.settings.SecurePreferences
import com.bara.heybara.data.history.AppDatabase
import com.bara.heybara.data.history.RoomConversationRepository
import com.bara.heybara.domain.history.Conversation
import com.bara.heybara.domain.history.ConversationRepository
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

    private var agentEngine: KoogAgentEngine? = null
    private var conversationRepository: ConversationRepository? = null
    private var appContext: Context? = null

    fun updateState(state: SessionState) {
        _sessionState.value = state
    }

    fun addMessage(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    fun clearChat() {
        _messages.value = emptyList()
        agentEngine?.release()
        agentEngine = null
    }

    fun updatePartialText(text: String) {
        _sttPartialText.value = text
    }

    fun updateApiKeyStatus(hasKey: Boolean) {
        _hasApiKey.value = hasKey
    }

    fun initAgent(context: Context) {
        if (agentEngine != null) return
        appContext = context.applicationContext
        val apiKey = SecurePreferences(context).getGeminiApiKey() ?: return
        val contactResolver = DeviceContactResolver(context)
        agentEngine = KoogAgentEngine(apiKey, contactResolver).also {
            it.setContext(context)
        }
        conversationRepository = RoomConversationRepository(
            AppDatabase.getInstance(context).conversationDao()
        )
    }

    fun sendMessage(text: String, context: Context) {
        if (text.isBlank()) return
        initAgent(context)

        val now = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date())
        addMessage(ChatMessage(text, isUser = true, timestamp = now))
        updateState(SessionState.PROCESSING)

        viewModelScope.launch {
            try {
                val result = agentEngine?.process(text)
                if (result != null) {
                    addMessage(ChatMessage(result, isUser = false, timestamp = now))
                } else {
                    addMessage(ChatMessage("AgentEngine이 초기화되지 않았습니다", isUser = false, timestamp = now))
                }
            } catch (e: Exception) {
                addMessage(ChatMessage("오류: ${e.message}", isUser = false, timestamp = now))
            } finally {
                updateState(SessionState.IDLE)
                saveConversationHistory()
            }
        }
    }

    // LLM 요약 후 Room DB에 저장 (별도 에이전트로 요약하여 대화 히스토리에 섞이지 않음)
    private fun saveConversationHistory() {
        val engine = agentEngine ?: return
        val repo = conversationRepository ?: return
        val transcript = engine.getConversationTranscript()
        if (transcript.isBlank()) return

        viewModelScope.launch {
            try {
                // 별도 에이전트로 요약 (메인 대화 히스토리에 영향 없음)
                val summaryAgent = KoogAgentEngine(
                    SecurePreferences(appContext!!).getGeminiApiKey()!!
                )
                val summaryResponse = summaryAgent.process(
                    "다음 대화를 한 줄로 요약하고 카테고리를 분류해줘. " +
                    "형식: 요약|카테고리 (카테고리는 call, sms, chat 중 하나)\n\n$transcript"
                )
                summaryAgent.release()

                val parts = summaryResponse.split("|").map { it.trim() }
                val topic = parts.getOrElse(0) { "대화" }
                val category = parts.getOrElse(1) { "chat" }.lowercase()
                    .let { if (it in listOf("call", "sms", "chat")) it else "chat" }

                repo.save(
                    Conversation(
                        topic = topic,
                        category = category,
                        inputMode = "text",
                        timestamp = System.currentTimeMillis(),
                        transcript = transcript
                    )
                )
                Log.d("MainViewModel", "대화 히스토리 저장: $topic ($category)")
            } catch (e: Exception) {
                Log.e("MainViewModel", "히스토리 요약 실패, 기본값 저장", e)
                repo.save(
                    Conversation(
                        topic = "대화",
                        category = "chat",
                        inputMode = "text",
                        timestamp = System.currentTimeMillis(),
                        transcript = transcript
                    )
                )
            }
        }
    }

    override fun onCleared() {
        agentEngine?.release()
        super.onCleared()
    }
}
