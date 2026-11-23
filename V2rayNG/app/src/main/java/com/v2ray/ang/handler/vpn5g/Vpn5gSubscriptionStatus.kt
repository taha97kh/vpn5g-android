package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_REDEEMED_SUBSCRIPTION_JSON
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_USER_SUBSCRIPTION_JSON
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Per-user subscription status (v1.1.11).
 *
 * The app reads the signed-in user's OWN row from
 * `public.vpn5g_registrations` with the user's own access token
 * (RLS policy "users read own registration row":
 * `auth.uid() = user_id` — see
 * `supabase/vpn5g_users_subscription.sql`). A user is a subscriber
 * only when `subscription_status` is `active` AND
 * `subscription_expires_at` is in the future. Subscribers get the
 * full server pool, no 30-minute timer and fast in-session failover;
 * everyone else keeps the exact free-tier behavior.
 *
 * Graceful degradation is the whole point of this file: the
 * subscription SQL may not have been run yet in the project (then
 * the table/columns/function do not exist and every call fails),
 * the user's row may have no `user_id` yet (registered before the
 * migration), or the device may be offline. Every one of those
 * cases means "not a subscriber" — never a crash, never a blocked
 * sign-in or connect, and a failed fetch never clears a cached
 * active subscription (only a successful fetch that finds a row
 * — or finds no row — overwrites the cache).
 */
object Vpn5gSubscriptionStatus {

    /** One row of vpn5g_registrations, as the app sees it. */
    data class Vpn5gUserSubscription(
        val status: String? = null,
        val expiresAtEpochSeconds: Long? = null,
        val plan: String? = null,
    )

    /** Result of a fetch attempt: found, genuinely absent, or failed. */
    sealed interface FetchResult {
        data class Found(val subscription: Vpn5gUserSubscription) : FetchResult
        data object NoRow : FetchResult
        data object Failed : FetchResult
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val TIMEOUT_MS = 15_000L

    /**
     * True only for an active subscription whose expiry is in the
     * future. Pure, JVM-tested. Null, "none", "cancelled", an expired
     * "active" row and a missing expiry are all "not a subscriber".
     */
    fun isSubscriber(
        subscription: Vpn5gUserSubscription?,
        nowEpochSeconds: Long,
    ): Boolean {
        if (subscription?.status != "active") return false
        val expiresAt = subscription.expiresAtEpochSeconds ?: return false
        return expiresAt > nowEpochSeconds
    }

    /**
     * Whole remaining days until expiry (0 when already expired).
     * Pure. The Premium screen shows this, never an invented date.
     */
    fun remainingDays(
        subscription: Vpn5gUserSubscription?,
        nowEpochSeconds: Long,
    ): Long {
        val expiresAt = subscription?.expiresAtEpochSeconds ?: return 0L
        val remaining = expiresAt - nowEpochSeconds
        if (remaining <= 0L) return 0L
        return remaining / 86_400L
    }

    /**
     * Parses a PostgREST row array into the subscription, or null
     * when the array is empty or unparsable. Pure, JVM-tested. The
     * expiry is an ISO-8601 timestamptz
     * ("2027-11-18T00:00:00+00:00"); an unparsable expiry is kept
     * as null (never a subscriber) instead of failing the row.
     */
    fun parseRow(body: String): Vpn5gUserSubscription? {
        val trimmed = body.trim()
        if (trimmed == "[]" || trimmed.isEmpty()) return null
        val status = Regex("\"subscription_status\"\\s*:\\s*\"([^\"]*)\"")
            .find(trimmed)?.groupValues?.get(1)
        val plan = Regex("\"subscription_plan\"\\s*:\\s*\"([^\"]*)\"")
            .find(trimmed)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val expiresRaw = Regex("\"subscription_expires_at\"\\s*:\\s*\"([^\"]+)\"")
            .find(trimmed)?.groupValues?.get(1)
        val expiresAt = expiresRaw?.let { parseIsoToEpoch(it) }
        // A row with no status field at all is not a row we understand.
        if (status == null && plan == null && expiresRaw == null) return null
        return Vpn5gUserSubscription(
            status = status,
            expiresAtEpochSeconds = expiresAt,
            plan = plan,
        )
    }

