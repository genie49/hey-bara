package com.bara.heybara.domain.session

enum class SessionState {
    IDLE,       // Porcupine only, low power
    LISTENING,  // STT active, waiting for speech
    PROCESSING, // AI parsing command
    CONFIRMING  // Waiting for user confirmation
}
