package com.bara.heybara.voice

import android.content.Context
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback

class PorcupineWakeWordDetector(
    private val context: Context,
    private val accessKey: String,
    private val keywordPath: String  // path to custom "헤이 바라" .ppn file
) : WakeWordDetector {

    private var porcupineManager: PorcupineManager? = null

    override fun start(onDetected: () -> Unit) {
        porcupineManager = PorcupineManager.Builder()
            .setAccessKey(accessKey)
            .setKeywordPath(keywordPath)
            .setSensitivity(0.5f)
            .build(context, PorcupineManagerCallback { keywordIndex ->
                if (keywordIndex >= 0) {
                    onDetected()
                }
            })
        porcupineManager?.start()
    }

    override fun stop() {
        porcupineManager?.stop()
    }

    override fun release() {
        porcupineManager?.delete()
        porcupineManager = null
    }
}
