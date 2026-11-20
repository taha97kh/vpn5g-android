package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_AUTH_SESSION_JSON
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Email auth service layer (Supabase Auth / GoTrue REST).
 *
 * Flows: email+password registration, OTP login, and password recovery by
 * 6-digit code — all against the project in [Vpn5gAuthConfig]. The Supabase
 * project does not exist yet, so every call returns [AuthResult.NotConfigured]
 * until the URL and anon key are filled in; the UI surfaces that honestly.
 *
 * The session (access/refresh token + email) is persisted in MMKV under
 * [KEY_AUTH_SESSION_JSON]. Passwords are never stored. The Gmail app
 * password that actually sends the codes lives in Supabase's SMTP settings,
 * never in this app.
 *
 * All calls run on Dispatchers.IO and never throw.
 */
object Vpn5gAuthService {

    sealed interface AuthResult {
        /** A signed-in session is available (also persisted). */
        data class SignedIn(val session: Vpn5gAuthSession) : AuthResult

        /** A code was sent; the UI must now ask for the 6-digit code. */
        data object CodeSent : AuthResult

        /** No Supabase project is configured yet (see Vpn5gAuthConfig). */
        data object NotConfigured : AuthResult

        /** The backend (or the input) rejected the request. */
        data class Error(val message: String) : AuthResult
    }

    /** Persisted account session. Tokens are MMKV-stored, never logged. */
    data class Vpn5gAuthSession(
        val email: String? = null,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val expiresAtEpochSeconds: Long? = null,
    )

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val TIMEOUT_MS = 15_000L

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    fun currentSession(): Vpn5gAuthSession? {
        val json = MmkvManager.decodeSettingsString(KEY_AUTH_SESSION_JSON)
        if (json.isNullOrBlank()) return null
        return try {
            JsonUtil.fromJsonSafe(json, Vpn5gAuthSession::class.java)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAuthService: session parse failed", e)
            null
        }
    }

    fun signOut() {
        MmkvManager.encodeSettings(KEY_AUTH_SESSION_JSON, "")
    }

    private fun saveSession(session: Vpn5gAuthSession) {
        MmkvManager.encodeSettings(KEY_AUTH_SESSION_JSON, JsonUtil.toJson(session))
    }

    /** Registration step 1: create the account and send the OTP code. */
    suspend fun signUpWithEmail(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            if (password.length < 6) {
                return@withContext AuthResult.Error("password_too_short")
            }
            post(
                path = "/auth/v1/signup",
                body = """{"email":"${jsonEscape(email)}","password":"${jsonEscape(password)}"}""",
            ) { code, _ ->
                // 200 = created (session or confirmation pending); the OTP
                // code is emailed by Supabase either way.
                if (code in 200..299) AuthResult.CodeSent
                else AuthResult.Error("signup_failed_$code")
            }
        }

    /** OTP login step 1: email a 6-digit login code (creates the user if new). */
    suspend fun sendLoginOtp(email: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            post(
                path = "/auth/v1/otp",
                body = """{"email":"${jsonEscape(email)}","create_user":true}""",
            ) { code, _ ->
                if (code in 200..299) AuthResult.CodeSent
                else AuthResult.Error("otp_failed_$code")
            }
        }

    /** Password-recovery step 1: email a 6-digit recovery code. */
    suspend fun sendRecoveryOtp(email: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            post(
                path = "/auth/v1/otp",
                body = """{"email":"${jsonEscape(email)}","create_user":false}""",
            ) { code, _ ->
                if (code in 200..299) AuthResult.CodeSent
                else AuthResult.Error("recovery_failed_$code")
            }
        }

    /**
     * Step 2 for login/recovery: verify the 6-digit code.
     * [type] is "email" for OTP login and "recovery" for password recovery.
     * On success a session is persisted and returned.
     */
    suspend fun verifyOtp(email: String, code: String, type: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            post(
                path = "/auth/v1/verify",
                body = """{"email":"${jsonEscape(email)}","token":"${jsonEscape(code)}","type":"${jsonEscape(type)}"}""",
            ) { status, body ->
                if (status !in 200..299) return@post AuthResult.Error("verify_failed_$status")
                parseSession(body, email)?.let { session ->
                    saveSession(session)
                    AuthResult.SignedIn(session)
                } ?: AuthResult.Error("verify_no_session")
            }
        }

    /** Recovery step 3: set a new password with the verified session. */
    suspend fun updatePassword(newPassword: String): AuthResult =
        withContext(Dispatchers.IO) {
            val session = currentSession()
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            if (session?.accessToken.isNullOrBlank()) {
                return@withContext AuthResult.Error("no_session")
            }
            if (newPassword.length < 6) {
                return@withContext AuthResult.Error("password_too_short")
            }
            put(
                path = "/auth/v1/user",
                token = session.accessToken,
                body = """{"password":"${jsonEscape(newPassword)}"}""",
            ) { code, _ ->
                if (code in 200..299) AuthResult.SignedIn(session)
                else AuthResult.Error("password_update_failed_$code")
            }
        }

    /** Email+password sign-in for an existing account. */
    suspend fun signInWithPassword(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            post(
                path = "/auth/v1/token?grant_type=password",
                body = """{"email":"${jsonEscape(email)}","password":"${jsonEscape(password)}"}""",
            ) { code, body ->
                if (code !in 200..299) return@post AuthResult.Error("signin_failed_$code")
                parseSession(body, email)?.let { session ->
                    saveSession(session)
                    AuthResult.SignedIn(session)
                } ?: AuthResult.Error("signin_no_session")
            }
        }

    private suspend fun post(
        path: String,
        body: String,
        handle: (Int, String) -> AuthResult,
    ): AuthResult {
        return try {
            val request = Request.Builder()
                .url(Vpn5gAuthConfig.SUPABASE_URL.trimEnd('/') + path)
                .header("apikey", Vpn5gAuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${Vpn5gAuthConfig.SUPABASE_ANON_KEY}")
                .post(body.toRequestBody(JSON))
                .build()
            client().newCall(request).execute().use { response ->
                handle(response.code, response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAuthService: POST $path failed", e)
            AuthResult.Error("network_error")
        }
    }

    private suspend fun put(
        path: String,
        token: String?,
        body: String,
        handle: (Int, String) -> AuthResult,
    ): AuthResult {
        return try {
            val request = Request.Builder()
                .url(Vpn5gAuthConfig.SUPABASE_URL.trimEnd('/') + path)
                .header("apikey", Vpn5gAuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $token")
                .put(body.toRequestBody(JSON))
                .build()
            client().newCall(request).execute().use { response ->
                handle(response.code, response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAuthService: PUT $path failed", e)
            AuthResult.Error("network_error")
        }
    }

    /** Pulls access/refresh tokens out of a GoTrue token response. */
    private fun parseSession(body: String, email: String): Vpn5gAuthSession? {
        return try {
            val access = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            val refresh = Regex("\"refresh_token\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            val expires = Regex("\"expires_in\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()
            if (access.isNullOrBlank()) return null
            Vpn5gAuthSession(
                email = email,
                accessToken = access,
                refreshToken = refresh,
                expiresAtEpochSeconds = expires?.let { System.currentTimeMillis() / 1000 + it },
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAuthService: session parse failed", e)
            null
        }
    }

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
