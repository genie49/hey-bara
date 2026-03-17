package com.bara.heybara.data.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.bara.heybara.data.settings.SecurePreferences
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"
    private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
    private const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
    private const val EMAIL_SCOPE = "email"

    private var accountEmail: String? = null
    private var cachedToken: String? = null

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
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(CALENDAR_SCOPE), Scope(TASKS_SCOPE))
                .build()
            GoogleSignIn.getClient(activity, gso)

            // 이미 로그인 + scope 동의된 계정 확인
            val existingAccount = GoogleSignIn.getLastSignedInAccount(activity)
            if (existingAccount?.email != null &&
                existingAccount.grantedScopes.containsAll(
                    listOf(Scope(CALENDAR_SCOPE), Scope(TASKS_SCOPE), Scope(EMAIL_SCOPE))
                )
            ) {
                accountEmail = existingAccount.email
                SecurePreferences(activity).setGoogleAccountEmail(existingAccount.email!!)
                Log.d(TAG, "Google 기존 로그인 사용: ${existingAccount.email}")
                // 토큰 미리 발급
                prefetchToken(activity)
                return SignInResult.Success
            }

            // AuthorizationClient로 scope 동의 요청
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE), Scope(TASKS_SCOPE), Scope(EMAIL_SCOPE)))
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
                prefetchToken(activity)
                SignInResult.Success
            } else {
                // 실패 시 캐시 전부 클리어 후 재시도 유도
                clearAllCache(activity)
                SignInResult.Failed("이메일을 가져올 수 없습니다. 다시 시도해 주세요.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google 로그인 실패", e)
            SignInResult.Failed(e.message ?: "알 수 없는 오류")
        }
    }

    suspend fun handleAuthResult(context: Context, result: AuthorizationResult): Boolean {
        val success = processResult(context, result)
        if (success) {
            prefetchToken(context)
        } else {
            clearAllCache(context)
        }
        return success
    }

    private suspend fun processResult(context: Context, result: AuthorizationResult): Boolean {
        val account = result.toGoogleSignInAccount()
        var email = account?.email
        Log.d(TAG, "processResult: toGoogleSignInAccount email=$email, account=${account?.displayName}")

        // GoogleSignIn에서 마지막 로그인 계정 확인 (사용자가 선택한 계정)
        if (email == null) {
            val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
            email = lastAccount?.email
            Log.d(TAG, "processResult: GoogleSignIn.lastAccount email=$email")
        }

        // accessToken을 캐시하고 UserInfo API에서 이메일 가져오기
        val accessToken = result.accessToken
        Log.d(TAG, "processResult: accessToken=${if (accessToken != null) "있음" else "없음"}")
        if (accessToken != null) {
            cachedToken = accessToken
            if (email == null) {
                email = fetchEmailFromToken(accessToken)
                Log.d(TAG, "processResult: UserInfo API email=$email")
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

    // 토큰 미리 발급 — 동의가 필요하면 여기서 동의 화면 자동 실행
    private suspend fun prefetchToken(context: Context) {
        val email = accountEmail ?: return
        withContext(Dispatchers.IO) {
            try {
                val token = GoogleAuthUtil.getToken(
                    context.applicationContext,
                    email,
                    "oauth2:$CALENDAR_SCOPE $TASKS_SCOPE"
                )
                cachedToken = token
                Log.d(TAG, "토큰 미리 발급 성공")
            } catch (e: UserRecoverableAuthException) {
                Log.w(TAG, "토큰: 추가 동의 필요, 동의 화면 실행")
                try {
                    val intent = e.intent ?: return@withContext null
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.applicationContext.startActivity(intent)
                } catch (ex: Exception) {
                    Log.e(TAG, "동의 화면 실행 실패", ex)
                }
            } catch (e: Exception) {
                Log.e(TAG, "토큰 미리 발급 실패", e)
            }
        }
    }

    private suspend fun fetchEmailFromToken(accessToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://www.googleapis.com/oauth2/v3/userinfo")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connect()
            val code = conn.responseCode
            val body = if (code == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            }
            conn.disconnect()
            Log.d(TAG, "UserInfo API 응답 ($code): $body")
            if (code == 200) {
                val json = kotlinx.serialization.json.Json.parseToJsonElement(body)
                json.jsonObject["email"]?.jsonPrimitive?.content
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "UserInfo API 실패", e)
            null
        }
    }

    private fun clearAllCache(context: Context) {
        Log.d(TAG, "캐시 전체 클리어")
        accountEmail = null
        cachedToken = null
        SecurePreferences(context).clearGoogleAccount()
        try {
            GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
        } catch (_: Exception) {}
        // 토큰 캐시도 무효화
        if (cachedToken != null) {
            try { GoogleAuthUtil.clearToken(context, cachedToken!!) } catch (_: Exception) {}
            cachedToken = null
        }
    }

    fun signOut(context: Context) {
        accountEmail = null
        cachedToken = null
        SecurePreferences(context).clearGoogleAccount()
        GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
        Log.d(TAG, "Google 로그아웃")
    }

    // Tool에서 호출 — 캐시된 토큰 사용, 없으면 재발급
    suspend fun getAccessToken(context: Context): String? {
        if (cachedToken != null) return cachedToken

        val email = accountEmail ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val token = GoogleAuthUtil.getToken(
                    context.applicationContext,
                    email,
                    "oauth2:$CALENDAR_SCOPE $TASKS_SCOPE"
                )
                cachedToken = token
                token
            } catch (e: UserRecoverableAuthException) {
                Log.w(TAG, "토큰: 사용자 동의 필요, 동의 화면 실행")
                try {
                    val intent = e.intent ?: return@withContext null
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.applicationContext.startActivity(intent)
                } catch (ex: Exception) {
                    Log.e(TAG, "동의 화면 실행 실패", ex)
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "토큰 획득 실패", e)
                null
            }
        }
    }
}

