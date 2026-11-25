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
            // v1.1.17: a gift is usable when it grants ANY duration
            // — plan days (a full-plan code) or gift hours/days.
            // A plan-only code (sub_* plan, no gift_hours/days)
            // is usable too: it grants the plan's days.
            "gift" -> grantDurationSeconds(entry) != null
            else -> false
        }
        if (!usable) return Validation.InvalidEntry
        return Validation.Valid(entry)
    }

    /**
     * Gift duration in seconds (v1.1.14): `gift_hours` wins when set
     * (the finer unit — 1 / 5 / 24 hours, 168 = 7 days), otherwise
     * `gift_days` in days. Null when the entry grants no duration.
     * Pure, JVM-tested.
     */
    fun giftDurationSeconds(entry: PromoCodeRemote): Long? {
        val hours = entry.gift_hours
        if (hours != null && hours > 0) return hours * 3_600L
        val days = entry.gift_days
        if (days != null && days > 0) return days * 86_400L
        return null
    }

    /**
     * Duration a code grants, in seconds (v1.1.17): a full-plan code
     * (plan/tier names a sub_* plan) grants that plan's DAYS — the
     * plan is the authority, so a sub_1 code grants 30 days even
     * when it also carries gift_hours 24 (the 1.1.15/1.1.16 bug: a
     * purchased sub_1 code granted only 24 hours, and a sub_3 code
     * only 24 hours instead of 90 days). A plain gift grants its
     * gift hours/days. Null when the entry grants no duration.
     * Pure, JVM-tested.
     */
    fun grantDurationSeconds(entry: PromoCodeRemote): Long? {
        val planId = entry.plan?.trim()?.lowercase()
            ?: entry.tier?.trim()?.lowercase()
        val planDays = planId?.let { Vpn5gPlans.SUB_PLAN_DAYS[it] }
        if (planDays != null && planDays > 0) return planDays * 86_400L
        return giftDurationSeconds(entry)
    }

    /**
     * Which Premium section a code belongs in (v1.1.16): gift codes and
     * purchased subscription codes have SEPARATE sections with their
     * own entry boxes, so a code typed in the wrong section is refused
     * with WRONG_SECTION instead of silently redeeming in the wrong
     * place. Pure, JVM-tested.
     *
     * - GIFT: type "gift" with no plan id and no plan tier — it grants
     *   the middle GIFT tier (hours, top-100 pool).
     * - SUBSCRIPTION: type "gift" whose `plan` or `tier` names a full
     *   plan (sub_1 / sub_3 / sub_6 / sub_12) — a purchased code.
     * - DISCOUNT: type "discount" — goes through the bot, never a
     *   section redeem.
     * - UNKNOWN: anything else (missing type, no usable benefit).
     */
    enum class CodeKind { GIFT, SUBSCRIPTION, DISCOUNT, UNKNOWN }

    fun codeKind(entry: PromoCodeRemote): CodeKind {
        return when (entry.type?.trim()?.lowercase()) {
            "discount" -> CodeKind.DISCOUNT
            "gift" -> {
                val plan = entry.plan?.trim().orEmpty()
                val tier = entry.tier?.trim().orEmpty()
                if (plan.startsWith("sub_") || tier.startsWith("sub_")) {
                    CodeKind.SUBSCRIPTION
                } else if (giftDurationSeconds(entry) != null) {
                    CodeKind.GIFT
                } else {
                    CodeKind.UNKNOWN
                }
            }
            else -> CodeKind.UNKNOWN
        }
    }

    /**
     * Finds [rawInput] in the fetched code list (case-insensitive,
     * trimmed), or null. Pure lookup — the kind pre-check in the
     * ViewModel uses this BEFORE the server RPC, so a code typed in
     * the wrong section is refused without ever marking it used
     * server-side.
     */
    fun findCode(
        rawInput: String,
        codes: List<PromoCodeRemote>?,
    ): PromoCodeRemote? {
        val input = rawInput.trim().uppercase()
        if (input.isEmpty() || codes.isNullOrEmpty()) return null
        return codes.firstOrNull { it.code?.trim()?.uppercase() == input }
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
     * NOTE (v1.1.20): the redeem flow no longer calls this — codes are
     * validated ONLY by the `validate_and_redeem_code` RPC, because the
     * published list's remaining_uses is stale the moment a code is
     * redeemed anywhere. Kept (with its tests) as the documented
     * config-list decision for reference; do not wire it back into
     * the redeem path.
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
     * is not a gift with a duration. Pure, JVM-tested. v1.1.17: the
     * duration is [grantDurationSeconds] — a full-plan code grants
     * the plan's days, not its gift_hours.
     */
    fun activationExpiry(entry: PromoCodeRemote, nowEpochSeconds: Long): Long? {
        if (entry.type?.trim()?.lowercase() != "gift") return null
        val durationSeconds = grantDurationSeconds(entry) ?: return null
        return nowEpochSeconds + durationSeconds
    }

    /**
     * Plan id a grant carries (v1.1.21): a sub_* plan id grants a
     * FULL subscription; a plan id that is in the published plans
     * (e.g. a gb_* volume plan) also grants that plan — full pool,
     * capped by its volume. Any other gift grants the middle GIFT
     * tier. [plans] is the published plan list: when it is null
     * (config not loaded) only the hardcoded sub_* ids are trusted,
     * so a malformed answer can never mint a full subscription by
     * itself. Pure.
     */
    fun grantPlanId(
        plan: String?,
        tier: String?,
        plans: List<SubscriptionPlanRemote>? = null,
    ): String {
        val candidate = plan?.takeIf { it.isNotBlank() }
            ?: tier?.takeIf { it.isNotBlank() }
        if (candidate != null) {
            val id = candidate.trim()
            if (id.lowercase() in Vpn5gPlans.SUB_PLAN_DAYS) return id
            if (plans.orEmpty().any {
                    it.id?.trim()?.equals(id, ignoreCase = true) == true
                }
            ) return id
        }
        return Vpn5gConstants.GIFT_TIER_PLAN_ID
    }

    /**
     * Plan id a gift code grants (v1.1.12; volume plans v1.1.18): a
     * sub_* plan id grants a FULL subscription; a plan id that is in
     * the published plans (e.g. a gb_* volume plan) also grants that
     * plan — full pool, capped by its volume. Any other gift grants
     * the middle GIFT tier (plan "gift"). Pure.
     */
    fun giftPlanId(
        entry: PromoCodeRemote,
        plans: List<SubscriptionPlanRemote>? = null,
    ): String {
        return grantPlanId(entry.plan, entry.tier, plans)
    }

    /**
     * The grant a published code entry derives, WITHOUT saving
     * anything (v1.1.19): the subscription the entry would activate
     * — plan id from [giftPlanId], expiry from [activationExpiry],
     * volume cap from the published plans when given. Null when the
     * entry grants nothing (a discount code, or a gift with no
     * duration). Pure (no MMKV, no config read — [plans] is passed
     * in; null means only the hardcoded sub_* ids are trusted), so
     * JVM tests can assert the derived grant directly. The redeem
     * flow uses this to recover a code the server validated but did
     * not grant (`applied:false` / stripped expires_at); the actual
     * activation still goes through [activateGift], which saves the
     * grant and records the redemption.
     */
    fun deriveGrantFromEntry(
        entry: PromoCodeRemote,
        nowEpochSeconds: Long,
        plans: List<SubscriptionPlanRemote>? = null,
    ): Vpn5gSubscriptionStatus.Vpn5gUserSubscription? {
        val expiresAt = activationExpiry(entry, nowEpochSeconds) ?: return null
        val plan = giftPlanId(entry, plans)
        return Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
            status = "active",
            expiresAtEpochSeconds = expiresAt,
            plan = plan,
            volumeCapBytes = Vpn5gVolume.capBytesForPlanId(plan, plans),
        )
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
        // v1.1.18: the plan id comes from the published plans when it
        // is one (a gb_* volume plan grants that plan, capped), and
        // the grant carries its volume cap from the start.
        // v1.1.19: the grant itself is derived by
        // [deriveGrantFromEntry]; this only saves it + records the
        // redemption.
        val plans = runCatching { Vpn5gRemoteConfig.cached().plans }.getOrNull()
        val subscription = deriveGrantFromEntry(entry, nowEpochSeconds, plans) ?: return null
        Vpn5gSubscriptionStatus.saveRedeemed(subscription)
        val link = redeem(
            entry,
            email = email,
            expiresAtEpochSeconds = subscription.expiresAtEpochSeconds,
        )
        return subscription to link
    }

    /**
     * Bot deep link for a code (v1.1.15): the user opens it so the bot
     * can confirm/apply the code centrally. Pure.
     */
    fun redeemLink(code: String): String {
        return "https://t.me/VPN5G_IRBot?start=redeem_${code.trim().uppercase()}"
    }

    /**
     * The grant a `validate_and_redeem_code` answer carries (v1.1.21):
     * the server's own expiry when present and in the future,
     * otherwise a grant re-derived from the answer's own fields — a
     * sub_* tier/plan grants that plan's days, a gb_* plan grants
     * its published days, a gift grants its `gift_hours` — so a code
     * the server validated but did not apply (`applied:false`, or a
     * stripped expiry) still grants in-app WITHOUT the code being in
     * the published list. The volume cap comes from the answer's
     * `gb_total` when present, else from the plan. A discount answer
     * grants nothing (the bot applies it). Null when the answer
     * carries no usable grant at all. Pure (no MMKV, no config read
     * — [plans] is passed in), so JVM tests can assert the derived
     * grant directly.
     */
    fun deriveGrantFromServer(
        server: Vpn5gAuthService.CodeServerResult.Validated,
        nowEpochSeconds: Long,
        plans: List<SubscriptionPlanRemote>? = null,
    ): Vpn5gSubscriptionStatus.Vpn5gUserSubscription? {
        val tier = server.tier?.trim()?.lowercase()
        if (tier == "discount") return null
        val plan = grantPlanId(server.plan, server.tier, plans)
        val serverExpiry = server.expiresAtEpochSeconds
        val expiresAt = if (serverExpiry != null && serverExpiry > nowEpochSeconds) {
            serverExpiry
        } else {
            val durationSeconds = grantDurationFromServer(server, plan, plans)
                ?: return null
            nowEpochSeconds + durationSeconds
        }
        val capBytes = server.gbTotal
            ?.takeIf { it > 0 }
            ?.let { Vpn5gVolume.capBytes(it.toDouble()) }
            ?: Vpn5gVolume.capBytesForPlanId(plan, plans)
        return Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
            status = "active",
            expiresAtEpochSeconds = expiresAt,
            plan = plan,
            volumeCapBytes = capBytes,
        )
    }

    /**
     * Duration a `Validated` answer grants, in seconds (v1.1.21): a
     * sub_* plan grants that plan's days — the plan is the authority,
     * so a sub_1 code grants 30 days even when it also carries
     * gift_hours 24; a gb_* plan grants its published days;
     * otherwise the answer's `gift_hours`. Null when the answer
     * grants no duration. Pure.
     */
    private fun grantDurationFromServer(
        server: Vpn5gAuthService.CodeServerResult.Validated,
        plan: String,
        plans: List<SubscriptionPlanRemote>? = null,
    ): Long? {
        val planDays = Vpn5gPlans.SUB_PLAN_DAYS[plan.lowercase()]
        if (planDays != null && planDays > 0) return planDays * 86_400L
        val published = plans.orEmpty().firstOrNull {
            it.id?.trim()?.equals(plan, ignoreCase = true) == true
        }
        val publishedDays = published?.let { Vpn5gPlans.planDays(it) }
        if (publishedDays != null && publishedDays > 0) return publishedDays * 86_400L
        val hours = server.giftHours
        if (hours != null && hours > 0) return hours * 3_600L
        return null
    }

    /**
     * Persists a server-validated grant locally and records the
     * redemption for [email] (v1.1.21): the subscription cache and
     * the local redemption record, shared by [activateServerGift]
     * and [activateRpcGrant] so both paths persist exactly the same
     * way. Never throws.
     */
    private fun saveGrant(
        code: String,
        subscription: Vpn5gSubscriptionStatus.Vpn5gUserSubscription,
        email: String,
    ) {
        Vpn5gSubscriptionStatus.saveRedeemed(subscription)
        val normalized = code.trim().uppercase()
        val stored = redeemedLocally().toMutableList()
        if (stored.none { it.code == normalized }) {
            stored.add(
                Redemption(
                    code = normalized,
                    type = "gift",
                    redeemedAtEpochMillis = System.currentTimeMillis(),
                    email = email.takeIf { it.isNotBlank() },
                    plan = subscription.plan,
                    expiresAtEpochSeconds = subscription.expiresAtEpochSeconds,
                )
            )
            saveRedeemed(stored)
        }
    }

    /**
     * Activates the grant a `validate_and_redeem_code` answer
     * carries (v1.1.21): the grant is derived from the answer
     * itself — its expiry when present, otherwise its tier / plan /
     * gift_hours / gb_total — so a code the server validated but did
     * not apply (`applied:false`, or a stripped expiry) still
     * grants in-app without the code being in the published list.
     * Saves the grant + records the redemption, like
     * [activateServerGift]. Returns null when the answer carries no
     * usable grant. Never throws.
     */
    fun activateRpcGrant(
        code: String,
        server: Vpn5gAuthService.CodeServerResult.Validated,
        email: String,
        nowEpochSeconds: Long,
    ): Vpn5gSubscriptionStatus.Vpn5gUserSubscription? {
        val plans = runCatching { Vpn5gRemoteConfig.cached().plans }.getOrNull()
        val subscription = deriveGrantFromServer(server, nowEpochSeconds, plans)
            ?: return null
        saveGrant(code, subscription, email)
        return subscription
    }

    /**
     * Activates a gift the SERVER validated (v1.1.15): the grant (plan
     * + expiry) comes from the `validate_and_redeem_code` RPC, not
     * from the published config list — the server already marked the
     * code used, so no reuse is possible. Records the redemption
     * locally (so this device cannot re-redeem) and saves the granted
     * subscription. Returns null when the server grant carries no
     * usable expiry (then the caller falls back to the bot-link path).
     * Never throws.
     */
    fun activateServerGift(
        code: String,
        plan: String?,
        expiresAtEpochSeconds: Long?,
        email: String,
        nowEpochSeconds: Long,
    ): Vpn5gSubscriptionStatus.Vpn5gUserSubscription? {
        val expiresAt = expiresAtEpochSeconds ?: return null
        if (expiresAt <= nowEpochSeconds) return null
        val grantedPlan = plan?.takeIf { it.isNotBlank() }
            ?: Vpn5gConstants.GIFT_TIER_PLAN_ID
        // v1.1.18: the server-validated grant carries its volume cap
        // from the published plans, like the config-list path.
        val plans = runCatching { Vpn5gRemoteConfig.cached().plans }.getOrNull()
        val subscription = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
            status = "active",
            expiresAtEpochSeconds = expiresAt,
            plan = grantedPlan,
            volumeCapBytes = Vpn5gVolume.capBytesForPlanId(grantedPlan, plans),
        )
        saveGrant(code, subscription, email)
        return subscription
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
        return redeemLink(normalized)
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
