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
 * never appear.
 *
 * v1.1.30 (Aref's order 2026-11-26): WITHIN each tier the order is
 * country-grouped — all servers of one country come before the
 * next country, in the priority US → DE → CA → sanction-bypassing
 * countries → the rest ([Vpn5gCountryGroups.priorityOf]), then by
 * the tier's own comparator. The failover chain therefore exhausts
 * one country before jumping to the next, exactly as ordered,
 * while the relaxation tiers above still guarantee the chain never
 * strands. [pick] follows the same grouped order (the dropped
 * server itself is excluded by the caller; its country-mates are
 * still tried before the next country). Pure, JVM-tested.
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
        // v1.1.30: country-grouped order — country priority first
        // (US → DE → CA → sanction-bypass → rest → unknown), then
        // the tier comparator (real-pinged first, fastest first).
        val byCountryThenPing = compareBy<Vpn5gServerUi>(
            { Vpn5gCountryGroups.priorityOf(it.countryCode) },
            { !it.hasRealPing },
            { healthAdjusted(it) },
        )

        val tiers = mutableListOf<List<Vpn5gServerUi>>()
        // Tier 1: standby queue, country-grouped within the queue
        // (stable sort keeps queue order inside one country).
        tiers += standbyQueue.mapNotNull { byGuid[it] }
            .filter { it.guid !in excluding && it.guid !in failedGuids }
            .sortedBy { Vpn5gCountryGroups.priorityOf(it.countryCode) }
        // Tier 2: tier-unlocked with a measured ping.
        tiers += eligible
            .filter { it.isFree && it.pingMillis > 0 && it.guid !in failedGuids }
            .sortedWith(byCountryThenPing)
        // Tier 3: any server with a measured ping.
        tiers += eligible
            .filter { it.pingMillis > 0 && it.guid !in failedGuids }
            .sortedWith(byCountryThenPing)
        // Tier 4: tier-unlocked, unknown ping allowed (seed / TCP fallback).
        tiers += eligible
            .filter { it.isFree && it.pingMillis >= 0 && it.guid !in failedGuids }
            .sortedWith(byCountryThenPing)
        // Tier 5: anything left, failed-store members last.
        tiers += eligible
            .sortedWith(
                compareBy<Vpn5gServerUi>(
                    { it.guid in failedGuids },
                    { Vpn5gCountryGroups.priorityOf(it.countryCode) },
                    { !it.hasRealPing },
                    { healthAdjusted(it) },
                )
            )

        val seen = mutableSetOf<String>()
        return tiers.flatten().filter { seen.add(it.guid) }
    }

    /**
     * The next failover target: the first candidate in the
     * country-grouped [ranked] order (v1.1.30). The caller excludes
     * the dropped server and everything already tried, so the chain
     * exhausts one country before moving to the next, in priority
     * order. [differentCountryThan] is kept for call-site
     * compatibility but no longer reorders: Aref's 2026-11-26 order
     * (exhaust the country first) supersedes the old
     * different-country preference. Null only when every server is
     * excluded.
     */
    fun pick(
        servers: List<Vpn5gServerUi>,
        excluding: Set<String>,
        failedGuids: Set<String>,
        standbyQueue: List<String> = emptyList(),
        healthScores: Map<String, Double> = emptyMap(),
        differentCountryThan: String? = null,
    ): Vpn5gServerUi? {
        return ranked(servers, excluding, failedGuids, standbyQueue, healthScores)
            .firstOrNull()
    }
}
