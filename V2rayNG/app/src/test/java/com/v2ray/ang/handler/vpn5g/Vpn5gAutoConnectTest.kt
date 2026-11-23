package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.4 §1 connect state machine, the best-server
 * picker and the failed-server TTL store (all pure Kotlin). */
class Vpn5gAutoConnectTest {

    private fun server(guid: String, ping: Long, free: Boolean = true, realPing: Boolean = true) =
        Vpn5gServerUi(
            guid = guid,
            name = guid,
            country = "بین‌الملل",
            pingMillis = ping,
            isSelected = false,
            isFree = free,
            // v1.1.7: the picker only trusts a real through-the-proxy ping.
            hasRealPing = realPing,
        )

    private fun section(servers: List<Vpn5gServerUi>) = Vpn5gCountryUi(
        label = "بین‌الملل",
        isFree = true,
        servers = servers,
    )

    // -- State machine: the happy path -------------------------------------

    @Test
    fun test_happy_path_to_connected() {
        var state = Vpn5gConnectState.IDLE
        state = Vpn5gAutoConnect.transition(state, Vpn5gConnectEvent.START)
        assertEquals(Vpn5gConnectState.CHECKING_INTERNET, state)
        state = Vpn5gAutoConnect.transition(state, Vpn5gConnectEvent.INTERNET_OK)
        assertEquals(Vpn5gConnectState.FINDING_SERVER, state)
        state = Vpn5gAutoConnect.transition(state, Vpn5gConnectEvent.SERVER_FOUND)
        assertEquals(Vpn5gConnectState.CONNECTING, state)
        state = Vpn5gAutoConnect.transition(state, Vpn5gConnectEvent.SERVICE_STARTED)
        assertEquals(Vpn5gConnectState.CREATING_TUNNEL, state)
        state = Vpn5gAutoConnect.transition(state, Vpn5gConnectEvent.TUNNEL_READY)
        assertEquals(Vpn5gConnectState.VERIFYING_TUNNEL, state)
        state = Vpn5gAutoConnect.transition(state, Vpn5gConnectEvent.VERIFIED)
        assertEquals(Vpn5gConnectState.CONNECTED, state)
    }

    // -- State machine: CONNECTED only after real verification --------------

