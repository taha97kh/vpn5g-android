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
 *     the device's public IP is read from Aref's own Supabase project
 *     (v1.1.10: Edge Function `supabase/functions/get-client-ip` —
 *     no third-party IP site; signup happens before any VPN
 *     connection, so the IP the function sees is the real one), then POST /rest/v1/rpc/check_registration asks
 *     the server whether the email or the IP is already registered.
 *     Either taken fails the signup with a stable key; any guard
 *     failure fails CLOSED (no signup is attempted on an unchecked
 *     registration). After
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
        /**
         * Supabase Auth UUID (`user.id` from the signup/token
         * response, v1.1.11). Null for sessions saved before 1.1.11;
         * re-filled on the next sign-in. The per-user subscription
         * lookup keys off this id.
         */
        val userId: String? = null,
    )

    /** Deep-link redirect for the recovery flow (flow 2). */
    const val RECOVERY_REDIRECT_URL = "vpn5g://auth/callback"

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val TIMEOUT_MS = 15_000L

    /** Edge Function that returns the caller's public IP (v1.1.10). */
    private const val CLIENT_IP_FUNCTION_PATH = "/functions/v1/get-client-ip"

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    fun currentSession(): Vpn5gAuthSession? {
        val json = MmkvManager.decodeSettingsString(KEY_AUTH_SESSION_JSON)
        if (json.isNullOrBlank()) return null
        return try {
            // v1.1.25: sessions stored before email normalization may
            // carry a mixed-case email ("Taha@gmail.com") — normalize
            // on read so every downstream RPC uses the canonical form.
            JsonUtil.fromJsonSafe(json, Vpn5gAuthSession::class.java)?.let { session ->
                session.copy(email = session.email?.let { Vpn5gEmailPolicy.normalize(it) })
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAuthService: session parse failed", e)
            null
        }
    }

    fun signOut() {
        MmkvManager.encodeSettings(KEY_AUTH_SESSION_JSON, "")
        // The next account must not inherit this one's subscription.
        Vpn5gSubscriptionStatus.clearCached()
    }

    private fun saveSession(session: Vpn5gAuthSession) {
        // v1.1.25: only the canonical (lowercase) email is ever stored.
        val normalized = session.copy(
            email = session.email?.let { Vpn5gEmailPolicy.normalize(it) }
        )
        MmkvManager.encodeSettings(KEY_AUTH_SESSION_JSON, JsonUtil.toJson(normalized))
    }

    /** Renew the access token this many seconds before it expires. */
    private const val TOKEN_REFRESH_SKEW_SECONDS = 60L

    /**
     * True when [session]'s access token is expired (or within the
     * skew window) AND a refresh token exists to renew it with
     * (v1.1.28). Unknown expiry = no proactive refresh (the
     * pre-1.1.28 behavior). Pure, JVM-tested.
     */
    internal fun needsTokenRefresh(
        session: Vpn5gAuthSession?,
        nowEpochSeconds: Long,
    ): Boolean {
        if (session == null) return false
        if (session.refreshToken.isNullOrBlank()) return false
        val expiresAt = session.expiresAtEpochSeconds ?: return false
        return expiresAt <= nowEpochSeconds + TOKEN_REFRESH_SKEW_SECONDS
    }

    /**
     * Returns a session with a live access token (v1.1.28): when
     * the stored token is expired, exchanges the refresh token
     * (POST /auth/v1/token?grant_type=refresh_token), persists the
     * renewed session and returns it. Any failure — offline, HTTP
     * error, unparsable body — returns the ORIGINAL session
     * unchanged, so callers degrade exactly as before (the
     * anon-key RPC path never needed the token). Never throws;
     * never logs tokens.
     */
    suspend fun freshSession(session: Vpn5gAuthSession?): Vpn5gAuthSession? {
        if (session == null) return null
        val nowSeconds = System.currentTimeMillis() / 1000
        if (!needsTokenRefresh(session, nowSeconds)) return session
        val refreshToken = session.refreshToken ?: return session
        val result = postRaw(
            path = "/auth/v1/token?grant_type=refresh_token",
            body = """{"refresh_token":"${jsonEscape(refreshToken)}"}""",
        ) ?: return session
        if (result.first !in 200..299) {
            LogUtil.e(
                AppConfig.TAG,
                "Vpn5gAuthService: token refresh HTTP ${result.first}",
            )
            return session
        }
        val renewed = parseSession(result.second, session.email.orEmpty())
            ?: return session
        val merged = renewed.copy(
            email = renewed.email ?: session.email,
            userId = renewed.userId ?: session.userId,
        )
        saveSession(merged)
        return merged
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
            // v1.1.25: canonical lowercase email for every call below.
            val email = Vpn5gEmailPolicy.normalize(email)
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
                        // v1.1.11: link the auth UUID to the
                        // registration row so the per-user subscription
                        // lookup can see it. Best effort only.
                        linkUserIdBestEffort(session)
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
            // v1.1.25: canonical lowercase email (see Vpn5gEmailPolicy).
            val email = Vpn5gEmailPolicy.normalize(email)
            post(
                path = "/auth/v1/token?grant_type=password",
                body = """{"email":"${jsonEscape(email)}","password":"${jsonEscape(password)}"}""",
            ) { code, body ->
                if (code in 200..299) {
                    parseSession(body, email)?.let { session ->
                        saveSession(session)
                        // v1.1.11: link the auth UUID to the
                        // registration row (best effort; rows
                        // registered before the migration get linked
                        // on the next sign-in).
                        linkUserIdBestEffort(session)
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
                body = """{"email":"${jsonEscape(Vpn5gEmailPolicy.normalize(email))}","redirect_to":"$RECOVERY_REDIRECT_URL"}""",
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
                        email = Vpn5gEmailPolicy.normalize(email),
                        accessToken = link.accessToken,
                        refreshToken = link.refreshToken,
                        expiresAtEpochSeconds = link.expiresInSeconds?.let {
                            System.currentTimeMillis() / 1000 + it
                        },
                    )
                    saveSession(session)
                    // v1.1.11: link the auth UUID to the registration
                    // row. The recovery link carries no user id, so
                    // this links by the JWT email instead — and a
                    // later sign-in fills the id.
                    linkUserIdBestEffort(session)
                    AuthResult.SignedIn(session)
                } else {
                    AuthResult.Error(updatePasswordErrorKey(code))
                }
            }
        }

    /**
     * The device's public IP, read from Aref's own Supabase project
     * (v1.1.10): GET /functions/v1/get-client-ip (Edge Function
     * `supabase/functions/get-client-ip`) answers {"ip": "..."} with
     * the IP Supabase sees for this request. No third-party IP site
     * is used anymore — signup happens before any VPN connection, so
     * the IP the function sees is the real one, and signup already
     * requires Supabase, so this adds no new failure mode. Null when
     * the function is missing (404), unreachable, or returns no IP;
     * the caller fails CLOSED on null. Never throws. The anon key is
     * sent exactly as for the other Supabase calls.
     */
    suspend fun fetchPublicIp(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(Vpn5gAuthConfig.SUPABASE_URL.trimEnd('/') + CLIENT_IP_FUNCTION_PATH)
                .header("apikey", Vpn5gAuthConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${Vpn5gAuthConfig.SUPABASE_ANON_KEY}")
                .get()
                .build()
            client().newCall(request).execute().use { response ->
                if (response.code !in 200..299) return@withContext null
                parseIp(response.body?.string().orEmpty())?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAuthService: get-client-ip failed", e)
            null
        }
    }

    /** Pulls the `ip` value out of the get-client-ip response body. Pure. */
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
            body = """{"p_email":"${jsonEscape(Vpn5gEmailPolicy.normalize(email))}","p_ip":"${jsonEscape(ip)}"}""",
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
     * Records a gift-code redemption server-side (PostgREST RPC
     * `record_code_redemption`, v1.1.14). True when the server stored
     * a NEW redemption row; false on any failure — INCLUDING when the
     * RPC does not exist yet (the bot side creates the table/RPC; the
     * app must never fail or block a redeem over it). Best effort by
     * design: the caller treats the local activation as done either
     * way. The bot uses the redemption count to enforce the
     * people-limit (remaining_uses) when it republishes app_config.json.
     * Never throws.
     */
    suspend fun recordCodeRedemption(code: String, email: String): Boolean {
        val result = postRaw(
            path = "/rest/v1/rpc/record_code_redemption",
            body = """{"p_code":"${jsonEscape(code.trim().uppercase())}","p_email":"${jsonEscape(Vpn5gEmailPolicy.normalize(email))}"}""",
        ) ?: return false
        if (result.first !in 200..299) {
            LogUtil.e(
                AppConfig.TAG,
                "Vpn5gAuthService: record_code_redemption HTTP ${result.first}",
            )
            return false
        }
        return result.second.trim() == "true"
    }

    /**
     * Result of a server-side code validation (v1.1.15, RPC
     * `validate_and_redeem_code`). The server checks existence,
     * already-used, expiry and remaining_uses and marks the code used
     * ATOMICALLY — a single-use code can never be reused, even from
     * two devices at once.
     */
    sealed interface CodeServerResult {
        /**
         * Server validated + marked the code used. [plan]/[expiresAt]
         * are the grant (gifts). [applied] is the server's own flag:
         * true = the grant landed in vpn5g_registrations, false = the
         * code was burned but nothing was granted (v1.1.19: the
         * function only grants IF a registrations row exists for the
         * email, and strips expires_at when it does not apply) —
         * the caller re-derives the grant from the answer's own
         * fields (tier / plan / gift_hours / gb_total, v1.1.21) or
         * from the published entry instead of showing VALID. Null
         * when the server did not report it. [tier] is the code's
         * tier as the server reports it ("discount" = a discount code
         * with no grant — the bot applies it; "gift" / a plan id = a
         * grant). [giftHours]/[gbTotal] are the answer's own
         * duration/volume, for that re-derivation.
         */
        data class Validated(
            val plan: String?,
            val expiresAtEpochSeconds: Long?,
            val applied: Boolean? = null,
            val tier: String? = null,
            /**
             * Gift duration in hours, as the server reports it
             * (v1.1.21): the RPC answer carries `gift_hours` next to
             * the tier — the caller re-derives the grant from it when
             * the answer has no usable expiry, instead of needing
             * the code in the published list.
             */
            val giftHours: Int? = null,
            /**
             * Volume of the grant in GB, as the server reports it
             * (v1.1.21): the RPC answer carries `gb_total` — the
             * caller caps the derived grant from it.
             */
            val gbTotal: Int? = null,
        ) : CodeServerResult
        /** Server definitively rejected the code ("unknown" | "already_used" | "expired" | "exhausted" | "not_yours" | "invalid_email" | "no_registration"). */
        data class Rejected(val status: String) : CodeServerResult
        /** RPC missing / offline / unparsable — the caller falls back to the config-list check. */
        data object Unavailable : CodeServerResult
    }

    /**
     * Validates + redeems a subscription/gift code SERVER-side
     * (PostgREST RPC `validate_and_redeem_code`, v1.1.15). Never
     * throws. The bot side implements the function (see
     * docs/app_config.md); the RPC EXISTS (live-probed 2026-11-24:
     * a fake code returns {"ok": false, "reason": "not_found"}).
     * When it is missing/offline/unparsable the call returns
     * [CodeServerResult.Unavailable] and the caller falls back to
     * the published config list — the fallback is documented,
     * never silent.
     */
    suspend fun validateAndRedeemCode(code: String, email: String): CodeServerResult {
        val result = postRaw(
            path = "/rest/v1/rpc/validate_and_redeem_code",
            body = """{"p_code":"${jsonEscape(code.trim().uppercase())}","p_email":"${jsonEscape(Vpn5gEmailPolicy.normalize(email))}"}""",
        ) ?: return CodeServerResult.Unavailable
        if (result.first !in 200..299) {
            LogUtil.e(
                AppConfig.TAG,
                "Vpn5gAuthService: validate_and_redeem_code HTTP ${result.first}",
            )
            return CodeServerResult.Unavailable
        }
        return parseCodeServerResult(result.second)
    }

    /**
     * Parses a `validate_and_redeem_code` response. TWO shapes are
     * accepted (v1.1.17 — the documented shape was never the real
     * one):
     * - documented: `{"status":"ok","plan":"sub_12",
     *   "expires_at":"2027-11-18T00:00:00+00:00"}` or
     *   `{"status":"already_used"}` (and the other reject statuses);
     * - real (live probe 2026-11-24): `{"ok":true,"plan":"sub_1",
     *   "expires_at":"..."}` or `{"ok":false,"reason":"not_found"}`
     *   (reasons: not_found | already_used | already_redeemed |
     *   expired | exhausted | not_yours | invalid_email).
     * v1.1.19: the live function burns the code FIRST and only
     * grants IF a row exists in vpn5g_registrations for the email —
     * with no row it answers ok:true with `applied:false` and
     * expires_at stripped, so the caller must never trust ok:true
     * alone. v1.1.21: the caller derives the grant from the answer's
     * own fields instead (tier / plan / gift_hours / gb_total) —
     * [Validated.applied] is still parsed, but no longer gates the
     * grant.
     * `expires_at` is also accepted as a bare numeric epoch
     * (unquoted digits) as a fallback. Pure, JVM-tested.
     * v1.1.20: the guard's fixed function also answers
     * `no_registration` (the email never registered — the code is NOT
     * consumed in that case) and reports the code's `tier` on success,
     * so a discount code is recognisable without the published list.
     * An unrecognised body is [CodeServerResult.Unavailable]
     * (fallback), never a false success.
     */
    internal fun parseCodeServerResult(body: String): CodeServerResult {
        val status = Regex("\"status\"\\s*:\\s*\"([^\"]*)\"")
            .find(body)?.groupValues?.get(1)?.trim()?.lowercase()
        if (status != null) {
            return when (status) {
                "ok", "valid", "validated" -> {
                    val plan = Regex("\"plan\"\\s*:\\s*\"([^\"]*)\"")
                        .find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                    CodeServerResult.Validated(
                        plan, parseExpiresAt(body), parseApplied(body), parseTier(body),
                        parseGiftHours(body), parseGbTotal(body),
                    )
                }
                "unknown", "already_used", "expired", "exhausted", "not_yours" ->
                    CodeServerResult.Rejected(status)
                // v1.1.19: the live function answers "already_redeemed"
                // (not "already_used") — same meaning, mapped to the
                // existing recovery status.
                "already_redeemed" -> CodeServerResult.Rejected("already_used")
                "invalid_email" -> CodeServerResult.Rejected("invalid_email")
                // v1.1.20: the email never registered — the code was
                // not consumed; the caller tells the user to register
                // first instead of calling the code invalid.
                "no_registration" -> CodeServerResult.Rejected("no_registration")
                else -> CodeServerResult.Unavailable
            }
        }
        // The real server shape: {"ok": bool, "reason": ...}.
        val ok = Regex("\"ok\"\\s*:\\s*(true|false)")
            .find(body)?.groupValues?.get(1)?.lowercase()
            ?: return CodeServerResult.Unavailable
        if (ok == "true") {
            val plan = Regex("\"plan\"\\s*:\\s*\"([^\"]*)\"")
                .find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            return CodeServerResult.Validated(
                plan, parseExpiresAt(body), parseApplied(body), parseTier(body),
                parseGiftHours(body), parseGbTotal(body),
            )
        }
        val reason = Regex("\"reason\"\\s*:\\s*\"([^\"]*)\"")
            .find(body)?.groupValues?.get(1)?.trim()?.lowercase()
        return when (reason) {
            "not_found" -> CodeServerResult.Rejected("unknown")
            "already_used", "expired", "exhausted", "not_yours" ->
                CodeServerResult.Rejected(reason)
            // v1.1.19: the live function's own reason spellings.
            "already_redeemed" -> CodeServerResult.Rejected("already_used")
            "invalid_email" -> CodeServerResult.Rejected("invalid_email")
            // v1.1.20: see the {"status":...} branch above.
            "no_registration" -> CodeServerResult.Rejected("no_registration")
            else -> CodeServerResult.Unavailable
        }
    }

    /**
     * The `gift_hours` of a `validate_and_redeem_code` response
     * (v1.1.21): the gift duration the code grants, used to re-derive
     * the grant when the answer has no usable expiry. Null when the
     * server did not report it. Pure.
     */
    private fun parseGiftHours(body: String): Int? {
        return Regex("\"gift_hours\"\\s*:\\s*([0-9]+)")
            .find(body)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * The `gb_total` of a `validate_and_redeem_code` response
     * (v1.1.21): the volume of the grant in GB, used to cap a grant
     * re-derived from the answer. Null when the server did not
     * report it. Pure.
     */
    private fun parseGbTotal(body: String): Int? {
        return Regex("\"gb_total\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")
            .find(body)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
    }

    /**
     * The `tier` of a `validate_and_redeem_code` response (v1.1.20):
     * "discount" for a discount code (no grant — the bot applies it),
     * "gift" or a plan id for a grant. Null when the server did not
     * report it. Pure.
     */
    private fun parseTier(body: String): String? {
        return Regex("\"tier\"\\s*:\\s*\"([^\"]*)\"")
            .find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?.lowercase()
    }

    /**
     * The `applied` flag of a `validate_and_redeem_code` response
     * (v1.1.19): true = the grant landed, false = the code was
     * burned with no grant. Null when the server did not report it
     * (older shape). Pure.
     */
    private fun parseApplied(body: String): Boolean? {
        return when (
            Regex("\"applied\"\\s*:\\s*(true|false)")
                .find(body)?.groupValues?.get(1)?.lowercase()
        ) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    /**
     * `expires_at` of a `validate_and_redeem_code` response
     * (v1.1.19): a quoted ISO-8601 string as before, or a bare
     * numeric epoch (unquoted digits) as a fallback — a quoted
     * all-digits value parses as an epoch too. Null when absent or
     * unparsable. Pure.
     */
    private fun parseExpiresAt(body: String): Long? {
        val quoted = Regex("\"expires_at\"\\s*:\\s*\"([^\"]+)\"")
            .find(body)?.groupValues?.get(1)
        if (quoted != null) {
            return Vpn5gSubscriptionStatus.parseIsoToEpoch(quoted)
                ?: quoted.trim().toLongOrNull()
        }
        return Regex("\"expires_at\"\\s*:\\s*([0-9]+)")
            .find(body)?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * Records a fully-viewed interstitial ad SERVER-side (PostgREST
     * RPC `record_ad_view`, v1.1.15). True when the server stored the
     * view; false on any failure — INCLUDING when the RPC does not
     * exist yet (the bot side creates the table/RPC; the app must
     * never fail, hang or block a connect over it). Best effort by
     * design: a failed record never stops the countdown completion
     * or the connect. Never throws.
     */
    suspend fun recordAdView(adId: String, email: String?): Boolean {
        val result = postRaw(
            path = "/rest/v1/rpc/record_ad_view",
            body = """{"p_ad_id":"${jsonEscape(adId)}","p_email":"${jsonEscape(Vpn5gEmailPolicy.normalize(email.orEmpty()))}"}""",
        ) ?: return false
        if (result.first !in 200..299) {
            LogUtil.e(
                AppConfig.TAG,
                "Vpn5gAuthService: record_ad_view HTTP ${result.first}",
            )
            return false
        }
        val body = result.second.trim()
        return body == "true" || Regex("\"verified\"\\s*:\\s*true").containsMatchIn(body)
    }

    /**
     * Records an ad-link tap SERVER-side (PostgREST RPC
     * `record_ad_click`, v1.1.15). Same best-effort contract as
     * [recordAdView]: a failure never blocks opening the link.
     * Never throws.
     */
    suspend fun recordAdClick(adId: String, email: String?): Boolean {
        val result = postRaw(
            path = "/rest/v1/rpc/record_ad_click",
            body = """{"p_ad_id":"${jsonEscape(adId)}","p_email":"${jsonEscape(Vpn5gEmailPolicy.normalize(email.orEmpty()))}"}""",
        ) ?: return false
        if (result.first !in 200..299) {
            LogUtil.e(
                AppConfig.TAG,
                "Vpn5gAuthService: record_ad_click HTTP ${result.first}",
            )
            return false
        }
        val body = result.second.trim()
        return body == "true" || Regex("\"verified\"\\s*:\\s*true").containsMatchIn(body)
    }

    /** Result of the server subscription lookup (v1.1.15). */
    sealed interface ServerSubscriptionResult {
        /** The server knows a grant for this email (plan + expiry). */
        data class Found(val subscription: Vpn5gSubscriptionStatus.Vpn5gUserSubscription) : ServerSubscriptionResult
        /** The server answered definitively: no grant for this email. */
        data object None : ServerSubscriptionResult
        /** RPC missing / offline / unparsable — the caller keeps its cache. */
        data object Unavailable : ServerSubscriptionResult
    }

    /**
     * Fetches the bot's subscription truth for [email] (PostgREST RPC
     * `get_subscription_status`, v1.1.15): bot-activated plans AND
     * redeemed gifts, as the bot recorded them centrally. Never
     * throws. Until the bot implements the function every call
     * returns [ServerSubscriptionResult.Unavailable] and the app
     * keeps its existing caches.
     */
    suspend fun fetchServerSubscription(email: String): ServerSubscriptionResult {
        val result = postRaw(
            path = "/rest/v1/rpc/get_subscription_status",
            body = """{"p_email":"${jsonEscape(Vpn5gEmailPolicy.normalize(email))}"}""",
        ) ?: return ServerSubscriptionResult.Unavailable
        if (result.first !in 200..299) {
            LogUtil.e(
                AppConfig.TAG,
                "Vpn5gAuthService: get_subscription_status HTTP ${result.first}",
            )
            return ServerSubscriptionResult.Unavailable
        }
        return parseServerSubscription(result.second)
    }

    /**
     * Parses a `get_subscription_status` response:
     * `{"status":"active","plan":"sub_12","expires_at":"2027-11-18T00:00:00+00:00"}`
     * or `{"status":"none"}`. Pure, JVM-tested. An unrecognised body
     * is [ServerSubscriptionResult.Unavailable] (keep the cache),
     * never a false grant or a false denial.
     */
    internal fun parseServerSubscription(body: String): ServerSubscriptionResult {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed == "[]" || trimmed == "null") {
            return ServerSubscriptionResult.None
        }
        val status = Regex("\"status\"\\s*:\\s*\"([^\"]*)\"")
            .find(trimmed)?.groupValues?.get(1)?.trim()?.lowercase()
            ?: return ServerSubscriptionResult.Unavailable
        if (status == "none" || status == "inactive" || status == "cancelled") {
            return ServerSubscriptionResult.None
        }
        val plan = Regex("\"plan\"\\s*:\\s*\"([^\"]*)\"")
            .find(trimmed)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val expiresRaw = Regex("\"expires_at\"\\s*:\\s*\"([^\"]+)\"")
            .find(trimmed)?.groupValues?.get(1)
        val expiresAt = expiresRaw?.let { Vpn5gSubscriptionStatus.parseIsoToEpoch(it) }
        return ServerSubscriptionResult.Found(
            Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
                status = status,
                expiresAtEpochSeconds = expiresAt,
                plan = plan,
            )
        )
    }

    /**
     * Makes sure a row exists in vpn5g_registrations for [email]
     * (v1.1.19): the server-side `validate_and_redeem_code` only
     * grants a code when such a row exists — without it the code is
     * burned and `applied:false` comes back. Fetches the current
     * public IP and calls [recordRegistration]. Best effort only:
     * true when the server stored the row, false on any failure
     * (including no network/IP) — the caller never blocks sign-in
     * or a redeem on this. Never throws.
     */
    suspend fun ensureRegistrationRow(email: String): Boolean {
        if (email.isBlank()) return false
        val ip = runCatching { fetchPublicIp() }.getOrNull() ?: return false
        return runCatching { recordRegistration(Vpn5gEmailPolicy.normalize(email), ip) }.getOrDefault(false)
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
            body = """{"p_email":"${jsonEscape(Vpn5gEmailPolicy.normalize(email))}","p_ip":"${jsonEscape(ip)}"}""",
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
                email = Vpn5gEmailPolicy.normalize(email),
                accessToken = access,
                refreshToken = refresh,
                expiresAtEpochSeconds = expires?.let { System.currentTimeMillis() / 1000 + it },
                userId = parseUserId(body),
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAuthService: session parse failed", e)
            null
        }
    }

    /**
     * Pulls `user.id` (the Supabase Auth UUID) out of a GoTrue token
     * response. Pure, JVM-tested. The `user` object carries the id
     * first, before any nested object, so a bounded scan is enough;
     * null when the response carries no user id (recovery-link
     * sessions get theirs on the next password sign-in).
     */
    internal fun parseUserId(body: String): String? {
        return Regex("\"user\"\\s*:\\s*\\{[^}]{0,400}?\"id\"\\s*:\\s*\"([^\"]+)\"")
            .find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    /**
     * Links the signed-in user's auth UUID to their registration row
     * (v1.1.11, [Vpn5gSubscriptionStatus.linkOwnUserId]). Best effort:
     * a failure (subscription SQL not run yet, offline) is logged and
     * never changes the sign-in result — the row can still be linked
     * on the next sign-in, or by the admin from the dashboard.
     */
    private suspend fun linkUserIdBestEffort(session: Vpn5gAuthSession) {
        if (session.userId.isNullOrBlank()) return
        val linked = Vpn5gSubscriptionStatus.linkOwnUserId(session)
        if (!linked) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAuthService: set_own_user_id failed")
        }
    }

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
