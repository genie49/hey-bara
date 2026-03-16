package com.bara.heybara

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class BaraApp : Application() {

    companion object {
        const val CHANNEL_ID = "hey_bara_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hey Bara 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "음성 비서 대기 상태"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
