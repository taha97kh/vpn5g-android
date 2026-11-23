package com.v2ray.ang.handler.vpn5g

/**
 * Country grouping for the Premium screen (v1.1.14).
 *
 * The live pool carries COUNTRY data only — no city data exists anywhere
 * (no city field in any source; Vpn5gGeoIp is country-only). A "country"
 * here is therefore one country with its servers; each server row is one
 * location inside that country, and city names are never invented.
 * Grouping keys off the enriched ISO country code (from the server
 * remark or the GeoIP enrichment), falling back to the section label
 * when a server was never enriched. Pure, JVM-tested.
 */
object Vpn5gCountryGroups {

    /** One country in the Premium list (at most [MAX_COUNTRIES] shown). */
    data class CountryGroup(
        val code: String,
        val nameFa: String,
        val flag: String,
        val servers: List<Vpn5gServerUi>,
        /** True when at least one server in the group is in the free tier. */
        val hasFreeServer: Boolean,
    ) {
        /** Fastest real-pinged server in the group, for one-tap connect. */
        val fastestGuid: String?
            get() = servers
                .filter { it.hasRealPing && it.pingMillis > 0 }
                .minByOrNull { it.pingMillis }?.guid
    }

    /** The Premium country list shows at most this many countries (Aref: 100). */
    const val MAX_COUNTRIES = 100

    /**
     * Groups the home-screen sections by country code. Servers whose
     * remark/enrichment never produced a code group under their section
     * label instead. Largest groups first; the result is capped at
     * [MAX_COUNTRIES].
     */
    fun groupByCountry(sections: List<Vpn5gCountryUi>): List<CountryGroup> {
        val byKey = linkedMapOf<String, MutableList<Vpn5gServerUi>>()
        val meta = mutableMapOf<String, Triple<String, String, String>>()
        sections.forEach { section ->
            section.servers.forEach { server ->
                val code = server.countryCode.takeIf { it.isNotBlank() }
                    ?: section.label
                val key = code
                byKey.getOrPut(key) { mutableListOf() }.add(server)
                if (key !in meta) {
                    val name = server.countryNameFa.takeIf { it.isNotBlank() }
                        ?: section.label
                    meta[key] = Triple(server.countryCode, name, server.flag)
                }
            }
        }
        return byKey.entries
            .map { (key, servers) ->
                val (code, name, flag) = meta[key] ?: Triple("", key, "🌐")
                CountryGroup(
                    code = code,
                    nameFa = name,
                    flag = flag,
                    servers = servers.sortedBy {
                        if (it.pingMillis <= 0L) Long.MAX_VALUE else it.pingMillis
                    },
                    hasFreeServer = servers.any { it.isFree },
                )
            }
            .sortedByDescending { it.servers.size }
            .take(MAX_COUNTRIES)
    }
}
