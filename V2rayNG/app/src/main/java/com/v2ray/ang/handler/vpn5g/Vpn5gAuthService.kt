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
 * Email auth service layer (Supabase Auth / GoTrue REST), three flows
 * (v1.1.9). Gmail-only is enforced by the UI ([Vpn5gEmailPolicy])
 * before any call is made.
 *
 * OTP is GONE (removed v1.1.9 on Aref's order): no code is sent,
 * asked for, or verified anywhere in the app. Registration is
 * email + password only. OTP login was removed together with OTP
 * signup, because the code screen served both and codes are exactly
 * the bypass he wants closed. Password recovery via the emailed LINK
 * stays: it is not a signup code, and it is the only way for users
 * who registered with a code earlier to set a password and log in.
 *
 * Endpoints (project in [Vpn5gAuthConfig]):
 *  1. Direct signup, no code — POST /auth/v1/signup
 *     {"email", "password"}. With "Confirm email" OFF in the Supabase
 *     panel the response carries a session and the user is signed in
 *     immediately. With it ON the response has a user but no session:
 *     the caller gets `email_not_confirmed` (the user taps the
 *     confirmation LINK in the email, then logs in with the
 *     password). A signup needs a password: GoTrue has no
 *     passwordless signup.
 *     Before signup, the one-account-per-IP+email guard runs (see
 *     [Vpn5gRegistrationGuard] and `supabase/vpn5g_registrations.sql`):
 *     the device's public IP is fetched directly (no proxy — signup
 *     happens before any VPN connection), then POST
 *     /rest/v1/rpc/check_registration asks the server whether the
 *     email or the IP is already registered. Either taken fails the
 *     signup with a stable key; any guard failure fails CLOSED
 *     (no signup is attempted on an unchecked registration). After
 *     a successful signup, POST /rest/v1/rpc/register_account
 *     records the (email, ip) pair; if that record call fails, the
 *     session is still real and returned — the table's UNIQUE(ip)
 *     is the backstop against a race. The guard applies ONLY to
 *     registration, never to password login or recovery.
 *  2. Forgot password — POST /auth/v1/recover {"email",
 *     "redirect_to": "vpn5g://auth/callback"} sends the recovery LINK
 *     (not a code). The link returns to the app as a deep link; its
 *     fragment carries `type=recovery` plus the session tokens (see
 *     [Vpn5gRecoveryLink]). The new password is then set with
 *     PUT /auth/v1/user {"password": ...} (Authorization: Bearer
 *     <access_token from the link>). The redirect URL must be
 *     allow-listed in the Supabase dashboard (Authentication → URL
 *     Configuration → Redirect URLs); without it GoTrue refuses the
 *     custom redirect.
 *  3. Password login — POST /auth/v1/token?grant_type=password
 *     {"email", "password"} returns the session directly.
 *
 * The session (access/refresh token + email) is persisted in MMKV under
 * [KEY_AUTH_SESSION_JSON]. The Gmail app password that sends the
 * recovery emails lives in Supabase's SMTP settings, never in this
 * app; custom SMTP is still not set up, so recovery emails may be
 * rate-limited by the built-in service. Nothing here fakes a send.
 *
 * All calls run on Dispatchers.IO and never throw: every failure is an
 * [AuthResult.Error] with a stable key, mapped by the ViewModel to a
 * Persian string. 429 is the Supabase rate limit; the ViewModel guards
 * sends with an in-flight lock so a double tap can never fire two
 * requests. The "coming soon" path ([AuthResult.NotConfigured])
 * remains for an unconfigured project and is the only case in which
 * no send is attempted.
 */
object Vpn5gAuthService {

    sealed interface AuthResult {
        /** A signed-in session is available (also persisted). */
        data class SignedIn(val session: Vpn5gAuthSession) : AuthResult

        /** A recovery link was accepted for sending (flow 2). */
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

    /** Deep-link redirect for the recovery flow (flow 2). */
    const val RECOVERY_REDIRECT_URL = "vpn5g://auth/callback"

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val TIMEOUT_MS = 15_000L

    /** Primary public-IP endpoint (direct fetch, no proxy). */
    private const val PUBLIC_IP_URL = "https://api.ipify.org?format=json"

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
     * Flow 1: direct signup (supabase.auth.signUp), guarded by the
     * one-account-per-IP+email rule. Requires a password — GoTrue has
     * no passwordless signup. With "Confirm email" OFF the response
     * carries a session and the user is signed in immediately; with
     * it ON the response has a user but no session and the caller gets
     * `email_not_confirmed`. An already-registered email is not a dead
     * end: the caller offers the password-login screen instead. An
     * already-registered IP is a hard stop for this internet
     * connection (`ip_already_registered`).
     */
    suspend fun signUp(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!Vpn5gAuthConfig.isConfigured()) return@withContext AuthResult.NotConfigured
            // Guard first: the public IP, then the server-side check.
            // Any failure here fails CLOSED — no signup is attempted
            // on an unchecked registration.
            val ip = fetchPublicIp()
                ?: return@withContext AuthResult.Error("network_error")
            val checkBody = checkRegistration(email, ip)
                ?: return@withContext AuthResult.Error("network_error")
            val outcome = Vpn5gRegistrationGuard.outcome(checkBody)
                ?: return@withContext AuthResult.Error("network_error")
            Vpn5gRegistrationGuard.errorKey(outcome)?.let { key ->
                return@withContext AuthResult.Error(key)
            }
            post(
                path = "/auth/v1/signup",
                body = """{"email":"${jsonEscape(email)}","password":"${jsonEscape(password)}"}""",
            ) { code, body ->
                if (code in 200..299) {
                    parseSession(body, email)?.let { session ->
                        saveSession(session)
                        // Best effort: the session is real even if the
                        // record call fails — the table's UNIQUE(ip)
                        // is the backstop against a race. The failure
                        // is logged, never shown as a signup error.
                        if (!recordRegistration(email, ip)) {
                            LogUtil.e(
                                AppConfig.TAG,
                                "Vpn5gAuthService: register_account failed for $email",
                            )
                        }
                        AuthResult.SignedIn(session)
                    } ?: AuthResult.Error("email_not_confirmed")
                } else {
                    AuthResult.Error(signupErrorKey(code, body))
                }
            }
        }

