package com.bara.heybara.data.action

import android.telephony.SmsManager
import android.util.Log

class SmsExecutor {

    fun sendSms(phoneNumber: String, message: String): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d("SmsExecutor", "SMS 전송: $phoneNumber, $message")
            true
        } catch (e: Exception) {
            Log.e("SmsExecutor", "SMS 전송 실패", e)
            false
        }
    }
}