    /**
     * ISO-8601 timestamp ("2027-11-18T00:00:00+00:00", "…Z", with or
     * without fractional seconds) to epoch seconds, or null when it
     * does not parse. Pure, JVM-tested. `java.time` is safe here: the
     * app enables core library desugaring.
     */
    fun parseIsoToEpoch(raw: String): Long? {
        return try {
            Instant.parse(raw.trim()).epochSecond
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches the signed-in user's own registration row. Returns
     * [FetchResult.Failed] on any transport or HTTP failure —
     * including when the subscription SQL has not been run yet (the
     * table/columns do not exist → 404) — and [FetchResult.NoRow]
     * when the query succeeds but the user has no row (no `user_id`
     * linked yet). Never throws; never logs tokens.
     */
    suspend fun fetchOwnRow(session: Vpn5gAuthService.Vpn5gAuthSession): FetchResult =
        withContext(Dispatchers.IO) {
            val userId = session.userId
            val token = session.accessToken
            if (userId.isNullOrBlank() || token.isNullOrBlank()) {
                return@withContext FetchResult.Failed
            }
            try {
                val url = Vpn5gAuthConfig.SUPABASE_URL.trimEnd('/') +
                    "/rest/v1/vpn5g_registrations" +
                    "?select=subscription_status,subscription_expires_at,subscription_plan" +
                    "&user_id=eq.$userId&limit=1"
                val request = Request.Builder()
                    .url(url)
                    .header("apikey", Vpn5gAuthConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                client().newCall(request).execute().use { response ->
                    if (response.code !in 200..299) {
                        LogUtil.e(
                            AppConfig.TAG,
                            "Vpn5gSubscriptionStatus: fetch own row HTTP ${response.code}",
                        )
                        return@withContext FetchResult.Failed
                    }
                    val body = response.body?.string().orEmpty()
                    val row = parseRow(body)
                    if (row == null) FetchResult.NoRow else FetchResult.Found(row)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gSubscriptionStatus: fetch own row failed", e)
                FetchResult.Failed
            }
        }

    /**
     * Links the signed-in user's auth UUID to their registration row
     * (PostgREST RPC `set_own_user_id`, SECURITY DEFINER — it matches
     * on the email inside the caller's JWT, so a user can only ever
     * link their own row). Best effort: true when a row was linked,
     * false on any failure — including when the subscription SQL has
     * not been run yet. Never throws; the caller never blocks sign-in
     * on this.
     */
    suspend fun linkOwnUserId(session: Vpn5gAuthService.Vpn5gAuthSession): Boolean =
        withContext(Dispatchers.IO) {
            val token = session.accessToken
            if (token.isNullOrBlank()) return@withContext false
            try {
                val request = Request.Builder()
                    .url(
                        Vpn5gAuthConfig.SUPABASE_URL.trimEnd('/') +
                            "/rest/v1/rpc/set_own_user_id"
                    )
                    .header("apikey", Vpn5gAuthConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer $token")
                    .post("{}".toRequestBody(JSON))
                    .build()
                client().newCall(request).execute().use { response ->
                    if (response.code !in 200..299) return@withContext false
                    response.body?.string().orEmpty().trim() == "true"
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gSubscriptionStatus: link user id failed", e)
                false
            }
        }

    /**
     * The effective subscription (v1.1.12): the later-expiring of the
     * bot-activated row and a locally redeemed gift code, considering
     * only currently-active ones. A redeemed code and a bot-activated
     * subscription combine sensibly — the later expiry wins — and a
     * redeemed code is never demoted by a row fetch that finds no row
     * or a "none" row. When neither is active, the row is returned (so
     * the UI can show its expired state) or the redeemed one when
     * there is no row at all. Pure, JVM-tested.
     */
    fun effectiveSubscription(
        row: Vpn5gUserSubscription?,
        redeemed: Vpn5gUserSubscription?,
        nowEpochSeconds: Long,
    ): Vpn5gUserSubscription? {
        val active = listOfNotNull(row, redeemed)
            .filter { isSubscriber(it, nowEpochSeconds) }
        val best = active.maxByOrNull { it.expiresAtEpochSeconds ?: 0L }
        if (best != null) return best
        return row ?: redeemed
    }

    /**
     * Refreshes the cached subscription for the current session.
     * A successful fetch overwrites the ROW cache (Found → the row;
     * NoRow → an explicit "none" so a stale active entry cannot
     * linger); a failed fetch keeps the cache untouched, so a
     * network error never silently demotes a subscriber. What is
     * returned is always the EFFECTIVE subscription (v1.1.12): the
     * row merged with a locally redeemed gift code — the later
     * expiry wins, and a redeemed code survives a NoRow/Failed
     * fetch. Returns the subscription to use, or null when there is
     * no session. Never throws.
     */
    suspend fun refresh(session: Vpn5gAuthService.Vpn5gAuthSession?): Vpn5gUserSubscription? {
        if (session == null) return null
        val nowSeconds = System.currentTimeMillis() / 1000
        return when (val result = fetchOwnRow(session)) {
            is FetchResult.Found -> {
                saveCached(result.subscription)
                effectiveSubscription(result.subscription, loadRedeemed(), nowSeconds)
            }
            FetchResult.NoRow -> {
                val none = Vpn5gUserSubscription(status = "none")
                saveCached(none)
                effectiveSubscription(none, loadRedeemed(), nowSeconds)
            }
            FetchResult.Failed -> effectiveSubscription(loadCached(), loadRedeemed(), nowSeconds)
        }
    }

    /** The cached subscription, or null when nothing was ever cached. */
    fun loadCached(): Vpn5gUserSubscription? {
        val json = MmkvManager.decodeSettingsString(KEY_USER_SUBSCRIPTION_JSON)
        if (json.isNullOrBlank()) return null
        return try {
            JsonUtil.fromJsonSafe(json, Vpn5gUserSubscription::class.java)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSubscriptionStatus: cache parse failed", e)
            null
        }
    }

    /** The locally redeemed gift-code subscription, or null when none was ever redeemed. */
    fun loadRedeemed(): Vpn5gUserSubscription? {
        val json = MmkvManager.decodeSettingsString(KEY_REDEEMED_SUBSCRIPTION_JSON)
        if (json.isNullOrBlank()) return null
        return try {
            JsonUtil.fromJsonSafe(json, Vpn5gUserSubscription::class.java)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSubscriptionStatus: redeemed cache parse failed", e)
            null
        }
    }

    /** The effective subscription from both caches (row + redeemed gift code). */
    fun loadEffective(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Vpn5gUserSubscription? {
        return effectiveSubscription(loadCached(), loadRedeemed(), nowEpochSeconds)
    }

    /** True when the effective subscription is currently active. */
    fun cachedIsSubscriber(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        return isSubscriber(loadEffective(nowEpochSeconds), nowEpochSeconds)
    }

    fun saveCached(subscription: Vpn5gUserSubscription) {
        MmkvManager.encodeSettings(
            KEY_USER_SUBSCRIPTION_JSON,
            JsonUtil.toJson(subscription),
        )
    }

    /** Saves a gift-code subscription granted by an in-app redemption (v1.1.12). */
    fun saveRedeemed(subscription: Vpn5gUserSubscription) {
        MmkvManager.encodeSettings(
            KEY_REDEEMED_SUBSCRIPTION_JSON,
            JsonUtil.toJson(subscription),
        )
    }

    /** Clears the cache (sign-out: the next account must not inherit it). */
    fun clearCached() {
        MmkvManager.encodeSettings(KEY_USER_SUBSCRIPTION_JSON, "")
        // A redeemed gift code belongs to the signed-out account too.
        MmkvManager.encodeSettings(KEY_REDEEMED_SUBSCRIPTION_JSON, "")
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
}
