package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.GIFT_TIER_PLAN_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Free trial logic (v1.1.26, contract v1): start rules, grant
 * window, disabled behavior and record persistence shape. Pure JVM.
 */
class Vpn5gTrialTest {

    private val now = 1_700_000_000L

    @Test
    fun `trial grant is a gift tier record while running`() {
        val record = Vpn5gTrial.TrialRecord(
            email = "user@example.com",
            startedAtEpochSeconds = now,
            hours = 24,
        )
        val grant = Vpn5gTrial.trialGrant(record, enabled = true, nowEpochSeconds = now + 60)
        assertEquals("active", grant?.status)
        assertEquals(GIFT_TIER_PLAN_ID, grant?.plan)
        assertEquals(now + 24 * 3_600L, grant?.expiresAtEpochSeconds)
    }

    @Test
    fun `trial grant ends at the window end and when disabled`() {
        val record = Vpn5gTrial.TrialRecord("user@example.com", now, 24)
        assertNull(Vpn5gTrial.trialGrant(record, true, now + 24 * 3_600L))
        assertNull(Vpn5gTrial.trialGrant(record, true, now + 24 * 3_600L + 1))
        assertNull(Vpn5gTrial.trialGrant(record, false, now + 60))
        assertNull(Vpn5gTrial.trialGrant(null, true, now))
    }

    @Test
    fun `trial hours are clamped inside the window math`() {
        val record = Vpn5gTrial.TrialRecord("user@example.com", now, 10_000)
        assertEquals(now + 720 * 3_600L, Vpn5gTrial.trialEndEpochSeconds(record))
    }

    @Test
    fun `trial starts once per account and never over an active grant`() {
        assertTrue(Vpn5gTrial.shouldStartTrial(true, "user@example.com", null, false))
        assertFalse(Vpn5gTrial.shouldStartTrial(false, "user@example.com", null, false))
        assertFalse(Vpn5gTrial.shouldStartTrial(true, null, null, false))
        assertFalse(Vpn5gTrial.shouldStartTrial(true, "  ", null, false))
        assertFalse(
            Vpn5gTrial.shouldStartTrial(
                true, "user@example.com",
                Vpn5gTrial.TrialRecord("user@example.com", now, 24),
                false,
            )
        )
        assertFalse(Vpn5gTrial.shouldStartTrial(true, "user@example.com", null, true))
    }

    @Test
    fun `records round trip through JSON`() {
        val records = mapOf(
            "user@example.com" to Vpn5gTrial.TrialRecord("user@example.com", now, 48),
            "other@example.com" to Vpn5gTrial.TrialRecord("other@example.com", now, 1),
        )
        val decoded = Vpn5gTrial.decodeRecords(Vpn5gTrial.encodeRecords(records))
        assertEquals(records, decoded)
        assertTrue(Vpn5gTrial.decodeRecords(null).isEmpty())
        assertTrue(Vpn5gTrial.decodeRecords("not json").isEmpty())
    }
}
