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
 *    benefit centrally and decrement remaining_uses. v1.1.12: for GIFT
 *    codes the app ALSO activates the subscription locally at once
 *    ([activateGift]) and the bot link is only an optional step for
 *    the server-side decrement; for DISCOUNT codes the bot remains the
 *    authority (the app never mints a discount by itself).
 *
 * v1.1.12 — in-app gift activation: a GIFT code now also activates the
 * subscription immediately inside the app ([activateGift]): the moment
 * the code verifies, the plan + gift_days are saved locally as the
 * redeemed subscription and the UI treats the user as a subscriber at
 * once — no admin approval, no second confirm screen, no waiting.
 * Discount codes still go through the bot (there is no subscription to
 * activate for them). The bot deep link is still returned, as an
 * OPTIONAL step, so the bot can decrement remaining_uses server-side;
 * the app cannot write app_config.json, so without that step the code
 * stays redeemable in the published list until the bot republishes.
 *
 * Ownership — honest limit: a published code entry carries `user_id`
 * (the Telegram id it was issued for), but the app has NO Telegram
 * identity for the signed-in user (it knows only the email + Supabase
 * auth UUID, and the vpn5g_registrations table has no Telegram-id
 * column), so the app CANNOT prove the code's `user_id` belongs to
 * the signed-in user. What it enforces instead: redeeming requires a
 * signed-in email, and the redemption is recorded with that email —
 * a different account on this device cannot reuse the code
 * ([Validation.NotYours]). True per-person binding rests on the code
 * being single-use and delivered only to its owner, plus the bot
 * decrementing remaining_uses.
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

        /** The code's expires_at is in the past (v1.1.12). */
        data object Expired : Validation

        /** The code was already redeemed on this device by a different signed-in email (v1.1.12). */
        data object NotYours : Validation

        /** Redeeming requires a signed-in email first (v1.1.12). */
        data object NotSignedIn : Validation
    }

    data class Redemption(
        val code: String,
        val type: String,
        val redeemedAtEpochMillis: Long,
        /** Email that redeemed the code; null for pre-v1.1.12 records. */
        val email: String? = null,
        /** Plan id the gift granted, when known. */
        val plan: String? = null,
        /** Expiry the gift granted (epoch seconds), when known. */
        val expiresAtEpochSeconds: Long? = null,
    )

    /**
     * Pure decision over one matched entry (no I/O; covered by JVM unit
     * tests in Vpn5gPromoCodesTest). [alreadyRedeemed] is whether this
     * device already redeemed the code.
     */
    fun validateEntry(entry: PromoCodeRemote, alreadyRedeemed: Boolean): Validation {
        val remaining = entry.remaining_uses
        if (remaining != null && remaining <= 0) return Validation.Exhausted
        if (alreadyRedeemed) return Validation.AlreadyRedeemed
        val type = entry.type?.trim()?.lowercase()
        val usable = when (type) {
            "discount" -> (entry.discount_percent ?: 0) in 1..100
            "gift" -> (entry.gift_days ?: 0) > 0
            else -> false
        }
        if (!usable) return Validation.InvalidEntry
        return Validation.Valid(entry)
    }

    /**
     * Full redeem decision for one matched entry (v1.1.12): the old
     * checks plus sign-in, per-email ownership and expiry. Pure except
     * for the expiry parse (no I/O); covered by JVM unit tests.
     * [signedInEmail] is null/blank when nobody is signed in.
     * [redeemedByOtherEmail] is true when this device already redeemed
     * the code under a different email. A legacy redemption record with
     * no email counts as [alreadyRedeemed], not as another owner.
     */
    fun validateEntryRedeem(
        entry: PromoCodeRemote,
        alreadyRedeemed: Boolean,
        redeemedByOtherEmail: Boolean,
        nowEpochSeconds: Long,
        signedInEmail: String?,
    ): Validation {
        if (signedInEmail.isNullOrBlank()) return Validation.NotSignedIn
        if (redeemedByOtherEmail) return Validation.NotYours
        if (alreadyRedeemed) return Validation.AlreadyRedeemed
        val expiresRaw = entry.expires_at?.takeIf { it.isNotBlank() }
        if (expiresRaw != null) {
            val expiresAt = Vpn5gSubscriptionStatus.parseIsoToEpoch(expiresRaw)
                ?: return Validation.InvalidEntry
            if (expiresAt <= nowEpochSeconds) return Validation.Expired
        }
        return validateEntry(entry, alreadyRedeemed = false)
    }

    /**
     * Validates [rawInput] against the fetched code list for the
     * in-app redeem flow (v1.1.12): unknown code, sign-in, ownership,
     * expiry, exhaustion and local re-redeem. Reads the local
     * redemption list from MMKV. Never throws.
     */
    fun validateForRedeem(
        rawInput: String,
        codes: List<PromoCodeRemote>?,
        nowEpochSeconds: Long,
        signedInEmail: String?,
    ): Validation {
        val input = rawInput.trim().uppercase()
        if (input.isEmpty()) return Validation.UnknownCode
        if (codes.isNullOrEmpty()) return Validation.UnknownCode
        val entry = codes.firstOrNull { it.code?.trim()?.uppercase() == input }
            ?: return Validation.UnknownCode
        val redemption = redemptionFor(input)
        val redeemedByOther = redemption?.email
            ?.takeIf { it.isNotBlank() }
            ?.let { it.trim().lowercase() != signedInEmail?.trim()?.lowercase() }
            ?: false
        val alreadyRedeemed = redemption != null && !redeemedByOther
        return validateEntryRedeem(
            entry = entry,
            alreadyRedeemed = alreadyRedeemed,
            redeemedByOtherEmail = redeemedByOther,
            nowEpochSeconds = nowEpochSeconds,
            signedInEmail = signedInEmail,
        )
    }

    /**
     * Expiry a gift code grants (epoch seconds), or null when the entry
     * is not a gift with days. Pure, JVM-tested.
     */
    fun activationExpiry(entry: PromoCodeRemote, nowEpochSeconds: Long): Long? {
        if (entry.type?.trim()?.lowercase() != "gift") return null
        val days = entry.gift_days ?: return null
        if (days <= 0) return null
        return nowEpochSeconds + days * 86_400L
    }

    /**
     * Activates a gift code in the app, immediately (v1.1.12): records
     * the redemption for [email] and saves the granted subscription
     * locally, so the UI treats the user as a subscriber at once — no
     * admin approval, no second confirm screen, no waiting. Returns the
     * granted subscription plus the bot deep link (optional: only so the
     * bot can decrement remaining_uses server-side), or null when the
     * entry is not a gift with days (discount codes still go through
     * the bot). Never throws.
     */
    fun activateGift(
        entry: PromoCodeRemote,
        email: String,
        nowEpochSeconds: Long,
    ): Pair<Vpn5gSubscriptionStatus.Vpn5gUserSubscription, String>? {
        val expiresAt = activationExpiry(entry, nowEpochSeconds) ?: return null
        val plan = entry.plan?.takeIf { it.isNotBlank() } ?: "gift"
        val subscription = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
            status = "active",
            expiresAtEpochSeconds = expiresAt,
            plan = plan,
        )
        Vpn5gSubscriptionStatus.saveRedeemed(subscription)
        val link = redeem(entry, email = email, expiresAtEpochSeconds = expiresAt)
        return subscription to link
    }

    /** Records a redemption locally and returns the bot deep link to confirm it. */
    fun redeem(
        code: PromoCodeRemote,
        email: String? = null,
        expiresAtEpochSeconds: Long? = null,
    ): String {
        val normalized = code.code?.trim()?.uppercase().orEmpty()
        val stored = redeemedLocally().toMutableList()
        if (stored.none { it.code == normalized }) {
            stored.add(
                Redemption(
                    code = normalized,
                    type = code.type?.trim()?.lowercase().orEmpty(),
                    redeemedAtEpochMillis = System.currentTimeMillis(),
                    email = email?.takeIf { it.isNotBlank() },
                    plan = code.plan?.takeIf { it.isNotBlank() },
                    expiresAtEpochSeconds = expiresAtEpochSeconds,
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

    /** The local redemption record for [rawInput], or null when never redeemed here. */
    fun redemptionFor(rawInput: String): Redemption? {
        val input = rawInput.trim().uppercase()
        if (input.isEmpty()) return null
        return redeemedLocally().firstOrNull { it.code == input }
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
