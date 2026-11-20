package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** JVM tests for the Gmail-only registration policy (pure Kotlin). */
class Vpn5gEmailPolicyTest {

    @Test
    fun test_gmail_accepted() {
        assertEquals(
            Vpn5gEmailPolicy.Check.OK,
            Vpn5gEmailPolicy.check("user@gmail.com"),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.OK,
            Vpn5gEmailPolicy.check("  User.Name+tag@GMAIL.COM  "),
        )
    }

    @Test
    fun test_non_gmail_rejected() {
        assertEquals(
            Vpn5gEmailPolicy.Check.NOT_GMAIL,
            Vpn5gEmailPolicy.check("user@yahoo.com"),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.NOT_GMAIL,
            Vpn5gEmailPolicy.check("user@googlemail.com"),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.NOT_GMAIL,
            Vpn5gEmailPolicy.check("user@gmail.com.evil.com"),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.NOT_GMAIL,
            Vpn5gEmailPolicy.check("user@fakegmail.com"),
        )
    }

    @Test
    fun test_disposable_rejected() {
        assertEquals(
            Vpn5gEmailPolicy.Check.DISPOSABLE,
            Vpn5gEmailPolicy.check("user@mailinator.com"),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.DISPOSABLE,
            Vpn5gEmailPolicy.check("user@10minutemail.com"),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.DISPOSABLE,
            Vpn5gEmailPolicy.check("user@tempmail.com"),
        )
    }

    @Test
    fun test_malformed_rejected() {
        assertEquals(
            Vpn5gEmailPolicy.Check.INVALID_FORMAT,
            Vpn5gEmailPolicy.check(""),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.INVALID_FORMAT,
            Vpn5gEmailPolicy.check("not-an-email"),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.INVALID_FORMAT,
            Vpn5gEmailPolicy.check("@gmail.com"),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.INVALID_FORMAT,
            Vpn5gEmailPolicy.check("user@"),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.INVALID_FORMAT,
            Vpn5gEmailPolicy.check("user @gmail.com"),
        )
        assertEquals(
            Vpn5gEmailPolicy.Check.INVALID_FORMAT,
            Vpn5gEmailPolicy.check("user@gmail"),
        )
    }
}
