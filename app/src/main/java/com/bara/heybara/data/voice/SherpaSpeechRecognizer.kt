package com.bara.heybara.data.voice

import com.bara.heybara.domain.voice.SpeechRecognizer

import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

class SherpaSpeechRecognizer(
    private val modelDir: String  // path to Zipformer Korean model files
) : SpeechRecognizer {

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var isListening = false

    private fun initRecognizer() {
        val config = OnlineRecognizerConfig(
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelDir/encoder-epoch-99-avg-1.onnx",
                    decoder = "$modelDir/decoder-epoch-99-avg-1.onnx",
                    joiner = "$modelDir/joiner-epoch-99-avg-1.onnx",
                ),
                tokens = "$modelDir/tokens.txt",
                modelType = "zipformer",
            ),
            enableEndpoint = true,
        )
        // 모델 파일을 assets가 아닌 파일 시스템 경로에서 로드하므로 AssetManager는 null
        recognizer = OnlineRecognizer(null, config)
    }

    override fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (recognizer == null) initRecognizer()
            stream = recognizer?.createStream()
            isListening = true
            // 오디오 녹음 및 스트림 전달은 BaraService의 오디오 스레드에서 처리
        } catch (e: Exception) {
            onError(e.message ?: "STT initialization failed")
        }
    }

    fun feedAudio(samples: FloatArray) {
        if (!isListening) return
        stream?.acceptWaveform(samples, 16000)
        recognizer?.let { rec ->
            stream?.let { s ->
                while (rec.isReady(s)) {
                    rec.decode(s)
                }
            }
        }
    }

    fun getPartialResult(): String {
        return stream?.let { recognizer?.getResult(it)?.text } ?: ""
    }

    override fun stop() {
        isListening = false
    }

    override fun release() {
        stream = null
        recognizer = null
        isListening = false
    }
}
