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
 * Email auth service layer (Supabase Auth / GoTrue REST), four flows
 * (v1.1.8). Gmail-only is enforced by the UI ([Vpn5gEmailPolicy])
 * before any call is made.
 *
 * Endpoints (project in [Vpn5gAuthConfig]):
 *  1. Direct signup/login, no code — POST /auth/v1/signup
 *     {"email", "password"}. With "Confirm email" OFF in the Supabase
 *     panel the response carries a session and the user is signed in
 *     immediately. With it ON the response has a user but no session,
 *     and the caller falls back to the OTP code screen (scenario 2).
 *     A signup needs a password: GoTrue has no passwordless signup.
 *  2. OTP login/signup — POST /auth/v1/otp {"email", "create_user":
 *     true} then POST /auth/v1/verify {"email", "token", "type":
 *     "email"}. The 6-digit code flow; signUp and OTP are never fired
 *     for the same attempt (the OTP fallback after a no-session signup
 *     is sequential, not parallel).
 *  3. Forgot password — POST /auth/v1/recover {"email",
 *     "redirect_to": "vpn5g://auth/callback"} sends the recovery LINK
 *     (not a code). The link returns to the app as a deep link; its
 *     fragment carries `type=recovery` plus the session tokens (see
 *     [Vpn5gRecoveryLink]). The new password is then set with
 *     PUT /auth/v1/user {"password": ...} (Authorization: Bearer
 *     <access_token from the link>). The redirect URL must be
 *     allow-listed in the Supabase dashboard (Authentication → URL
 *     Configuration → Redirect URLs); without it GoTrue refuses the
 *     custom redirect.
 *  4. Password login — POST /auth/v1/token?grant_type=password
 *     {"email", "password"} returns the session directly.
 *
 * The session (access/refresh token + email) is persisted in MMKV under
 * [KEY_AUTH_SESSION_JSON]. The Gmail app password that actually sends
 * the codes lives in Supabase's SMTP settings, never in this app, and
 * custom SMTP is still not set up — until it is, OTP emails may not
 * arrive at all. Nothing here fakes a send.
 *
 * All calls run on Dispatchers.IO and never throw: every failure is an
 * [AuthResult.Error] with a stable key, mapped by the ViewModel to a
 * Persian string. 429 is the Supabase rate limit (one OTP send per 60
 * seconds per email); the ViewModel guards sends with a cooldown and
 * an in-flight lock so a double tap can never fire two requests.
 * The "coming soon" path ([AuthResult.NotConfigured]) remains for an
 * unconfigured project and is the only case in which no send is
 * attempted.
 */
object Vpn5gAuthService {

    sealed interface AuthResult {
        /** A signed-in session is available (also persisted). */
        data class SignedIn(val session: Vpn5gAuthSession) : AuthResult

        /** A code was sent; the UI must now ask for the 6-digit code. */
        data object CodeSent : AuthResult

        /**
         * Signup succeeded but the backend returned no session
         * ("Confirm email" is ON in the panel): the UI falls back to
         * the OTP code screen instead of signing the user in.
         */
        data object NeedsCode : AuthResult

        /** A recovery link was accepted for sending (scenario 3). */
        data object RecoveryLinkSent : AuthResult

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

    /** Deep-link redirect for the recovery flow (scenario 3). */
    const val RECOVERY_REDIRECT_URL = "vpn5g://auth/callback"

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

    /**
     * Scenario 1: direct signup (supabase.auth.signUp). Requires a
     * password — GoTrue has no passwordless signup. With "Confirm
     * email" OFF the response carries a session and the user is signed
     * in immediately; with it ON the response has a user but no
     * session ([AuthResult.NeedsCode]) and the UI falls back to OTP.
     * An already-registered email is not a dead end: the caller offers
     * the password-login screen instead.
     */
    suspend fun signUp(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            post(
                path = "/auth/v1/signup",
                body = """{"email":"${jsonEscape(email)}","password":"${jsonEscape(password)}"}""",
            ) { code, body ->
                if (code in 200..299) {
                    parseSession(body, email)?.let { session ->
                        saveSession(session)
                        AuthResult.SignedIn(session)
                    } ?: AuthResult.NeedsCode
                } else {
                    AuthResult.Error(signupErrorKey(code, body))
                }
            }
        }

