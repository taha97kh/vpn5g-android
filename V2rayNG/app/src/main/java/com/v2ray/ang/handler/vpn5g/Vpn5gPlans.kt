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
}
