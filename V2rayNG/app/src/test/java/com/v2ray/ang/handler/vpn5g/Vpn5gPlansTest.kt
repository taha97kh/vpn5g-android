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
}
