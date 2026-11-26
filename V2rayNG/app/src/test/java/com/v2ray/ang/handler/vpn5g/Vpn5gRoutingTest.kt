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

    // -- v1.1.34 expanded default + migration -------------------------------

    @Test
    fun test_expanded_default_packages() {
        // Aref's pre-activated list: Telegram, Instagram, Gemini,
        // ChatGPT, Chrome, Google Flow + same-class apps.
        assertTrue(Vpn5gRouting.DEFAULT_PER_APP_PACKAGES.contains("com.instagram.android"))
        assertTrue(Vpn5gRouting.DEFAULT_PER_APP_PACKAGES.contains("org.telegram.messenger"))
        assertTrue(Vpn5gRouting.DEFAULT_PER_APP_PACKAGES.contains("com.google.android.apps.bard"))
        assertTrue(Vpn5gRouting.DEFAULT_PER_APP_PACKAGES.contains("com.openai.chatgpt"))
        assertTrue(Vpn5gRouting.DEFAULT_PER_APP_PACKAGES.contains("com.android.chrome"))
        assertTrue(Vpn5gRouting.DEFAULT_PER_APP_PACKAGES.contains("com.google.android.apps.labs.whisk"))
        assertTrue(Vpn5gRouting.DEFAULT_PER_APP_PACKAGES.contains("com.whatsapp"))
        assertTrue(Vpn5gRouting.DEFAULT_PER_APP_PACKAGES.contains("com.google.android.youtube"))
        assertTrue(Vpn5gRouting.DEFAULT_PER_APP_PACKAGES.contains("com.twitter.android"))
        assertTrue(Vpn5gRouting.DEFAULT_PER_APP_PACKAGES.contains("com.facebook.katana"))
        // The old default is preserved verbatim for migration.
        assertEquals(
            listOf("com.instagram.android", "org.telegram.messenger"),
            Vpn5gRouting.OLD_DEFAULT_PER_APP_PACKAGES,
        )
        // Selection is still installed-only.
        assertEquals(
            setOf("com.openai.chatgpt", "com.android.chrome"),
            Vpn5gRouting.defaultPerAppSelection(
                setOf("com.openai.chatgpt", "com.android.chrome", "com.other.app")
            ),
        )
    }

    @Test
    fun test_should_migrate_only_untouched_old_default() {
        val oldBoth = setOf("com.instagram.android", "org.telegram.messenger")
        // Untouched old default in the seeded allowlist: migrate.
        assertTrue(
            Vpn5gRouting.shouldMigrateToExpandedDefault(
                expandedSeeded = false, existingSelection = oldBoth,
                installedOldDefault = oldBoth,
                perAppEnabled = true, bypassApps = false,
            )
        )
        // Already migrated once: never again.
        assertFalse(
            Vpn5gRouting.shouldMigrateToExpandedDefault(
                expandedSeeded = true, existingSelection = oldBoth,
                installedOldDefault = oldBoth,
                perAppEnabled = true, bypassApps = false,
            )
        )
        // User added an app: customized, untouched.
        assertFalse(
            Vpn5gRouting.shouldMigrateToExpandedDefault(
                expandedSeeded = false,
                existingSelection = oldBoth + "com.example.app",
                installedOldDefault = oldBoth,
                perAppEnabled = true, bypassApps = false,
            )
        )
        // User removed an app: customized, untouched.
        assertFalse(
            Vpn5gRouting.shouldMigrateToExpandedDefault(
                expandedSeeded = false,
                existingSelection = setOf("com.instagram.android"),
                installedOldDefault = oldBoth,
                perAppEnabled = true, bypassApps = false,
            )
        )
        // Bypass mode: the set means something else — untouched.
        assertFalse(
            Vpn5gRouting.shouldMigrateToExpandedDefault(
                expandedSeeded = false, existingSelection = oldBoth,
                installedOldDefault = oldBoth,
                perAppEnabled = true, bypassApps = true,
            )
        )
        // Per-app off: untouched.
        assertFalse(
            Vpn5gRouting.shouldMigrateToExpandedDefault(
                expandedSeeded = false, existingSelection = oldBoth,
                installedOldDefault = oldBoth,
                perAppEnabled = false, bypassApps = false,
            )
        )
    }

    // -- v1.1.34 Iranian apps bypass ----------------------------------------

    @Test
    fun test_should_seed_iranian_bypass_once() {
        assertTrue(Vpn5gRouting.shouldSeedIranianBypass(seeded = false))
        assertFalse(Vpn5gRouting.shouldSeedIranianBypass(seeded = true))
    }

    @Test
    fun test_iranian_bypass_packages_cover_the_order() {
        val pkgs = Vpn5gRouting.IRANIAN_BYPASS_PACKAGES
        // Messengers, ride-hailing, commerce, video, telecom.
        assertTrue(pkgs.contains("ir.eitaa.messenger"))
        assertTrue(pkgs.contains("ir.resaneh1.iptv"))
        assertTrue(pkgs.contains("mobi.mmdt.ottplus"))
        assertTrue(pkgs.contains("cab.snapp.passenger"))
        assertTrue(pkgs.contains("taxi.tapsi.passenger"))
        assertTrue(pkgs.contains("com.digikala"))
        assertTrue(pkgs.contains("ir.divar"))
        assertTrue(pkgs.contains("com.aparat"))
        assertTrue(pkgs.contains("com.shatelland.namava.mobile"))
        assertTrue(pkgs.contains("com.myirancell"))
        assertTrue(pkgs.contains("ir.mci.ecareapp"))
        // Banks named in the order.
        assertTrue(pkgs.contains("ir.bmi.bam.nativeweb")) // Melli (Bam)
        assertTrue(pkgs.contains("com.pmb.mobile")) // Mellat
        assertTrue(pkgs.contains("com.isc.bsinew")) // Saderat
        assertTrue(pkgs.contains("ir.tejaratbank.tata.mobile.android.tejarat"))
        assertTrue(pkgs.contains("mob.banking.android.pasargad"))
        assertTrue(pkgs.contains("com.samanpr.blu")) // BluBank
        assertTrue(pkgs.contains("ir.mobillet.app")) // Saman (Mobillet)
        assertTrue(pkgs.contains("com.parsmobapp")) // Parsian
        assertTrue(pkgs.contains("com.refahbank.dpi.android")) // Refah
        assertTrue(pkgs.contains("com.tosan.dara.sepah")) // Sepah
        assertTrue(pkgs.contains("com.sibche.aspardproject.app")) // Ap
        // No duplicates.
        assertEquals(pkgs.size, pkgs.toSet().size)
    }

    @Test
    fun test_per_app_decision_all_modes() {
        val iranian = setOf("ir.eitaa.messenger", "com.digikala")
        val self = "com.taha97kh.vpn5g"
        // Per-app off (all-apps/FULL): self + Iranian bypass.
        val off = Vpn5gRouting.perAppDecision(
            perAppEnabled = false, bypassApps = false,
            userSet = setOf("com.example.app"), iranianSet = iranian,
            selfPackage = self,
        )
        assertEquals(Vpn5gRouting.PerAppApply.DISALLOW, off.apply)
        assertEquals(iranian + self, off.packages)
        // Bypass mode: user set + Iranian set + self bypass.
        val bypass = Vpn5gRouting.perAppDecision(
            perAppEnabled = true, bypassApps = true,
            userSet = setOf("com.example.app"), iranianSet = iranian,
            selfPackage = self,
        )
        assertEquals(Vpn5gRouting.PerAppApply.DISALLOW, bypass.apply)
        assertEquals(setOf("com.example.app") + iranian + self, bypass.packages)
        // Allowlist with empty selection: upstream fallback
        // (everything tunnels) minus self + Iranian.
        val emptyAllow = Vpn5gRouting.perAppDecision(
            perAppEnabled = true, bypassApps = false,
            userSet = emptySet(), iranianSet = iranian, selfPackage = self,
        )
        assertEquals(Vpn5gRouting.PerAppApply.DISALLOW, emptyAllow.apply)
        assertEquals(iranian + self, emptyAllow.packages)
        // Allowlist with a selection: ONLY the user set tunnels;
        // Iranian apps go direct unless ticked.
        val allow = Vpn5gRouting.perAppDecision(
            perAppEnabled = true, bypassApps = false,
            userSet = setOf("com.instagram.android", self),
            iranianSet = iranian, selfPackage = self,
        )
        assertEquals(Vpn5gRouting.PerAppApply.ALLOW_ONLY, allow.apply)
        assertEquals(setOf("com.instagram.android"), allow.packages)
        // A ticked Iranian app tunnels (explicit user choice).
        val ticked = Vpn5gRouting.perAppDecision(
            perAppEnabled = true, bypassApps = false,
            userSet = setOf("com.digikala"), iranianSet = iranian,
            selfPackage = self,
        )
        assertEquals(Vpn5gRouting.PerAppApply.ALLOW_ONLY, ticked.apply)
        assertEquals(setOf("com.digikala"), ticked.packages)
    }
}
