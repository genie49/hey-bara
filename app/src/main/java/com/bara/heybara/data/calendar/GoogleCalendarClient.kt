package com.bara.heybara.data.calendar

import android.content.Context
import android.util.Log
import com.bara.heybara.data.auth.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GoogleCalendarClient(private val context: Context) {

    companion object {
        private const val TAG = "GoogleCalendarClient"
        private const val BASE_URL = "https://www.googleapis.com/calendar/v3"
        private const val CALENDAR_ID = "primary"
        private const val TIME_ZONE = "Asia/Seoul"
    }

    suspend fun listEvents(date: String, days: Int = 1): String {
        val token = GoogleAuthManager.getAccessToken(context)
            ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."

        return withContext(Dispatchers.IO) {
            try {
                val timeMin = "${date}T00:00:00+09:00"
                // days일 후
                val endDate = java.time.LocalDate.parse(date).plusDays(days.toLong())
                val timeMax = "${endDate}T00:00:00+09:00"

                val url = "$BASE_URL/calendars/${enc(CALENDAR_ID)}/events" +
                        "?timeMin=${enc(timeMin)}&timeMax=${enc(timeMax)}" +
                        "&maxResults=20&singleEvents=true&orderBy=startTime" +
                        "&timeZone=${enc(TIME_ZONE)}"

                val json = httpGet(url, token)
                val items = json.jsonObject["items"]?.jsonArray ?: return@withContext "조회 기간에 일정이 없습니다."

                if (items.isEmpty()) return@withContext "조회 기간에 일정이 없습니다."

                items.mapIndexed { i, item ->
                    val obj = item.jsonObject
                    val summary = obj["summary"]?.jsonPrimitive?.content ?: "(제목 없음)"
                    val start = obj["start"]?.jsonObject?.let {
                        it["dateTime"]?.jsonPrimitive?.content ?: it["date"]?.jsonPrimitive?.content
                    } ?: ""
                    val end = obj["end"]?.jsonObject?.let {
                        it["dateTime"]?.jsonPrimitive?.content ?: it["date"]?.jsonPrimitive?.content
                    } ?: ""
                    val id = obj["id"]?.jsonPrimitive?.content ?: ""
                    "${i + 1}. $summary ($start ~ $end) [id:$id]"
                }.joinToString("\n")
            } catch (e: Exception) {
                Log.e(TAG, "일정 조회 실패", e)
                "일정을 가져오는 데 실패했습니다: ${e.message}"
            }
        }
    }

    suspend fun createEvent(
        title: String,
        startDateTime: String,
        endDateTime: String?,
        description: String?
    ): String {
        val token = GoogleAuthManager.getAccessToken(context)
            ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."

        return withContext(Dispatchers.IO) {
            try {
                val end = endDateTime ?: run {
                    val start = java.time.OffsetDateTime.parse(startDateTime)
                    start.plusHours(1).toString()
                }

                val body = buildJsonObject {
                    put("summary", title)
                    putJsonObject("start") {
                        put("dateTime", startDateTime)
                        put("timeZone", TIME_ZONE)
                    }
                    putJsonObject("end") {
                        put("dateTime", end)
                        put("timeZone", TIME_ZONE)
                    }
                    if (!description.isNullOrBlank()) {
                        put("description", description)
                    }
                }

                val url = "$BASE_URL/calendars/${enc(CALENDAR_ID)}/events"
                httpPost(url, token, body.toString())
                "'$title' 일정을 추가했습니다. ($startDateTime)"
            } catch (e: Exception) {
                Log.e(TAG, "일정 생성 실패", e)
                "일정 추가에 실패했습니다: ${e.message}"
            }
        }
    }

    suspend fun updateEvent(
        eventId: String,
        title: String?,
        startDateTime: String?,
        endDateTime: String?
    ): String {
        val token = GoogleAuthManager.getAccessToken(context)
            ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."

        return withContext(Dispatchers.IO) {
            try {
                val body = buildJsonObject {
                    if (!title.isNullOrBlank()) put("summary", title)
                    if (!startDateTime.isNullOrBlank()) {
                        putJsonObject("start") {
                            put("dateTime", startDateTime)
                            put("timeZone", TIME_ZONE)
                        }
                    }
                    if (!endDateTime.isNullOrBlank()) {
                        putJsonObject("end") {
                            put("dateTime", endDateTime)
                            put("timeZone", TIME_ZONE)
                        }
                    }
                }

                val url = "$BASE_URL/calendars/${enc(CALENDAR_ID)}/events/${enc(eventId)}"
                httpPatch(url, token, body.toString())
                "일정을 수정했습니다."
            } catch (e: Exception) {
                Log.e(TAG, "일정 수정 실패", e)
                "일정 수정에 실패했습니다: ${e.message}"
            }
        }
    }

    suspend fun deleteEvent(eventId: String): String {
        val token = GoogleAuthManager.getAccessToken(context)
            ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."

        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/calendars/${enc(CALENDAR_ID)}/events/${enc(eventId)}"
                httpDelete(url, token)
                "일정을 삭제했습니다."
            } catch (e: Exception) {
                Log.e(TAG, "일정 삭제 실패", e)
                "일정 삭제에 실패했습니다: ${e.message}"
            }
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(urlStr: String, token: String): JsonElement {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connect()
        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            conn.disconnect()
            throw Exception("API 오류 ($code): $err")
        }
        conn.disconnect()
        return Json.parseToJsonElement(body)
    }

    private fun httpPost(urlStr: String, token: String, jsonBody: String) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.write(jsonBody.toByteArray())
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            conn.disconnect()
            throw Exception("API 오류 ($code): $err")
        }
        conn.disconnect()
    }

    private fun httpPatch(urlStr: String, token: String, jsonBody: String) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        conn.doOutput = true
        conn.outputStream.write(jsonBody.toByteArray())
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            conn.disconnect()
            throw Exception("API 오류 ($code): $err")
        }
        conn.disconnect()
    }

    private fun httpDelete(urlStr: String, token: String) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299 && code != 204) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            conn.disconnect()
            throw Exception("API 오류 ($code): $err")
        }
        conn.disconnect()
    }
}
