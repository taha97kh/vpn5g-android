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

    // v1.1.8: direct signup (scenario 1).

    @Test
    fun test_signup_rate_limited() {
        assertEquals("rate_limited", Vpn5gAuthService.signupErrorKey(429, ""))
    }

    @Test
    fun test_signup_email_already_registered() {
        assertEquals(
            "email_already_registered",
            Vpn5gAuthService.signupErrorKey(
                422, """{"msg":"User already registered"}"""
            ),
        )
        assertEquals(
            "email_already_registered",
            Vpn5gAuthService.signupErrorKey(422, "user already registered"),
        )
    }

    @Test
    fun test_signup_weak_password() {
        assertEquals(
            "weak_password",
            Vpn5gAuthService.signupErrorKey(
                400, """{"msg":"Password should be at least 6 characters."}"""
            ),
        )
    }

    @Test
    fun test_signup_other_status_is_generic() {
        assertEquals("signup_failed", Vpn5gAuthService.signupErrorKey(422, """{"msg":"other"}"""))
        assertEquals("signup_failed", Vpn5gAuthService.signupErrorKey(400, """{"msg":"other"}"""))
        assertEquals("signup_failed", Vpn5gAuthService.signupErrorKey(500, ""))
        assertEquals("signup_failed", Vpn5gAuthService.signupErrorKey(0, ""))
    }

    // v1.1.8: password login (scenario 4).

    @Test
    fun test_password_login_rate_limited() {
        assertEquals("rate_limited", Vpn5gAuthService.passwordLoginErrorKey(429, ""))
    }

    @Test
    fun test_password_login_invalid_credentials() {
        assertEquals(
            "invalid_credentials",
            Vpn5gAuthService.passwordLoginErrorKey(
                400, """{"msg":"Invalid login credentials"}"""
            ),
        )
    }

    @Test
    fun test_password_login_other_status_is_generic() {
        assertEquals("password_login_failed", Vpn5gAuthService.passwordLoginErrorKey(400, """{"msg":"other"}"""))
        assertEquals("password_login_failed", Vpn5gAuthService.passwordLoginErrorKey(500, ""))
        assertEquals("password_login_failed", Vpn5gAuthService.passwordLoginErrorKey(0, ""))
    }

    // v1.1.8: recovery link (scenario 3).

    @Test
    fun test_recovery_rate_limited() {
        assertEquals("rate_limited", Vpn5gAuthService.recoveryErrorKey(429))
    }

    @Test
    fun test_recovery_other_status_is_generic() {
        assertEquals("recovery_link_failed", Vpn5gAuthService.recoveryErrorKey(400))
        assertEquals("recovery_link_failed", Vpn5gAuthService.recoveryErrorKey(500))
        assertEquals("recovery_link_failed", Vpn5gAuthService.recoveryErrorKey(0))
    }

    @Test
    fun test_update_password_rate_limited() {
        assertEquals("rate_limited", Vpn5gAuthService.updatePasswordErrorKey(429))
    }

    @Test
    fun test_update_password_invalid_link() {
        for (status in listOf(400, 401, 403)) {
            assertEquals(
                "recovery_link_invalid",
                Vpn5gAuthService.updatePasswordErrorKey(status),
                "status $status",
            )
        }
    }

    @Test
    fun test_update_password_other_status_is_generic() {
        assertEquals("update_password_failed", Vpn5gAuthService.updatePasswordErrorKey(500))
        assertEquals("update_password_failed", Vpn5gAuthService.updatePasswordErrorKey(0))
    }

    // v1.1.8: session detection on a signup response.

    @Test
    fun test_has_session_with_access_token() {
        assertEquals(
            true,
            Vpn5gAuthService.hasSession(
                """{"access_token":"abc","refresh_token":"def","expires_in":3600}"""
            ),
        )
        assertEquals(
            true,
            Vpn5gAuthService.hasSession("""{"access_token" : "abc"}"""),
        )
    }

    @Test
    fun test_has_session_without_access_token() {
        // A user without a session: "Confirm email" is ON.
        assertEquals(
            false,
            Vpn5gAuthService.hasSession(
                """{"id":"u1","email":"a@gmail.com","aud":"authenticated"}"""
            ),
        )
        assertEquals(false, Vpn5gAuthService.hasSession(""))
        assertEquals(false, Vpn5gAuthService.hasSession("""{"access_token":""}"""))
    }
}
