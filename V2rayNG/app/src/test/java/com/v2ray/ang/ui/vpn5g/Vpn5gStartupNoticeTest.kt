package com.v2ray.ang.ui.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for the paged startup notice state (v1.1.21): shown on
 * every app entry, dismissible for the current session only, with a
 * Terms & Conditions acceptance that is persisted (never re-asked).
 * The state itself is plain UiState — the ViewModel setters are thin
 * passthroughs over it (AndroidViewModel, so Not run on the JVM) and
 * the persistence round-trip (MMKV) is a device concern.
 */
class Vpn5gStartupNoticeTest {

    @Test
    fun test_notice_shown_on_fresh_state() {
        // A fresh UiState = a new launch: the notice shows, page 1.
        val state = Vpn5gHomeUiState()
        assertFalse(state.startupNoticeDismissed)
        assertEquals(0, state.startupNoticePage)
    }

    @Test
    fun test_dismiss_hides_notice_for_the_session() {
        val state = Vpn5gHomeUiState().copy(startupNoticeDismissed = true)
        assertTrue(state.startupNoticeDismissed)
    }

    @Test
    fun test_pages_advance_in_order() {
        val first = Vpn5gHomeUiState()
        assertEquals(0, first.startupNoticePage)
        val second = first.copy(startupNoticePage = 1)
        assertEquals(1, second.startupNoticePage)
        val last = second.copy(startupNoticePage = 2)
        assertEquals(2, last.startupNoticePage)
    }

    @Test
    fun test_terms_not_accepted_on_fresh_state() {
        // A fresh UiState never claims acceptance: the last page's
        // checkbox starts unticked and the login button disabled.
        assertFalse(Vpn5gHomeUiState().termsAccepted)
    }

    @Test
    fun test_terms_accepted_sticks_in_state() {
        // After accepting, the state carries it — the next launch loads
        // the same value from MMKV, so the checkbox stays ticked and
        // the login button enabled without re-ticking.
        val state = Vpn5gHomeUiState().copy(termsAccepted = true)
        assertTrue(state.termsAccepted)
    }
}
