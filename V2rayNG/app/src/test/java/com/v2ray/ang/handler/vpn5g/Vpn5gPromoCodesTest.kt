package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the pure promo-code decision ([Vpn5gPromoCodes.validateEntry]
 * and [Vpn5gPromoCodes.validateEntryRedeem]).
 * [Vpn5gPromoCodes.validateForRedeem] also reads the local redemption list
 * from MMKV, so it is not covered here.
 */
class Vpn5gPromoCodesTest {

    private fun discount(code: String, percent: Int?, remaining: Int?) = PromoCodeRemote(
        code = code,
        type = "discount",
        discount_percent = percent,
        remaining_uses = remaining,
    )

    private fun gift(code: String, days: Int?, remaining: Int?) = PromoCodeRemote(
        code = code,
        type = "gift",
        gift_days = days,
        remaining_uses = remaining,
    )

    @Test
    fun test_valid_discount_and_gift() {
        assertTrue(
            Vpn5gPromoCodes.validateEntry(discount("SAVE20", 20, 5), false)
                is Vpn5gPromoCodes.Validation.Valid
        )
        assertTrue(
            Vpn5gPromoCodes.validateEntry(gift("FREE7", 7, null), false)
                is Vpn5gPromoCodes.Validation.Valid
        )
        // Null remaining_uses means unlimited.
        assertTrue(
            Vpn5gPromoCodes.validateEntry(discount("SAVE20", 20, null), false)
                is Vpn5gPromoCodes.Validation.Valid
        )
    }

    @Test
    fun test_exhausted() {
        assertEquals(
            Vpn5gPromoCodes.Validation.Exhausted,
            Vpn5gPromoCodes.validateEntry(discount("SAVE20", 20, 0), false),
        )
    }

    @Test
    fun test_already_redeemed() {
        assertEquals(
            Vpn5gPromoCodes.Validation.AlreadyRedeemed,
            Vpn5gPromoCodes.validateEntry(discount("SAVE20", 20, 5), true),
        )
    }

    @Test
    fun test_malformed_entries() {
        // Discount percent outside 1..100.
        assertEquals(
            Vpn5gPromoCodes.Validation.InvalidEntry,
            Vpn5gPromoCodes.validateEntry(discount("BAD", 0, 5), false),
        )
        assertEquals(
            Vpn5gPromoCodes.Validation.InvalidEntry,
            Vpn5gPromoCodes.validateEntry(discount("BAD", 101, 5), false),
        )
        assertEquals(
            Vpn5gPromoCodes.Validation.InvalidEntry,
            Vpn5gPromoCodes.validateEntry(discount("BAD", null, 5), false),
        )
        // Gift with no days.
        assertEquals(
            Vpn5gPromoCodes.Validation.InvalidEntry,
            Vpn5gPromoCodes.validateEntry(gift("BAD", 0, 5), false),
        )
        assertEquals(
            Vpn5gPromoCodes.Validation.InvalidEntry,
            Vpn5gPromoCodes.validateEntry(gift("BAD", null, 5), false),
        )
        // Unknown type.
        assertEquals(
            Vpn5gPromoCodes.Validation.InvalidEntry,
            Vpn5gPromoCodes.validateEntry(
                PromoCodeRemote(code = "BAD", type = "mystery", remaining_uses = 5),
                false,
            ),
        )
    }

    // v1.1.12: in-app redeem validation — expiry, sign-in, ownership, activation math.

    private fun giftWithExpiry(code: String, days: Int, remaining: Int?, expiresAt: String?) =
        PromoCodeRemote(
            code = code,
            type = "gift",
            gift_days = days,
            remaining_uses = remaining,
            expires_at = expiresAt,
            plan = "sub_12",
        )

    @Test
    fun test_redeem_future_expiry_is_valid() {
        // The live 867110 code: expires 2027-11-18T19:14:59Z.
        val entry = giftWithExpiry("867110", 360, 1, "2027-11-18T19:14:59+00:00")
        assertEquals(
            Vpn5gPromoCodes.Validation.Valid(entry),
            Vpn5gPromoCodes.validateEntryRedeem(
                entry, false, false, 1_795_392_000L, "a@gmail.com"
            ),
        )
    }

    @Test
    fun test_redeem_past_expiry_is_expired() {
        val entry = giftWithExpiry("OLD", 30, 1, "2026-11-01T00:00:00Z")
        assertEquals(
            Vpn5gPromoCodes.Validation.Expired,
            Vpn5gPromoCodes.validateEntryRedeem(
                entry, false, false, 1_795_392_000L, "a@gmail.com"
            ),
        )
    }

    @Test
    fun test_redeem_unparsable_expiry_is_invalid() {
        val entry = giftWithExpiry("BAD", 30, 1, "not a date")
        assertEquals(
            Vpn5gPromoCodes.Validation.InvalidEntry,
            Vpn5gPromoCodes.validateEntryRedeem(
                entry, false, false, 1_795_392_000L, "a@gmail.com"
            ),
        )
    }

    @Test
    fun test_redeem_requires_sign_in() {
        val entry = giftWithExpiry("867110", 360, 1, "2027-11-18T19:14:59+00:00")
        assertEquals(
            Vpn5gPromoCodes.Validation.NotSignedIn,
            Vpn5gPromoCodes.validateEntryRedeem(
                entry, false, false, 1_795_392_000L, null
            ),
        )
        assertEquals(
            Vpn5gPromoCodes.Validation.NotSignedIn,
            Vpn5gPromoCodes.validateEntryRedeem(
                entry, false, false, 1_795_392_000L, "  "
            ),
        )
    }

