package com.bara.heybara.data.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.bara.heybara.data.settings.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"
    private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
    private const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"

    private var accountEmail: String? = null

    fun isAuthenticated(): Boolean = accountEmail != null

    fun getAccountEmail(): String? = accountEmail

    fun restore(context: Context) {
        accountEmail = SecurePreferences(context).getGoogleAccountEmail()
    }

    // 인증 결과: 성공 or resolution PendingIntent 반환
    sealed class SignInResult {
        data object Success : SignInResult()
        data class NeedsConsent(val pendingIntent: PendingIntent) : SignInResult()
        data class Failed(val message: String) : SignInResult()
    }

    suspend fun signIn(activity: Activity): SignInResult {
        return try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE), Scope(TASKS_SCOPE)))
                .build()

            val authClient = Identity.getAuthorizationClient(activity)
            val result: AuthorizationResult = authClient.authorize(authRequest).await()

            if (result.hasResolution()) {
                val pi = result.pendingIntent
                if (pi != null) {
                    Log.d(TAG, "Google 인증: 사용자 동의 필요")
                    return SignInResult.NeedsConsent(pi)
                }
                return SignInResult.Failed("동의 화면을 열 수 없습니다")
            }

            if (processResult(activity, result)) {
                SignInResult.Success
            } else {
                SignInResult.Failed("이메일을 가져올 수 없습니다")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google 로그인 실패", e)
            SignInResult.Failed(e.message ?: "알 수 없는 오류")
        }
    }

    // resolution Intent 결과 처리
    fun handleAuthResult(activity: Activity, result: AuthorizationResult): Boolean {
        return processResult(activity, result)
    }

    private fun processResult(context: Context, result: AuthorizationResult): Boolean {
        val account = result.toGoogleSignInAccount()
        val email = account?.email
        if (email != null) {
            accountEmail = email
            SecurePreferences(context).setGoogleAccountEmail(email)
            Log.d(TAG, "Google 로그인 성공: $email")
            return true
        }
        Log.w(TAG, "Google 로그인: 이메일 없음")
        return false
    }

    fun signOut(context: Context) {
        accountEmail = null
        SecurePreferences(context).clearGoogleAccount()
        Log.d(TAG, "Google 로그아웃")
    }

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
