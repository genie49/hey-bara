package com.bara.heybara.data.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
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

    sealed class SignInResult {
        data object Success : SignInResult()
        data class NeedsConsent(val pendingIntent: PendingIntent) : SignInResult()
        data class Failed(val message: String) : SignInResult()
    }

    suspend fun signIn(activity: Activity): SignInResult {
        return try {
            // 먼저 GoogleSignIn으로 계정 선택 + 이메일 획득
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(CALENDAR_SCOPE), Scope(TASKS_SCOPE))
                .build()
            val signInClient = GoogleSignIn.getClient(activity, gso)

            // 이미 로그인된 계정이 있는지 확인
            val existingAccount = GoogleSignIn.getLastSignedInAccount(activity)
            if (existingAccount?.email != null &&
                existingAccount.grantedScopes.containsAll(
                    listOf(Scope(CALENDAR_SCOPE), Scope(TASKS_SCOPE))
                )
            ) {
                accountEmail = existingAccount.email
                SecurePreferences(activity).setGoogleAccountEmail(existingAccount.email!!)
                Log.d(TAG, "Google 기존 로그인 사용: ${existingAccount.email}")
                return SignInResult.Success
            }

            // AuthorizationClient로 scope 동의 요청
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

    fun handleAuthResult(context: Context, result: AuthorizationResult): Boolean {
        return processResult(context, result)
    }

    private fun processResult(context: Context, result: AuthorizationResult): Boolean {
        // AuthorizationResult에서 이메일 시도
        val account = result.toGoogleSignInAccount()
        var email = account?.email

        // 없으면 GoogleSignIn에서 가져오기
        if (email == null) {
            email = GoogleSignIn.getLastSignedInAccount(context)?.email
        }

        // 그래도 없으면 Account 객체에서 시도
        if (email == null) {
            val accounts = android.accounts.AccountManager.get(context)
                .getAccountsByType("com.google")
            if (accounts.size == 1) {
                email = accounts[0].name
            }
        }

        if (email != null) {
            accountEmail = email
            SecurePreferences(context).setGoogleAccountEmail(email)
            Log.d(TAG, "Google 로그인 성공: $email")
            return true
        }
        Log.w(TAG, "Google 로그인: 이메일을 가져올 수 없음")
        return false
    }

    fun signOut(context: Context) {
        accountEmail = null
        SecurePreferences(context).clearGoogleAccount()
        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
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
