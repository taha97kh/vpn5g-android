package com.v2ray.ang.ui.vpn5g

import com.v2ray.ang.handler.vpn5g.SubscriptionPlanRemote
import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the upsell → subscription details swap (v1.1.22,
 * fix #3): while ANY subscription is active (gift or sub) the
 * «ارتقا به نسخه پرو» upsell becomes «مشخصات اشتراک»; when the
 * subscription ends, the upsell returns.
 */
class Vpn5gSubscriptionDetailsTest {

    @Test
    fun test_no_subscription_shows_upsell() {
        assertFalse(hasActiveSubscription(Vpn5gHomeUiState()))
    }

    @Test
    fun test_subscriber_shows_details() {
        assertTrue(
            hasActiveSubscription(Vpn5gHomeUiState(isSubscriber = true)),
        )
    }

    @Test
    fun test_gift_shows_details() {
        assertTrue(hasActiveSubscription(Vpn5gHomeUiState(isGift = true)))
    }

    @Test
    fun test_expired_row_alone_shows_upsell() {
        // A stale row with no active tier is not a subscription: the
        // gate is the tier flags, never the row alone.
        val expired = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
            "active", 1_700_000_000L, "sub_1"
        )
        val state = Vpn5gHomeUiState(
            userSubscription = expired,
            isSubscriber = false,
            isGift = false,
        )
        assertFalse(hasActiveSubscription(state))
    }

    @Test
    fun test_plan_name_uses_published_persian_name() {
        val state = Vpn5gHomeUiState(
            userSubscription = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
                "active", 1_800_000_000L, "sub_1"
            ),
            plans = listOf(
                SubscriptionPlanRemote(id = "sub_1", name_fa = "ماهانه"),
                SubscriptionPlanRemote(id = "sub_3", name_fa = "سه‌ماهه"),
            ),
        )
        assertEquals("ماهانه", subscriptionPlanDisplayName(state))
    }

    @Test
    fun test_plan_name_never_shows_raw_plan_id() {
        // v1.1.30 (Aref's order): subscription labels are Persian —
        // the raw id ("gb_50" / "sub_12") is never shown. An unknown
        // id reads «اشتراک ویژه»; a known sub_* id reads from its
        // duration.
        val unknown = Vpn5gHomeUiState(
            userSubscription = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
                "active", 1_800_000_000L, "gb_50"
            ),
            plans = listOf(
                SubscriptionPlanRemote(id = "sub_1", name_fa = "ماهانه"),
            ),
        )
        assertEquals("اشتراک ویژه", subscriptionPlanDisplayName(unknown))

        val yearly = Vpn5gHomeUiState(
            userSubscription = Vpn5gSubscriptionStatus.Vpn5gUserSubscription(
                "active", 1_800_000_000L, "sub_12"
            ),
            plans = emptyList(),
        )
        assertEquals("اشتراک ۱ ساله ویژه", subscriptionPlanDisplayName(yearly))
    }

    @Test
    fun test_plan_name_null_without_subscription() {
        assertNull(subscriptionPlanDisplayName(Vpn5gHomeUiState()))
    }
}
