package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.21 routing-mode mapping (pure Kotlin). */
class Vpn5gRoutingTest {

    // -- Stored value -> mode --------------------------------------------

    @Test
    fun test_mode_from_stored() {
        assertEquals(Vpn5gRoutingMode.SMART, Vpn5gRouting.modeFromStored("smart"))
        assertEquals(Vpn5gRoutingMode.FULL, Vpn5gRouting.modeFromStored("full"))
        // Unknown / missing values are SMART (the default, and the
        // pre-1.1.21 behaviour of the upstream prefs).
        assertEquals(Vpn5gRoutingMode.SMART, Vpn5gRouting.modeFromStored(null))
        assertEquals(Vpn5gRoutingMode.SMART, Vpn5gRouting.modeFromStored(""))
        assertEquals(Vpn5gRoutingMode.SMART, Vpn5gRouting.modeFromStored("banana"))
    }

    @Test
    fun test_mode_to_stored_roundtrip() {
        assertEquals("smart", Vpn5gRouting.modeToStored(Vpn5gRoutingMode.SMART))
        assertEquals("full", Vpn5gRouting.modeToStored(Vpn5gRoutingMode.FULL))
        for (mode in Vpn5gRoutingMode.entries) {
            assertEquals(mode, Vpn5gRouting.modeFromStored(Vpn5gRouting.modeToStored(mode)))
        }
    }

    // -- Mode -> upstream per-app-proxy prefs ------------------------------

    @Test
    fun test_smart_with_direct_first_honours_bypass() {
        val (perApp, bypass) = Vpn5gRouting.resolvePerAppProxy(
            Vpn5gRoutingMode.SMART, directFirst = true
        )
        assertTrue(perApp)
        assertTrue(bypass)
    }

    @Test
    fun test_smart_without_direct_first_disables_per_app() {
        val (perApp, bypass) = Vpn5gRouting.resolvePerAppProxy(
            Vpn5gRoutingMode.SMART, directFirst = false
        )
        assertFalse(perApp)
        assertTrue(bypass)
    }

    @Test
    fun test_full_never_enables_per_app() {
        for (directFirst in listOf(true, false)) {
            val (perApp, bypass) = Vpn5gRouting.resolvePerAppProxy(
                Vpn5gRoutingMode.FULL, directFirst
            )
            assertFalse(perApp)
            assertTrue(bypass)
        }
    }

    // -- Split-tunnel display state ---------------------------------------

    @Test
    fun test_split_tunnel_active_only_smart_and_direct_first() {
        assertTrue(
            Vpn5gRouting.isSplitTunnelActive(Vpn5gRoutingMode.SMART, true)
        )
        assertFalse(
            Vpn5gRouting.isSplitTunnelActive(Vpn5gRoutingMode.SMART, false)
        )
        assertFalse(
            Vpn5gRouting.isSplitTunnelActive(Vpn5gRoutingMode.FULL, true)
        )
        assertFalse(
            Vpn5gRouting.isSplitTunnelActive(Vpn5gRoutingMode.FULL, false)
        )
    }

    // -- v1.1.29 default per-app seed (Instagram + Telegram) -----------------

    @Test
    fun test_default_per_app_selection_only_installed() {
        assertEquals(
            setOf("com.instagram.android", "org.telegram.messenger"),
            Vpn5gRouting.defaultPerAppSelection(
                setOf("com.instagram.android", "org.telegram.messenger", "com.other.app")
            ),
        )
        assertEquals(
            setOf("org.telegram.messenger"),
            Vpn5gRouting.defaultPerAppSelection(setOf("org.telegram.messenger")),
        )
        assertTrue(
            Vpn5gRouting.defaultPerAppSelection(setOf("com.other.app")).isEmpty()
        )
        assertTrue(Vpn5gRouting.defaultPerAppSelection(emptySet()).isEmpty())
    }

    @Test
    fun test_should_seed_only_when_untouched() {
        // Fresh install: seed may run.
        assertTrue(
            Vpn5gRouting.shouldSeedPerAppDefaults(
                seeded = false, existingSelection = emptySet(),
                perAppEnabled = false, routingChosen = false,
            )
        )
        // Already seeded: never again.
        assertFalse(
            Vpn5gRouting.shouldSeedPerAppDefaults(
                seeded = true, existingSelection = emptySet(),
                perAppEnabled = false, routingChosen = false,
            )
        )
        // User customized the selection: never override.
        assertFalse(
            Vpn5gRouting.shouldSeedPerAppDefaults(
                seeded = false, existingSelection = setOf("com.example.app"),
                perAppEnabled = false, routingChosen = false,
            )
        )
        // Per-app proxy already enabled by the user: never override.
        assertFalse(
            Vpn5gRouting.shouldSeedPerAppDefaults(
                seeded = false, existingSelection = emptySet(),
                perAppEnabled = true, routingChosen = false,
            )
        )
        // Routing mode already chosen: never override.
        assertFalse(
            Vpn5gRouting.shouldSeedPerAppDefaults(
                seeded = false, existingSelection = emptySet(),
                perAppEnabled = false, routingChosen = true,
            )
        )
    }
}
