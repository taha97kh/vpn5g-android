package com.v2ray.ang

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import androidx.work.WorkManager
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.vpn5g.Vpn5gCrashHandler
import com.v2ray.ang.ui.compose.ThemeManager
import kotlin.concurrent.thread

class AngApplication : Application() {
    companion object {
        lateinit var application: AngApplication
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base?.let(ContextCompat::getContextForLanguage))
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        // v1.1.16: record uncaught exceptions to a shareable file
        // BEFORE anything else runs, so a crash anywhere — including
        // the connect chain — leaves a report behind. The handler
        // chains to Android's default, so the crash dialog still shows.
        Vpn5gCrashHandler.install(this)

        MmkvManager.initialize(this)

        AppLocaleManager.initialize(this)

        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.initApp(this)

        // VPN 5G (v1.1.2): the launcher is Vpn5gMainActivity, not
        // MainActivity, so the geo data files (.dat) the Xray core needs
        // were never installed on this path — the first connect tap failed
        // until the user opened Advanced Settings (MainActivity), whose
        // initialize() copies them. Copy them here, once per install, on
        // a background thread: initAssets() only copies files that are
        // missing, so it is idempotent and safe to run at every launch.
        thread(name = "vpn5g-init-assets") {
            SettingsManager.initAssets(this, assets)
        }

        // Initialize theme state from MMKV
        ThemeManager.refresh()
    }
}
