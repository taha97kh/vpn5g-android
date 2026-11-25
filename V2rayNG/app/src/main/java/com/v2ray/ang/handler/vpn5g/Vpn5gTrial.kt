package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.GIFT_TIER_PLAN_ID
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_TRIAL_JSON
import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus.Vpn5gUserSubscription
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil

/**
 * Free trial (v1.1.26, remote-control contract v1).
 *
 * The bot only DECLARES the trial (`trial_enabled` / `trial_hours`
 * in app_config.json); the start/end logic is the app's. Rules:
 *  - one trial per account (normalized email), persisted in MMKV —
 *    sign-out never clears it, so it cannot be farmed by re-login;
 *  - the trial starts on the first subscription refresh where the
 *    trial is enabled, the user is signed in, and no other active
 *    grant (subscription or gift) exists;
 *  - while running it grants the GIFT tier (top-100 pool, no
 *    30-minute cutoff) — the same record shape as a gift redemption,
 *    so every existing tier/lock/volume path applies unchanged;
 *  - `trial_enabled=false` means no trial: a running trial grant is
 *    ignored while disabled (the record stays, so re-enabling
 *    resumes the same window — it never extends it).
 */
object Vpn5gTrial {

    /** One account's trial window. */
    data class TrialRecord(
        val email: String? = null,
        val startedAtEpochSeconds: Long? = null,
        val hours: Int? = null,
    )

    /** End of the trial window (epoch seconds), or null when incomplete. Pure. */
    fun trialEndEpochSeconds(record: TrialRecord?): Long? {
        val start = record?.startedAtEpochSeconds ?: return null
        val hours = record.hours ?: return null
        return start + Vpn5gRemoteCommands.clampTrialHours(hours) * 3_600L
    }

    /**
     * The trial as a subscription record (GIFT tier) when it is
     * enabled and still running at [nowEpochSeconds]; null otherwise
     * (disabled, expired, or no record). Pure, JVM-tested.
     */
    fun trialGrant(
        record: TrialRecord?,
        enabled: Boolean,
        nowEpochSeconds: Long,
    ): Vpn5gUserSubscription? {
        if (!enabled || record == null) return null
        val end = trialEndEpochSeconds(record) ?: return null
        if (end <= nowEpochSeconds) return null
        return Vpn5gUserSubscription(
            status = "active",
            expiresAtEpochSeconds = end,
            plan = GIFT_TIER_PLAN_ID,
        )
    }

    /**
     * Whether a trial should start now: enabled, signed in, this
     * account never had one, and no other active grant covers the
     * user (a subscriber/gift user must not burn their trial).
     * Pure, JVM-tested.
     */
    fun shouldStartTrial(
        enabled: Boolean,
        email: String?,
        existing: TrialRecord?,
        hasActiveGrant: Boolean,
    ): Boolean {
        if (!enabled || hasActiveGrant) return false
        if (email.isNullOrBlank()) return false
        return existing == null
    }

    // ---- Persistence (MMKV, JSON map email → record) ----------------

    fun loadRecords(): Map<String, TrialRecord> {
        val json = MmkvManager.decodeSettingsString(KEY_TRIAL_JSON)
        return decodeRecords(json)
    }

    fun loadRecord(email: String?): TrialRecord? {
        val key = email?.let { Vpn5gEmailPolicy.normalize(it) }?.ifEmpty { null }
            ?: return null
        return loadRecords()[key]
    }

    fun saveRecord(record: TrialRecord) {
        val key = record.email?.let { Vpn5gEmailPolicy.normalize(it) }
            ?.ifEmpty { null } ?: return
        val updated = loadRecords() + (key to record.copy(email = key))
        MmkvManager.encodeSettings(KEY_TRIAL_JSON, encodeRecords(updated))
    }

    /** Encodes the record map as JSON. Pure, JVM-tested. */
    fun encodeRecords(records: Map<String, TrialRecord>): String =
        JsonUtil.toJson(records)

    /** Decodes the stored JSON map; anything unparsable is empty. Pure. */
    fun decodeRecords(json: String?): Map<String, TrialRecord> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val type = object :
                com.google.gson.reflect.TypeToken<Map<String, TrialRecord>>() {}.type
            com.google.gson.Gson().fromJson<Map<String, TrialRecord>>(json, type)
                ?.filterKeys { it.isNotBlank() }
                .orEmpty()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gTrial: record parse failed", e)
            emptyMap()
        }
    }

    /**
     * The trial grant for [email] right now, starting the trial when
     * [shouldStartTrial] says so. Called from the subscription
     * refresh merge; [hasActiveGrant] is whether row/RPC/redeemed
     * already give an active tier. Never throws.
     */
    fun grantFor(
        email: String?,
        enabled: Boolean,
        hours: Int?,
        hasActiveGrant: Boolean,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): Vpn5gUserSubscription? {
        val normalized = email?.let { Vpn5gEmailPolicy.normalize(it) }
            ?.ifEmpty { null } ?: return null
        var record = loadRecord(normalized)
        if (shouldStartTrial(enabled, normalized, record, hasActiveGrant)) {
            record = TrialRecord(
                email = normalized,
                startedAtEpochSeconds = nowEpochSeconds,
                hours = Vpn5gRemoteCommands.clampTrialHours(hours),
            )
            runCatching { saveRecord(record) }
                .onFailure {
                    LogUtil.e(AppConfig.TAG, "Vpn5gTrial: save failed", it)
                }
        }
        return trialGrant(record, enabled, nowEpochSeconds)
    }
}