    /**
     * Flow 3: password login (supabase.auth.signInWithPassword).
     * POST /auth/v1/token?grant_type=password. Returns the session
     * directly; a wrong email/password is a friendly error, never a
     * crash. The registration guard does NOT apply here.
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

    /**
     * Flow 2, step 1: send the recovery LINK
     * (supabase.auth.resetPasswordForEmail). GoTrue emails a link to
     * [RECOVERY_REDIRECT_URL]; the link returns to the app as a deep
     * link carrying `type=recovery` and the session tokens (see
     * [Vpn5gRecoveryLink]). This is deliberately NOT a code. GoTrue
     * answers 200 even for an unregistered email (no account
     * enumeration), so any 2xx means "accepted for sending". The
     * registration guard does NOT apply here.
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
     * Flow 2, step 2: set the new password
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
     * The device's public IP, fetched directly (no proxy — signup
     * happens before any VPN connection, so the direct fetch is the
     * real IP). Primary is ipify, fallback is the app's IP API
     * ([AppConfig.IP_API_URL], api.ip.sb/geoip — the same endpoint
     * [Vpn5gTunnelCheck] uses). Null when neither source answers;
     * the caller fails CLOSED on null. Never throws.
     */
    suspend fun fetchPublicIp(): String? = withContext(Dispatchers.IO) {
        fetchIpFrom(PUBLIC_IP_URL) ?: fetchIpFrom(AppConfig.IP_API_URL)
    }

    private suspend fun fetchIpFrom(url: String): String? {
        val body = getRaw(url) ?: return null
        return parseIp(body)?.takeIf { it.isNotBlank() }
    }

    /** Pulls the `ip` value out of an ipify / ip.sb JSON body. Pure. */
    internal fun parseIp(body: String): String? {
        return Regex("\"ip\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    }

    /**
     * Asks the server whether the email or the IP is already
     * registered (PostgREST RPC `check_registration`). Returns the
     * response body on 2xx, null on any failure (transport or
     * non-2xx — including a missing function when the SQL file has
     * not been run yet). Never throws.
     */
    private suspend fun checkRegistration(email: String, ip: String): String? {
        val result = postRaw(
            path = "/rest/v1/rpc/check_registration",
            body = """{"p_email":"${jsonEscape(email)}","p_ip":"${jsonEscape(ip)}"}""",
        ) ?: return null
        if (result.first !in 200..299) {
            LogUtil.e(
                AppConfig.TAG,
                "Vpn5gAuthService: check_registration HTTP ${result.first}",
            )
            return null
        }
        return result.second
    }

    /**
     * Records the (email, ip) pair after a successful signup
     * (PostgREST RPC `register_account`). True when the server
     * stored the row; false on any failure — the caller still treats
     * the session as signed in. Never throws.
     */
    private suspend fun recordRegistration(email: String, ip: String): Boolean {
        val result = postRaw(
            path = "/rest/v1/rpc/register_account",
            body = """{"p_email":"${jsonEscape(email)}","p_ip":"${jsonEscape(ip)}"}""",
        ) ?: return false
        if (result.first !in 200..299) {
            LogUtil.e(
                AppConfig.TAG,
                "Vpn5gAuthService: register_account HTTP ${result.first}",
            )
            return false
        }
        return result.second.trim() == "true"
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
     * reports `email_not_confirmed` instead of signing in.
     */
    internal fun hasSession(body: String): Boolean {
        val access = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        return !access.isNullOrBlank()
    }

    /** Raw POST returning (status, body), or null on transport failure. */
    private suspend fun postRaw(
        path: String,
        body: String,
    ): Pair<Int, String>? {
        return try {
            val request = Request.Builder()
                .url(Vpn5gAuthConfig.SUPABASE_URL.trimEnd('/') + path)
                .header("apikey", Vpn5gAuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${Vpn5gAuthConfig.SUPABASE_ANON_KEY}")
                .post(body.toRequestBody(JSON))
                .build()
            client().newCall(request).execute().use { response ->
                response.code to (response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAuthService: POST $path failed", e)
            null
        }
    }

    /** Raw GET returning the body on 2xx, null otherwise. Never throws. */
    private suspend fun getRaw(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            client().newCall(request).execute().use { response ->
                if (response.code !in 200..299) null
                else response.body?.string()
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAuthService: GET $url failed", e)
            null
        }
    }

    private suspend fun post(
        path: String,
        body: String,
        handle: suspend (Int, String) -> AuthResult,
    ): AuthResult {
        val result = postRaw(path, body)
            ?: return AuthResult.Error("network_error")
        return handle(result.first, result.second)
    }

    private suspend fun put(
        path: String,
        bearerToken: String,
        body: String,
        handle: suspend (Int, String) -> AuthResult,
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
