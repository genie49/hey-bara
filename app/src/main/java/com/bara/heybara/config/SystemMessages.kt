package com.bara.heybara.config

object SystemMessages {
    // 시스템 레벨 메시지 (에이전트 개입 전)
    const val LISTEN_FAIL = "잘 못 들었어요, 다시 말해주세요"
    const val NETWORK_FAIL = "인터넷 연결이 안 돼요"
    const val PERMISSION_NEEDED = "이 기능을 사용하려면 권한이 필요해요"
    const val UNDERSTAND_FAIL = "이해하지 못했어요"
    const val API_KEY_INVALID = "API 키를 확인해주세요"
    const val API_KEY_NEEDED = "설정에서 API Key를 입력해주세요"
    const val ACTION_CANCELLED = "취소할게요"

    // 임시 MVP 문자열 (추후 에이전트 응답으로 대체)
    const val ECHO_PREFIX = "라고 하셨나요?"
}
