package com.bara.heybara.data.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class CallExecutor(private val context: Context) {

    fun call(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d("CallExecutor", "전화 걸기: $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e("CallExecutor", "전화 걸기 실패", e)
            false
        }
    }
}
