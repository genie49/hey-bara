package com.bara.heybara.data.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import kotlinx.serialization.json.*
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.ceil
import kotlin.random.Random

class SupertonicInference(private val modelDir: String) {

    companion object {
        private const val TAG = "SupertonicInference"
        private const val SAMPLE_RATE = 44100
        private const val CHUNK_SIZE = 3072       // 512 * 6
        private const val LATENT_DIM = 144        // 24 * 6
        private const val DENOISE_STEPS = 2
        private const val SPEED = 1.05f
    }

    private val env = OrtEnvironment.getEnvironment()
    private var durationPredictor: OrtSession? = null
    private var textEncoder: OrtSession? = null
    private var vectorEstimator: OrtSession? = null
    private var vocoder: OrtSession? = null

    // 보이스 스타일 텐서 데이터
    private var styleTtl: Array<Array<FloatArray>>? = null  // [1, 50, 256]
    private var styleDp: Array<Array<FloatArray>>? = null    // [1, 8, 16]

    fun load() {
        Log.d(TAG, "모델 로딩 시작")
        durationPredictor = env.createSession("$modelDir/duration_predictor.onnx")
        textEncoder = env.createSession("$modelDir/text_encoder.onnx")
        vectorEstimator = env.createSession("$modelDir/vector_estimator.onnx")
        vocoder = env.createSession("$modelDir/vocoder.onnx")
        loadVoiceStyle()
        Log.d(TAG, "모델 로딩 완료")
    }

    private fun loadVoiceStyle() {
        val json = Json.parseToJsonElement(File(modelDir, "voice_style.json").readText()).jsonObject

        val ttlObj = json["style_ttl"]!!.jsonObject
        val ttlData = ttlObj["data"]!!.jsonArray
        val ttlDims = ttlObj["dims"]!!.jsonArray.map { it.jsonPrimitive.int }
        styleTtl = parse3dArray(ttlData, ttlDims[0], ttlDims[1], ttlDims[2])

        val dpObj = json["style_dp"]!!.jsonObject
        val dpData = dpObj["data"]!!.jsonArray
        val dpDims = dpObj["dims"]!!.jsonArray.map { it.jsonPrimitive.int }
        styleDp = parse3dArray(dpData, dpDims[0], dpDims[1], dpDims[2])

        Log.d(TAG, "보이스 스타일 로드: ttl[${ttlDims.joinToString()}], dp[${dpDims.joinToString()}]")
    }

    private fun parse3dArray(data: JsonArray, d0: Int, d1: Int, d2: Int): Array<Array<FloatArray>> {
        return Array(d0) { i ->
            Array(d1) { j ->
                FloatArray(d2) { k ->
                    data[i].jsonArray[j].jsonArray[k].jsonPrimitive.float
                }
            }
        }
    }

    fun synthesize(textIds: LongArray, textMask: FloatArray): ShortArray {
        val batchSize = 1
        val textLen = textIds.size

        // 텐서 생성
        val textIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(textIds), longArrayOf(1, textLen.toLong()))
        val textMaskTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(textMask), longArrayOf(1, 1, textLen.toLong()))
        val styleDpTensor = createStyleTensor(styleDp!!)
        val styleTtlTensor = createStyleTensor(styleTtl!!)

        // 1. Duration Predictor
        val dpResult = durationPredictor!!.run(mapOf(
            "text_ids" to textIdsTensor,
            "style_dp" to styleDpTensor,
            "text_mask" to textMaskTensor
        ))
        val duration = (dpResult[0].value as FloatArray)[0] / SPEED
        Log.d(TAG, "예상 길이: ${duration}초")

        // 2. Text Encoder
        val teResult = textEncoder!!.run(mapOf(
            "text_ids" to textIdsTensor,
            "style_ttl" to styleTtlTensor,
            "text_mask" to textMaskTensor
        ))
        val textEmb = teResult[0].value  // float[][][]

        // latent 크기 계산
        val wavLenMax = (duration * SAMPLE_RATE).toLong()
        val latentLen = ceil(wavLenMax.toDouble() / CHUNK_SIZE).toInt().coerceAtLeast(1)

        // 3. Vector Estimator (2스텝 루프)
        var latent = FloatArray(LATENT_DIM * latentLen) { Random.nextFloat() * 2 - 1 } // randn
        val latentMask = FloatArray(latentLen) { 1.0f }

        val textEmbTensor = teResult[0] as OnnxTensor
        val latentMaskTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(latentMask), longArrayOf(1, 1, latentLen.toLong()))

        for (step in 0 until DENOISE_STEPS) {
            val noisyLatentTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(latent), longArrayOf(1, LATENT_DIM.toLong(), latentLen.toLong()))
            val currentStepTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(step.toFloat())), longArrayOf(1))
            val totalStepTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(DENOISE_STEPS.toFloat())), longArrayOf(1))

            val veResult = vectorEstimator!!.run(mapOf(
                "noisy_latent" to noisyLatentTensor,
                "text_emb" to textEmbTensor,
                "style_ttl" to styleTtlTensor,
                "latent_mask" to latentMaskTensor,
                "text_mask" to textMaskTensor,
                "current_step" to currentStepTensor,
                "total_step" to totalStepTensor
            ))

            latent = (veResult[0].value as Array<Array<FloatArray>>).flatMap { it.flatMap { arr -> arr.toList() } }.toFloatArray()
            Log.d(TAG, "디노이징 스텝 ${step + 1}/$DENOISE_STEPS 완료")
        }

        // 4. Vocoder
        val latentTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(latent), longArrayOf(1, LATENT_DIM.toLong(), latentLen.toLong()))
        val vocResult = vocoder!!.run(mapOf("latent" to latentTensor))
        val wavFloat = (vocResult[0].value as Array<FloatArray>)[0]

        // float → 16-bit PCM
        val sampleCount = (SAMPLE_RATE * duration).toInt().coerceAtMost(wavFloat.size)
        val pcm = ShortArray(sampleCount) { i ->
            (wavFloat[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }

        Log.d(TAG, "합성 완료: ${pcm.size} samples (${pcm.size / SAMPLE_RATE.toFloat()}초)")
        return pcm
    }

    private fun createStyleTensor(style: Array<Array<FloatArray>>): OnnxTensor {
        val d0 = style.size
        val d1 = style[0].size
        val d2 = style[0][0].size
        val flat = FloatArray(d0 * d1 * d2)
        var idx = 0
        for (i in 0 until d0) for (j in 0 until d1) for (k in 0 until d2) {
            flat[idx++] = style[i][j][k]
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), longArrayOf(d0.toLong(), d1.toLong(), d2.toLong()))
    }

    fun release() {
        durationPredictor?.close()
        textEncoder?.close()
        vectorEstimator?.close()
        vocoder?.close()
        durationPredictor = null
        textEncoder = null
        vectorEstimator = null
        vocoder = null
    }
}
