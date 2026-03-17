package com.bara.heybara.data.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// STT 모델 다운로드 및 설치 관리
object ModelInstaller {

    private const val TAG = "ModelInstaller"
    private const val BASE_URL = "https://huggingface.co/k2-fsa/sherpa-onnx-streaming-zipformer-korean-2024-06-16/resolve/main"

    private val STT_FILES = listOf(
        "encoder-epoch-99-avg-1.onnx",
        "decoder-epoch-99-avg-1.onnx",
        "joiner-epoch-99-avg-1.onnx",
        "tokens.txt"
    )

    private val _installState = MutableStateFlow(InstallState.NOT_INSTALLED)
    val installState: StateFlow<InstallState> = _installState

    // 0~100 진행률
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress

    fun checkInstalled(context: Context) {
        val sttDir = File(context.filesDir, "models/stt")
        val allExist = STT_FILES.all { File(sttDir, it).exists() }
        _installState.value = if (allExist) InstallState.INSTALLED else InstallState.NOT_INSTALLED
    }

    fun isInstalled(context: Context): Boolean {
        val sttDir = File(context.filesDir, "models/stt")
        return STT_FILES.all { File(sttDir, it).exists() }
    }

    suspend fun install(context: Context) {
        if (_installState.value == InstallState.DOWNLOADING) return
        _installState.value = InstallState.DOWNLOADING
        _progress.value = 0

        val sttDir = File(context.filesDir, "models/stt")
        sttDir.mkdirs()

        try {
            for ((index, fileName) in STT_FILES.withIndex()) {
                val destFile = File(sttDir, fileName)
                if (destFile.exists()) {
                    _progress.value = ((index + 1) * 100) / STT_FILES.size
                    continue
                }

                withContext(Dispatchers.IO) {
                    downloadFile("$BASE_URL/$fileName", destFile) { fileProgress ->
                        val overall = ((index * 100) + fileProgress) / STT_FILES.size
                        _progress.value = overall
                    }
                }
                Log.d(TAG, "다운로드 완료: $fileName")
            }
            _installState.value = InstallState.INSTALLED
            _progress.value = 100
            Log.d(TAG, "STT 모델 설치 완료")
        } catch (e: Exception) {
            Log.e(TAG, "모델 설치 실패", e)
            _installState.value = InstallState.ERROR
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

    enum class InstallState {
        NOT_INSTALLED, DOWNLOADING, INSTALLED, ERROR
    }
}
