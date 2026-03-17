package com.bara.heybara.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "hey_bara_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getGeminiApiKey(): String? {
        return prefs.getString(KEY_GEMINI_API, null)
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API, key).apply()
    }

    fun clearGeminiApiKey() {
        prefs.edit().remove(KEY_GEMINI_API).apply()
    }

    // 웨이크워드 감도 (0.0~1.0, 기본 0.5)
    fun getWakeWordSensitivity(): Float {
        return prefs.getFloat(KEY_WAKE_SENSITIVITY, 0.5f)
    }

    fun setWakeWordSensitivity(value: Float) {
        prefs.edit().putFloat(KEY_WAKE_SENSITIVITY, value).apply()
    }

    // Google 계정 이메일
    fun getGoogleAccountEmail(): String? {
        return prefs.getString(KEY_GOOGLE_EMAIL, null)
    }

    fun setGoogleAccountEmail(email: String) {
        prefs.edit().putString(KEY_GOOGLE_EMAIL, email).apply()
    }

    fun clearGoogleAccount() {
        prefs.edit().remove(KEY_GOOGLE_EMAIL).apply()
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_WAKE_SENSITIVITY = "wake_word_sensitivity"
        private const val KEY_GOOGLE_EMAIL = "google_account_email"
    }
}
