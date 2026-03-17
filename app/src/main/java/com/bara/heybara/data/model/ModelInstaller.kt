package com.bara.heybara.data.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelInstaller {

    private const val TAG = "ModelInstaller"

    // STT: HuggingFace 개별 파일 다운로드
    private const val STT_BASE_URL =
        "https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main"

    private val STT_FILES = listOf(
        "encoder-epoch-99-avg-1.onnx",
        "decoder-epoch-99-avg-1.onnx",
        "joiner-epoch-99-avg-1.onnx",
        "tokens.txt"
    )

    // KWS: GitHub release tar.bz2
    private const val KWS_ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2"

    // tar.bz2에서 추출할 파일 (원본명 → 저장명)
    private val KWS_FILES = mapOf(
        "encoder-epoch-13-avg-2-chunk-8-left-64.int8.onnx" to "encoder.onnx",
        "decoder-epoch-13-avg-2-chunk-8-left-64.onnx" to "decoder.onnx",
        "joiner-epoch-13-avg-2-chunk-8-left-64.int8.onnx" to "joiner.onnx",
        "tokens.txt" to "tokens.txt"
    )

    // "HH EY1 B AA1 R AH0 @HEY_BARA" — text2token으로 생성한 커스텀 키워드
    private const val KEYWORDS_CONTENT = "HH EY1 B AA1 R AH0 @HEY_BARA\n"

    // --- STT 상태 ---
    private val _sttState = MutableStateFlow(InstallState.NOT_INSTALLED)
    val sttState: StateFlow<InstallState> = _sttState
    private val _sttProgress = MutableStateFlow(0)
    val sttProgress: StateFlow<Int> = _sttProgress

    // --- TTS 상태 ---
    private val _ttsState = MutableStateFlow(InstallState.NOT_INSTALLED)
    val ttsState: StateFlow<InstallState> = _ttsState
    private val _ttsProgress = MutableStateFlow(0)
    val ttsProgress: StateFlow<Int> = _ttsProgress

    // TTS: HuggingFace Supertonic 2
    private const val TTS_BASE_URL =
        "https://huggingface.co/Supertone/supertonic-2/resolve/main/onnx"

    private val TTS_FILES = listOf(
        "duration_predictor.onnx",
        "text_encoder.onnx",
        "vector_estimator.onnx",
        "vocoder.onnx",
        "tts.json",
        "unicode_indexer.json"
    )

    // 보이스 스타일 (여성 F1)
    private const val VOICE_STYLE_URL =
        "https://huggingface.co/Supertone/supertonic-2/resolve/main/voice_styles/F1.json"

    // --- KWS 상태 ---
    private val _kwsState = MutableStateFlow(InstallState.NOT_INSTALLED)
    val kwsState: StateFlow<InstallState> = _kwsState
    private val _kwsProgress = MutableStateFlow(0)
    val kwsProgress: StateFlow<Int> = _kwsProgress

    fun checkInstalled(context: Context) {
        _sttState.value = if (isSttInstalled(context)) InstallState.INSTALLED else InstallState.NOT_INSTALLED
        _kwsState.value = if (isKwsInstalled(context)) InstallState.INSTALLED else InstallState.NOT_INSTALLED
        _ttsState.value = if (isTtsInstalled(context)) InstallState.INSTALLED else InstallState.NOT_INSTALLED
    }

    fun isSttInstalled(context: Context): Boolean {
        val sttDir = File(context.filesDir, "models/stt")
        return STT_FILES.all { File(sttDir, it).exists() }
    }

    fun isKwsInstalled(context: Context): Boolean {
        val kwsDir = File(context.filesDir, "models/kws")
        return KWS_FILES.values.all { File(kwsDir, it).exists() } &&
                File(kwsDir, "keywords.txt").exists()
    }

    fun isTtsInstalled(context: Context): Boolean {
        val ttsDir = File(context.filesDir, "models/tts")
        return TTS_FILES.all { File(ttsDir, it).exists() } &&
                File(ttsDir, "voice_style.json").exists()
    }

    fun isAllInstalled(context: Context): Boolean = isSttInstalled(context) && isKwsInstalled(context)

    // --- STT 설치 ---
    suspend fun installStt(context: Context) {
        if (_sttState.value == InstallState.DOWNLOADING) return
        _sttState.value = InstallState.DOWNLOADING
        _sttProgress.value = 0

        val sttDir = File(context.filesDir, "models/stt")
        sttDir.mkdirs()

        try {
            for ((index, fileName) in STT_FILES.withIndex()) {
                val destFile = File(sttDir, fileName)
                if (destFile.exists()) {
                    _sttProgress.value = ((index + 1) * 100) / STT_FILES.size
                    continue
                }
                withContext(Dispatchers.IO) {
                    downloadFile("$STT_BASE_URL/$fileName", destFile) { fileProgress ->
                        _sttProgress.value = ((index * 100) + fileProgress) / STT_FILES.size
                    }
                }
                Log.d(TAG, "STT 다운로드 완료: $fileName")
            }
            _sttState.value = InstallState.INSTALLED
            _sttProgress.value = 100
        } catch (e: Exception) {
            Log.e(TAG, "STT 설치 실패", e)
            _sttState.value = InstallState.ERROR
        }
    }

    // --- KWS 설치 ---
    suspend fun installKws(context: Context) {
        if (_kwsState.value == InstallState.DOWNLOADING) return
        _kwsState.value = InstallState.DOWNLOADING
        _kwsProgress.value = 0

        val kwsDir = File(context.filesDir, "models/kws")
        kwsDir.mkdirs()

        try {
            withContext(Dispatchers.IO) {
                // tar.bz2 다운로드 후 추출
                val archiveFile = File(context.cacheDir, "kws-model.tar.bz2")
                downloadFile(KWS_ARCHIVE_URL, archiveFile) { progress ->
                    _kwsProgress.value = (progress * 0.8).toInt() // 80%까지 다운로드
                }
                _kwsProgress.value = 80

                extractTarBz2(archiveFile, kwsDir, KWS_FILES)
                archiveFile.delete()
                _kwsProgress.value = 95

                // 커스텀 keywords.txt 생성
                File(kwsDir, "keywords.txt").writeText(KEYWORDS_CONTENT)
            }
            _kwsState.value = InstallState.INSTALLED
            _kwsProgress.value = 100
            Log.d(TAG, "KWS 모델 설치 완료")
        } catch (e: Exception) {
            Log.e(TAG, "KWS 설치 실패", e)
            _kwsState.value = InstallState.ERROR
        }
    }

    // --- TTS 설치 ---
    suspend fun installTts(context: Context) {
        if (_ttsState.value == InstallState.DOWNLOADING) return
        _ttsState.value = InstallState.DOWNLOADING
        _ttsProgress.value = 0

        val ttsDir = File(context.filesDir, "models/tts")
        ttsDir.mkdirs()

        try {
            // ONNX 모델 + 설정 파일 다운로드
            val totalFiles = TTS_FILES.size + 1 // +1 for voice style
            for ((index, fileName) in TTS_FILES.withIndex()) {
                val destFile = File(ttsDir, fileName)
                if (destFile.exists()) {
                    _ttsProgress.value = ((index + 1) * 100) / totalFiles
                    continue
                }
                withContext(Dispatchers.IO) {
                    downloadFile("$TTS_BASE_URL/$fileName", destFile) { fileProgress ->
                        _ttsProgress.value = ((index * 100) + fileProgress) / totalFiles
                    }
                }
                Log.d(TAG, "TTS 다운로드 완료: $fileName")
            }

            // 보이스 스타일 다운로드
            val voiceFile = File(ttsDir, "voice_style.json")
            if (!voiceFile.exists()) {
                withContext(Dispatchers.IO) {
                    downloadFile(VOICE_STYLE_URL, voiceFile) { progress ->
                        _ttsProgress.value = 90 + (progress / 10)
                    }
                }
                Log.d(TAG, "TTS 보이스 스타일 다운로드 완료")
            }

            _ttsState.value = InstallState.INSTALLED
            _ttsProgress.value = 100
            Log.d(TAG, "TTS 모델 설치 완료")
        } catch (e: Exception) {
            Log.e(TAG, "TTS 설치 실패", e)
            _ttsState.value = InstallState.ERROR
        }
    }

    private fun downloadFile(urlStr: String, destFile: File, onProgress: (Int) -> Unit) {
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connect()

        val totalSize = connection.contentLength.toLong()
        var downloaded = 0L

        connection.inputStream.use { input ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (totalSize > 0) {
                        onProgress(((downloaded * 100) / totalSize).toInt())
                    }
                }
            }
        }
        tempFile.renameTo(destFile)
    }

    // tar.bz2에서 필요한 파일만 추출
    private fun extractTarBz2(archiveFile: File, destDir: File, fileMap: Map<String, String>) {
        val extracted = mutableSetOf<String>()
        archiveFile.inputStream().buffered().use { fileIn ->
            BZip2CompressorInputStream(BufferedInputStream(fileIn)).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        val entryName = File(entry.name).name // 경로 제거, 파일명만
                        val targetName = fileMap[entryName]
                        if (targetName != null && !entry.isDirectory) {
                            val destFile = File(destDir, targetName)
                            destFile.outputStream().use { out ->
                                tarIn.copyTo(out)
                            }
                            extracted.add(entryName)
                            Log.d(TAG, "KWS 추출: $entryName → $targetName")
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }
        }
    }

    enum class InstallState {
        NOT_INSTALLED, DOWNLOADING, INSTALLED, ERROR
    }
}
