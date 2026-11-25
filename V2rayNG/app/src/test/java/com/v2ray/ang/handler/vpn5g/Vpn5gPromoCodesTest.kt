package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun test_gift_plan_id_volume_plan() {
        // v1.1.18: a gift code whose plan is a published volume plan
        // (gb_20) grants that plan — full pool, capped by its volume.
        // Without the published plans the same entry falls back to
        // the GIFT tier, so a malformed entry can never mint a full
        // subscription by itself.
        val entry = PromoCodeRemote(
            code = "X", type = "gift", gift_hours = 24, plan = "gb_20"
        )
        val plans = listOf(SubscriptionPlanRemote(id = "gb_20", gb = 20.0))
        assertEquals("gb_20", Vpn5gPromoCodes.giftPlanId(entry, plans))
        assertEquals("gift", Vpn5gPromoCodes.giftPlanId(entry))
        assertEquals("gift", Vpn5gPromoCodes.giftPlanId(entry, emptyList()))
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

    // v1.1.16: gift codes and purchased subscription codes have
    // SEPARATE sections — the kind decides which box a code belongs in.
    @Test
    fun test_code_kind_gift_hours_is_gift() {
        assertEquals(
            Vpn5gPromoCodes.CodeKind.GIFT,
            Vpn5gPromoCodes.codeKind(
                PromoCodeRemote(code = "G1", type = "gift", gift_hours = 24)
            ),
        )
        assertEquals(
            Vpn5gPromoCodes.CodeKind.GIFT,
            Vpn5gPromoCodes.codeKind(
                PromoCodeRemote(code = "G2", type = "gift", gift_days = 7)
            ),
        )
    }

    @Test
    fun test_code_kind_plan_grant_is_subscription() {
        // Live codes 584285 / 446843 / 867110 / 328441 / 352755 all
        // grant a full plan — they belong in the subscription section.
        assertEquals(
            Vpn5gPromoCodes.CodeKind.SUBSCRIPTION,
            Vpn5gPromoCodes.codeKind(
                PromoCodeRemote(code = "584285", type = "gift", plan = "sub_12", gift_hours = 24)
            ),
        )
        assertEquals(
            Vpn5gPromoCodes.CodeKind.SUBSCRIPTION,
            Vpn5gPromoCodes.codeKind(
                PromoCodeRemote(code = "446843", type = "gift", tier = "sub_1", gift_hours = 24)
            ),
        )
    }

    @Test
    fun test_code_kind_discount_and_unknown() {
        assertEquals(
            Vpn5gPromoCodes.CodeKind.DISCOUNT,
            Vpn5gPromoCodes.codeKind(
                PromoCodeRemote(code = "SAVE20", type = "discount", discount_percent = 20)
            ),
        )
        // A gift with no duration grants nothing — unknown section.
        assertEquals(
            Vpn5gPromoCodes.CodeKind.UNKNOWN,
            Vpn5gPromoCodes.codeKind(
                PromoCodeRemote(code = "BROKEN", type = "gift")
            ),
        )
        assertEquals(
            Vpn5gPromoCodes.CodeKind.UNKNOWN,
            Vpn5gPromoCodes.codeKind(PromoCodeRemote(code = "X")),
        )
    }

    @Test
    fun test_find_code_is_case_insensitive_and_trimmed() {
        val codes = listOf(
            PromoCodeRemote(code = "AbC123", type = "gift", gift_hours = 5),
        )
        assertEquals("AbC123", Vpn5gPromoCodes.findCode("  abc123 ", codes)?.code)
        assertNull(Vpn5gPromoCodes.findCode("nope", codes))
        assertNull(Vpn5gPromoCodes.findCode("", codes))
        assertNull(Vpn5gPromoCodes.findCode("AbC123", null))
    }

    // v1.1.17: a full-plan code grants the PLAN's days, not its
    // gift_hours — the 1.1.15/1.1.16 bug granted a purchased sub_1
    // code only 24 hours (its gift_hours) instead of 30 days.

    @Test
    fun test_grant_duration_plan_days_win_over_gift_hours() {
        // The live 352755 code: sub_1 with gift_hours 24.
        assertEquals(
            30L * 86_400L,
            Vpn5gPromoCodes.grantDurationSeconds(
                PromoCodeRemote(
                    code = "352755", type = "gift", plan = "sub_1",
                    gift_hours = 24, remaining_uses = 0,
                )
            ),
        )
        // The live 599835 code: sub_3 with gift_hours 24.
        assertEquals(
            90L * 86_400L,
            Vpn5gPromoCodes.grantDurationSeconds(
                PromoCodeRemote(
                    code = "599835", type = "gift", plan = "sub_3",
                    gift_hours = 24, remaining_uses = 0,
                )
            ),
        )
        // Plan via tier, gift_days present too: the plan still wins.
        assertEquals(
            360L * 86_400L,
            Vpn5gPromoCodes.grantDurationSeconds(
                PromoCodeRemote(code = "X", type = "gift", tier = "sub_12", gift_days = 30)
            ),
        )
        // Plain gift: hours/days as before.
        assertEquals(
            24L * 3_600L,
            Vpn5gPromoCodes.grantDurationSeconds(
                PromoCodeRemote(code = "G", type = "gift", gift_hours = 24)
            ),
        )
        assertEquals(
            7L * 86_400L,
            Vpn5gPromoCodes.grantDurationSeconds(
                PromoCodeRemote(code = "G", type = "gift", gift_days = 7)
            ),
        )
        // No plan, no duration: nothing.
        assertNull(
            Vpn5gPromoCodes.grantDurationSeconds(
                PromoCodeRemote(code = "BAD", type = "gift")
            )
        )
        // Discount: nothing.
        assertNull(
            Vpn5gPromoCodes.grantDurationSeconds(discount("SAVE20", 20, 5))
        )
    }

    @Test
    fun test_activation_expiry_uses_plan_days_for_plan_codes() {
        // 352755 (sub_1, gift_hours 24) grants 30 days, not 24 hours.
        assertEquals(
            1_795_392_000L + 30L * 86_400L,
            Vpn5gPromoCodes.activationExpiry(
                PromoCodeRemote(
                    code = "352755", type = "gift", plan = "sub_1",
                    gift_hours = 24, remaining_uses = 0,
                ),
                1_795_392_000L,
            ),
        )
    }

    @Test
    fun test_plan_only_gift_is_valid() {
        // A full-plan code with NO gift_hours/gift_days is usable:
        // it grants the plan's days. (Before 1.1.17 it was
        // InvalidEntry — "کد معتبر نیست" for a perfectly good code.)
        assertTrue(
            Vpn5gPromoCodes.validateEntry(
                PromoCodeRemote(
                    code = "P1", type = "gift", plan = "sub_1", remaining_uses = 1
                ),
                false,
            ) is Vpn5gPromoCodes.Validation.Valid
        )
        // But a gift with neither plan nor duration is still invalid.
        assertEquals(
            Vpn5gPromoCodes.Validation.InvalidEntry,
            Vpn5gPromoCodes.validateEntry(
                PromoCodeRemote(code = "BAD", type = "gift", remaining_uses = 1),
                false,
            ),
        )
    }

    // -- Grant derived from the RPC answer (v1.1.21) -----------------------
    // The 1.1.20 failure: a validated-but-unapplied code (applied:false,
    // or a stripped expiry) only recovered when the code was in the
    // published list — codes published minutes earlier were not, so both
    // 493401 (gift) and 301963 (gb_50) ended NOT_APPLIED. The answer's
    // own fields now derive the grant; the list is never needed.
    private val rpcNow = 1_800_000_000L // ~2027-01-15
    private val rpcExpiry = Vpn5gSubscriptionStatus.parseIsoToEpoch(
        "2027-01-25T23:30:08+00:00"
    )!!
    private val rpcPlans = listOf(
        SubscriptionPlanRemote(id = "sub_1", days = 30),
        SubscriptionPlanRemote(id = "sub_3", days = 90),
        SubscriptionPlanRemote(id = "sub_6", days = 180),
        SubscriptionPlanRemote(id = "sub_12", days = 360),
        SubscriptionPlanRemote(id = "gb_20", days = 30, gb = 20.0),
        SubscriptionPlanRemote(id = "gb_50", days = 30, gb = 50.0),
        SubscriptionPlanRemote(id = "gb_100", days = 90, gb = 100.0),
        SubscriptionPlanRemote(id = "gb_200", days = 180, gb = 200.0),
    )

    private fun validated(
        tier: String?,
        plan: String?,
        applied: Boolean? = true,
        giftHours: Int? = 24,
        gbTotal: Int? = 50,
        expiresAt: Long? = null,
    ) = Vpn5gAuthService.CodeServerResult.Validated(
        plan = plan,
        expiresAtEpochSeconds = expiresAt,
        applied = applied,
        tier = tier,
        giftHours = giftHours,
        gbTotal = gbTotal,
    )

    @Test
    fun test_derive_grant_from_server_uses_server_expiry() {
        // applied:true + parseable expiry: the server's expiry is the
        // grant, whatever the other fields say.
        val grant = Vpn5gPromoCodes.deriveGrantFromServer(
            validated("gift", "gift", expiresAt = rpcExpiry), rpcNow, rpcPlans
        )
        assertNotNull(grant)
        assertEquals("active", grant?.status)
        assertEquals("gift", grant?.plan)
        assertEquals(rpcExpiry, grant?.expiresAtEpochSeconds)
        assertEquals(50L * 1_000_000_000L, grant?.volumeCapBytes)
    }

    @Test
    fun test_derive_grant_from_server_gift_hours() {
        // applied:false + no expiry (the 493401 shape): 24 gift hours.
        val grant = Vpn5gPromoCodes.deriveGrantFromServer(
            validated("gift", "gift", applied = false), rpcNow, rpcPlans
        )
        assertNotNull(grant)
        assertEquals("gift", grant?.plan)
        assertEquals(rpcNow + 24 * 3_600L, grant?.expiresAtEpochSeconds)
    }

    @Test
    fun test_derive_grant_from_server_gb_plan() {
        // applied:false + no expiry (the 301963 shape): gb_50 grants
        // its published 30 days, capped at the answer's 50 GB.
        val grant = Vpn5gPromoCodes.deriveGrantFromServer(
            validated("gb_50", "gb_50", applied = false), rpcNow, rpcPlans
        )
        assertNotNull(grant)
        assertEquals("gb_50", grant?.plan)
        assertEquals(rpcNow + 30 * 86_400L, grant?.expiresAtEpochSeconds)
        assertEquals(50L * 1_000_000_000L, grant?.volumeCapBytes)
    }

    @Test
    fun test_derive_grant_from_server_every_tier() {
        val expectedDays = mapOf(
            "sub_1" to 30L, "sub_3" to 90L, "sub_6" to 180L, "sub_12" to 360L,
            "gb_20" to 30L, "gb_50" to 30L, "gb_100" to 90L, "gb_200" to 180L,
        )
        for ((tier, days) in expectedDays) {
            val grant = Vpn5gPromoCodes.deriveGrantFromServer(
                validated(tier, tier, applied = false), rpcNow, rpcPlans
            )
            assertNotNull(grant, tier)
            assertEquals(tier, grant?.plan, tier)
            assertEquals(rpcNow + days * 86_400L, grant?.expiresAtEpochSeconds, tier)
        }
        // The plain gift tier grants its gift hours, not plan days.
        val gift = Vpn5gPromoCodes.deriveGrantFromServer(
            validated("gift", "gift", applied = false), rpcNow, rpcPlans
        )
        assertEquals(rpcNow + 24 * 3_600L, gift?.expiresAtEpochSeconds)
    }

    @Test
    fun test_derive_grant_from_server_sub_plan_days_win_over_gift_hours() {
        // The 1.1.15/1.1.16 bug, re-checked for the RPC path: a sub_1
        // code that also carries gift_hours 24 grants 30 days, never
        // 24 hours.
        val grant = Vpn5gPromoCodes.deriveGrantFromServer(
            validated("sub_1", "sub_1", applied = false, giftHours = 24),
            rpcNow, rpcPlans
        )
        assertEquals(rpcNow + 30 * 86_400L, grant?.expiresAtEpochSeconds)
    }

    @Test
    fun test_derive_grant_from_server_no_grant_cases() {
        // A discount answer grants nothing (the bot applies it).
        assertNull(
            Vpn5gPromoCodes.deriveGrantFromServer(
                validated("discount", null, giftHours = null, gbTotal = null),
                rpcNow, rpcPlans
            )
        )
        // An answer with no usable field at all grants nothing — the
        // caller falls back to the row re-read, then the entry.
        assertNull(
            Vpn5gPromoCodes.deriveGrantFromServer(
                validated(null, null, applied = false, giftHours = null, gbTotal = null),
                rpcNow, rpcPlans
            )
        )
        // A gift with no duration and no expiry grants nothing.
        assertNull(
            Vpn5gPromoCodes.deriveGrantFromServer(
                validated("gift", "gift", giftHours = null, gbTotal = null),
                rpcNow, rpcPlans
            )
        )
    }

    @Test
    fun test_activate_rpc_grant_null_branches_are_jvm_safe() {
        // Only the null branches are JVM-testable: a usable grant saves
        // to MMKV, which is a device concern (Not run here).
        assertNull(
            Vpn5gPromoCodes.activateRpcGrant(
                "BAD", validated("discount", null, giftHours = null, gbTotal = null),
                "a@b.c", rpcNow
            )
        )
    }
}