    /**
     * Scenario 4: password login (supabase.auth.signInWithPassword).
     * POST /auth/v1/token?grant_type=password. Returns the session
     * directly; a wrong email/password is a friendly error, never a
     * crash.
     */
    suspend fun signInWithPassword(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            post(
                path = "/auth/v1/token?grant_type=password",
                body = """{"email":"${jsonEscape(email)}","password":"${jsonEscape(password)}"}""",
            ) { code, body ->
                if (code in 200..299) {
                    parseSession(body, email)?.let { session ->
                        saveSession(session)
                        AuthResult.SignedIn(session)
                    } ?: AuthResult.Error("password_login_failed")
                } else {
                    AuthResult.Error(passwordLoginErrorKey(code, body))
                }
            }
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
     * Scenario 3, step 1: send the recovery LINK
     * (supabase.auth.resetPasswordForEmail). GoTrue emails a link to
     * [RECOVERY_REDIRECT_URL]; the link returns to the app as a deep
     * link carrying `type=recovery` and the session tokens (see
     * [Vpn5gRecoveryLink]). This is deliberately NOT an OTP code:
     * the user asked for the link flow. GoTrue answers 200 even for
     * an unregistered email (no account enumeration), so any 2xx
     * means "accepted for sending".
     */
    suspend fun sendRecoveryLink(email: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            post(
                path = "/auth/v1/recover",
                body = """{"email":"${jsonEscape(email)}","redirect_to":"$RECOVERY_REDIRECT_URL"}""",
            ) { code, _ ->
                if (code in 200..299) AuthResult.RecoveryLinkSent
                else AuthResult.Error(recoveryErrorKey(code))
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
     * Scenario 3, step 2: set the new password
     * (supabase.auth.updateUser). PUT /auth/v1/user with the
     * access token from the recovery link as the Bearer token. The
     * link's tokens ARE the session: on success they are persisted
     * (with the link's refresh token) and the user is signed in.
     * The [link] tokens are never logged.
     */
    suspend fun updatePassword(link: Vpn5gRecoveryLink.Parsed, email: String, newPassword: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            put(
                path = "/auth/v1/user",
                bearerToken = link.accessToken,
                body = """{"password":"${jsonEscape(newPassword)}"}""",
            ) { code, _ ->
                if (code in 200..299) {
                    val session = Vpn5gAuthSession(
                        email = email,
                        accessToken = link.accessToken,
                        refreshToken = link.refreshToken,
                        expiresAtEpochSeconds = link.expiresInSeconds?.let {
                            System.currentTimeMillis() / 1000 + it
                        },
                    )
                    saveSession(session)
                    AuthResult.SignedIn(session)
                } else {
                    AuthResult.Error(updatePasswordErrorKey(code))
                }
            }
        }

    /**
     * Maps a signup HTTP status + body to a stable error key. Pure,
     * JVM-tested. GoTrue answers an already-registered email with 422
     * ("User already registered") and a too-short password with 400
     * ("Password should be at least 6 characters").
     */
    internal fun signupErrorKey(status: Int, body: String): String = when {
        status == 429 -> "rate_limited"
        status == 422 && body.contains("already registered", ignoreCase = true) ->
            "email_already_registered"
        status == 400 && body.contains("password", ignoreCase = true) &&
            body.contains("at least 6", ignoreCase = true) -> "weak_password"
        else -> "signup_failed"
    }

    /**
     * Maps a password-login HTTP status + body to a stable error key.
     * Pure, JVM-tested. GoTrue answers wrong credentials with 400
     * ("Invalid login credentials").
     */
    internal fun passwordLoginErrorKey(status: Int, body: String): String = when {
        status == 429 -> "rate_limited"
        status == 400 && body.contains("invalid login credentials", ignoreCase = true) ->
            "invalid_credentials"
        else -> "password_login_failed"
    }

    /**
     * Maps a recovery-link HTTP status to a stable error key. Pure,
     * JVM-tested.
     */
    internal fun recoveryErrorKey(status: Int): String = when (status) {
        429 -> "rate_limited"
        else -> "recovery_link_failed"
    }

    /**
     * Maps a password-update HTTP status to a stable error key. Pure,
     * JVM-tested. 401/403 means the recovery link (its token) is no
     * longer valid — the user takes a fresh link.
     */
    internal fun updatePasswordErrorKey(status: Int): String = when (status) {
        429 -> "rate_limited"
        400, 401, 403 -> "recovery_link_invalid"
        else -> "update_password_failed"
    }

    /**
     * True when a GoTrue response body carries a session
     * (an `access_token`). Pure, JVM-tested. A signup response with a
     * user but no session means "Confirm email" is ON, and the caller
     * falls back to OTP instead of signing in.
     */
    internal fun hasSession(body: String): Boolean {
        val access = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        return !access.isNullOrBlank()
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

    private suspend fun put(
        path: String,
        bearerToken: String,
        body: String,
        handle: (Int, String) -> AuthResult,
    ): AuthResult {
        return try {
            val request = Request.Builder()
                .url(Vpn5gAuthConfig.SUPABASE_URL.trimEnd('/') + path)
                .header("apikey", Vpn5gAuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $bearerToken")
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
