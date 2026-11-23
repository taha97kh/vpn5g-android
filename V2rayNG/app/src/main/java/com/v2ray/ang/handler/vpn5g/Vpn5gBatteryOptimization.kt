package com.v2ray.ang.handler.vpn5g

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_BATTERY_OPT_PROMPTED

/**
 * Battery-optimization exemption (v1.1.12, part of "اتصال پایدار").
 *
 * Stock Android lets battery-saver and cleaner apps kill a background
 * service; the exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
 * is what keeps the VPN service alive when the app is not in the
 * foreground. The app asks ONCE, on the first connect, through the
 * system dialog — the user can still refuse, and refusing never blocks
 * connecting. The manifest declares
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; this app is distributed
 * through GitHub releases, not Google Play, so the Play policy that
 * restricts this permission does not apply here.
 *
 * Never throws; a device without a PowerManager is treated as exempt.
 */
object Vpn5gBatteryOptimization {

    /** True when the system already lets this app run ignoring battery optimizations. */
    fun isExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** System intent that asks the user for the exemption. */
    fun exemptionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /** True once the exemption was asked for (asked at most once). */
    fun wasPrompted(): Boolean =
        MmkvManager.decodeSettingsBool(KEY_BATTERY_OPT_PROMPTED, false)

    fun markPrompted() {
        MmkvManager.encodeSettings(KEY_BATTERY_OPT_PROMPTED, true)
    }
}
