package com.bara.heybara.data.voice

import com.bara.heybara.domain.voice.WakeWordDetector

import android.content.Context
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback

class PorcupineWakeWordDetector(
    private val context: Context,
    private val accessKey: String,
    private val keywordPath: String,  // .ppn 파일 경로
    private val modelPath: String     // 한국어 모델(.pv) 경로
) : WakeWordDetector {

    private var porcupineManager: PorcupineManager? = null

    override fun start(onDetected: () -> Unit) {
        porcupineManager = PorcupineManager.Builder()
            .setAccessKey(accessKey)
            .setKeywordPath(keywordPath)
            .setModelPath(modelPath)
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
