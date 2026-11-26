package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the pure country grouping ([Vpn5gCountryGroups]). */
class Vpn5gCountryGroupsTest {

    private fun server(
        guid: String,
        code: String,
        nameFa: String,
        pingMillis: Long,
        isFree: Boolean = true,
    ): Vpn5gServerUi {
        return Vpn5gServerUi(
            guid = guid,
            name = guid,
            country = code,
            pingMillis = pingMillis,
            isSelected = false,
            isFree = isFree,
            flag = "🏳",
            countryNameFa = nameFa,
            countryCode = code,
            hasRealPing = pingMillis > 0,
        )
    }

    @Test
    fun test_groups_by_country_code() {
        val sections = listOf(
            Vpn5gCountryUi(
                label = "بین‌الملل",
                isFree = true,
                servers = listOf(
                    server("a", "US", "آمریکا", 100),
                    server("b", "DE", "آلمان", 50),
                    server("c", "US", "آمریکا", 200),
                ),
            )
        )
        val groups = Vpn5gCountryGroups.groupByCountry(sections)
        assertEquals(2, groups.size)
        assertEquals("US", groups[0].code)
        assertEquals(2, groups[0].servers.size)
        assertEquals("DE", groups[1].code)
        // Fastest server in the group, for one-tap connect.
        assertEquals("a", groups[0].fastestGuid)
    }

    @Test
    fun test_capped_at_100_countries() {
        val sections = (1..150).map { i ->
            Vpn5gCountryUi(
                label = "c$i",
                isFree = true,
                servers = listOf(server("g$i", "C$i", "کشور $i", 10)),
            )
        }
        val groups = Vpn5gCountryGroups.groupByCountry(sections)
        assertEquals(Vpn5gCountryGroups.MAX_COUNTRIES, groups.size)
        assertEquals(100, Vpn5gCountryGroups.MAX_COUNTRIES)
    }

    @Test
    fun test_locked_group_has_no_free_server() {
        val sections = listOf(
            Vpn5gCountryUi(
                label = "locked",
                isFree = false,
                servers = listOf(server("x", "FR", "فرانسه", 80, isFree = false)),
            )
        )
        val groups = Vpn5gCountryGroups.groupByCountry(sections)
        assertTrue(groups.single().servers.isNotEmpty())
        assertEquals(false, groups.single().hasFreeServer)
        // The fastest server still resolves (locking is a UI gate, not grouping).
        assertEquals("x", groups.single().fastestGuid)
    }

    // -- Failover priority + sanction-bypass (v1.1.30) --------------------------

    @Test
    fun test_priority_order_us_de_ca_then_sanction_bypass() {
        assertEquals(0, Vpn5gCountryGroups.priorityOf("US"))
        assertEquals(1, Vpn5gCountryGroups.priorityOf("DE"))
        assertEquals(2, Vpn5gCountryGroups.priorityOf("CA"))
        // Other sanction-bypassing countries.
        assertEquals(3, Vpn5gCountryGroups.priorityOf("NL"))
        assertEquals(3, Vpn5gCountryGroups.priorityOf("GB"))
        assertEquals(3, Vpn5gCountryGroups.priorityOf("JP"))
        // Known but not sanction-bypassing.
        assertEquals(4, Vpn5gCountryGroups.priorityOf("IR"))
        assertEquals(4, Vpn5gCountryGroups.priorityOf("RU"))
        // Unknown / blank / null last; case-insensitive.
        assertEquals(5, Vpn5gCountryGroups.priorityOf(""))
        assertEquals(5, Vpn5gCountryGroups.priorityOf(null))
        assertEquals(0, Vpn5gCountryGroups.priorityOf("us"))
        assertEquals(1, Vpn5gCountryGroups.priorityOf(" de "))
    }

    @Test
    fun test_is_sanction_bypass() {
        assertTrue(Vpn5gCountryGroups.isSanctionBypass("US"))
        assertTrue(Vpn5gCountryGroups.isSanctionBypass("nl"))
        assertEquals(false, Vpn5gCountryGroups.isSanctionBypass("IR"))
        assertEquals(false, Vpn5gCountryGroups.isSanctionBypass(""))
        assertEquals(false, Vpn5gCountryGroups.isSanctionBypass(null))
    }

    @Test
    fun test_sanction_bypass_groups_only_bypass_in_priority_order() {
        val sections = listOf(
            Vpn5gCountryUi(
                label = "بین‌الملل",
                isFree = true,
                servers = listOf(
                    server("ir1", "IR", "ایران", 10),
                    server("nl1", "NL", "هلند", 20),
                    server("de1", "DE", "آلمان", 30),
                    server("us1", "US", "آمریکا", 40),
                    server("ca1", "CA", "کانادا", 50),
                    server("de2", "DE", "آلمان", 60),
                ),
            )
        )
        val groups = Vpn5gCountryGroups.sanctionBypassGroups(sections)
        // IR is excluded; priority order US → DE → CA → NL.
        assertEquals(listOf("US", "DE", "CA", "NL"), groups.map { it.code })
        assertEquals(2, groups[1].servers.size)
    }
}
