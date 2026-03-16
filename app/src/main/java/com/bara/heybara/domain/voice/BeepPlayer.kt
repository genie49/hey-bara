package com.bara.heybara.domain.voice

interface BeepPlayer {
    fun playBeep(onDone: () -> Unit = {})
    fun release()
}
