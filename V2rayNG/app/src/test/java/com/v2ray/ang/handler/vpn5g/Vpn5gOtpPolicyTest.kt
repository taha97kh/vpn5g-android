package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.5 §7 OTP policy (pure Kotlin): resend is
 * limited to once per 60 seconds, and 5 wrong codes in a session
 * require a fresh code. */
class Vpn5gOtpPolicyTest {

    private val sentAt = 1_000_000L

    @Test
    fun test_resend_blocked_during_cooldown() {
        assertFalse(Vpn5gOtpPolicy.canResend(sentAt, sentAt))
        assertFalse(Vpn5gOtpPolicy.canResend(sentAt, sentAt + 59_000L))
        assertEquals(60L, Vpn5gOtpPolicy.resendWaitSeconds(sentAt, sentAt))
        assertEquals(1L, Vpn5gOtpPolicy.resendWaitSeconds(sentAt, sentAt + 59_000L))
    }

    @Test
    fun test_resend_allowed_after_cooldown() {
        assertTrue(Vpn5gOtpPolicy.canResend(sentAt, sentAt + 60_000L))
        assertTrue(Vpn5gOtpPolicy.canResend(sentAt, sentAt + 120_000L))
        assertEquals(0L, Vpn5gOtpPolicy.resendWaitSeconds(sentAt, sentAt + 60_000L))
    }

    @Test
    fun test_resend_allowed_when_no_code_was_sent() {
        assertTrue(Vpn5gOtpPolicy.canResend(0L, sentAt))
        assertEquals(0L, Vpn5gOtpPolicy.resendWaitSeconds(0L, sentAt))
    }

    @Test
    fun test_lockout_after_five_wrong_codes() {
        assertFalse(Vpn5gOtpPolicy.isLockedOut(0))
        assertFalse(Vpn5gOtpPolicy.isLockedOut(4))
        assertTrue(Vpn5gOtpPolicy.isLockedOut(5))
        assertTrue(Vpn5gOtpPolicy.isLockedOut(6))
        assertEquals(5, Vpn5gOtpPolicy.MAX_WRONG_CODE_ATTEMPTS)
        assertEquals(60, Vpn5gOtpPolicy.RESEND_COOLDOWN_SECONDS)
    }
}
