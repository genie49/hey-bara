package com.bara.heybara.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

// assets 폴더의 파일을 앱 내부 저장소로 복사하는 유틸
object AssetCopier {

    // 단일 파일 복사 (이미 존재하면 건너뜀)
    fun copyIfNeeded(context: Context, assetPath: String, destDir: File): File {
        val destFile = File(destDir, File(assetPath).name)
        if (destFile.exists()) return destFile

        destDir.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    // 디렉토리 내 모든 파일 복사
    fun copyDirIfNeeded(context: Context, assetDir: String, destDir: File) {
        val files = context.assets.list(assetDir) ?: return
        destDir.mkdirs()
        for (fileName in files) {
            val assetPath = "$assetDir/$fileName"
            // 하위 디렉토리인지 확인
            val subList = context.assets.list(assetPath)
            if (subList != null && subList.isNotEmpty()) {
                copyDirIfNeeded(context, assetPath, File(destDir, fileName))
            } else {
                copyIfNeeded(context, assetPath, destDir)
            }
        }
    }
}
