package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_REDEEMED_CODES_JSON
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil

/**
 * Discount / gift code redemption (v1.1).
 *
 * Codes are created and managed by the ADMIN through the Telegram bot
 * (@VPN5G_IRBot): the bot publishes the valid list in app_config.json
 * ("codes": [{code, type, discount_percent | gift_days, max_uses,
 * remaining_uses}]) and decrements remaining_uses when a code is redeemed.
 * NO codes are hardcoded in the app.
 *
 * What the app does:
 *  - validates the entered code against the last fetched list (case-insensitive,
 *    trimmed): unknown code, exhausted code (remaining_uses == 0), and
 *    already-redeemed-on-this-device are all rejected with a reason;
 *  - records a successful redemption locally (MMKV JSON list) so the same
 *    code cannot be redeemed twice on this device;
 *  - returns the bot deep link the user opens so the bot can apply the
 *    benefit centrally and decrement remaining_uses (the bot is the
 *    authority for subscriptions; the app never mints one by itself).
 *
 * What the bot side still needs (see docs/app_config.md): admin commands to
 * add/edit/remove codes in app_config.json, a /start=redeem_<CODE> handler
 * that validates the code again server-side, applies the discount/gift, and
 * decrements remaining_uses.
 */
object Vpn5gPromoCodes {

    sealed interface Validation {
        /** The code is valid and may be redeemed. */
        data class Valid(val code: PromoCodeRemote) : Validation

        /** The code is not in the fetched list (or no list was fetched). */
        data object UnknownCode : Validation

        /** The code exists but its uses are exhausted. */
        data object Exhausted : Validation

        /** This device already redeemed this code. */
        data object AlreadyRedeemed : Validation

        /** The code entry itself is malformed (no usable benefit). */
        data object InvalidEntry : Validation
    }

    data class Redemption(
        val code: String,
        val type: String,
        val redeemedAtEpochMillis: Long,
    )

    /** Validates [rawInput] against the fetched code list. Never throws. */
    fun validate(rawInput: String, codes: List<PromoCodeRemote>?): Validation {
        val input = rawInput.trim().uppercase()
        if (input.isEmpty()) return Validation.UnknownCode
        if (codes.isNullOrEmpty()) return Validation.UnknownCode
        val entry = codes.firstOrNull { it.code?.trim()?.uppercase() == input }
            ?: return Validation.UnknownCode
        val remaining = entry.remaining_uses
        if (remaining != null && remaining <= 0) return Validation.Exhausted
        if (isRedeemedLocally(input)) return Validation.AlreadyRedeemed
        val type = entry.type?.trim()?.lowercase()
        val usable = when (type) {
            "discount" -> (entry.discount_percent ?: 0) in 1..100
            "gift" -> (entry.gift_days ?: 0) > 0
            else -> false
        }
        if (!usable) return Validation.InvalidEntry
        return Validation.Valid(entry)
    }

    /** Records a redemption locally and returns the bot deep link to confirm it. */
    fun redeem(code: PromoCodeRemote): String {
        val normalized = code.code?.trim()?.uppercase().orEmpty()
        val stored = redeemedLocally().toMutableList()
        if (stored.none { it.code == normalized }) {
            stored.add(
                Redemption(
                    code = normalized,
                    type = code.type?.trim()?.lowercase().orEmpty(),
                    redeemedAtEpochMillis = System.currentTimeMillis(),
                )
            )
            saveRedeemed(stored)
        }
        // The bot applies the benefit and decrements remaining_uses.
        return "https://t.me/VPN5G_IRBot?start=redeem_$normalized"
    }

    fun isRedeemedLocally(rawInput: String): Boolean {
        val input = rawInput.trim().uppercase()
        return redeemedLocally().any { it.code == input }
    }

    fun redeemedLocally(): List<Redemption> {
        val json = MmkvManager.decodeSettingsString(KEY_REDEEMED_CODES_JSON)
        if (json.isNullOrBlank()) return emptyList()
        return try {
            JsonUtil.fromJsonSafe(json, Array<Redemption>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gPromoCodes: redeemed list parse failed", e)
            emptyList()
        }
    }

    private fun saveRedeemed(list: List<Redemption>) {
        MmkvManager.encodeSettings(KEY_REDEEMED_CODES_JSON, JsonUtil.toJson(list))
    }
}
