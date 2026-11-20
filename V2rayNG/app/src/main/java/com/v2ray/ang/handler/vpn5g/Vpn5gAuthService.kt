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
 * Email auth service layer (Supabase Auth / GoTrue REST), passwordless OTP.
 *
 * One flow for registration, login, and recovery: the user enters a Gmail
 * address, a real 6-digit code is emailed, the user enters the code, and
 * the account is verified and signed in. Gmail-only is enforced by the UI
 * ([Vpn5gEmailPolicy]) before any call is made.
 *
 * Endpoints (project in [Vpn5gAuthConfig]):
 *  - POST /auth/v1/otp {"email", "create_user": true} — registration and
 *    login (a new user is created on first use).
 *  - POST /auth/v1/otp {"email", "create_user": false} — recovery (the
 *    email must already be registered).
 *  - POST /auth/v1/verify {"email", "token", "type": "email"} — checks the
 *    code and returns the session.
 *
 * There is no password anywhere in this flow by design. The older
 * email+password signup (/auth/v1/signup) was dropped: its default
 * confirmation email carries a LINK, not a 6-digit code, so the app's code
 * field could never work with it; /otp is the documented code flow
 * (Supabase: "email OTPs are a form of passwordless login where users key
 * in a six digit code sent to their email address").
 *
 * The session (access/refresh token + email) is persisted in MMKV under
 * [KEY_AUTH_SESSION_JSON]. The Gmail app password that actually sends the
 * codes lives in Supabase's SMTP settings, never in this app.
 *
 * All calls run on Dispatchers.IO and never throw. The "coming soon" path
 * ([AuthResult.NotConfigured]) remains for an unconfigured project and is
 * the only case in which no send is attempted.
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

    /** OTP step 1: email a 6-digit code; creates the account on first use. */
    suspend fun sendLoginOtp(email: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            post(
                path = "/auth/v1/otp",
                body = """{"email":"${jsonEscape(email)}","create_user":true}""",
            ) { code, _ ->
                if (code in 200..299) AuthResult.CodeSent
                else AuthResult.Error(sendErrorKey(code))
            }
        }

    /**
     * Recovery step 1: email a 6-digit code to an already-registered Gmail.
     * Recovery is the same OTP flow as login — the verified code signs the
     * user straight back in (there is no password to reset).
     */
    suspend fun sendRecoveryOtp(email: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            post(
                path = "/auth/v1/otp",
                body = """{"email":"${jsonEscape(email)}","create_user":false}""",
            ) { code, _ ->
                if (code in 200..299) AuthResult.CodeSent
                else AuthResult.Error(sendErrorKey(code))
            }
        }

    /**
     * OTP step 2: verify the 6-digit code (type "email" for every mode).
     * On success a session is persisted and returned.
     */
    suspend fun verifyOtp(email: String, code: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            post(
                path = "/auth/v1/verify",
                body = """{"email":"${jsonEscape(email)}","token":"${jsonEscape(code)}","type":"email"}""",
            ) { status, body ->
                if (status !in 200..299) return@post AuthResult.Error(verifyErrorKey(status))
                parseSession(body, email)?.let { session ->
                    saveSession(session)
                    AuthResult.SignedIn(session)
                } ?: AuthResult.Error("verify_no_session")
            }
        }

    /**
     * Maps an OTP-send HTTP status to a stable error key. Pure, JVM-tested.
     * 429 is Supabase's one-code-per-60-seconds limit; 422 means the email
     * is not registered (recovery with create_user=false).
     */
    internal fun sendErrorKey(status: Int): String = when (status) {
        429 -> "rate_limited"
        422 -> "email_not_registered"
        else -> "otp_failed"
    }

    /**
     * Maps an OTP-verify HTTP status to a stable error key. Pure,
     * JVM-tested. GoTrue answers a wrong or expired code with 400
     * ("Token has expired or is invalid"); 429 is the rate limit.
     */
    internal fun verifyErrorKey(status: Int): String = when (status) {
        429 -> "rate_limited"
        400, 401, 403, 404, 422 -> "code_wrong_or_expired"
        else -> "verify_failed"
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
