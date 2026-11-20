package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** JVM tests for the Supabase OTP error-key mapping (pure Kotlin). */
class Vpn5gAuthErrorKeyTest {

    @Test
    fun test_send_rate_limited() {
        assertEquals("rate_limited", Vpn5gAuthService.sendErrorKey(429))
    }

    @Test
    fun test_send_email_not_registered() {
        assertEquals("email_not_registered", Vpn5gAuthService.sendErrorKey(422))
    }

    @Test
    fun test_send_other_status_is_generic() {
        assertEquals("otp_failed", Vpn5gAuthService.sendErrorKey(400))
        assertEquals("otp_failed", Vpn5gAuthService.sendErrorKey(500))
        assertEquals("otp_failed", Vpn5gAuthService.sendErrorKey(0))
    }

    @Test
    fun test_verify_wrong_or_expired_code() {
        for (status in listOf(400, 401, 403, 404, 422)) {
            assertEquals(
                "code_wrong_or_expired",
                Vpn5gAuthService.verifyErrorKey(status),
                "status $status",
            )
        }
    }

    @Test
    fun test_verify_rate_limited() {
        assertEquals("rate_limited", Vpn5gAuthService.verifyErrorKey(429))
    }

    @Test
    fun test_verify_other_status_is_generic() {
        assertEquals("verify_failed", Vpn5gAuthService.verifyErrorKey(500))
        assertEquals("verify_failed", Vpn5gAuthService.verifyErrorKey(0))
    }
}
