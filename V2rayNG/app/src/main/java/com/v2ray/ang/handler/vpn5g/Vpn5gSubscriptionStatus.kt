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
        /**
         * Volume cap of this grant in bytes (v1.1.18, Model B),
         * resolved from the plan id via the published plans
         * ([Vpn5gVolume.capBytesForPlanId]). Null = the plan has no
         * cap (unlimited) or the cap could not be resolved yet (the
         * plan config had not loaded) — never an invented number.
         * Persisted in the MMKV JSON caches, so an older cache
         * without the field reads as null (unlimited).
         */
        val volumeCapBytes: Long? = null,
    )

    /**
     * Access tier (v1.1.14): FREE = top-20 servers + 30-minute timer;
     * GIFT = a redeemed gift code (top-100 servers, the countries those
     * servers belong to, no 30-minute cutoff while it lasts); SUBSCRIBER
     * = a bot-activated plan (full pool, no timer).
     */
    enum class Vpn5gTier { FREE, GIFT, SUBSCRIBER }

    /** Result of a fetch attempt: found, genuinely absent, or failed. */
    sealed interface FetchResult {
        data class Found(val subscription: Vpn5gUserSubscription) : FetchResult
        data object NoRow : FetchResult
        data object Failed : FetchResult
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val TIMEOUT_MS = 15_000L

    /**
     * True when this grant's volume cap is reached (v1.1.18, Model
     * B): the on-device measured consumption for THIS grant
     * ([Vpn5gVolume.bytesFor] — bytes measured under another grant
     * never count) is >= the grant's cap. No cap, or no attributable
     * consumption, is never exhausted. Pure, JVM-tested.
     */
    fun isVolumeExhausted(
        subscription: Vpn5gUserSubscription?,
        volume: Vpn5gVolume.ConsumedState?,
    ): Boolean {
        val capBytes = subscription?.volumeCapBytes ?: return false
        val consumed = Vpn5gVolume.bytesFor(subscription, volume) ?: return false
        return Vpn5gVolume.isExhausted(consumed, capBytes)
    }

    /**
     * True only for an active FULL subscription whose expiry is in the
     * future. Pure, JVM-tested. Null, "none", "cancelled", an expired
     * "active" row and a missing expiry are all "not a subscriber".
     * v1.1.14: a gift-tier redemption (plan "gift") is NOT a full
     * subscription — it is [Vpn5gTier.GIFT] (top-100 pool, no timer),
     * never the full pool.
     * v1.1.18 (Model B): when [volume] is given, a grant whose
     * volume cap is reached is NOT a subscriber either — volume or
     * time, whichever first. A null [volume] means "consumption
     * unknown" and keeps the time-only decision (existing callers
     * and tests are unaffected).
     */
    fun isSubscriber(
        subscription: Vpn5gUserSubscription?,
        nowEpochSeconds: Long,
        volume: Vpn5gVolume.ConsumedState? = null,
    ): Boolean {
        if (subscription?.status != "active") return false
        if (subscription.plan == Vpn5gConstants.GIFT_TIER_PLAN_ID) return false
        val expiresAt = subscription.expiresAtEpochSeconds ?: return false
        if (expiresAt <= nowEpochSeconds) return false
        return !isVolumeExhausted(subscription, volume)
    }

    /**
     * True only for an active GIFT-tier redemption (plan "gift") whose
     * expiry is in the future. Pure, JVM-tested. The gift tier sits
     * between free and VIP: more servers than free, not the full pool,
     * and no 30-minute cutoff while it lasts.
     * v1.1.18 (Model B): a gift-tier grant with a cap also ends when
     * its volume is reached — [volume] is enforced the same way as
     * for a full subscription.
     */
    fun isGiftTier(
        subscription: Vpn5gUserSubscription?,
        nowEpochSeconds: Long,
        volume: Vpn5gVolume.ConsumedState? = null,
    ): Boolean {
        if (subscription?.status != "active") return false
        if (subscription.plan != Vpn5gConstants.GIFT_TIER_PLAN_ID) return false
        val expiresAt = subscription.expiresAtEpochSeconds ?: return false
        if (expiresAt <= nowEpochSeconds) return false
        return !isVolumeExhausted(subscription, volume)
    }

    /** The access tier a subscription grants right now. Pure, JVM-tested. */
    fun tierOf(
        subscription: Vpn5gUserSubscription?,
        nowEpochSeconds: Long,
        volume: Vpn5gVolume.ConsumedState? = null,
    ): Vpn5gTier {
        return when {
            isSubscriber(subscription, nowEpochSeconds, volume) -> Vpn5gTier.SUBSCRIBER
            isGiftTier(subscription, nowEpochSeconds, volume) -> Vpn5gTier.GIFT
            else -> Vpn5gTier.FREE
        }
    }

    /** Whole remaining hours of a gift-tier subscription (0 when not a gift / expired). Pure. */
    fun giftRemainingHours(
        subscription: Vpn5gUserSubscription?,
        nowEpochSeconds: Long,
        volume: Vpn5gVolume.ConsumedState? = null,
    ): Long {
        if (!isGiftTier(subscription, nowEpochSeconds, volume)) return 0L
        val remaining = (subscription?.expiresAtEpochSeconds ?: 0L) - nowEpochSeconds
        if (remaining <= 0L) return 0L
        return remaining / 3_600L
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
     * The effective subscription (v1.1.12, tier-aware v1.1.14): a
     * FULL subscription always beats a gift-tier redemption, whatever
     * the expiries — a longer gift must never demote a subscriber to
     * the top-100 pool. Within one tier the later expiry wins. A
     * redeemed code is never demoted by a row fetch that finds no row
     * or a "none" row. When neither is active, the row is returned (so
     * the UI can show its expired state) or the redeemed one when
     * there is no row at all. Pure, JVM-tested.
     * v1.1.18 (Model B): when [volume] is given, a grant whose
     * volume cap is reached is not "active" for the merge either —
     * a live grant with an earlier expiry beats an exhausted grant
     * with a later one, so volume exhaustion can never hide a live
     * grant. The fallback (neither active) still returns the row /
     * redeemed record so the UI can show WHY it ended.
     */
    fun effectiveSubscription(
        row: Vpn5gUserSubscription?,
        redeemed: Vpn5gUserSubscription?,
        nowEpochSeconds: Long,
        volume: Vpn5gVolume.ConsumedState? = null,
    ): Vpn5gUserSubscription? {
        val active = listOfNotNull(row, redeemed)
            .filter {
                isSubscriber(it, nowEpochSeconds, volume) ||
                    isGiftTier(it, nowEpochSeconds, volume)
            }
        val bestSubscriber = active
            .filter { isSubscriber(it, nowEpochSeconds, volume) }
            .maxByOrNull { it.expiresAtEpochSeconds ?: 0L }
        if (bestSubscriber != null) return bestSubscriber
        val bestGift = active
            .filter { isGiftTier(it, nowEpochSeconds, volume) }
            .maxByOrNull { it.expiresAtEpochSeconds ?: 0L }
        if (bestGift != null) return bestGift
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
        val rowCandidate = when (val result = fetchOwnRow(session)) {
            is FetchResult.Found -> {
                // v1.1.18: the row's plan cap is attached before the
                // cache is saved, so the cached record meters.
                val capped = withVolumeCap(result.subscription)
                if (capped != null) saveCached(capped)
                capped
            }
            FetchResult.NoRow -> {
                val none = Vpn5gUserSubscription(status = "none")
                saveCached(none)
                none
            }
            FetchResult.Failed -> loadCached()
        }
        // v1.1.15 (server-side enforcement): the bot's subscription
        // lookup is the authority for redeemed gifts too — a local
        // gift the server does not know is not trusted. The RPC does
        // not exist yet on the bot side: then the lookup returns
        // Unavailable and the local caches stand (current behavior).
        // Only a DEFINITIVE server answer changes anything: Found
        // merges in as server truth; None drops the local gift.
        val serverResult = session.email?.takeIf { it.isNotBlank() }?.let { email ->
            runCatching { Vpn5gAuthService.fetchServerSubscription(email) }.getOrNull()
        } ?: Vpn5gAuthService.ServerSubscriptionResult.Unavailable
        val serverSub = (serverResult as? Vpn5gAuthService.ServerSubscriptionResult.Found)
            ?.subscription?.let { withVolumeCap(it) }
        val trustedRedeemed = when (serverResult) {
            is Vpn5gAuthService.ServerSubscriptionResult.None -> null
            else -> loadRedeemed()?.let { withVolumeCap(it) }
        }
        // v1.1.18 (Model B): the merge is volume-aware — a grant
        // whose cap is reached is not "active", so a live grant with
        // an earlier expiry beats it. The consumed bytes are
        // attributed by grant key (see Vpn5gVolume.bytesFor), so
        // bytes measured under one grant never end another.
        val volume = Vpn5gVolumeStore.consumedState()
        val merged = effectiveSubscription(rowCandidate, serverSub, nowSeconds, volume)
        return effectiveSubscription(merged, trustedRedeemed, nowSeconds, volume)
    }

    /**
     * Attaches a subscription record's plan volume cap (v1.1.18):
     * resolved fresh from the cached published plans whenever the
     * plan id is in them — so an admin cap change (including
     * REMOVING the cap) takes effect on the next refresh — otherwise
     * the record's stored cap is kept (never silently dropped to
     * unlimited just because the plan config had not loaded yet, or
     * the plan id is not in the published list). Never throws.
     */
    private fun withVolumeCap(
        subscription: Vpn5gUserSubscription?,
    ): Vpn5gUserSubscription? {
        if (subscription == null) return null
        val plans = runCatching { Vpn5gRemoteConfig.cached().plans }.getOrNull()
            ?: return subscription
        val plan = plans.firstOrNull {
            it.id?.trim()?.equals(subscription.plan?.trim(), ignoreCase = true) == true
        } ?: return subscription
        val fresh = Vpn5gVolume.capBytes(Vpn5gPlans.volumeCapGb(plan))
        if (fresh == subscription.volumeCapBytes) return subscription
        return subscription.copy(volumeCapBytes = fresh)
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

    /** True when the effective subscription is currently the GIFT tier. */
    fun cachedIsGift(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        return isGiftTier(loadEffective(nowEpochSeconds), nowEpochSeconds)
    }

    /** The effective access tier right now (FREE / GIFT / SUBSCRIBER). */
    fun cachedTier(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Vpn5gTier {
        return tierOf(loadEffective(nowEpochSeconds), nowEpochSeconds)
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
