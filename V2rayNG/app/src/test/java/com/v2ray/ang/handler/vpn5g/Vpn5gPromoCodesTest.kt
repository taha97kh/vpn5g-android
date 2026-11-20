package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the pure promo-code decision ([Vpn5gPromoCodes.validateEntry]).
 * The full [Vpn5gPromoCodes.validate] also reads the local redemption list
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
}
