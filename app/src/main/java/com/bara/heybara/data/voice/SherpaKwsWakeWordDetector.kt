package com.bara.heybara.data.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.bara.heybara.domain.voice.WakeWordDetector
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlin.concurrent.thread

class SherpaKwsWakeWordDetector(
    private val context: Context,
    private val modelDir: String  // KWS 모델 파일 디렉토리 경로
) : WakeWordDetector {

    companion object {
        private const val TAG = "SherpaKwsWakeWord"
        private const val SAMPLE_RATE = 16000
    }

    private var keywordSpotter: KeywordSpotter? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile
    private var isRunning = false

    override fun start(onDetected: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO 권한 없음")
            return
        }

        val config = KeywordSpotterConfig(
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelDir/encoder.onnx",
                    decoder = "$modelDir/decoder.onnx",
                    joiner = "$modelDir/joiner.onnx",
                ),
                tokens = "$modelDir/tokens.txt",
                modelType = "zipformer2",
            ),
            keywordsFile = "$modelDir/keywords.txt",
            keywordsScore = 1.5f,
            keywordsThreshold = 0.25f,
        )
        keywordSpotter = KeywordSpotter(null, config)

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        isRunning = true
        audioRecord?.startRecording()

        val stream = keywordSpotter!!.createStream()
        recordingThread = thread(name = "kws-audio") {
            val buffer = ShortArray(bufferSize / 2)
            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val samples = FloatArray(read) { buffer[it] / 32768.0f }
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    while (keywordSpotter?.isReady(stream) == true) {
                        keywordSpotter?.decode(stream)
                        val result = keywordSpotter?.getResult(stream)
                        if (result != null && result.keyword.isNotBlank()) {
                            Log.d(TAG, "웨이크워드 감지: ${result.keyword}")
                            keywordSpotter?.reset(stream)
                            if (isRunning) {
                                onDetected()
                            }
                        }
                    }
                }
            }
        }
        Log.d(TAG, "KWS 웨이크워드 감지 시작")
    }

    override fun stop() {
        isRunning = false
        audioRecord?.stop()
        recordingThread?.join(1000)
        recordingThread = null
    }

    override fun release() {
        stop()
        audioRecord?.release()
        audioRecord = null
        keywordSpotter?.release()
        keywordSpotter = null
    }
}
