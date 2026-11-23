package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.9 one-account-per-IP+email guard (pure Kotlin). */
class Vpn5gRegistrationGuardTest {

    @Test
    fun test_empty_array_is_ok() {
        assertEquals(
            Vpn5gRegistrationGuard.CheckOutcome.OK,
            Vpn5gRegistrationGuard.outcome("[]"),
        )
        assertEquals(
            Vpn5gRegistrationGuard.CheckOutcome.OK,
            Vpn5gRegistrationGuard.outcome("  []\n"),
        )
        assertNull(
            Vpn5gRegistrationGuard.errorKey(
                Vpn5gRegistrationGuard.CheckOutcome.OK
            )
        )
    }

    @Test
    fun test_email_taken() {
        assertEquals(
            Vpn5gRegistrationGuard.CheckOutcome.EMAIL_TAKEN,
            Vpn5gRegistrationGuard.outcome("""[{"kind":"email"}]"""),
        )
        assertEquals(
            "email_already_registered",
            Vpn5gRegistrationGuard.errorKey(
                Vpn5gRegistrationGuard.CheckOutcome.EMAIL_TAKEN
            ),
        )
    }

    @Test
    fun test_ip_taken() {
        assertEquals(
            Vpn5gRegistrationGuard.CheckOutcome.IP_TAKEN,
            Vpn5gRegistrationGuard.outcome("""[{"kind":"ip"}]"""),
        )
        assertEquals(
            "ip_already_registered",
            Vpn5gRegistrationGuard.errorKey(
                Vpn5gRegistrationGuard.CheckOutcome.IP_TAKEN
            ),
        )
    }

    @Test
    fun test_both_taken_email_wins() {
        assertEquals(
            Vpn5gRegistrationGuard.CheckOutcome.EMAIL_TAKEN,
            Vpn5gRegistrationGuard.outcome(
                """[{"kind":"email"},{"kind":"ip"}]"""
            ),
        )
        assertEquals(
            Vpn5gRegistrationGuard.CheckOutcome.EMAIL_TAKEN,
            Vpn5gRegistrationGuard.outcome(
                """[{"kind":"ip"},{"kind":"email"}]"""
            ),
        )
    }

    @Test
    fun test_unparseable_body_is_null() {
        // Fail closed: a body that is not a JSON array must never be
        // read as "free".
        assertNull(Vpn5gRegistrationGuard.outcome(""))
        assertNull(Vpn5gRegistrationGuard.outcome("not json"))
        assertNull(Vpn5gRegistrationGuard.outcome("""{"kind":"ip"}"""))
        assertNull(Vpn5gRegistrationGuard.parseKinds("error"))
    }

    @Test
    fun test_parse_kinds_tolerates_spacing() {
        assertEquals(
            setOf("email"),
            Vpn5gRegistrationGuard.parseKinds("""[ { "kind" : "email" } ]"""),
        )
    }
}
