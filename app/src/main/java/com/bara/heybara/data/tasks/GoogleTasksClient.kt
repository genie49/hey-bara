package com.bara.heybara.data.tasks

import android.content.Context
import android.util.Log
import com.bara.heybara.data.auth.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GoogleTasksClient(private val context: Context) {

    companion object {
        private const val TAG = "GoogleTasksClient"
        private const val BASE_URL = "https://www.googleapis.com/tasks/v1"
        private const val TASK_LIST_ID = "@default"
    }

    suspend fun listTasks(showCompleted: Boolean = false): String {
        val token = GoogleAuthManager.getAccessToken(context)
            ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."

        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/lists/${enc(TASK_LIST_ID)}/tasks" +
                        "?maxResults=20&showCompleted=$showCompleted&showHidden=$showCompleted"

                val json = httpGet(url, token)
                val items = json.jsonObject["items"]?.jsonArray

                if (items.isNullOrEmpty()) return@withContext "할일이 없습니다."

                items.mapIndexed { i, item ->
                    val obj = item.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: "(제목 없음)"
                    val status = obj["status"]?.jsonPrimitive?.content ?: ""
                    val due = obj["due"]?.jsonPrimitive?.content?.take(10) ?: ""
                    val id = obj["id"]?.jsonPrimitive?.content ?: ""
                    val statusLabel = if (status == "completed") "완료" else "미완료"
                    val dueLabel = if (due.isNotBlank()) " (기한: $due)" else ""
                    "${i + 1}. $title [$statusLabel]$dueLabel [id:$id]"
                }.joinToString("\n")
            } catch (e: Exception) {
                Log.e(TAG, "할일 조회 실패", e)
                "할일을 가져오는 데 실패했습니다: ${e.message}"
            }
        }
    }

    suspend fun createTask(title: String, dueDate: String?, notes: String?): String {
        val token = GoogleAuthManager.getAccessToken(context)
            ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."

        return withContext(Dispatchers.IO) {
            try {
                val body = buildJsonObject {
                    put("title", title)
                    if (!dueDate.isNullOrBlank()) {
                        put("due", "${dueDate}T00:00:00.000Z")
                    }
                    if (!notes.isNullOrBlank()) {
                        put("notes", notes)
                    }
                }

                val url = "$BASE_URL/lists/${enc(TASK_LIST_ID)}/tasks"
                httpPost(url, token, body.toString())
                val dueLabel = if (!dueDate.isNullOrBlank()) " (기한: $dueDate)" else ""
                "할일 '$title'을(를) 추가했습니다.$dueLabel"
            } catch (e: Exception) {
                Log.e(TAG, "할일 생성 실패", e)
                "할일 추가에 실패했습니다: ${e.message}"
            }
        }
    }

    suspend fun completeTask(taskId: String): String {
        val token = GoogleAuthManager.getAccessToken(context)
            ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."

        return withContext(Dispatchers.IO) {
            try {
                val body = buildJsonObject {
                    put("status", "completed")
                }.toString()

                val url = "$BASE_URL/lists/${enc(TASK_LIST_ID)}/tasks/${enc(taskId)}"
                httpPatch(url, token, body)
                "할일을 완료 처리했습니다."
            } catch (e: Exception) {
                Log.e(TAG, "할일 완료 실패", e)
                "할일 완료 처리에 실패했습니다: ${e.message}"
            }
        }
    }

    suspend fun deleteTask(taskId: String): String {
        val token = GoogleAuthManager.getAccessToken(context)
            ?: return "Google 계정이 연결되지 않았습니다. 설정에서 연결해 주세요."

        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/lists/${enc(TASK_LIST_ID)}/tasks/${enc(taskId)}"
                httpDelete(url, token)
                "할일을 삭제했습니다."
            } catch (e: Exception) {
                Log.e(TAG, "할일 삭제 실패", e)
                "할일 삭제에 실패했습니다: ${e.message}"
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
