package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_VOLUME_CONSUMED_BYTES
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_VOLUME_GRANT_KEY

/**
 * Persistent on-device consumption counter (v1.1.18, Model B).
 *
 * The counter lives in MMKV (multi-process, so the :daemon speed job
 * can write it and the UI process can read it) and is keyed to the
 * current grant ([Vpn5gVolume.grantKeyFor] — plan + expiry):
 *
 * - [ensureGrant] is called by the UI process whenever the effective
 *   subscription is published (startup, refresh, redeem, sign-out).
 *   A NEW grant key resets the counter to 0; the SAME key leaves it
 *   alone — so consumption is cumulative across sessions for the
 *   current grant and is NEVER reset on app restart.
 * - [addIntervalBytes] is called ONLY by the :daemon notification
 *   speed job (the single querier of the native stats) with the
 *   real tunnel bytes of that interval (proxy up + down). No other
 *   writer exists: the UI never accumulates sampled rates (that
 *   would be an estimate), and no second querier can steal bytes —
 *   the native counters reset on every query.
 * - [clear] runs on sign-out so the next account inherits nothing.
 *
 * The pure transition lives in [Vpn5gVolume.accumulate] (JVM-tested);
 * these are the only MMKV touch points. Never throws.
 */
object Vpn5gVolumeStore {

    /** Consumed tunnel bytes for the current grant, or 0 when none. */
    fun consumedBytes(): Long {
        return try {
            MmkvManager.decodeSettingsLong(KEY_VOLUME_CONSUMED_BYTES, 0L)
                .coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    /** Grant key the counter belongs to, or null when no grant is metered. */
    fun grantKey(): String? {
        return try {
            MmkvManager.decodeSettingsString(KEY_VOLUME_GRANT_KEY)
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Measured consumption as one attributable value (v1.1.18): the
     * grant key + bytes. Null when no grant is metered — tier
     * decisions then fall back to time-only (never exhausted on
     * unknown consumption).
     */
    fun consumedState(): Vpn5gVolume.ConsumedState? {
        val key = grantKey() ?: return null
        return Vpn5gVolume.ConsumedState(key, consumedBytes())
    }

    /**
     * Makes the counter belong to [subscription]'s grant: a new grant
     * key resets the counter to 0, the same key keeps it, and no grant
     * (null key — free tier / signed out) clears the key and the
     * counter. Idempotent.
     */
    fun ensureGrant(subscription: Vpn5gSubscriptionStatus.Vpn5gUserSubscription?) {
        try {
            val key = Vpn5gVolume.grantKeyFor(subscription)
            val stored = MmkvManager.decodeSettingsString(KEY_VOLUME_GRANT_KEY)
                ?.takeIf { it.isNotBlank() }
            if (stored == key) return
            MmkvManager.encodeSettings(KEY_VOLUME_GRANT_KEY, key ?: "")
            MmkvManager.encodeSettings(KEY_VOLUME_CONSUMED_BYTES, 0L)
        } catch (_: Exception) {
            // A failed counter reset must never block a redeem/refresh.
        }
    }

    /**
     * Adds one interval of real tunnel bytes to the current grant's
     * counter. No-ops when no grant key is stored (free tier) or the
     * interval is empty — the daemon calls this every ~1 s while the
     * tunnel runs, so a no-op here is the normal free-tier path.
     */
    fun addIntervalBytes(intervalBytes: Long) {
        try {
            val key = MmkvManager.decodeSettingsString(KEY_VOLUME_GRANT_KEY)
                ?.takeIf { it.isNotBlank() } ?: return
            val current = Vpn5gVolume.VolumeCounter(
                grantKey = key,
                consumedBytes = consumedBytes(),
            )
            val next = Vpn5gVolume.accumulate(current, key, intervalBytes)
            MmkvManager.encodeSettings(KEY_VOLUME_CONSUMED_BYTES, next.consumedBytes)
        } catch (_: Exception) {
            // Metering must never crash the notification job.
        }
    }

    /** Clears the grant key and the counter (sign-out). */
    fun clear() {
        try {
            MmkvManager.encodeSettings(KEY_VOLUME_GRANT_KEY, "")
            MmkvManager.encodeSettings(KEY_VOLUME_CONSUMED_BYTES, 0L)
        } catch (_: Exception) {
            // Best effort only.
        }
    }
}
