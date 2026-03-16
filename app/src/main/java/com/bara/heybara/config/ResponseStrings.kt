package com.bara.heybara.config

object ResponseStrings {
    // System-level messages (before agent involvement)
    const val LISTEN_FAIL = "잘 못 들었어요, 다시 말해주세요"
    const val NETWORK_FAIL = "인터넷 연결이 안 돼요"
    const val PERMISSION_NEEDED = "이 기능을 사용하려면 권한이 필요해요"

    // Temporary MVP strings (will be replaced by agent responses)
    const val ECHO_PREFIX = "라고 하셨나요?"
}
