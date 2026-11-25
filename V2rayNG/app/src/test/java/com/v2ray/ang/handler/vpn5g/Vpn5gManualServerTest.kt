package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.5 §3 manual server resolver (pure Kotlin):
 * a manual pick is validated exactly like auto — exists, free, has a
 * ping, and did not fail tunnel verification within the 6h TTL. */
class Vpn5gManualServerTest {

    private fun server(
        guid: String,
        ping: Long,
        free: Boolean = true,
        isSeed: Boolean = false,
    ) = Vpn5gServerUi(
        guid = guid,
        name = guid,
        country = "بین‌الملل",
        pingMillis = ping,
        isSelected = false,
        isFree = free,
        isSeed = isSeed,
    )

    private fun section(servers: List<Vpn5gServerUi>) = Vpn5gCountryUi(
        label = "بین‌الملل",
        isFree = true,
        servers = servers,
    )

    private val sections = listOf(
        section(listOf(server("a", 100L), server("b", 200L, free = false), server("c", 0L))),
    )

    @Test
    fun test_usable_pick_resolves_to_server() {
        val result = Vpn5gManualServer.resolve(sections, "a", emptyMap(), 1_000_000L)
        assertNotNull(result.server)
        assertEquals("a", result.server?.guid)
        assertNull(result.problem)
    }

    @Test
    fun test_missing_guid_is_not_found() {
        val result = Vpn5gManualServer.resolve(sections, "gone", emptyMap(), 1_000_000L)
        assertNull(result.server)
        assertEquals(Vpn5gManualServer.Problem.NOT_FOUND, result.problem)
    }

    @Test
    fun test_blank_guid_is_not_found() {
        for (blank in listOf<String?>(null, "", "   ")) {
            val result = Vpn5gManualServer.resolve(sections, blank, emptyMap(), 1_000_000L)
            assertNull(result.server)
            assertEquals(Vpn5gManualServer.Problem.NOT_FOUND, result.problem)
        }
    }

    @Test
    fun test_premium_pick_is_not_free() {
        val result = Vpn5gManualServer.resolve(sections, "b", emptyMap(), 1_000_000L)
        assertNull(result.server)
        assertEquals(Vpn5gManualServer.Problem.NOT_FREE, result.problem)
    }

    @Test
    fun test_pick_without_ping_is_no_ping() {
        val result = Vpn5gManualServer.resolve(sections, "c", emptyMap(), 1_000_000L)
        assertNull(result.server)
        assertEquals(Vpn5gManualServer.Problem.NO_PING, result.problem)
    }

    @Test
    fun test_seed_pick_without_ping_resolves() {
        // v1.1.20: a seed server has no measured ping yet, but it is
        // free-eligible (build-time verified) and the connect chain
        // verifies the tunnel — a manual tap on it must work like a
        // tap on a real-pinged server.
        val seedSections = listOf(section(listOf(server("seed", 0L, isSeed = true))))
        val result = Vpn5gManualServer.resolve(seedSections, "seed", emptyMap(), 1_000_000L)
        assertNotNull(result.server)
        assertEquals("seed", result.server?.guid)
        assertNull(result.problem)
    }

    @Test
    fun test_recently_failed_pick_is_failed_recently() {
        val now = 1_000_000L
        val failed = Vpn5gFailedServers.mark(emptyMap(), "a", now)
        val result = Vpn5gManualServer.resolve(sections, "a", failed, now + 1_000L)
        assertNull(result.server)
        assertEquals(Vpn5gManualServer.Problem.FAILED_RECENTLY, result.problem)
        // After the 6h TTL the same pick is usable again.
        val afterTtl = Vpn5gManualServer.resolve(
            sections, "a", failed, now + Vpn5gFailedServers.TTL_MS + 1L
        )
        assertEquals("a", afterTtl.server?.guid)
        assertNull(afterTtl.problem)
    }
}
