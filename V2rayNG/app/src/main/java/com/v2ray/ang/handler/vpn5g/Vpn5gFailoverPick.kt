package com.v2ray.ang.handler.vpn5g

/**
 * Failover candidate selection with relaxation tiers (v1.1.29).
 *
 * Before 1.1.29 the drop failover and the verify loop each kept
 * their own copy of the same strict filter (tier-unlocked,
 * pingMillis > 0, not in the 6-hour failed store). On the FREE
 * tier that filter could empty the pool — 20 free servers, a few
 * dead, the rest in the failed store — and the chain fell into
 * 60-second empty-pool rounds that eventually gave up, stranding
 * the session on «در حال تغییر سرور...» with no way back but a
 * manual toggle (Aref's 2026-11-25 device report).
 *
 * [ranked] returns every candidate in failover order through
 * relaxation tiers, strictest first:
 *
 *  1. the pre-ranked standby queue ([standbyQueue], maintained by
 *     the in-session liveness probes) — alive right now, in queue
 *     order;
 *  2. tier-unlocked (isFree) with a measured ping, real-pinged
 *     first, then health-adjusted ping;
 *  3. any server with a measured ping (subscriber fill pool);
 *  4. tier-unlocked with ping >= 0 (seed / unknown-ping servers —
 *     the TCP-reachable fallback);
 *  5. any server not excluded, failed-store members last — a
 *     server that failed hours ago beats no server at all.
 *
 * Excluded guids (the dropped server, already-tried servers)
 * never appear. Within the returned order a different-country
 * candidate is preferred over the dropped server's country (see
 * [pick]). Pure, JVM-tested.
 */
object Vpn5gFailoverPick {

    /**
     * All failover candidates in priority order (see class doc).
     * [excluding] holds guids that must never be picked (dropped +
     * already tried); [failedGuids] is the 6-hour failed store;
     * [standbyQueue] is the pre-ranked replacement queue (guids,
     * best first); [healthScores] maps dedupeKey → health score.
     */
    fun ranked(
        servers: List<Vpn5gServerUi>,
        excluding: Set<String>,
        failedGuids: Set<String>,
        standbyQueue: List<String> = emptyList(),
        healthScores: Map<String, Double> = emptyMap(),
    ): List<Vpn5gServerUi> {
        val byGuid = servers.associateBy { it.guid }
        val eligible = servers.filter { it.guid !in excluding }
        fun healthAdjusted(server: Vpn5gServerUi): Long =
            Vpn5gHealthScore.adjustedPing(
                server.pingMillis, healthScores[server.dedupeKey] ?: 0.0
            )
        val byPing = compareBy<Vpn5gServerUi>({ !it.hasRealPing }, { healthAdjusted(it) })

        val tiers = mutableListOf<List<Vpn5gServerUi>>()
        // Tier 1: standby queue, in queue order (still-valid guids only).
        tiers += standbyQueue.mapNotNull { byGuid[it] }
            .filter { it.guid !in excluding && it.guid !in failedGuids }
        // Tier 2: tier-unlocked with a measured ping.
        tiers += eligible
            .filter { it.isFree && it.pingMillis > 0 && it.guid !in failedGuids }
            .sortedWith(byPing)
        // Tier 3: any server with a measured ping.
        tiers += eligible
            .filter { it.pingMillis > 0 && it.guid !in failedGuids }
            .sortedWith(byPing)
        // Tier 4: tier-unlocked, unknown ping allowed (seed / TCP fallback).
        tiers += eligible
            .filter { it.isFree && it.pingMillis >= 0 && it.guid !in failedGuids }
            .sortedWith(byPing)
        // Tier 5: anything left, failed-store members last.
        tiers += eligible
            .sortedWith(compareBy({ it.guid in failedGuids }, { !it.hasRealPing }, { healthAdjusted(it) }))

        val seen = mutableSetOf<String>()
        return tiers.flatten().filter { seen.add(it.guid) }
    }

    /**
     * The next failover target: the best ranked candidate,
     * preferring a different country than [differentCountryThan]
     * (the dropped server's country is avoided first); falls back
     * to the same country when nothing else is left. Null only
     * when every server is excluded.
     */
    fun pick(
        servers: List<Vpn5gServerUi>,
        excluding: Set<String>,
        failedGuids: Set<String>,
        standbyQueue: List<String> = emptyList(),
        healthScores: Map<String, Double> = emptyMap(),
        differentCountryThan: String? = null,
    ): Vpn5gServerUi? {
        val candidates = ranked(servers, excluding, failedGuids, standbyQueue, healthScores)
        if (differentCountryThan.isNullOrBlank()) return candidates.firstOrNull()
        return candidates.firstOrNull { it.country != differentCountryThan }
            ?: candidates.firstOrNull()
    }
}
