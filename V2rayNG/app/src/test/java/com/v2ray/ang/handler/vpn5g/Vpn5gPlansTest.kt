package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the pure plan helpers ([Vpn5gPlans]). */
class Vpn5gPlansTest {

    @Test
    fun test_days_field_wins() {
        assertEquals(
            45,
            Vpn5gPlans.planDays(SubscriptionPlanRemote(id = "sub_1", days = 45))
        )
    }

    @Test
    fun test_sub_plan_ids() {
        assertEquals(30, Vpn5gPlans.planDays(SubscriptionPlanRemote(id = "sub_1")))
        assertEquals(90, Vpn5gPlans.planDays(SubscriptionPlanRemote(id = "sub_3")))
        assertEquals(180, Vpn5gPlans.planDays(SubscriptionPlanRemote(id = "sub_6")))
        assertEquals(360, Vpn5gPlans.planDays(SubscriptionPlanRemote(id = "sub_12")))
    }

    @Test
    fun test_period_fallback() {
        assertEquals(
            30,
            Vpn5gPlans.planDays(SubscriptionPlanRemote(id = "monthly", period = "monthly"))
        )
        assertEquals(
            365,
            Vpn5gPlans.planDays(SubscriptionPlanRemote(id = "yearly", period = "yearly"))
        )
    }

    @Test
    fun test_unknown_plan_has_no_days() {
        assertNull(
            Vpn5gPlans.planDays(SubscriptionPlanRemote(id = "weird", period = "weekly"))
        )
        assertNull(Vpn5gPlans.planDays(SubscriptionPlanRemote(id = null)))
    }

    @Test
    fun test_displayable_requires_id_price_and_duration() {
        assertTrue(
            Vpn5gPlans.isDisplayable(
                SubscriptionPlanRemote(id = "sub_1", price_toman = 100_000L)
            )
        )
        assertFalse(
            Vpn5gPlans.isDisplayable(
                SubscriptionPlanRemote(id = "sub_1", price_toman = null)
            )
        )
        assertFalse(
            Vpn5gPlans.isDisplayable(
                SubscriptionPlanRemote(id = "weird", period = "weekly", price_toman = 1L)
            )
        )
    }

    @Test
    fun test_displayable_sorted_shortest_first() {
        val plans = Vpn5gPlans.displayablePlans(
            listOf(
                SubscriptionPlanRemote(id = "sub_12", price_toman = 1L),
                SubscriptionPlanRemote(id = "sub_1", price_toman = 1L),
                SubscriptionPlanRemote(id = "weird", period = "weekly", price_toman = 1L),
            )
        )
        assertEquals(listOf("sub_1", "sub_12"), plans.map { it.id })
    }

    // -- Volume cap (v1.1.18, Model B) ------------------------------------------

    @Test
    fun test_volume_cap_volume_gb_wins_over_gb() {
        assertEquals(
            50.0,
            Vpn5gPlans.volumeCapGb(
                SubscriptionPlanRemote(id = "gb_50", volume_gb = 50.0, gb = 20.0)
            )
        )
    }

    @Test
    fun test_volume_cap_gb_alias() {
        // The live config publishes only `gb` for the gb_* plans.
        assertEquals(
            20.0,
            Vpn5gPlans.volumeCapGb(
                SubscriptionPlanRemote(id = "gb_20", gb = 20.0)
            )
        )
    }

    @Test
    fun test_volume_cap_absent_is_unlimited() {
        assertNull(Vpn5gPlans.volumeCapGb(SubscriptionPlanRemote(id = "sub_1")))
        assertNull(Vpn5gPlans.volumeCapGb(null))
    }

    @Test
    fun test_volume_cap_zero_or_negative_is_unlimited() {
        assertNull(
            Vpn5gPlans.volumeCapGb(SubscriptionPlanRemote(id = "gb_0", gb = 0.0))
        )
        assertNull(
            Vpn5gPlans.volumeCapGb(SubscriptionPlanRemote(id = "gb_x", volume_gb = -5.0))
        )
    }

    // -- Plan groups + Persian names (v1.1.30) --------------------------------

    @Test
    fun test_group_of_by_volume_cap() {
        assertEquals(
            Vpn5gPlans.PlanGroup.GB,
            Vpn5gPlans.groupOf(SubscriptionPlanRemote(id = "gb_50", gb = 50.0, days = 30)),
        )
        assertEquals(
            Vpn5gPlans.PlanGroup.TIME,
            Vpn5gPlans.groupOf(SubscriptionPlanRemote(id = "sub_12")),
        )
    }

    @Test
    fun test_plans_in_group_splits_and_sorts() {
        val plans = listOf(
            SubscriptionPlanRemote(id = "sub_12", price_toman = 350_000L),
            SubscriptionPlanRemote(id = "gb_200", price_toman = 350_000L, gb = 200.0, days = 180),
            SubscriptionPlanRemote(id = "sub_1", price_toman = 50_000L),
            SubscriptionPlanRemote(id = "gb_20", price_toman = 50_000L, gb = 20.0, days = 30),
        )
        assertEquals(
            listOf("gb_20", "gb_200"),
            Vpn5gPlans.plansInGroup(plans, Vpn5gPlans.PlanGroup.GB).map { it.id },
        )
        assertEquals(
            listOf("sub_1", "sub_12"),
            Vpn5gPlans.plansInGroup(plans, Vpn5gPlans.PlanGroup.TIME).map { it.id },
        )
        assertTrue(Vpn5gPlans.plansInGroup(null, Vpn5gPlans.PlanGroup.GB).isEmpty())
    }

