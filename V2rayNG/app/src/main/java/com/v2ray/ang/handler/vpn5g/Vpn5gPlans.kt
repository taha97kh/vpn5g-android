package com.v2ray.ang.handler.vpn5g

/**
 * Subscription-plan helpers (v1.1.14).
 *
 * Plans come ONLY from the remote app_config.json (bot-published); nothing
 * is hardcoded in the app. The bot publishes plan ids `sub_1` / `sub_3` /
 * `sub_6` / `sub_12` (30 / 90 / 180 / 360 days) — the admin-users package
 * activates exactly those ids — but older published configs carry
 * `period: "monthly" | "yearly"` instead. Both shapes are accepted:
 * an explicit `days` field wins, then the sub_* id, then the period.
 * A plan with no usable duration and no known period is not displayable
 * (the Premium screen shows no card for it rather than an invented
 * duration). Pure, JVM-tested.
 */
object Vpn5gPlans {

    /** Days granted by the bot's subscription plan ids. */
    val SUB_PLAN_DAYS = mapOf(
        "sub_1" to 30,
        "sub_3" to 90,
        "sub_6" to 180,
        "sub_12" to 360,
    )

    /** Duration in days a plan grants, or null when it cannot be known. */
    fun planDays(plan: SubscriptionPlanRemote): Int? {
        val explicit = plan.days
        if (explicit != null && explicit > 0) return explicit
        val byId = plan.id?.trim()?.lowercase()?.let { SUB_PLAN_DAYS[it] }
        if (byId != null) return byId
        return when (plan.period?.trim()?.lowercase()) {
            "monthly" -> 30
            "yearly" -> 365
            else -> null
        }
    }

    /** True when the plan can be shown as a card (id + price + known duration). */
    fun isDisplayable(plan: SubscriptionPlanRemote): Boolean {
        if (plan.id.isNullOrBlank()) return false
        if (plan.price_toman == null) return false
        return planDays(plan) != null
    }

    /** Displayable plans only, shortest duration first. */
    fun displayablePlans(plans: List<SubscriptionPlanRemote>?): List<SubscriptionPlanRemote> {
        return plans.orEmpty()
            .filter { isDisplayable(it) }
            .sortedBy { planDays(it) ?: Int.MAX_VALUE }
    }

    /**
     * Volume cap of a plan in gigabytes (v1.1.18, Model B), or null
     * when the plan has no cap (unlimited). The canonical field is
     * `volume_gb`; `gb` is the alias the bot already publishes for
     * the gb_* plans — when both are set, `volume_gb` wins. A cap
     * of 0 or less is not a cap (treated as absent, never as "0 GB
     * left"). Pure, JVM-tested.
     */
    fun volumeCapGb(plan: SubscriptionPlanRemote?): Double? {
        val cap = plan?.volume_gb ?: plan?.gb ?: return null
        if (cap <= 0.0 || cap.isNaN() || cap.isInfinite()) return null
        return cap
    }

    // -- Plan groups (v1.1.30, subscription screen redesign) ------------------

    /**
     * The two top-level plan groups on the subscription screen
     * (Aref's spec 2026-11-26): 📦 GB packages (volume-capped,
     * Model B) and 📅 unlimited time-based plans. A plan with a
     * volume cap is a GB plan; a plan without one is time-based.
     * Pure, JVM-tested.
     */
    enum class PlanGroup { GB, TIME }

    /** Which group [plan] belongs to (cap present → GB, else TIME). Pure. */
    fun groupOf(plan: SubscriptionPlanRemote): PlanGroup =
        if (volumeCapGb(plan) != null) PlanGroup.GB else PlanGroup.TIME

    /** Displayable plans of one group, shortest duration first. Pure. */
    fun plansInGroup(
        plans: List<SubscriptionPlanRemote>?,
        group: PlanGroup,
    ): List<SubscriptionPlanRemote> =
        displayablePlans(plans).filter { groupOf(it) == group }

    /**
     * Persian display name for a plan (v1.1.30, Aref's order:
     * subscription labels are written in Persian, never "Sub_12").
     * The published `name_fa` wins when present; otherwise the name
     * is derived from the plan's own published facts (cap + days) —
     * a GB plan reads «بسته ۲۰ گیگابایت — ۳۰ روزه», a time plan
     * «اشتراک ۱ ماهه» / «اشتراک ۱ ساله ویژه». Returns null only
     * when neither a name nor a duration is known (the card is not
     * displayable then anyway). The numbers come from the plan
     * itself (published data), never from a hardcoded price table.
     * Pure, JVM-tested.
     */
    fun displayNameFa(plan: SubscriptionPlanRemote): String? {
        plan.name_fa?.takeIf { it.isNotBlank() }?.let { return it }
        val days = planDays(plan) ?: return null
        val capGb = volumeCapGb(plan)
        return if (capGb != null) {
            "بسته ${formatGbFa(capGb)} گیگابایت — ${toFaDigits(days)} روزه"
        } else {
            when {
                days >= 360 -> "اشتراک ۱ ساله ویژه"
                days >= 180 -> "اشتراک ۶ ماهه"
                days >= 90 -> "اشتراک ۳ ماهه"
                days >= 30 -> "اشتراک ۱ ماهه"
                else -> "اشتراک ${toFaDigits(days)} روزه"
            }
        }
    }

