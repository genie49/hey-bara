package com.bara.heybara.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.bara.heybara.data.settings.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await  // kotlinx-coroutines-play-services
import kotlinx.coroutines.withContext

object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"
    private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
    private const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"

    private var accountEmail: String? = null

    fun isAuthenticated(): Boolean = accountEmail != null

    fun getAccountEmail(): String? = accountEmail

    // 앱 시작 시 SecurePreferences에서 복원
    fun restore(context: Context) {
        accountEmail = SecurePreferences(context).getGoogleAccountEmail()
    }

    // 설정 화면에서 호출
    suspend fun signIn(activity: Activity): Boolean {
        return try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE), Scope(TASKS_SCOPE)))
                .build()

            val authClient = Identity.getAuthorizationClient(activity)
            val result: AuthorizationResult = authClient.authorize(authRequest).await()

            if (result.hasResolution()) {
                // 사용자 동의 필요 — Intent를 시작해야 함
                // 이 경우는 Activity에서 ActivityResultLauncher로 처리 필요
                Log.d(TAG, "Google 인증: 사용자 동의 필요 (resolution)")
                return false
            }

            val account = result.toGoogleSignInAccount()
            val email = account?.email
            if (email != null) {
                accountEmail = email
                SecurePreferences(activity).setGoogleAccountEmail(email)
                Log.d(TAG, "Google 로그인 성공: $email")
                true
            } else {
                Log.w(TAG, "Google 로그인: 이메일 없음")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google 로그인 실패", e)
            false
        }
    }

    // resolution Intent 결과 처리
    fun handleAuthResult(activity: Activity, result: AuthorizationResult): Boolean {
        val account = result.toGoogleSignInAccount()
        val email = account?.email
        if (email != null) {
            accountEmail = email
            SecurePreferences(activity).setGoogleAccountEmail(email)
            Log.d(TAG, "Google 로그인 성공 (resolution): $email")
            return true
        }
        return false
    }

    fun signOut(context: Context) {
        accountEmail = null
        SecurePreferences(context).clearGoogleAccount()
        Log.d(TAG, "Google 로그아웃")
    }

    // Tool에서 호출 — GoogleAuthUtil이 토큰 캐싱/갱신 자동 처리
    suspend fun getAccessToken(context: Context): String? {
        val email = accountEmail ?: return null
        return withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(
                    context.applicationContext,
                    email,
                    "oauth2:$CALENDAR_SCOPE $TASKS_SCOPE"
                )
            } catch (e: Exception) {
                Log.e(TAG, "토큰 획득 실패", e)
                null
            }
        }
    }
}
