package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.5 announcement visibility rules (pure Kotlin):
 * a live announcement with non-blank text is shown unless the user
 * already dismissed this version of it; anything else shows nothing. */
class Vpn5gAnnouncementTest {

    private fun announcement(
        enabled: Boolean? = true,
        text: String? = "تخفیف ویژه",
        imageUrl: String? = null,
        id: String? = "ann-1",
    ) = AnnouncementRemote(
        enabled = enabled,
        text = text,
        image_url = imageUrl,
        id = id,
    )

    @Test
    fun test_live_announcement_is_shown() {
        assertTrue(Vpn5gAnnouncement.shouldShow(announcement(), null))
        assertTrue(Vpn5gAnnouncement.shouldShow(announcement(imageUrl = "https://x/y.jpg"), null))
    }

    @Test
    fun test_null_or_disabled_or_blank_is_hidden() {
        assertFalse(Vpn5gAnnouncement.shouldShow(null, null))
        assertFalse(Vpn5gAnnouncement.shouldShow(announcement(enabled = false), null))
        assertFalse(Vpn5gAnnouncement.shouldShow(announcement(enabled = null), null))
        assertFalse(Vpn5gAnnouncement.shouldShow(announcement(text = null), null))
        assertFalse(Vpn5gAnnouncement.shouldShow(announcement(text = ""), null))
        assertFalse(Vpn5gAnnouncement.shouldShow(announcement(text = "   "), null))
    }

    @Test
    fun test_dismissed_announcement_is_hidden_until_republished() {
        val first = announcement(id = "ann-1")
        assertFalse(Vpn5gAnnouncement.shouldShow(first, "ann-1"))
        // A republished version (new id) is shown again.
        assertTrue(Vpn5gAnnouncement.shouldShow(announcement(id = "ann-2"), "ann-1"))
    }

    @Test
    fun test_dismiss_key_prefers_id_then_text() {
        assertEquals("ann-1", Vpn5gAnnouncement.dismissKey(announcement(id = "ann-1")))
        assertEquals("تخفیف ویژه", Vpn5gAnnouncement.dismissKey(announcement(id = null)))
        assertEquals("تخفیف ویژه", Vpn5gAnnouncement.dismissKey(announcement(id = "  ")))
        assertEquals("", Vpn5gAnnouncement.dismissKey(announcement(id = null, text = null)))
        // A dismissed-by-text announcement stays hidden for the same text.
        assertFalse(
            Vpn5gAnnouncement.shouldShow(
                announcement(id = null, text = "تخفیف ویژه"),
                "تخفیف ویژه",
            )
        )
    }
}
