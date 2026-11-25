package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM tests for the v1.1.29 standby queue ranking (pure part):
 * alive only, TLS-proven first, fastest TCP delay, capped.
 */
class Vpn5gStandbyLivenessTest {

    private fun result(guid: String, tcp: Long, tls: Boolean = false) =
        Vpn5gStandbyLiveness.Result(guid, tcp, tls)

    @Test
    fun test_dead_results_never_enter_queue() {
        val queue = Vpn5gStandbyLiveness.rankQueue(
            listOf(result("dead", -1), result("alive", 100)),
        )
        assertEquals(listOf("alive"), queue)
    }

    @Test
    fun test_tls_proven_first_then_fastest() {
        val queue = Vpn5gStandbyLiveness.rankQueue(
            listOf(
                result("tcpFast", 50, tls = false),
                result("tlsSlow", 500, tls = true),
                result("tlsFast", 100, tls = true),
                result("tcpSlow", 900, tls = false),
            ),
        )
        assertEquals(listOf("tlsFast", "tlsSlow", "tcpFast", "tcpSlow"), queue)
    }

    @Test
    fun test_queue_capped_at_limit() {
        val results = (1..10).map { result("g$it", it * 10L) }
        val queue = Vpn5gStandbyLiveness.rankQueue(results)
        assertEquals(Vpn5gConstants.STANDBY_QUEUE_SIZE, queue.size)
        assertEquals(listOf("g1", "g2", "g3", "g4", "g5"), queue)
        assertEquals(2, Vpn5gStandbyLiveness.rankQueue(results, limit = 2).size)
    }

    @Test
    fun test_empty_and_all_dead() {
        assertEquals(emptyList<String>(), Vpn5gStandbyLiveness.rankQueue(emptyList()))
        assertEquals(
            emptyList<String>(),
            Vpn5gStandbyLiveness.rankQueue(listOf(result("x", -5), result("y", -1))),
        )
    }
}
