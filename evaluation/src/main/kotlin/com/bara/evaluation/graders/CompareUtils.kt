package com.bara.evaluation.graders

/**
 * 기대값과 실제값을 비교하는 유틸리티
 *
 * 지원 패턴:
 * - "*" : null이 아닌 모든 값과 매치
 * - "regex:패턴" : 정규식 매치
 * - ">=N", "<=N", ">N", "<N" : 숫자 비교
 * - 그 외 : 문자열 정확 일치
 */
fun matchExpect(expect: String, actual: Any?): Boolean {
    if (expect == "*") return actual != null
    if (expect.startsWith("regex:")) {
        val pattern = expect.removePrefix("regex:")
        return actual?.toString()?.let { Regex(pattern).containsMatchIn(it) } ?: false
    }
    val numericOps = listOf(">=", "<=", ">", "<")
    for (op in numericOps) {
        if (expect.startsWith(op)) {
            val threshold = expect.removePrefix(op).toDoubleOrNull() ?: return false
            val value = when (actual) {
                is Number -> actual.toDouble()
                else -> actual?.toString()?.toDoubleOrNull() ?: return false
            }
            return when (op) {
                ">=" -> value >= threshold
                "<=" -> value <= threshold
                ">" -> value > threshold
                "<" -> value < threshold
                else -> false
            }
        }
    }
    return actual?.toString() == expect
}
