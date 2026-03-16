package com.bara.evaluation.mocks

/** 전화 기록 */
data class CallRecord(val contact: String, val phoneNumber: String)

/** 문자 기록 */
data class SmsRecord(val contact: String, val phoneNumber: String, val message: String)

/**
 * Mock 상태 저장소 — 평가 중 에이전트의 부수효과를 기록하고 조회
 *
 * 경로 문법: "calls.last.contact", "sms.0.message", "calls.count" 등
 */
class MockStateStore {
    val calls = mutableListOf<CallRecord>()
    val sms = mutableListOf<SmsRecord>()
    val searchQueries = mutableListOf<String>()

    fun recordCall(contact: String, phoneNumber: String) {
        calls.add(CallRecord(contact, phoneNumber))
    }

    fun recordSms(contact: String, phoneNumber: String, message: String) {
        sms.add(SmsRecord(contact, phoneNumber, message))
    }

    fun recordSearch(query: String) {
        searchQueries.add(query)
    }

    /** 점(.) 구분 경로로 상태 조회 */
    fun get(path: String): Any? {
        val parts = path.split(".")
        if (parts.isEmpty()) return null
        val collection: List<Any> = when (parts[0]) {
            "calls" -> calls
            "sms" -> sms
            "searchQueries" -> searchQueries
            else -> return null
        }
        return resolvePath(collection, parts.drop(1))
    }

    private fun resolvePath(collection: List<Any>, parts: List<String>): Any? {
        if (parts.isEmpty()) return collection
        val accessor = parts[0]
        val element: Any? = when (accessor) {
            "count" -> return collection.size
            "last" -> collection.lastOrNull()
            "first" -> collection.firstOrNull()
            else -> {
                val index = accessor.toIntOrNull() ?: return null
                collection.getOrNull(index)
            }
        }
        if (element == null) return null
        if (parts.size == 1) return element
        val field = parts[1]
        return when (element) {
            is CallRecord -> when (field) {
                "contact" -> element.contact
                "phoneNumber" -> element.phoneNumber
                else -> null
            }
            is SmsRecord -> when (field) {
                "contact" -> element.contact
                "phoneNumber" -> element.phoneNumber
                "message" -> element.message
                else -> null
            }
            is String -> element
            else -> null
        }
    }

    /** 모든 기록 초기화 */
    fun reset() {
        calls.clear()
        sms.clear()
        searchQueries.clear()
    }
}
