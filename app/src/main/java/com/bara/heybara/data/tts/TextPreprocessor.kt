package com.bara.heybara.data.tts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import java.io.File
import java.text.Normalizer

class TextPreprocessor(modelDir: String) {

    // unicode_indexer.json: 4096개 int 배열 (codepoint → token ID)
    private val unicodeIndexer: IntArray

    init {
        val indexerJson = File(modelDir, "unicode_indexer.json").readText()
        val jsonArray = Json.parseToJsonElement(indexerJson).jsonArray
        unicodeIndexer = IntArray(jsonArray.size) { jsonArray[it].jsonPrimitive.int }
    }

    fun preprocess(text: String): Pair<LongArray, FloatArray> {
        val cleaned = cleanText(text)
        val wrapped = "<ko>$cleaned</ko>"
        return tokenize(wrapped)
    }

    private fun cleanText(text: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFKD)
        // 이모지 제거
        t = t.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE00-\\uFE0F]"), "")
        // 특수문자 치환
        t = t.replace(Regex("[\u2014\u2013]"), "-")
        t = t.replace(Regex("[\u201C\u201D\u2018\u2019\u300C\u300D\u300E\u300F]"), "\"")
        t = t.replace("\uFF08", "(").replace("\uFF09", ")")
        // 공백 정리
        t = t.replace(Regex("\\s+"), " ").trim()
        // 끝에 마침표 없으면 추가
        if (t.isNotEmpty() && t.last() !in ".!?。") {
            t = "$t."
        }
        return t
    }

    private fun tokenize(text: String): Pair<LongArray, FloatArray> {
        val tokenIds = mutableListOf<Long>()
        for (char in text) {
            val codepoint = char.code
            if (codepoint < unicodeIndexer.size) {
                val tokenId = unicodeIndexer[codepoint]
                if (tokenId >= 0) {
                    tokenIds.add(tokenId.toLong())
                }
            }
        }
        val ids = tokenIds.toLongArray()
        val mask = FloatArray(ids.size) { 1.0f }
        return Pair(ids, mask)
    }
}
