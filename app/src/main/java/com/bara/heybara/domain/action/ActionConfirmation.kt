package com.bara.heybara.domain.action

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// UI에 표시할 확인 요청
data class ConfirmationRequest(
    val description: String,  // "마루타1님에게 '안녕하세요'라고 문자를 보냅니다"
    val type: ActionType,
    val deferred: CompletableDeferred<Boolean>
)

enum class ActionType { CALL, SMS }

// Tool ↔ UI 사이의 확인 브릿지 (싱글톤)
object ActionConfirmation {
    private val _pendingRequest = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingRequest: StateFlow<ConfirmationRequest?> = _pendingRequest

    // Tool에서 호출: UI에 확인 요청 후 결과 대기
    suspend fun requestConfirmation(description: String, type: ActionType): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _pendingRequest.value = ConfirmationRequest(description, type, deferred)
        return try {
            deferred.await()
        } finally {
            _pendingRequest.value = null
        }
    }

    // UI에서 호출: 사용자 응답 전달
    fun confirm() {
        _pendingRequest.value?.deferred?.complete(true)
    }

    fun deny() {
        _pendingRequest.value?.deferred?.complete(false)
    }
}