    @Test
    fun test_display_name_published_name_fa_wins() {
        assertEquals(
            "سالانه طلایی",
            Vpn5gPlans.displayNameFa(
                SubscriptionPlanRemote(id = "sub_12", name_fa = "سالانه طلایی")
            ),
        )
    }

    @Test
    fun test_display_name_derived_in_persian() {
        // GB plan: «بسته ۵۰ گیگابایت — ۳۰ روزه» (Persian digits).
        assertEquals(
            "بسته ۵۰ گیگابایت — ۳۰ روزه",
            Vpn5gPlans.displayNameFa(
                SubscriptionPlanRemote(id = "gb_50", gb = 50.0, days = 30)
            ),
        )
        // Time plans by duration; the raw id is never shown.
        assertEquals(
            "اشتراک ۱ ماهه",
            Vpn5gPlans.displayNameFa(SubscriptionPlanRemote(id = "sub_1")),
        )
        assertEquals(
            "اشتراک ۳ ماهه",
            Vpn5gPlans.displayNameFa(SubscriptionPlanRemote(id = "sub_3")),
        )
        assertEquals(
            "اشتراک ۶ ماهه",
            Vpn5gPlans.displayNameFa(SubscriptionPlanRemote(id = "sub_6")),
        )
        assertEquals(
            "اشتراک ۱ ساله ویژه",
            Vpn5gPlans.displayNameFa(SubscriptionPlanRemote(id = "sub_12")),
        )
        // No duration at all → no name (the card is not displayable).
        assertNull(
            Vpn5gPlans.displayNameFa(SubscriptionPlanRemote(id = "weird", period = "weekly"))
        )
    }

    @Test
    fun test_display_name_for_plan_id_never_raw() {
        val plans = listOf(
            SubscriptionPlanRemote(id = "gb_50", gb = 50.0, days = 30, price_toman = 1L),
        )
        // Resolved against the published plans first.
        assertEquals(
            "بسته ۵۰ گیگابایت — ۳۰ روزه",
            Vpn5gPlans.displayNameForPlanId("gb_50", plans),
        )
        // Unknown sub_* id still reads in Persian from its duration.
        assertEquals(
            "اشتراک ۱ ساله ویژه",
            Vpn5gPlans.displayNameForPlanId("sub_12", plans),
        )
        // Gift tier and fully unknown ids.
        assertEquals("هدیه", Vpn5gPlans.displayNameForPlanId("gift", plans))
        assertEquals("اشتراک ویژه", Vpn5gPlans.displayNameForPlanId("mystery", plans))
        assertNull(Vpn5gPlans.displayNameForPlanId(null, plans))
        assertNull(Vpn5gPlans.displayNameForPlanId("  ", plans))
    }

    // -- Payment methods (v1.1.30, config-driven) ------------------------------

    @Test
    fun test_payment_methods_none_published_is_empty() {
        assertTrue(Vpn5gPlans.paymentMethods(null).isEmpty())
        assertTrue(Vpn5gPlans.paymentMethods(PaymentGatewayRemote()).isEmpty())
    }

    @Test
    fun test_payment_methods_gateway_needs_enabled_and_url() {
        val disabled = PaymentGatewayRemote(enabled = false, url = "https://pay.example")
        assertTrue(Vpn5gPlans.paymentMethods(disabled).isEmpty())
        val noUrl = PaymentGatewayRemote(enabled = true, url = null)
        assertTrue(Vpn5gPlans.paymentMethods(noUrl).isEmpty())
        val ok = PaymentGatewayRemote(
            enabled = true, url = "https://pay.example", name_fa = "درگاه بانکی",
        )
        val methods = Vpn5gPlans.paymentMethods(ok)
        assertEquals(1, methods.size)
        assertEquals(Vpn5gPlans.PaymentMethodUi.Kind.GATEWAY, methods[0].kind)
        assertEquals("https://pay.example", methods[0].url)
        assertEquals("درگاه بانکی", methods[0].nameFa)
    }

    @Test
    fun test_payment_methods_link_and_usdt() {
        val gateway = PaymentGatewayRemote(
            payment_link_url = "https://link.example/pay",
            payment_link_name_fa = "پرداخت با لینک",
            payment_link_guide_fa = "راهنمای لینک",
            usdt_address = "TXyzWalletAddress",
            usdt_network = "TRC20",
            usdt_guide_fa = "راهنمای تتر",
        )
        val methods = Vpn5gPlans.paymentMethods(gateway)
        assertEquals(2, methods.size)
        // Display order: gateway, payment link, USDT.
        assertEquals(Vpn5gPlans.PaymentMethodUi.Kind.PAYMENT_LINK, methods[0].kind)
        assertEquals("https://link.example/pay", methods[0].url)
        assertEquals("راهنمای لینک", methods[0].guideFa)
        assertEquals(Vpn5gPlans.PaymentMethodUi.Kind.USDT, methods[1].kind)
        assertEquals("TXyzWalletAddress", methods[1].walletAddress)
        assertEquals("TRC20", methods[1].network)
        assertEquals("راهنمای تتر", methods[1].guideFa)
        // Blank fields hide the method (never a dead button).
        val blank = PaymentGatewayRemote(
            payment_link_url = "  ",
            usdt_address = "",
        )
        assertTrue(Vpn5gPlans.paymentMethods(blank).isEmpty())
    }
}
