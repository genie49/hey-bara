package com.bara.evaluation.core

import kotlinx.coroutines.delay

/**
 * Exponential backoff으로 재시도
 * 1초 → 2초 → 4초 간격으로 최대 maxRetries회 재시도
 */
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000,
    block: suspend () -> T,
): T {
    var lastException: Exception? = null
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            if (attempt < maxRetries - 1) {
                val delayMs = initialDelayMs * (1L shl attempt)
                println("  [retry] ${attempt + 1}/$maxRetries 실패, ${delayMs}ms 후 재시도: ${e.message}")
                delay(delayMs)
            }
        }
    }
    throw lastException!!
}
