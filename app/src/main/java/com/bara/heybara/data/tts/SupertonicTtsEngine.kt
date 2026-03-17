package com.bara.heybara.data.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.bara.heybara.domain.voice.TtsEngine
import kotlinx.coroutines.*

class SupertonicTtsEngine(modelDir: String) : TtsEngine {

    companion object {
        private const val TAG = "SupertonicTts"
        private const val SAMPLE_RATE = 44100
    }

    private val preprocessor = TextPreprocessor(modelDir)
    private val inference = SupertonicInference(modelDir)
    private var audioTrack: AudioTrack? = null
    private var isLoaded = false

    fun loadModels() {
        if (isLoaded) return
        try {
            inference.load()
            isLoaded = true
            Log.d(TAG, "Supertonic 모델 로드 완료")
        } catch (e: Exception) {
            Log.e(TAG, "모델 로드 실패", e)
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (text.isBlank() || !isLoaded) {
            onDone()
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            try {
                Log.d(TAG, "합성 시작: $text")
                val (textIds, textMask) = preprocessor.preprocess(text)
                val pcm = inference.synthesize(textIds, textMask)
                playAudio(pcm)
                Log.d(TAG, "재생 완료")
            } catch (e: Exception) {
                Log.e(TAG, "TTS 실패", e)
            } finally {
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    private fun playAudio(pcm: ShortArray) {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize.coerceAtLeast(pcm.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(pcm, 0, pcm.size)
        audioTrack?.play()

        // 재생 완료까지 대기
        val durationMs = (pcm.size * 1000L) / SAMPLE_RATE
        Thread.sleep(durationMs + 100)

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    override fun stop() {
        audioTrack?.stop()
    }

    override fun release() {
        audioTrack?.release()
        audioTrack = null
        inference.release()
        isLoaded = false
    }
}