    /**
     * Persian display name for an ACTIVE subscription's plan id
     * (v1.1.30): resolves the id against the published plans first
     * ([displayNameFa]); an unknown sub_* id still reads in Persian
     * from its known duration («اشتراک ۱ ساله ویژه» for sub_12) —
     * the raw id ("sub_12") is NEVER shown to the user. The gift
     * tier reads «هدیه». Null only for a null/blank id. Pure.
     */
    fun displayNameForPlanId(
        planId: String?,
        plans: List<SubscriptionPlanRemote>?,
    ): String? {
        val id = planId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        plans?.firstOrNull { it.id?.trim()?.equals(id, ignoreCase = true) == true }
            ?.let { displayNameFa(it)?.let { name -> return name } }
        if (id.equals(Vpn5gConstants.GIFT_TIER_PLAN_ID, ignoreCase = true)) {
            return "هدیه"
        }
        val days = SUB_PLAN_DAYS[id.lowercase()] ?: return "اشتراک ویژه"
        return when {
            days >= 360 -> "اشتراک ۱ ساله ویژه"
            days >= 180 -> "اشتراک ۶ ماهه"
            days >= 90 -> "اشتراک ۳ ماهه"
            else -> "اشتراک ۱ ماهه"
        }
    }

    /** GB amount without a trailing ".0" («۲۰» not «۲۰٫۰»), Persian digits. Pure. */
    fun formatGbFa(gb: Double): String {
        val text = if (gb == gb.toLong().toDouble()) {
            gb.toLong().toString()
        } else {
            gb.toString()
        }
        return toFaDigits(text)
    }

    /** Latin digits → Persian digits (۰۱۲۳۴۵۶۷۸۹); other chars untouched. Pure. */
    fun toFaDigits(value: Any): String {
        val fa = "۰۱۲۳۴۵۶۷۸۹"
        return value.toString().map { ch ->
            if (ch in '0'..'9') fa[ch - '0'] else ch
        }.joinToString("")
    }

    // -- Payment methods (v1.1.30, config-driven) ------------------------------

    /**
     * One resolved payment method for the purchase area, built ONLY
     * from the published [PaymentGatewayRemote] (bot-managed): the
     * direct bank gateway, the admin's payment link, and the USDT
     * (Tether) wallet. A method with no published destination is
     * absent — the UI never shows a dead button. A future bot-side
     * exchange integration only has to publish these fields; no app
     * update is needed. Pure, JVM-tested.
     */
    data class PaymentMethodUi(
        val kind: Kind,
        /** Published display name (Persian), or null → UI default label. */
        val nameFa: String?,
        /** URL to open (gateway / payment link), or null for USDT. */
        val url: String?,
        /** USDT wallet address (kind == USDT only), else null. */
        val walletAddress: String?,
        /** USDT network label (e.g. "TRC20"), else null. */
        val network: String?,
        /** Published guide text shown under the method, else null. */
        val guideFa: String?,
    ) {
        enum class Kind { GATEWAY, PAYMENT_LINK, USDT }
    }

    /**
     * The payment methods to show, in display order: the direct
     * bank gateway (only when enabled with a URL), then the admin
     * payment link (only when a URL is published), then USDT (only
     * when a wallet address is published). Empty when the bot
     * published none — the purchase then goes through the bot, as
     * before. Pure, JVM-tested.
     */
    fun paymentMethods(gateway: PaymentGatewayRemote?): List<PaymentMethodUi> {
        if (gateway == null) return emptyList()
        val methods = mutableListOf<PaymentMethodUi>()
        val gatewayUrl = gateway.url?.takeIf { it.isNotBlank() }
        if (gateway.enabled == true && gatewayUrl != null) {
            methods += PaymentMethodUi(
                kind = PaymentMethodUi.Kind.GATEWAY,
                nameFa = gateway.name_fa?.takeIf { it.isNotBlank() },
                url = gatewayUrl,
                walletAddress = null,
                network = null,
                guideFa = null,
            )
        }
        val linkUrl = gateway.payment_link_url?.takeIf { it.isNotBlank() }
        if (linkUrl != null) {
            methods += PaymentMethodUi(
                kind = PaymentMethodUi.Kind.PAYMENT_LINK,
                nameFa = gateway.payment_link_name_fa?.takeIf { it.isNotBlank() },
                url = linkUrl,
                walletAddress = null,
                network = null,
                guideFa = gateway.payment_link_guide_fa?.takeIf { it.isNotBlank() },
            )
        }
        val usdtAddress = gateway.usdt_address?.takeIf { it.isNotBlank() }
        if (usdtAddress != null) {
            methods += PaymentMethodUi(
                kind = PaymentMethodUi.Kind.USDT,
                nameFa = gateway.usdt_name_fa?.takeIf { it.isNotBlank() },
                url = null,
                walletAddress = usdtAddress,
                network = gateway.usdt_network?.takeIf { it.isNotBlank() },
                guideFa = gateway.usdt_guide_fa?.takeIf { it.isNotBlank() },
            )
        }
        return methods
    }
}
