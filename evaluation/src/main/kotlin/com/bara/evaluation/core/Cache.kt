package com.bara.evaluation.core

import com.bara.evaluation.agent.SYSTEM_PROMPT
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * 파일 기반 평가 캐시 — 통과한 태스크를 캐싱하여 재실행 방지
 *
 * 태스크의 ID + input + dataset + expected + 시스템 프롬프트 + 모델 ID를
 * SHA-256 해시하여 캐시 키로 사용한다.
 */
class Cache(
    private val cacheDir: File = File("evaluation/.cache"),
    private val modelId: String = "gemini-3.1-flash-lite-preview",
) {
    private val json = Json { prettyPrint = true }
    private val cacheFile = File(cacheDir, "passed.json")
    private val passedHashes: MutableSet<String> = loadHashes()

    /** 태스크가 캐시에 존재하는지 확인 */
    fun has(task: Task): Boolean = computeHash(task) in passedHashes

    /** 통과한 태스크를 캐시에 추가 */
    fun add(task: Task) {
        passedHashes.add(computeHash(task))
        save()
    }

    /** 캐시 초기화 */
    fun clear() {
        passedHashes.clear()
        if (cacheFile.exists()) cacheFile.delete()
    }

    /** 캐시된 항목 수 */
    fun size(): Int = passedHashes.size

    /** 태스크 해시 계산 */
    fun computeHash(task: Task): String = Companion.computeHash(task, modelId)

    private fun loadHashes(): MutableSet<String> {
        if (!cacheFile.exists()) return mutableSetOf()
        return try {
            json.decodeFromString<List<String>>(cacheFile.readText()).toMutableSet()
        } catch (_: Exception) {
            mutableSetOf()
        }
    }

    private fun save() {
        cacheDir.mkdirs()
        cacheFile.writeText(json.encodeToString(passedHashes.toList()))
    }

    companion object {
        private val json = Json

        /** 태스크 해시 계산 (정적 메서드) */
        fun computeHash(task: Task, modelId: String = "gemini-3.1-flash-lite-preview"): String {
            val expectedJson = json.encodeToString(task.expected)
            val raw = "${task.id}|${task.input}|${task.dataset}|$expectedJson|$SYSTEM_PROMPT|$modelId"
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
