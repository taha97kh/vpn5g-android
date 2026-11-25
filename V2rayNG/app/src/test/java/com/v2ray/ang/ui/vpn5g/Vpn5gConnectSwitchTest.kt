package com.v2ray.ang.ui.vpn5g

import com.v2ray.ang.handler.vpn5g.Vpn5gConnectState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM tests for the sliding connect switch state mapping (v1.1.22,
 * fix #1): the switch reflects the real tunnel state machine exactly
 * as the circular button did — only the control shape changed.
 */
class Vpn5gConnectSwitchTest {

    @Test
    fun test_idle_is_off() {
        assertEquals(
            Vpn5gConnectSwitchVisual.OFF,
            connectSwitchVisual(Vpn5gConnectState.IDLE),
        )
    }

    @Test
    fun test_connected_is_on() {
        assertEquals(
            Vpn5gConnectSwitchVisual.ON,
            connectSwitchVisual(Vpn5gConnectState.CONNECTED),
        )
    }

    @Test
    fun test_failed_is_failed() {
        assertEquals(
            Vpn5gConnectSwitchVisual.FAILED,
            connectSwitchVisual(Vpn5gConnectState.FAILED),
        )
    }

    @Test
    fun test_disconnecting_is_disconnecting() {
        assertEquals(
            Vpn5gConnectSwitchVisual.DISCONNECTING,
            connectSwitchVisual(Vpn5gConnectState.DISCONNECTING),
        )
    }

    @Test
    fun test_all_connecting_states_map_to_connecting() {
        val connecting = listOf(
            Vpn5gConnectState.CHECKING_INTERNET,
            Vpn5gConnectState.FINDING_SERVER,
            Vpn5gConnectState.CONNECTING,
            Vpn5gConnectState.CREATING_TUNNEL,
            Vpn5gConnectState.VERIFYING_TUNNEL,
            Vpn5gConnectState.SWITCHING_SERVER,
            Vpn5gConnectState.RETRYING,
        )
        connecting.forEach { state ->
            assertEquals(
                Vpn5gConnectSwitchVisual.CONNECTING,
                connectSwitchVisual(state),
                "state $state",
            )
        }
    }

    @Test
    fun test_every_state_maps_to_a_visual() {
        // Exhaustive: every state of the machine has a switch visual.
        Vpn5gConnectState.values().forEach { state ->
            connectSwitchVisual(state)
        }
        assertEquals(11, Vpn5gConnectState.values().size)
    }
}
