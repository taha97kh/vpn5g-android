package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for «حالت امن اینستاگرام» (v1.1.34): the pure pin
 * decisions — normalization, when the pin is active, the server
 * filters, and the default country at enable time.
 */
class Vpn5gInstagramSafeModeTest {

    private fun server(
        guid: String,
        ping: Long = 100,
        countryCode: String = "",
        realPing: Boolean = true,
    ) = Vpn5gServerUi(
        guid = guid,
        name = guid,
        country = "بین‌الملل",
        pingMillis = ping,
        isSelected = false,
        isFree = true,
        countryCode = countryCode,
        hasRealPing = realPing,
    )

    // -- Normalization ---------------------------------------------------

    @Test
    fun test_normalize_code() {
        assertEquals("DE", Vpn5gInstagramSafeMode.normalizeCode("de"))
        assertEquals("DE", Vpn5gInstagramSafeMode.normalizeCode(" de "))
        assertEquals("", Vpn5gInstagramSafeMode.normalizeCode(""))
        assertEquals("", Vpn5gInstagramSafeMode.normalizeCode("   "))
        assertEquals("", Vpn5gInstagramSafeMode.normalizeCode(null))
    }

    // -- When the pin is active -------------------------------------------

    @Test
    fun test_pinned_country_null_when_off_or_unset() {
        // Mode off: no pin, whatever is stored.
        assertNull(Vpn5gInstagramSafeMode.pinnedCountry(false, "DE"))
        assertNull(Vpn5gInstagramSafeMode.pinnedCountry(false, ""))
        assertNull(Vpn5gInstagramSafeMode.pinnedCountry(false, null))
        // Mode on but no country chosen yet: still no pin.
        assertNull(Vpn5gInstagramSafeMode.pinnedCountry(true, ""))
        assertNull(Vpn5gInstagramSafeMode.pinnedCountry(true, "  "))
        assertNull(Vpn5gInstagramSafeMode.pinnedCountry(true, null))
        // Mode on with a country: normalized pin.
        assertEquals("DE", Vpn5gInstagramSafeMode.pinnedCountry(true, "de"))
        assertEquals("US", Vpn5gInstagramSafeMode.pinnedCountry(true, " US "))
    }

    @Test
    fun test_matches_pinned() {
        // Null pin matches everything (mode off = pre-1.1.34).
        assertTrue(Vpn5gInstagramSafeMode.matchesPinned("DE", null))
        assertTrue(Vpn5gInstagramSafeMode.matchesPinned("", null))
        assertTrue(Vpn5gInstagramSafeMode.matchesPinned(null, null))
        // Pinned: case-insensitive code equality only.
        assertTrue(Vpn5gInstagramSafeMode.matchesPinned("DE", "DE"))
        assertTrue(Vpn5gInstagramSafeMode.matchesPinned("de", "DE"))
        assertFalse(Vpn5gInstagramSafeMode.matchesPinned("US", "DE"))
        assertFalse(Vpn5gInstagramSafeMode.matchesPinned("", "DE"))
        assertFalse(Vpn5gInstagramSafeMode.matchesPinned(null, "DE"))
    }

    // -- Filters ------------------------------------------------------------

    @Test
    fun test_filter_ui_servers() {
        val servers = listOf(
            server("de1", countryCode = "DE"),
            server("us1", countryCode = "US"),
            server("de2", countryCode = "de"),
            server("none", countryCode = ""),
        )
        // Null pin: the list is returned untouched.
        assertEquals(servers, Vpn5gInstagramSafeMode.filterUiServers(servers, null))
        assertEquals(
            listOf("de1", "de2"),
            Vpn5gInstagramSafeMode.filterUiServers(servers, "DE").map { it.guid },
        )
        // Exhausted pinned country: empty, never another country.
        assertTrue(Vpn5gInstagramSafeMode.filterUiServers(servers, "FR").isEmpty())
    }

    @Test
    fun test_filter_daemon_candidates() {
        fun candidate(guid: String, code: String) = Vpn5gDaemonPick.Candidate(
            guid = guid, delayMs = 100, hasRealPing = true,
            adjustedDelayMs = 100, countryCode = code,
        )
        val candidates = listOf(
            candidate("de1", "DE"), candidate("us1", "US"), candidate("none", ""),
        )
        assertEquals(
            candidates,
            Vpn5gInstagramSafeMode.filterDaemonCandidates(candidates, null),
        )
        assertEquals(
            listOf("de1"),
            Vpn5gInstagramSafeMode.filterDaemonCandidates(candidates, "DE")
                .map { it.guid },
        )
        assertTrue(
            Vpn5gInstagramSafeMode.filterDaemonCandidates(candidates, "NL").isEmpty()
        )
    }

    // -- Default country at enable time -------------------------------------

    @Test
    fun test_default_country_prefers_current_server() {
        val servers = listOf(
            server("fastUs", ping = 10, countryCode = "US"),
            server("currentDe", ping = 900, countryCode = "DE"),
        )
        // Connected: the live session's country wins, however slow.
        assertEquals(
            "DE",
            Vpn5gInstagramSafeMode.defaultCountryCode(servers, currentGuid = "currentDe"),
        )
        // Unknown current guid falls through to fastest real-pinged.
        assertEquals(
            "US",
            Vpn5gInstagramSafeMode.defaultCountryCode(servers, currentGuid = "missing"),
        )
        assertEquals("US", Vpn5gInstagramSafeMode.defaultCountryCode(servers))
    }

    @Test
    fun test_default_country_fastest_real_then_fastest_then_first() {
        // A faster TCP-only ping never beats a real-pinged server.
        val mixed = listOf(
            server("tcpFast", ping = 5, countryCode = "NL", realPing = false),
            server("realSlow", ping = 500, countryCode = "DE", realPing = true),
        )
        assertEquals("DE", Vpn5gInstagramSafeMode.defaultCountryCode(mixed))
        // No real ping anywhere: fastest measured wins.
        val tcpOnly = listOf(
            server("slow", ping = 800, countryCode = "FR", realPing = false),
            server("fast", ping = 50, countryCode = "NL", realPing = false),
        )
        assertEquals("NL", Vpn5gInstagramSafeMode.defaultCountryCode(tcpOnly))
        // Nothing measured: first coded server.
        val unmeasured = listOf(
            server("nocode", ping = 0, countryCode = ""),
            server("first", ping = 0, countryCode = "CA"),
            server("second", ping = 0, countryCode = "US"),
        )
        assertEquals("CA", Vpn5gInstagramSafeMode.defaultCountryCode(unmeasured))
    }

    @Test
    fun test_default_country_null_without_codes() {
        assertNull(Vpn5gInstagramSafeMode.defaultCountryCode(emptyList()))
        assertNull(
            Vpn5gInstagramSafeMode.defaultCountryCode(
                listOf(server("a", countryCode = ""), server("b", countryCode = " ")),
            )
        )
        // Codes are normalized on the way out.
        assertEquals(
            "DE",
            Vpn5gInstagramSafeMode.defaultCountryCode(
                listOf(server("a", countryCode = "de")),
            ),
        )
    }
}