    @Test
    fun test_redeem_by_other_email_is_not_yours() {
        val entry = giftWithExpiry("867110", 360, 1, "2027-11-18T19:14:59+00:00")
        assertEquals(
            Vpn5gPromoCodes.Validation.NotYours,
            Vpn5gPromoCodes.validateEntryRedeem(
                entry, false, true, 1_795_392_000L, "b@gmail.com"
            ),
        )
        // Same email re-entering: already redeemed, not "not yours".
        assertEquals(
            Vpn5gPromoCodes.Validation.AlreadyRedeemed,
            Vpn5gPromoCodes.validateEntryRedeem(
                entry, true, false, 1_795_392_000L, "a@gmail.com"
            ),
        )
    }

    @Test
    fun test_activation_expiry_math() {
        // 360 days from 2026-11-23T00:00:00Z (1_795_392_000).
        assertEquals(
            1_795_392_000L + 360L * 86_400L,
            Vpn5gPromoCodes.activationExpiry(
                giftWithExpiry("867110", 360, 1, "2027-11-18T19:14:59+00:00"),
                1_795_392_000L,
            ),
        )
        // 30 days: the 446843 code.
        assertEquals(
            1_795_392_000L + 30L * 86_400L,
            Vpn5gPromoCodes.activationExpiry(
                PromoCodeRemote(
                    code = "446843", type = "gift", gift_days = 30,
                    remaining_uses = 1, plan = "sub_1",
                ),
                1_795_392_000L,
            ),
        )
        // Discount codes and gifts without days grant nothing.
        assertNull(
            Vpn5gPromoCodes.activationExpiry(discount("SAVE20", 20, 5), 1_795_392_000L)
        )
        assertNull(
            Vpn5gPromoCodes.activationExpiry(gift("BAD", 0, 5), 1_795_392_000L)
        )
    }

    @Test
    fun test_gift_hours_valid_and_expiry() {
        // v1.1.14: 1 / 5 / 24 hours and 168 hours (7 days).
        val oneHour = PromoCodeRemote(
            code = "H1", type = "gift", gift_hours = 1, remaining_uses = 5
        )
        assertTrue(
            Vpn5gPromoCodes.validateEntry(oneHour, false)
                is Vpn5gPromoCodes.Validation.Valid
        )
        assertEquals(
            1_795_392_000L + 3_600L,
            Vpn5gPromoCodes.activationExpiry(oneHour, 1_795_392_000L),
        )
        val week = PromoCodeRemote(
            code = "W7", type = "gift", gift_hours = 168, remaining_uses = 5
        )
        assertEquals(
            1_795_392_000L + 168L * 3_600L,
            Vpn5gPromoCodes.activationExpiry(week, 1_795_392_000L),
        )
    }

    @Test
    fun test_gift_hours_win_over_days() {
        // Both set: the finer unit (hours) decides.
        val both = PromoCodeRemote(
            code = "BOTH", type = "gift", gift_days = 30, gift_hours = 5,
            remaining_uses = 5,
        )
        assertEquals(5L * 3_600L, Vpn5gPromoCodes.giftDurationSeconds(both))
        assertEquals(
            1_795_392_000L + 5L * 3_600L,
            Vpn5gPromoCodes.activationExpiry(both, 1_795_392_000L),
        )
    }

    @Test
    fun test_gift_plan_id() {
        // A sub_* plan id grants a FULL subscription; any other gift
        // grants the middle GIFT tier.
        assertEquals(
            "sub_12",
            Vpn5gPromoCodes.giftPlanId(
                PromoCodeRemote(code = "X", type = "gift", gift_days = 360, plan = "sub_12")
            )
        )
        assertEquals(
            "gift",
            Vpn5gPromoCodes.giftPlanId(
                PromoCodeRemote(code = "X", type = "gift", gift_hours = 5)
            )
        )
        assertEquals(
            "gift",
            Vpn5gPromoCodes.giftPlanId(
                PromoCodeRemote(code = "X", type = "gift", gift_hours = 5, tier = "gift")
            )
        )
    }

    @Test
    fun test_redeem_link_builds_bot_deep_link() {
        // v1.1.15: the redeem deep link is normalized (trimmed +
        // uppercased) — the same link the app and the bot both use.
        assertEquals(
            "https://t.me/VPN5G_IRBot?start=redeem_GIFT5H",
            Vpn5gPromoCodes.redeemLink("gift5h"),
        )
        assertEquals(
            "https://t.me/VPN5G_IRBot?start=redeem_GIFT5H",
            Vpn5gPromoCodes.redeemLink("  GIFT5H  "),
        )
    }

    @Test
    fun test_activate_server_gift_rejects_unusable_grants() {
        // v1.1.15: a server grant with no expiry, or an expiry already
        // past, activates nothing. These are the only activateServerGift
        // branches that are JVM-testable: a usable grant saves the
        // subscription to MMKV, which is a device concern (Not run here).
        assertNull(
            Vpn5gPromoCodes.activateServerGift(
                "GIFT5H", "gift", null, "a@b.c", 1_700_000_000L
            )
        )
        assertNull(
            Vpn5gPromoCodes.activateServerGift(
                "GIFT5H", "gift", 1_700_000_000L, "a@b.c", 1_700_000_000L
            )
        )
        assertNull(
            Vpn5gPromoCodes.activateServerGift(
                "GIFT5H", "sub_12", 1_699_000_000L, "a@b.c", 1_700_000_000L
            )
        )
    }
}
