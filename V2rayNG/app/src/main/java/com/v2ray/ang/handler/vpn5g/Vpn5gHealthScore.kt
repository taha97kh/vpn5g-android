package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlin.math.pow

/**
 * Health-score memory (v1.1.24, Lantern-inspired): a per-server
 * success/failure history keyed by server identity
 * ([Vpn5gServerValidator.dedupeKey], stable across refreshes while
 * GUIDs are re-issued). The auto picker ranks by health-adjusted
 * ping instead of last ping alone: a server that carried a real
 * tunnel before beats an equally fast stranger, and a server that
 * keeps failing sinks even when its TCP ping looks good.
 *
 * Scores decay toward zero with a 24 h half-life
 * ([Vpn5gConstants.HEALTH_HALF_LIFE_MS]) so old evidence fades — a
 * server good last week does not win forever, and a server that was
 * dead yesterday can recover. One verified tunnel = +1, one failed
 * verification / failed real probe = -1.5 (failures weigh more:
 * they cost the user a dead connect attempt). Scores are clamped
 * to [[Vpn5gConstants.HEALTH_SCORE_MIN], [Vpn5gConstants.HEALTH_SCORE_MAX]].
 *
 * The pure functions are unit tested; [load]/[save]/[record] are the
 * only MMKV touch points. Stored as a small JSON map
 * dedupeKey → {score, successes, failures, updatedAtMs} under
 * [Vpn5gConstants.KEY_HEALTH_SCORES_JSON], pruned to
 * [Vpn5gConstants.HEALTH_MAX_ENTRIES] newest-updated entries.
 */
object Vpn5gHealthScore {

    data class HealthEntry(
        val score: Double = 0.0,
        val successes: Int = 0,
        val failures: Int = 0,
        val updatedAtMs: Long = 0L,
    )

    const val SUCCESS_DELTA = 1.0
    const val FAILURE_DELTA = -1.5

    /** Score after decaying [entry] to [nowMs] (pure). */
    fun effectiveScore(entry: HealthEntry, nowMs: Long): Double {
        if (entry.updatedAtMs <= 0L || nowMs <= entry.updatedAtMs) return entry.score
        val halfLives = (nowMs - entry.updatedAtMs).toDouble() /
            Vpn5gConstants.HEALTH_HALF_LIFE_MS.toDouble()
        return entry.score * 0.5.pow(halfLives)
    }

    /** Decayed score per dedupe key at [nowMs] (pure). */
    fun scoresAt(entries: Map<String, HealthEntry>, nowMs: Long): Map<String, Double> =
        entries.mapValues { (_, entry) -> effectiveScore(entry, nowMs) }

    /** Record one outcome for [key] at [nowMs] (pure). */
    fun record(
        entries: Map<String, HealthEntry>,
        key: String,
        success: Boolean,
        nowMs: Long,
    ): Map<String, HealthEntry> {
        if (key.isBlank()) return entries
        val old = entries[key] ?: HealthEntry(updatedAtMs = nowMs)
        val decayed = effectiveScore(old, nowMs)
        val next = (decayed + if (success) SUCCESS_DELTA else FAILURE_DELTA)
            .coerceIn(Vpn5gConstants.HEALTH_SCORE_MIN, Vpn5gConstants.HEALTH_SCORE_MAX)
        val updated = old.copy(
            score = next,
            successes = old.successes + if (success) 1 else 0,
            failures = old.failures + if (success) 0 else 1,
            updatedAtMs = nowMs,
        )
        return prune(entries + (key to updated))
    }

    /** Keep at most [Vpn5gConstants.HEALTH_MAX_ENTRIES] newest entries (pure). */
    fun prune(entries: Map<String, HealthEntry>): Map<String, HealthEntry> {
        if (entries.size <= Vpn5gConstants.HEALTH_MAX_ENTRIES) return entries
        return entries.entries
            .sortedByDescending { it.value.updatedAtMs }
            .take(Vpn5gConstants.HEALTH_MAX_ENTRIES)
            .associate { it.key to it.value }
    }

    /**
     * Health-adjusted ping for ranking (pure): each health point
     * shaves [Vpn5gConstants.HEALTH_PING_BONUS_MS] off the measured
     * ping, so a proven 300 ms server (score 3 → 180) outranks an
     * unproven 250 ms stranger, while a failing server (score -3)
     * is pushed back by 120 ms. Never below 1 ms.
     */
    fun adjustedPing(pingMillis: Long, score: Double): Long {
        val adjusted = pingMillis - (score * Vpn5gConstants.HEALTH_PING_BONUS_MS).toLong()
        return adjusted.coerceAtLeast(1L)
    }

    fun load(): Map<String, HealthEntry> {
        return try {
            val raw = MmkvManager.decodeSettingsString(Vpn5gConstants.KEY_HEALTH_SCORES_JSON)
                ?: return emptyMap()
            @Suppress("UNCHECKED_CAST")
            val parsed = JsonUtil.fromJson(raw, Map::class.java) as? Map<String, Any>
                ?: return emptyMap()
            parsed.mapNotNull { (key, value) ->
                val fields = value as? Map<*, *> ?: return@mapNotNull null
                key to HealthEntry(
                    score = (fields["score"] as? Number)?.toDouble() ?: 0.0,
                    successes = (fields["successes"] as? Number)?.toInt() ?: 0,
                    failures = (fields["failures"] as? Number)?.toInt() ?: 0,
                    updatedAtMs = (fields["updatedAtMs"] as? Number)?.toLong() ?: 0L,
                )
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(entries: Map<String, HealthEntry>) {
        try {
            MmkvManager.encodeSettings(
                Vpn5gConstants.KEY_HEALTH_SCORES_JSON,
                JsonUtil.toJson(entries),
            )
        } catch (e: Exception) {
            LogUtil.e("Vpn5gHealthScore", "Failed to persist health scores", e)
        }
    }

    /** Load, record one outcome, save. Best effort, never throws. */
    fun recordOutcome(key: String, success: Boolean, nowMs: Long = System.currentTimeMillis()) {
        if (key.isBlank()) return
        runCatching { save(record(load(), key, success, nowMs)) }
            .onFailure { e -> LogUtil.e("Vpn5gHealthScore", "recordOutcome failed", e) }
    }

    /** Decayed scores ready for the picker. Never throws. */
    fun currentScores(nowMs: Long = System.currentTimeMillis()): Map<String, Double> =
        runCatching { scoresAt(load(), nowMs) }.getOrDefault(emptyMap())
}
