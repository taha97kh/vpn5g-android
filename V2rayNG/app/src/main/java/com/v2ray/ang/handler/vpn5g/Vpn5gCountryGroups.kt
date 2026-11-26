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

    // -- Country priority + sanction-bypass set (v1.1.30) ----------------------
    //
    // Aref's order (2026-11-26): failover must exhaust ALL servers of
    // one country before jumping to the next, in this priority —
    // آمریکا (US) → آلمان (DE) → کانادا (CA) → the other countries
    // that bypass sanctions (Google Flow / Gemini geo-blocks) →
    // everything else. The same set backs the «مخصوص اپ‌های
    // تحریم‌شده» menu on the server screen.

    /**
     * Countries whose IPs generally pass the geo-checks of
     * sanction-blocked services (Google Flow, Gemini, …): not
     * sanctioned regions, so a tunnel exit there is treated as a
     * normal user. Upper-case ISO codes. Pure data, JVM-tested.
     */
    val SANCTION_BYPASS_CODES: Set<String> = setOf(
        "US", "CA", "GB", "DE", "FR", "NL", "FI", "SE", "NO", "DK",
        "CH", "AT", "BE", "IE", "IT", "ES", "PT", "PL", "CZ", "SK",
        "HU", "RO", "BG", "GR", "JP", "KR", "SG", "AU", "NZ", "IL",
        "AE", "TR", "IN", "BR", "MX", "AR", "CL", "ZA", "MY", "TH",
        "VN", "ID", "PH", "TW", "HK",
    )

    /**
     * Failover priority of a country code (lower = tried first):
     * US 0, DE 1, CA 2, other sanction-bypassing countries 3, any
     * other known country 4, unknown 5. Case-insensitive; blank =
     * unknown. Pure, JVM-tested.
     */
    fun priorityOf(countryCode: String?): Int {
        val code = countryCode?.trim()?.uppercase().orEmpty()
        return when (code) {
            "US" -> 0
            "DE" -> 1
            "CA" -> 2
            "" -> 5
            else -> if (code in SANCTION_BYPASS_CODES) 3 else 4
        }
    }

    /** True when [countryCode] is a sanction-bypassing country. Pure. */
    fun isSanctionBypass(countryCode: String?): Boolean {
        val code = countryCode?.trim()?.uppercase().orEmpty()
        return code in SANCTION_BYPASS_CODES
    }

    /**
     * Country groups for the «مخصوص اپ‌های تحریم‌شده» menu: only
     * sanction-bypassing countries, in failover-priority order
     * (US, DE, CA, then the rest by group size). Pure, JVM-tested.
     */
    fun sanctionBypassGroups(sections: List<Vpn5gCountryUi>): List<CountryGroup> {
        return groupByCountry(sections)
            .filter { isSanctionBypass(it.code) }
            .sortedWith(
                compareBy<CountryGroup>({ priorityOf(it.code) }, { -it.servers.size })
            )
    }

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
