package com.bara.heybara.voice

interface BeepPlayer {
    fun playBeep(onDone: () -> Unit = {})
    fun release()
}
