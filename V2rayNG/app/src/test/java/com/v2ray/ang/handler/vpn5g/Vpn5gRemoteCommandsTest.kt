package com.v2ray.ang.handler.vpn5g

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.handler.vpn5g.Vpn5gRemoteCommands.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Remote-control contract v1 (v1.1.26): command planning —
 * execute-once, expiry, order, unknown types, email targeting —
 * plus the min_version compare and trial-hours clamp. Pure JVM.
 */
class Vpn5gRemoteCommandsTest {

    private fun cmd(
        id: String?,
        type: String?,
        payloadJson: String? = null,
        expiresAt: String? = null,
    ): RemoteCommandRemote = RemoteCommandRemote(
        id = id,
        type = type,
        payload = payloadJson?.let { JsonParser.parseString(it).asJsonObject },
        created_at = "2026-11-25 10:00:00",
        expires_at = expiresAt,
    )

    private fun plan(
        commands: List<RemoteCommandRemote>?,
        executed: Set<String> = emptySet(),
        nowMs: Long = 1_700_000_000_000L,
        currentEmail: String? = "user@example.com",
    ) = Vpn5gRemoteCommands.plan(commands, executed, nowMs, currentEmail)

    // ---- version compare (min_version) ----------------------------

    @Test
    fun `min version compares numerically part by part`() {
        assertTrue(Vpn5gRemoteCommands.isBelowMinVersion("1.1.9", "1.1.24"))
        assertTrue(Vpn5gRemoteCommands.isBelowMinVersion("1.1.25", "1.1.26"))
        assertFalse(Vpn5gRemoteCommands.isBelowMinVersion("1.1.26", "1.1.26"))
        assertFalse(Vpn5gRemoteCommands.isBelowMinVersion("1.1.26", "1.1.24"))
        assertFalse(Vpn5gRemoteCommands.isBelowMinVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun `min version null or blank means no gate`() {
        assertFalse(Vpn5gRemoteCommands.isBelowMinVersion("1.1.25", null))
        assertFalse(Vpn5gRemoteCommands.isBelowMinVersion("1.1.25", ""))
        assertFalse(Vpn5gRemoteCommands.isBelowMinVersion("1.1.25", "   "))
        assertFalse(Vpn5gRemoteCommands.isBelowMinVersion(null, "1.1.26"))
    }

    // ---- trial hours clamp ----------------------------------------

    @Test
    fun `trial hours clamp to 1 to 720 with 24 default`() {
        assertEquals(24, Vpn5gRemoteCommands.clampTrialHours(null))
        assertEquals(1, Vpn5gRemoteCommands.clampTrialHours(0))
        assertEquals(1, Vpn5gRemoteCommands.clampTrialHours(-5))
        assertEquals(720, Vpn5gRemoteCommands.clampTrialHours(721))
        assertEquals(720, Vpn5gRemoteCommands.clampTrialHours(10_000))
        assertEquals(48, Vpn5gRemoteCommands.clampTrialHours(48))
    }

    // ---- expiry parsing -------------------------------------------

    @Test
    fun `space separated UTC and ISO timestamps parse to the same instant`() {
        val space = Vpn5gRemoteCommands.parseTimeToEpochMs("2026-11-25 10:00:00")
        val iso = Vpn5gRemoteCommands.parseTimeToEpochMs("2026-11-25T10:00:00Z")
        assertEquals(space, iso)
        assertNull(Vpn5gRemoteCommands.parseTimeToEpochMs(null))
        assertNull(Vpn5gRemoteCommands.parseTimeToEpochMs("not a date"))
    }

    @Test
    fun `expired commands are skipped, unparsable expiry never expires`() {
        val now = Vpn5gRemoteCommands.parseTimeToEpochMs("2026-11-25 12:00:00")!!
        assertTrue(
            Vpn5gRemoteCommands.isExpired(cmd("a", "logout", expiresAt = "2026-11-25 11:59:59"), now)
        )
        assertFalse(
            Vpn5gRemoteCommands.isExpired(cmd("a", "logout", expiresAt = "2026-11-25 12:00:01"), now)
        )
        assertFalse(Vpn5gRemoteCommands.isExpired(cmd("a", "logout", expiresAt = null), now))
        assertFalse(Vpn5gRemoteCommands.isExpired(cmd("a", "logout", expiresAt = "garbage"), now))
    }

    // ---- planning --------------------------------------------------

    @Test
    fun `absent commands plan nothing`() {
        assertTrue(plan(null).isEmpty())
        assertTrue(plan(emptyList()).isEmpty())
    }

    @Test
    fun `executed ids and unknown types are skipped`() {
        val planned = plan(
            listOf(
                cmd("done", "reload_config"),
                cmd("mystery", "teleport_user"),
                cmd("fresh", "reload_config"),
            ),
            executed = setOf("done"),
        )
        assertEquals(1, planned.size)
        assertEquals("fresh", planned.single().command.id)
        assertEquals(Action.ReloadConfig, planned.single().action)
    }

    @Test
    fun `array order is preserved`() {
        val planned = plan(
            listOf(
                cmd("1", "clear_cache"),
                cmd("2", "show_message", """{"title":"T","text":"hi"}"""),
                cmd("3", "refresh_subscription"),
            )
        )
        assertEquals(listOf("1", "2", "3"), planned.map { it.command.id })
    }

    @Test
    fun `email null targets the current user`() {
        val planned = plan(
            listOf(
                cmd("r", "refresh_subscription"),
                cmd("l", "logout", """{"email":null}"""),
            )
        )
        assertEquals(2, planned.size)
    }

    @Test
    fun `email for another user is skipped and not planned`() {
        val planned = plan(
            listOf(
                cmd("r", "refresh_subscription", """{"email":"other@example.com"}"""),
                cmd("l", "logout", """{"email":"Other@Example.com"}"""),
            ),
            currentEmail = "user@example.com",
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `email match is case insensitive`() {
        val planned = plan(
            listOf(cmd("l", "logout", """{"email":"User@Example.COM"}""")),
            currentEmail = "user@example.com",
        )
        assertEquals(1, planned.size)
        assertEquals(Action.Logout, planned.single().action)
    }

    @Test
    fun `show message needs text and keeps title`() {
        val planned = plan(
            listOf(
                cmd("a", "show_message", """{"title":"خبر","text":"متن"}"""),
                cmd("b", "show_message", """{"title":"فقط عنوان"}"""),
                cmd("c", "show_message", """{"text":"  "}"""),
            )
        )
        assertEquals(1, planned.size)
        val action = planned.single().action as Action.ShowMessage
        assertEquals("خبر", action.title)
        assertEquals("متن", action.text)
    }

    @Test
    fun `force update version is optional`() {
        val planned = plan(
            listOf(
                cmd("a", "force_update", """{"version":"1.2.0"}"""),
                cmd("b", "force_update", """{"version":null}"""),
            )
        )
        assertEquals("1.2.0", (planned[0].action as Action.ForceUpdate).version)
        assertNull((planned[1].action as Action.ForceUpdate).version)
    }

    @Test
    fun `set config only accepts known keys`() {
        val planned = plan(
            listOf(
                cmd("a", "set_config", """{"key":"notice","value":"سلام"}"""),
                cmd("b", "set_config", """{"key":"evil_key","value":1}"""),
                cmd("c", "set_config", """{"key":"trial_hours","value":48}"""),
            )
        )
        assertEquals(listOf("a", "c"), planned.map { it.command.id })
        val setNotice = planned[0].action as Action.SetConfig
        assertEquals("notice", setNotice.key)
        assertEquals("سلام", setNotice.value?.asString)
    }

    @Test
    fun `blank ids are skipped`() {
        val planned = plan(
            listOf(cmd(null, "logout"), cmd("  ", "logout"), cmd("ok", "logout"))
        )
        assertEquals(listOf("ok"), planned.map { it.command.id })
    }

    // ---- executed-id store (pure encode/decode) --------------------

    @Test
    fun `executed ids round trip and cap at the limit`() {
        val ids = (1..5).map { "id-$it" }
        assertEquals(ids.toSet(), Vpn5gRemoteCommands.decodeIds(Vpn5gRemoteCommands.encodeIds(ids)))
        assertEquals(emptySet<String>(), Vpn5gRemoteCommands.decodeIds(null))
        assertEquals(emptySet<String>(), Vpn5gRemoteCommands.decodeIds("not json"))

        var acc: List<String> = emptyList()
        for (i in 1..(Vpn5gRemoteCommands.MAX_EXECUTED_IDS + 50)) {
            acc = Vpn5gRemoteCommands.addExecuted(acc, "cmd-$i")
        }
        assertEquals(Vpn5gRemoteCommands.MAX_EXECUTED_IDS, acc.size)
        assertEquals("cmd-51", acc.first())
        // Re-adding an existing id does not duplicate it.
        val again = Vpn5gRemoteCommands.addExecuted(acc, "cmd-51")
        assertEquals(acc.size, again.size)
        assertEquals(1, again.count { it == "cmd-51" })
    }

    @Test
    fun `resolve email falls back to current user`() {
        assertEquals(
            "a@b.com",
            Vpn5gRemoteCommands.resolveEmail(" A@B.com ", "c@d.com"),
        )
        assertEquals(
            "c@d.com",
            Vpn5gRemoteCommands.resolveEmail(null, "C@D.com"),
        )
        assertNull(Vpn5gRemoteCommands.resolveEmail(null, null))
        assertNull(Vpn5gRemoteCommands.resolveEmail("", "  "))
    }
}