    @Test
    fun test_connected_is_unreachable_without_verification() {
        // VERIFIED from any state other than VERIFYING_TUNNEL is a no-op —
        // including CREATING_TUNNEL, where the tunnel exists but has not
        // been verified yet.
        assertEquals(
            Vpn5gConnectState.IDLE,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.IDLE, Vpn5gConnectEvent.VERIFIED),
        )
        assertEquals(
            Vpn5gConnectState.CONNECTING,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CONNECTING, Vpn5gConnectEvent.VERIFIED),
        )
        assertEquals(
            Vpn5gConnectState.CHECKING_INTERNET,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CHECKING_INTERNET, Vpn5gConnectEvent.VERIFIED),
        )
        assertEquals(
            Vpn5gConnectState.CREATING_TUNNEL,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CREATING_TUNNEL, Vpn5gConnectEvent.VERIFIED),
        )
        assertEquals(
            Vpn5gConnectState.RETRYING,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.RETRYING, Vpn5gConnectEvent.VERIFIED),
        )
        assertEquals(
            Vpn5gConnectState.SWITCHING_SERVER,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.SWITCHING_SERVER, Vpn5gConnectEvent.VERIFIED),
        )
        // Only VERIFYING_TUNNEL + VERIFIED connects.
        assertEquals(
            Vpn5gConnectState.CONNECTED,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.VERIFYING_TUNNEL, Vpn5gConnectEvent.VERIFIED),
        )
        // SERVICE_STARTED alone never connects either: it only moves the
        // chain to CREATING_TUNNEL, where verification still has to pass.
        assertEquals(
            Vpn5gConnectState.CREATING_TUNNEL,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CONNECTING, Vpn5gConnectEvent.SERVICE_STARTED),
        )
    }

    @Test
    fun test_verify_failed_drops_connected_to_retrying() {
        assertEquals(
            Vpn5gConnectState.RETRYING,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CONNECTED, Vpn5gConnectEvent.VERIFY_FAILED),
        )
        // A failed verification while the tunnel is being built or
        // verified switches to the next server instead of retrying the
        // same one.
        assertEquals(
            Vpn5gConnectState.SWITCHING_SERVER,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CREATING_TUNNEL, Vpn5gConnectEvent.VERIFY_FAILED),
        )
        assertEquals(
            Vpn5gConnectState.SWITCHING_SERVER,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.VERIFYING_TUNNEL, Vpn5gConnectEvent.VERIFY_FAILED),
        )
    }

    @Test
    fun test_failures_land_in_retrying() {
        assertEquals(
            Vpn5gConnectState.RETRYING,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CHECKING_INTERNET, Vpn5gConnectEvent.INTERNET_FAILED),
        )
        assertEquals(
            Vpn5gConnectState.RETRYING,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.FINDING_SERVER, Vpn5gConnectEvent.SERVER_MISSING),
        )
        assertEquals(
            Vpn5gConnectState.RETRYING,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CONNECTING, Vpn5gConnectEvent.SERVICE_FAILED),
        )
    }

    @Test
    fun test_retrying_restarts_the_chain() {
        assertEquals(
            Vpn5gConnectState.CHECKING_INTERNET,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.RETRYING, Vpn5gConnectEvent.START),
        )
        assertEquals(
            Vpn5gConnectState.CREATING_TUNNEL,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.RETRYING, Vpn5gConnectEvent.SERVICE_STARTED),
        )
    }

    @Test
    fun test_restarting_switches_server() {
        // A restart/switch while connecting, connected or retrying lands
        // in SWITCHING_SERVER (v1.1.5 §2: the clean-restart failover);
        // the restart only returns to CREATING_TUNNEL once the daemon
        // reports the new core started.
        assertEquals(
            Vpn5gConnectState.SWITCHING_SERVER,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CONNECTED, Vpn5gConnectEvent.RESTARTING),
        )
        assertEquals(
            Vpn5gConnectState.SWITCHING_SERVER,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CONNECTING, Vpn5gConnectEvent.RESTARTING),
        )
        assertEquals(
            Vpn5gConnectState.SWITCHING_SERVER,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.RETRYING, Vpn5gConnectEvent.RESTARTING),
        )
        assertEquals(
            Vpn5gConnectState.SWITCHING_SERVER,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.VERIFYING_TUNNEL, Vpn5gConnectEvent.SWITCHING),
        )
        assertEquals(
            Vpn5gConnectState.CREATING_TUNNEL,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.SWITCHING_SERVER, Vpn5gConnectEvent.SERVICE_STARTED),
        )
        // While switching, further restart/switch/verify-failed events
        // are absorbed — the cycle is already in flight.
        assertEquals(
            Vpn5gConnectState.SWITCHING_SERVER,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.SWITCHING_SERVER, Vpn5gConnectEvent.VERIFY_FAILED),
        )
    }

    @Test
    fun test_user_disconnect_always_lands_in_idle() {
        for (from in Vpn5gConnectState.entries) {
            assertEquals(
                Vpn5gConnectState.IDLE,
                Vpn5gAutoConnect.transition(from, Vpn5gConnectEvent.USER_DISCONNECT),
                "USER_DISCONNECT from $from",
            )
        }
    }

    @Test
    fun test_give_up_lands_in_failed_except_idle_and_connected() {
        // v1.1.5 §2: giving up after a failed connect shows the precise
        // error (FAILED) instead of silently returning to IDLE. Only a
        // clean stop (CONNECTED — e.g. the 30-minute timer) and the
        // already-idle state stay IDLE.
        for (from in Vpn5gConnectState.entries) {
            val expected = when (from) {
                Vpn5gConnectState.IDLE -> Vpn5gConnectState.IDLE
                Vpn5gConnectState.CONNECTED -> Vpn5gConnectState.IDLE
                Vpn5gConnectState.FAILED -> Vpn5gConnectState.FAILED
                else -> Vpn5gConnectState.FAILED
            }
            assertEquals(
                expected,
                Vpn5gAutoConnect.transition(from, Vpn5gConnectEvent.GIVE_UP),
                "GIVE_UP from $from",
            )
        }
    }

    @Test
    fun test_failed_retries_from_start() {
        assertEquals(
            Vpn5gConnectState.CHECKING_INTERNET,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.FAILED, Vpn5gConnectEvent.START),
        )
        // Other events while failed are no-ops until the user retries.
        assertEquals(
            Vpn5gConnectState.FAILED,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.FAILED, Vpn5gConnectEvent.SERVER_FOUND),
        )
        assertEquals(
            Vpn5gConnectState.FAILED,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.FAILED, Vpn5gConnectEvent.VERIFIED),
        )
    }

    @Test
    fun test_tunnel_ready_moves_to_verifying() {
        assertEquals(
            Vpn5gConnectState.VERIFYING_TUNNEL,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CREATING_TUNNEL, Vpn5gConnectEvent.TUNNEL_READY),
        )
        // Repeated ready events while verifying are idempotent.
        assertEquals(
            Vpn5gConnectState.VERIFYING_TUNNEL,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.VERIFYING_TUNNEL, Vpn5gConnectEvent.TUNNEL_READY),
        )
    }

    @Test
    fun test_nonsense_events_are_no_ops() {
        assertEquals(
            Vpn5gConnectState.IDLE,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.IDLE, Vpn5gConnectEvent.SERVER_FOUND),
        )
        assertEquals(
            Vpn5gConnectState.CONNECTED,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.CONNECTED, Vpn5gConnectEvent.SERVICE_STARTED),
        )
        assertEquals(
            Vpn5gConnectState.FINDING_SERVER,
            Vpn5gAutoConnect.transition(Vpn5gConnectState.FINDING_SERVER, Vpn5gConnectEvent.VERIFIED),
        )
    }

    // -- Best-server picker ---------------------------------------------------

    @Test
    fun test_pick_best_server_fastest_free_with_ping() {
        val sections = listOf(
            section(listOf(server("a", 300L), server("b", 120L), server("c", 0L))),
            section(listOf(server("d", 90L, free = false))),
        )
        assertEquals("b", Vpn5gAutoConnect.pickBestServer(sections)?.guid)
    }

    @Test
    fun test_pick_best_server_excludes_failed_guids() {
        val sections = listOf(
            section(listOf(server("a", 100L), server("b", 200L))),
        )
        assertEquals(
            "b",
            Vpn5gAutoConnect.pickBestServer(sections, excluding = setOf("a"))?.guid,
        )
        assertNull(
            Vpn5gAutoConnect.pickBestServer(sections, excluding = setOf("a", "b")),
        )
    }

    @Test
    fun test_pick_best_server_empty_when_nothing_pinged() {
        val sections = listOf(section(listOf(server("a", 0L), server("b", -1L))))
        assertNull(Vpn5gAutoConnect.pickBestServer(sections))
        assertNull(Vpn5gAutoConnect.pickBestServer(emptyList()))
    }

    @Test
    fun test_pick_best_server_ignores_tcp_only() {
        // v1.1.7: a 1 ms TCP-only ping (a middlebox answering the handshake
        // in-transit) must never win the auto pick — the slower server
        // with a real through-the-proxy ping wins instead.
        val sections = listOf(
            section(
                listOf(
                    server("fake-fast", 1L, realPing = false),
                    server("real-slow", 900L, realPing = true),
                )
            ),
        )
        assertEquals("real-slow", Vpn5gAutoConnect.pickBestServer(sections)?.guid)
    }

    @Test
    fun test_pick_best_server_empty_when_nothing_real_pinged() {
        // v1.1.7: TCP-only servers are not pickable at all.
        val sections = listOf(
            section(listOf(server("a", 10L, realPing = false), server("b", 20L, realPing = false))),
        )
        assertNull(Vpn5gAutoConnect.pickBestServer(sections))
    }

    // -- Failed-server TTL store (pure functions) ------------------------------

    @Test
    fun test_failed_servers_mark_and_is_failed() {
        val now = 1_000_000L
        val entries = Vpn5gFailedServers.mark(emptyMap(), "guid-1", now)
        assertTrue(Vpn5gFailedServers.isFailed(entries, "guid-1", now + 1_000L))
        assertFalse(Vpn5gFailedServers.isFailed(entries, "guid-2", now + 1_000L))
        // After the 6h TTL the server is pickable again.
        assertFalse(
            Vpn5gFailedServers.isFailed(entries, "guid-1", now + Vpn5gFailedServers.TTL_MS),
        )
        assertFalse(
            Vpn5gFailedServers.isFailed(entries, "guid-1", now + Vpn5gFailedServers.TTL_MS + 1L),
        )
    }

    @Test
    fun test_failed_servers_prune() {
        val now = 10_000_000L
        val entries = mapOf(
            "fresh" to now - 1_000L,
            "stale" to now - Vpn5gFailedServers.TTL_MS - 1L,
            "zero" to 0L,
        )
        val pruned = Vpn5gFailedServers.prune(entries, now)
        assertEquals(setOf("fresh"), pruned.keys)
    }
}
