package com.v2ray.ang.handler.vpn5g

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Other-VPN detection (v1.1.29, Aref's order): when the user
 * enters the app with another VPN already active on the phone,
 * the app says so (Persian notice on Home) so the two tunnels do
 * not silently fight.
 *
 * Android reality: only ONE VpnService can be active at a time.
 * An app cannot stop another app's VPN directly — but the moment
 * OUR VpnService starts, the system revokes and stops the
 * previous one automatically. So the notice is informational
 * ("connecting with VPN 5G will stop it"), never a blocker: our
 * connect proceeds and Android replaces the other tunnel.
 *
 * Detection: the active network carries TRANSPORT_VPN while our
 * own service is not running. Android does not tell an app WHICH
 * package owns the VPN transport, so "VPN transport present +
 * our core not running" is the honest signal. The decision is
 * pure and JVM-tested; [isOtherVpnActive] with a Context is the
 * thin ConnectivityManager read.
 */
object Vpn5gOtherVpn {

    /** Pure decision: a VPN transport we do not own is active. */
    fun isOtherVpnActive(
        vpnTransportActive: Boolean,
        ourServiceRunning: Boolean,
    ): Boolean = vpnTransportActive && !ourServiceRunning

    /**
     * Reads the active network's transports. [ourServiceRunning]
     * is the caller's knowledge of our own core state. Never
     * throws — any failure reads as "no other VPN".
     */
    fun isOtherVpnActive(context: Context, ourServiceRunning: Boolean): Boolean =
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val vpnTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            isOtherVpnActive(vpnTransport, ourServiceRunning)
        }.getOrDefault(false)
}
