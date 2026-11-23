package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the v1.1.6 config sources: the four new GitHub sources
 * are in the default list and classified as large (sampled) files, and
 * base64-wrapped subscription bodies (MatinGhanbari super-sub, our own
 * HF sub.txt) decode to plain config-URI lines. Pure functions only —
 * no Android APIs, no network.
 */
class Vpn5gConfigSourcesTest {

    @Test
    fun test_default_sources_include_new_github_sources() {
        val sources = Vpn5gConstants.DEFAULT_CONFIG_SOURCES
        assertEquals(Vpn5gConstants.LIVE_JSON, sources.first())
        assertTrue(sources.contains(Vpn5gConstants.SUB_TXT))
        assertTrue(sources.contains(Vpn5gConstants.GITHUB_SOURCE_1))
        assertTrue(sources.contains(Vpn5gConstants.GITHUB_SOURCE_2))
        assertTrue(sources.contains(Vpn5gConstants.GITHUB_SOURCE_3))
        assertTrue(sources.contains(Vpn5gConstants.GITHUB_SOURCE_4))
        assertTrue(sources.contains(Vpn5gConstants.GITHUB_SOURCE_5))
        assertTrue(sources.contains(Vpn5gConstants.GITHUB_SOURCE_6))
        assertEquals(8, sources.size)
        // Every default source is HTTPS — never plain HTTP.
        assertTrue(sources.all { it.startsWith("https://") })
    }

    @Test
    fun test_capped_sources_are_the_github_files_only() {
        listOf(
            Vpn5gConstants.GITHUB_SOURCE_1,
            Vpn5gConstants.GITHUB_SOURCE_2,
            Vpn5gConstants.GITHUB_SOURCE_3,
            Vpn5gConstants.GITHUB_SOURCE_4,
            Vpn5gConstants.GITHUB_SOURCE_5,
            Vpn5gConstants.GITHUB_SOURCE_6,
        ).forEach { url ->
            assertTrue(Vpn5gConstants.isCappedSource(url), url)
        }
        assertFalse(Vpn5gConstants.isCappedSource(Vpn5gConstants.LIVE_JSON))
        assertFalse(Vpn5gConstants.isCappedSource(Vpn5gConstants.SUB_TXT))
        assertFalse(Vpn5gConstants.isCappedSource(Vpn5gConstants.APP_CONFIG))
    }

    @Test
    fun test_plain_body_is_returned_unchanged() {
        val body = "vless://11111111-2222-3333-4444-555555555555@1.2.3.4:443#test\n" +
            "ss://YWVzLTEyOC1nY206cGFzc3dvcmQ@5.6.7.8:8388#two\n"
        assertEquals(body, Vpn5gSync.decodeSubscriptionBody(body))
    }

    @Test
    fun test_base64_body_decodes_to_uri_lines() {
        // base64 of two config-URI lines (vless + ss), as published by
        // MatinGhanbari super-sub and our own HF sub.txt.
        val blob = "dmxlc3M6Ly8xMTExMTExMS0yMjIyLTMzMzMtNDQ0NC01NTU1NTU1NTU1NTVAMS4yLjMuNDo0NDMjb25lCnNzOi8vWVdWekxURXlPQzFuWTIwNmNHRnpjM2R2Y21RQDUuNi43Ljg6ODM4OCN0d28K"
        val decoded = Vpn5gSync.decodeSubscriptionBody(blob)
        assertTrue(decoded.contains("vless://11111111-2222-3333-4444-555555555555@1.2.3.4:443#one"))
        assertTrue(decoded.contains("ss://YWVzLTEyOC1nY206cGFzc3dvcmQ@5.6.7.8:8388#two"))
    }

    @Test
    fun test_base64_body_with_line_breaks_decodes() {
        val blob = "dmxlc3M6Ly8xMTExMTExMS0yMjIyLTMzMzMtNDQ0NC01NTU1NTU1NTU1\nNTVAMS4yLjMuNDo0NDMjdGVzdA==\n"
        val decoded = Vpn5gSync.decodeSubscriptionBody(blob)
        assertEquals(
            "vless://11111111-2222-3333-4444-555555555555@1.2.3.4:443#test",
            decoded,
        )
    }

    @Test
    fun test_garbage_and_non_uri_base64_are_returned_unchanged() {
        assertEquals("not base64 at all!!!", Vpn5gSync.decodeSubscriptionBody("not base64 at all!!!"))
        assertEquals("", Vpn5gSync.decodeSubscriptionBody(""))
        // Valid base64, but decodes to text with no config-URI line.
        val noUri = java.util.Base64.getEncoder()
            .encodeToString("just some plain text, no configs here".toByteArray(Charsets.UTF_8))
        assertEquals(noUri, Vpn5gSync.decodeSubscriptionBody(noUri))
    }
}
