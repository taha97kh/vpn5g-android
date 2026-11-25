package com.v2ray.ang.handler.vpn5g

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_EXECUTED_COMMAND_IDS_JSON
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Remote commands from the bot (v1.1.26, remote-control contract v1).
 *
 * The bot publishes admin commands in app_config.json under
 * `commands` (an array that always mirrors the bot's pending list).
 * The app executes each command EXACTLY ONCE (ids persisted in
 * MMKV), skips expired ones, ignores unknown types, runs them in
 * array order, and asks the user for no confirmation (the admin
 * issued them). A failed config fetch executes nothing new — the
 * caller only plans from a successfully fetched config (or the
 * cached one at startup, where execute-once makes it safe).
 *
 * This file is the pure planner + the executed-id store. The actual
 * effects (refresh, dialogs, logout…) live in the ViewModel, which
 * owns the UI state they touch.
 */
object Vpn5gRemoteCommands {

    /** What one planned command will do. */
    sealed interface Action {
        /** Re-read the subscription from the server (own account). */
        data object RefreshSubscription : Action

        /** Show a dialog with this title/text (once). */
        data class ShowMessage(val title: String, val text: String) : Action

        /** Clear local caches (never login/subscription data). */
        data object ClearCache : Action

        /** Forced-update screen; [version] null = use min_version. */
        data class ForceUpdate(val version: String?) : Action

        /** Re-read app_config.json immediately. */
        data object ReloadConfig : Action

        /** Set one known local setting from the payload value. */
        data class SetConfig(val key: String, val value: JsonElement?) : Action

        /** Sign the current user out. */
        data object Logout : Action
    }

    /** One command ready to run, with its resolved [action]. */
    data class PlannedCommand(
        val command: RemoteCommandRemote,
        val action: Action,
    )

    /** Command types this build understands (contract v1). */
    val KNOWN_TYPES = setOf(
        "refresh_subscription",
        "show_message",
        "clear_cache",
        "force_update",
        "reload_config",
        "set_config",
        "logout",
    )

    /**
     * Keys a `set_config` command may set (contract v1 raw-edit
     * allowlist). Anything else is ignored (and logged by the
     * caller) — codes/plans/ads/announcement/commands have their
     * own publishers and are never set this way.
     */
    val SET_CONFIG_KEYS = setOf(
        "notice",
        "min_version",
        "subscription_status",
        "payment_gateway",
        "trial_enabled",
        "trial_hours",
        "config_sources",
    )

    /** Most executed ids kept; oldest are pruned beyond this. */
    const val MAX_EXECUTED_IDS = 200

    /**
     * Parses a command timestamp to epoch millis. The bot publishes
     * UTC "YYYY-MM-DD HH:MM:SS" (space separator); ISO-8601 with T
     * (with Z or an offset, optional fraction) is accepted too.
     * Null when unparsable. Pure, JVM-tested. `java.time` is safe
     * here: the app enables core library desugaring.
     */
    fun parseTimeToEpochMs(raw: String?): Long? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // ISO-8601 instant / offset forms first.
        runCatching { return Instant.parse(text).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        // Bot shape: "2026-11-25 14:05:00" in UTC.
        runCatching {
            return LocalDateTime.parse(
                text,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            ).toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        // ISO local date-time without offset: treat as UTC.
        runCatching {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        return null
    }

    /**
     * True when the command's expiry has passed at [nowMs]. A
     * command with no parsable expiry is NOT treated as expired
     * (the bot always publishes one; a malformed value must not
     * silently kill an admin command — execute-once still bounds
     * it). Pure, JVM-tested.
     */
    fun isExpired(command: RemoteCommandRemote, nowMs: Long): Boolean {
        val expiresAt = parseTimeToEpochMs(command.expires_at) ?: return false
        return expiresAt <= nowMs
    }

    /**
     * The email a command applies to: the payload email when given
     * (normalized), otherwise the currently signed-in user
     * (normalized). Null when neither exists. Pure, JVM-tested.
     */
    fun resolveEmail(payloadEmail: String?, currentEmail: String?): String? {
        val explicit = payloadEmail?.let { Vpn5gEmailPolicy.normalize(it) }.orEmpty()
        if (explicit.isNotEmpty()) return explicit
        val current = currentEmail?.let { Vpn5gEmailPolicy.normalize(it) }.orEmpty()
        return current.ifEmpty { null }
    }

    /**
     * True when [installed] is below the published [minVersion]
     * (contract v1: numeric part-by-part compare, never string
     * compare — "1.1.9" < "1.1.24"). A null/blank/unparsable
     * min_version means no force. Pure, JVM-tested.
     */
    fun isBelowMinVersion(installed: String?, minVersion: String?): Boolean {
        if (minVersion.isNullOrBlank()) return false
        return Vpn5gAppUpdate.isNewerVersion(minVersion, installed)
    }

    /** Trial length clamped to the contract range 1–720 h (default 24). Pure. */
    fun clampTrialHours(hours: Int?): Int = (hours ?: 24).coerceIn(1, 720)

    /**
     * Plans the commands to run now, in array order (oldest first).
     * Skips: blank id, already-executed id, expired, unknown type,
     * and user-targeted commands (refresh/logout) addressed to a
     * DIFFERENT account than the signed-in one — those are left
     * unmarked so they can still apply if that account signs in
     * before expiry. Pure, JVM-tested.
     */
    fun plan(
        commands: List<RemoteCommandRemote>?,
        executedIds: Set<String>,
        nowMs: Long,
        currentEmail: String?,
    ): List<PlannedCommand> {
        if (commands.isNullOrEmpty()) return emptyList()
        val current = currentEmail?.let { Vpn5gEmailPolicy.normalize(it) }?.ifEmpty { null }
        val planned = mutableListOf<PlannedCommand>()
        for (command in commands) {
            val id = command.id?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (id in executedIds) continue
            if (isExpired(command, nowMs)) continue
            val type = command.type?.trim()?.lowercase() ?: continue
            if (type !in KNOWN_TYPES) continue
            val payload = command.payload
            val action: Action = when (type) {
                "refresh_subscription" -> {
                    val target = resolveEmail(payload.stringField("email"), current)
                    if (target == null || target != current) continue
                    Action.RefreshSubscription
                }
                "show_message" -> {
                    val text = payload.stringField("text")?.takeIf { it.isNotBlank() }
                        ?: continue
                    Action.ShowMessage(
                        title = payload.stringField("title")?.trim().orEmpty(),
                        text = text.trim(),
                    )
                }
                "clear_cache" -> Action.ClearCache
                "force_update" -> Action.ForceUpdate(
                    version = payload.stringField("version")?.trim()?.ifEmpty { null },
                )
                "reload_config" -> Action.ReloadConfig
                "set_config" -> {
                    val key = payload.stringField("key")?.trim()?.lowercase()
                        ?: continue
                    if (key !in SET_CONFIG_KEYS) continue
                    Action.SetConfig(key = key, value = payload?.get("value"))
                }
                "logout" -> {
                    val target = resolveEmail(payload.stringField("email"), current)
                    if (target == null || target != current) continue
                    Action.Logout
                }
                else -> continue
            }
            planned += PlannedCommand(command, action)
        }
        return planned
    }

    /** A string field of the payload, or null (absent / JSON null / non-string). */
    private fun JsonObject?.stringField(name: String): String? {
        val element = this?.get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
        return element.asString
    }

    // ---- Executed-id store (MMKV) -----------------------------------

    /** Encodes executed ids as a JSON array string. Pure, JVM-tested. */
    fun encodeIds(ids: Collection<String>): String =
        JsonUtil.toJson(ids.toList())

    /** Decodes the stored JSON array; anything unparsable is empty. Pure. */
    fun decodeIds(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val parsed = JsonUtil.fromJsonSafe(json, Array<String>::class.java)
            parsed?.filter { it.isNotBlank() }?.toSet().orEmpty()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gRemoteCommands: id store parse failed", e)
            emptySet()
        }
    }

    /** Adds [id] keeping at most [MAX_EXECUTED_IDS] (oldest pruned). Pure. */
    fun addExecuted(ids: Collection<String>, id: String): List<String> {
        val merged = (ids + id).distinct()
        return if (merged.size <= MAX_EXECUTED_IDS) {
            merged
        } else {
            merged.takeLast(MAX_EXECUTED_IDS)
        }
    }

    fun loadExecutedIds(): Set<String> =
        decodeIds(MmkvManager.decodeSettingsString(KEY_EXECUTED_COMMAND_IDS_JSON))

    fun markExecuted(id: String) {
        val updated = addExecuted(loadExecutedIds(), id)
        MmkvManager.encodeSettings(KEY_EXECUTED_COMMAND_IDS_JSON, encodeIds(updated))
    }
}
