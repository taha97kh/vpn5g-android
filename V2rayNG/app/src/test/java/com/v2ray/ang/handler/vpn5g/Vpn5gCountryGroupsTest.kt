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
}
