package com.v2ray.ang.handler.vpn5g

import java.util.Locale

/**
 * Volume metering (v1.1.18, Model B — Aref, 2026-11-25).
 *
 * - The CAP of a plan comes from the server plan config
 *   (`volume_gb`, alias `gb`, in app_config.json — set by the admin,
 *   never invented by the app). Null/absent = no cap (unlimited).
 * - CONSUMPTION is measured on-device from real tunnel bytes: the
 *   :daemon notification speed job is the single querier of the
 *   native stats, and the native counters reset on every query, so
 *   each query's value IS the real bytes of that interval — the
 *   daemon accumulates those interval bytes into
 *   [Vpn5gVolumeStore]. Consumption is cumulative across sessions
 *   for the current grant (never reset on app restart); a new grant
 *   (new plan/expiry) starts a new counter.
 * - A subscription is INACTIVE when consumed bytes >= cap (cap
 *   present) OR its expiry has passed — whichever comes first. The
 *   tier decisions in [Vpn5gSubscriptionStatus] take the consumed
 *   bytes and enforce exactly that.
 *
 * No server-side per-user metering exists anywhere (no Marzban/X-UI
 * panel, no traffic API) — the numbers here are the device's own
 * measurement, and the UI labels them as such. Nothing in this file
 * touches MMKV or Android APIs: pure, JVM-tested.
 */
object Vpn5gVolume {

    /** One gigabyte, in bytes (decimal GB, as the plan names use it). */
    const val GB_BYTES: Long = 1_000_000_000L

    /** Volume cap of a plan in bytes, or null when the plan has no cap. Pure. */
    fun capBytes(capGb: Double?): Long? {
        val gb = capGb ?: return null
        if (gb <= 0.0 || gb.isNaN() || gb.isInfinite()) return null
        val bytes = gb * GB_BYTES
        if (bytes > Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
        return bytes.toLong()
    }

    /**
     * Volume cap in bytes for the plan id, looked up in the published
     * plans (v1.1.18). Null when the plan id is unknown, the plan has
     * no cap field, or no plans were published — never an invented
     * number. Pure, JVM-tested.
     */
    fun capBytesForPlanId(
        planId: String?,
        plans: List<SubscriptionPlanRemote>?,
    ): Long? {
        val id = planId?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val plan = plans.orEmpty().firstOrNull {
            it.id?.trim()?.equals(id, ignoreCase = true) == true
        } ?: return null
        return capBytes(Vpn5gPlans.volumeCapGb(plan))
    }

    /**
     * True when the cap is reached (v1.1.18, Model B): consumed bytes
     * >= cap bytes. A null cap means unlimited (never exhausted); a
     * null consumed value means "not measured" (never exhausted —
     * callers must not treat unknown consumption as a reason to end
     * a subscription). Pure, JVM-tested.
     */
    fun isExhausted(consumedBytes: Long?, capBytes: Long?): Boolean {
        if (capBytes == null) return false
        if (consumedBytes == null) return false
        return consumedBytes >= capBytes
    }

    /** Remaining bytes until the cap, or null when unlimited. Never negative. Pure. */
    fun remainingBytes(consumedBytes: Long, capBytes: Long?): Long? {
        if (capBytes == null) return null
        return (capBytes - consumedBytes).coerceAtLeast(0L)
    }

    /** Fraction of the cap consumed (0..1), or null when unlimited. Pure. */
    fun usageFraction(consumedBytes: Long, capBytes: Long?): Float? {
        if (capBytes == null || capBytes <= 0L) return null
        return (consumedBytes.coerceAtLeast(0L).toFloat() / capBytes.toFloat())
            .coerceIn(0f, 1f)
    }

    /**
     * Grant key for the consumption counter (v1.1.18): the plan id +
     * expiry identify one grant. Null when there is no active grant
     * (no subscription, not "active", no plan, no expiry) — the
     * counter does not run for the free tier. Pure, JVM-tested.
     */
    fun grantKeyFor(
        subscription: Vpn5gSubscriptionStatus.Vpn5gUserSubscription?,
    ): String? {
        if (subscription?.status != "active") return null
        val plan = subscription.plan?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val expiresAt = subscription.expiresAtEpochSeconds ?: return null
        return "$plan|$expiresAt"
    }

    /**
     * Measured consumption for one grant (v1.1.18): the grant key
     * the bytes were measured under, plus the bytes. The key is what
     * makes a byte count attributable — bytes measured under grant A
     * must never end grant B.
     */
    data class ConsumedState(val grantKey: String?, val bytes: Long)

    /**
     * Consumed bytes attributable to [subscription] (v1.1.18): the
     * state's bytes when the state's grant key IS this
     * subscription's grant, null otherwise (unknown — never
     * treated as exhausted). Pure, JVM-tested.
     */
    fun bytesFor(
        subscription: Vpn5gSubscriptionStatus.Vpn5gUserSubscription?,
        state: ConsumedState?,
    ): Long? {
        if (state == null) return null
        if (grantKeyFor(subscription) != state.grantKey) return null
        return state.bytes.coerceAtLeast(0L)
    }

    /** One grant's consumption counter (pure state for [accumulate]). */
    data class VolumeCounter(
        val grantKey: String?,
        val consumedBytes: Long,
    )

    /**
     * Next counter after one interval of real tunnel bytes (v1.1.18).
     * No grant key = nothing to meter (counter unchanged). A different
     * grant key = a new grant: the counter restarts from this
     * interval's bytes. The add saturates instead of wrapping on a
     * very long-lived grant. Pure, JVM-tested.
     */
    fun accumulate(
        counter: VolumeCounter,
        grantKey: String?,
        intervalBytes: Long,
    ): VolumeCounter {
        if (grantKey == null) return counter
        val bytes = intervalBytes.coerceAtLeast(0L)
        if (counter.grantKey != grantKey) {
            return VolumeCounter(grantKey, bytes)
        }
        val current = counter.consumedBytes.coerceAtLeast(0L)
        val next = if (current > Long.MAX_VALUE - bytes) Long.MAX_VALUE else current + bytes
        return VolumeCounter(grantKey, next)
    }

    /**
     * Next session counter after one interval of real tunnel bytes
     * (v1.1.33): the grant-independent session total. Unlike
     * [accumulate] there is no grant key — the session counter runs
     * for every user (free tier included) so the displayed session
     * consumption is always the real measured bytes. Saturates
     * instead of wrapping. Pure, JVM-tested.
     */
    fun accumulateSession(currentBytes: Long, intervalBytes: Long): Long {
        val current = currentBytes.coerceAtLeast(0L)
        val bytes = intervalBytes.coerceAtLeast(0L)
        return if (current > Long.MAX_VALUE - bytes) Long.MAX_VALUE else current + bytes
    }

    /**
     * Gigabytes with one decimal, e.g. "12.3" (v1.1.18). The unit
     * ("گیگابایت") lives in the string resource, not here. Uses the
     * given locale (the app's fa locale renders Persian digits, like
     * the existing speed strings); tests pass [Locale.US].
     */
    fun formatGb(bytes: Long, locale: Locale = Locale.getDefault()): String {
        val gb = bytes.coerceAtLeast(0L).toDouble() / GB_BYTES
        return String.format(locale, "%.1f", gb)
    }
}
