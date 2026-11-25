package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.4 §1 connect state machine, the best-server
 * picker and the failed-server TTL store (all pure Kotlin). */
class Vpn5gAutoConnectTest {

    private fun server(
        guid: String,
        ping: Long,
        free: Boolean = true,
        realPing: Boolean = true,
        isSeed: Boolean = false,
        dedupeKey: String = "",
    ) =
        Vpn5gServerUi(
            guid = guid,
            name = guid,
            country = "بین‌الملل",
            pingMillis = ping,
            isSelected = false,
            isFree = free,
            // v1.1.7: the picker only trusts a real through-the-proxy ping.
            hasRealPing = realPing,
            // v1.1.20: a build-time verified seed server.
            isSeed = isSeed,
            dedupeKey = dedupeKey,
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
    fun test_user_disconnect_lands_in_disconnecting_except_idle_and_failed() {
        // v1.1.18: USER_DISCONNECT lands in DISCONNECTING from any
        // live state — the button must not flip to "Disconnected"
        // instantly while the daemon may still be stopping. From
        // IDLE / FAILED there is nothing to stop, and a repeated tap
        // while already disconnecting stays there.
        for (from in Vpn5gConnectState.entries) {
            val expected = when (from) {
                Vpn5gConnectState.IDLE,
                Vpn5gConnectState.FAILED,
                Vpn5gConnectState.DISCONNECTING -> from
                else -> Vpn5gConnectState.DISCONNECTING
            }
            assertEquals(
                expected,
                Vpn5gAutoConnect.transition(from, Vpn5gConnectEvent.USER_DISCONNECT),
                "USER_DISCONNECT from $from",
            )
        }
    }

    @Test
    fun test_disconnecting_restart_resumes_as_creating_tunnel() {
        // A restart racing the disconnect (server tap while stopping):
        // the new core started, so the chain resumes as a tunnel
        // being created — it will be verified like any other start.
        assertEquals(
            Vpn5gConnectState.CREATING_TUNNEL,
            Vpn5gAutoConnect.transition(
                Vpn5gConnectState.DISCONNECTING, Vpn5gConnectEvent.SERVICE_STARTED
            ),
        )
    }

    @Test
    fun test_disconnecting_absorbs_other_events() {
        // While disconnecting, connect-chain events are no-ops until
        // the stop lands or the timeout fires — a stray VERIFIED must
        // never show "connected" during a disconnect.
        for (event in listOf(
            Vpn5gConnectEvent.START,
            Vpn5gConnectEvent.INTERNET_OK,
            Vpn5gConnectEvent.SERVER_FOUND,
            Vpn5gConnectEvent.VERIFIED,
            Vpn5gConnectEvent.TUNNEL_READY,
            Vpn5gConnectEvent.VERIFY_FAILED,
            Vpn5gConnectEvent.RESTARTING,
        )) {
            assertEquals(
                Vpn5gConnectState.DISCONNECTING,
                Vpn5gAutoConnect.transition(Vpn5gConnectState.DISCONNECTING, event),
                "event=$event",
            )
        }
    }

    @Test
    fun test_disconnect_timeout_is_5_seconds() {
        assertEquals(5_000L, Vpn5gAutoConnect.DISCONNECT_TIMEOUT_MS)
    }

    @Test
    fun test_give_up_lands_in_failed_except_idle_connected_and_disconnecting() {
        // v1.1.5 §2: giving up after a failed connect shows the precise
        // error (FAILED) instead of silently returning to IDLE. Only a
        // clean stop (CONNECTED — e.g. the 30-minute timer) and the
        // already-idle state stay IDLE. v1.1.18: while DISCONNECTING
        // the stop is still in flight, so GIVE_UP stays there and the
        // timeout resolves it.
        for (from in Vpn5gConnectState.entries) {
            val expected = when (from) {
                Vpn5gConnectState.IDLE -> Vpn5gConnectState.IDLE
                Vpn5gConnectState.CONNECTED -> Vpn5gConnectState.IDLE
                Vpn5gConnectState.FAILED -> Vpn5gConnectState.FAILED
                Vpn5gConnectState.DISCONNECTING -> Vpn5gConnectState.DISCONNECTING
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
    fun test_pick_best_server_tcp_pool_when_nothing_real_pinged() {
        // v1.1.23: TCP-pinged servers inside the tier pool ARE
        // pickable when no real-pinged or seed server exists — this
        // is the subscriber/gift fill pool (locks open after redeem
        // must be usable for connection). Real still wins over TCP
        // (see test_pick_best_server_ignores_tcp_only).
        val sections = listOf(
            section(listOf(server("a", 10L, realPing = false), server("b", 20L, realPing = false))),
        )
        assertEquals("a", Vpn5gAutoConnect.pickBestServer(sections)?.guid)
    }

    @Test
    fun test_pick_best_server_provisional_fallback_outside_pool() {
        // v1.1.23: on entry, before the real probe verifies
        // anything (and with no seed trust), the fastest TCP-pinged
        // server overall is the provisional auto pick — the switch
        // must not sit in FINDING_SERVER with «هنوز سروری نیست»
        // while hundreds of servers are listed.
        val sections = listOf(
            section(
                listOf(
                    server("locked-fast", 5L, free = false, realPing = false),
                    server("locked-slow", 50L, free = false, realPing = false),
                    server("unpinged", 0L, free = false, realPing = false),
                )
            ),
        )
        assertEquals("locked-fast", Vpn5gAutoConnect.pickBestServer(sections)?.guid)
    }

    @Test
    fun test_pick_best_server_pool_tcp_beats_provisional() {
        // A TCP server inside the tier pool beats a faster TCP
        // server outside the pool.
        val sections = listOf(
            section(
                listOf(
                    server("outside", 1L, free = false, realPing = false),
                    server("in-pool", 100L, free = true, realPing = false),
                )
            ),
        )
        assertEquals("in-pool", Vpn5gAutoConnect.pickBestServer(sections)?.guid)
    }

    @Test
    fun test_pick_best_server_provisional_respects_excluding() {
        val sections = listOf(
            section(
                listOf(
                    server("a", 5L, free = false, realPing = false),
                    server("b", 9L, free = false, realPing = false),
                )
            ),
        )
        assertEquals(
            "b",
            Vpn5gAutoConnect.pickBestServer(sections, excluding = setOf("a"))?.guid,
        )
    }

    // -- v1.1.24: health-score ranking + fast serial failover -----------

    @Test
    fun test_pick_best_server_health_beats_raw_ping() {
        // A proven 300 ms server (score 3 -> adjusted 180) beats an
        // unproven 250 ms stranger.
        val sections = listOf(
            section(
                listOf(
                    server("stranger", 250L, dedupeKey = "key-stranger"),
                    server("proven", 300L, dedupeKey = "key-proven"),
                )
            ),
        )
        val scores = mapOf("key-proven" to 3.0)
        assertEquals(
            "proven",
            Vpn5gAutoConnect.pickBestServer(sections, healthScores = scores)?.guid,
        )
        // Without health history the raw fastest still wins (1.1.23 order).
        assertEquals("stranger", Vpn5gAutoConnect.pickBestServer(sections)?.guid)
    }

    @Test
    fun test_pick_best_server_bad_health_sinks() {
        // A failing 100 ms server (score -3 -> adjusted 220) loses to
        // a clean 200 ms server.
        val sections = listOf(
            section(
                listOf(
                    server("flaky", 100L, dedupeKey = "key-flaky"),
                    server("clean", 200L, dedupeKey = "key-clean"),
                )
            ),
        )
        val scores = mapOf("key-flaky" to -3.0)
        assertEquals(
            "clean",
            Vpn5gAutoConnect.pickBestServer(sections, healthScores = scores)?.guid,
        )
    }

    @Test
    fun test_rank_candidates_orders_by_health_adjusted_ping() {
        val sections = listOf(
            section(
                listOf(
                    server("a", 400L, dedupeKey = "key-a"),
                    server("b", 200L, dedupeKey = "key-b"),
                    server("c", 300L, dedupeKey = "key-c"),
                )
            ),
        )
        val scores = mapOf("key-a" to 5.0) // 400 - 200 = 200, ties b on ping, order stable by input
        val ranked = Vpn5gAutoConnect.rankCandidates(sections, healthScores = scores)
        assertEquals(listOf("a", "b", "c"), ranked.map { it.guid })
        // No history: plain ping order.
        val plain = Vpn5gAutoConnect.rankCandidates(sections)
        assertEquals(listOf("b", "c", "a"), plain.map { it.guid })
    }

    @Test
    fun test_rank_candidates_respects_excluding_and_tier_steps() {
        // Real-pinged step wins over TCP pool even with health scores.
        val sections = listOf(
            section(
                listOf(
                    server("tcp-fast", 10L, realPing = false, dedupeKey = "key-tcp"),
                    server("real-slow", 900L, realPing = true, dedupeKey = "key-real"),
                )
            ),
        )
        val ranked = Vpn5gAutoConnect.rankCandidates(
            sections, healthScores = mapOf("key-tcp" to 5.0),
        )
        assertEquals(listOf("real-slow"), ranked.map { it.guid })
        assertTrue(
            Vpn5gAutoConnect.rankCandidates(sections, excluding = setOf("real-slow", "tcp-fast"))
                .isEmpty(),
        )
    }

    @Test
    fun test_verify_timeout_short_for_first_attempts() {
        assertEquals(
            Vpn5gAutoConnect.FAST_VERIFY_TIMEOUT_MS,
            Vpn5gAutoConnect.verifyTimeoutMsForAttempt(0),
        )
        assertEquals(
            Vpn5gAutoConnect.FAST_VERIFY_TIMEOUT_MS,
            Vpn5gAutoConnect.verifyTimeoutMsForAttempt(2),
        )
        assertEquals(
            Vpn5gAutoConnect.FULL_VERIFY_TIMEOUT_MS,
            Vpn5gAutoConnect.verifyTimeoutMsForAttempt(3),
        )
        assertEquals(
            Vpn5gAutoConnect.FULL_VERIFY_TIMEOUT_MS,
            Vpn5gAutoConnect.verifyTimeoutMsForAttempt(10),
        )
    }

    // -- v1.1.23: switch tap means OFF while the chain is live ---------

    @Test
    fun test_connect_active_while_testing_or_connecting() {
        // Daemon not started yet (isRunning false) but the chain is
        // live — a tap must cancel, never restart the chain.
        val live = listOf(
            Vpn5gConnectState.CHECKING_INTERNET,
            Vpn5gConnectState.FINDING_SERVER,
            Vpn5gConnectState.CONNECTING,
            Vpn5gConnectState.CREATING_TUNNEL,
            Vpn5gConnectState.VERIFYING_TUNNEL,
            Vpn5gConnectState.SWITCHING_SERVER,
            Vpn5gConnectState.RETRYING,
            Vpn5gConnectState.DISCONNECTING,
        )
        live.forEach { state ->
            assertTrue(Vpn5gAutoConnect.isConnectActive(state, isRunning = false), "state $state")
        }
    }

    @Test
    fun test_connect_active_when_running_or_connected() {
        assertTrue(Vpn5gAutoConnect.isConnectActive(Vpn5gConnectState.CONNECTED, isRunning = true))
        assertTrue(Vpn5gAutoConnect.isConnectActive(Vpn5gConnectState.IDLE, isRunning = true))
    }

    @Test
    fun test_connect_not_active_when_idle_or_failed() {
        assertFalse(Vpn5gAutoConnect.isConnectActive(Vpn5gConnectState.IDLE, isRunning = false))
        assertFalse(Vpn5gAutoConnect.isConnectActive(Vpn5gConnectState.FAILED, isRunning = false))
    }

    // -- v1.1.20: seed fallback (fresh-install auto-connect) --------------

    @Test
    fun test_pick_best_server_falls_back_to_seed() {
        // No real-pinged server yet (fresh install) — the build-time
        // verified seed is the pick; the connect chain still verifies
        // the tunnel.
        val sections = listOf(
            section(listOf(server("seed-a", 0L, realPing = false, isSeed = true))),
        )
        assertEquals("seed-a", Vpn5gAutoConnect.pickBestServer(sections)?.guid)
    }

    @Test
    fun test_pick_best_server_prefers_real_over_seed() {
        val sections = listOf(
            section(
                listOf(
                    server("seed-a", 0L, realPing = false, isSeed = true),
                    server("real", 900L, realPing = true),
                )
            ),
        )
        assertEquals("real", Vpn5gAutoConnect.pickBestServer(sections)?.guid)
    }

    @Test
    fun test_pick_best_server_ignores_dead_seed() {
        // A seed the real probe failed is not pickable.
        val sections = listOf(
            section(listOf(server("dead-seed", -1L, realPing = false, isSeed = true))),
        )
        assertNull(Vpn5gAutoConnect.pickBestServer(sections))
    }

    @Test
    fun test_pick_best_server_seed_respects_excluding() {
        val sections = listOf(
            section(
                listOf(
                    server("seed-a", 0L, realPing = false, isSeed = true),
                    server("seed-b", 0L, realPing = false, isSeed = true),
                )
            ),
        )
        assertEquals(
            "seed-b",
            Vpn5gAutoConnect.pickBestServer(sections, excluding = setOf("seed-a"))?.guid,
        )
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

    // -- Startup auto-connect decision (v1.1.17, pure) ------------------------

    @Test
    fun test_startup_auto_connect_fires_when_a_server_is_pickable() {
        assertTrue(
            Vpn5gAutoConnect.shouldStartupAutoConnect(
                startupAutoConnectDone = false,
                isRunning = false,
                userStopRequested = false,
                connectState = Vpn5gConnectState.IDLE,
                hasPickableServer = true,
            )
        )
    }

    @Test
    fun test_startup_auto_connect_never_fires_twice() {
        assertFalse(
            Vpn5gAutoConnect.shouldStartupAutoConnect(
                startupAutoConnectDone = true,
                isRunning = false,
                userStopRequested = false,
                connectState = Vpn5gConnectState.IDLE,
                hasPickableServer = true,
            )
        )
    }

    @Test
    fun test_startup_auto_connect_blocked_while_running_or_stopping() {
        // Already connected: no second chain.
        assertFalse(
            Vpn5gAutoConnect.shouldStartupAutoConnect(
                startupAutoConnectDone = false,
                isRunning = true,
                userStopRequested = false,
                connectState = Vpn5gConnectState.IDLE,
                hasPickableServer = true,
            )
        )
        // The user asked to stop: a later refresh must not reconnect.
        assertFalse(
            Vpn5gAutoConnect.shouldStartupAutoConnect(
                startupAutoConnectDone = false,
                isRunning = false,
                userStopRequested = true,
                connectState = Vpn5gConnectState.IDLE,
                hasPickableServer = true,
            )
        )
    }

    @Test
    fun test_startup_auto_connect_blocked_when_chain_busy_or_failed() {
        for (state in listOf(
            Vpn5gConnectState.CHECKING_INTERNET,
            Vpn5gConnectState.FINDING_SERVER,
            Vpn5gConnectState.CONNECTING,
            Vpn5gConnectState.CONNECTED,
            Vpn5gConnectState.RETRYING,
            Vpn5gConnectState.FAILED,
            Vpn5gConnectState.DISCONNECTING,
        )) {
            assertFalse(
                Vpn5gAutoConnect.shouldStartupAutoConnect(
                    startupAutoConnectDone = false,
                    isRunning = false,
                    userStopRequested = false,
                    connectState = state,
                    hasPickableServer = true,
                ),
                "state=$state",
            )
        }
    }

    @Test
    fun test_startup_auto_connect_blocked_without_a_pickable_server() {
        assertFalse(
            Vpn5gAutoConnect.shouldStartupAutoConnect(
                startupAutoConnectDone = false,
                isRunning = false,
                userStopRequested = false,
                connectState = Vpn5gConnectState.IDLE,
                hasPickableServer = false,
            )
        )
    }
}
