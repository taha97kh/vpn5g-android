package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the in-app update check (pure Kotlin, v1.1.3). */
class Vpn5gAppUpdateTest {

    @Test
    fun test_newer_patch_is_newer() {
        assertTrue(Vpn5gAppUpdate.isNewerVersion("v1.1.4", "1.1.3"))
        assertTrue(Vpn5gAppUpdate.isNewerVersion("1.1.4", "1.1.3"))
    }

    @Test
    fun test_same_version_is_not_newer() {
        assertFalse(Vpn5gAppUpdate.isNewerVersion("v1.1.3", "1.1.3"))
        assertFalse(Vpn5gAppUpdate.isNewerVersion("1.1.3", "v1.1.3"))
    }

    @Test
    fun test_older_version_is_not_newer() {
        assertFalse(Vpn5gAppUpdate.isNewerVersion("1.1.2", "1.1.3"))
        assertFalse(Vpn5gAppUpdate.isNewerVersion("v1.0.0", "1.1.3"))
    }

    @Test
    fun test_minor_and_major_beat_patch() {
        assertTrue(Vpn5gAppUpdate.isNewerVersion("1.2", "1.1.3"))
        assertTrue(Vpn5gAppUpdate.isNewerVersion("v2.0", "1.9.9"))
        assertTrue(Vpn5gAppUpdate.isNewerVersion("1.10.0", "1.9.9"))
    }

    @Test
    fun test_pre_release_is_older_than_release() {
        assertFalse(Vpn5gAppUpdate.isNewerVersion("1.1.3-beta", "1.1.3"))
        assertTrue(Vpn5gAppUpdate.isNewerVersion("1.1.4-beta", "1.1.3"))
    }

    @Test
    fun test_unparseable_input_is_never_newer() {
        assertFalse(Vpn5gAppUpdate.isNewerVersion(null, "1.1.3"))
        assertFalse(Vpn5gAppUpdate.isNewerVersion("", "1.1.3"))
        assertFalse(Vpn5gAppUpdate.isNewerVersion("garbage", "1.1.3"))
        assertFalse(Vpn5gAppUpdate.isNewerVersion("1.1.3", null))
        assertFalse(Vpn5gAppUpdate.isNewerVersion("1.1.3", "garbage"))
        assertFalse(Vpn5gAppUpdate.isNewerVersion("v", "1.1.3"))
    }

    @Test
    fun test_display_version_strips_v() {
        assertEquals("1.1.4", Vpn5gAppUpdate.displayVersion("v1.1.4"))
        assertEquals("1.1.3", Vpn5gAppUpdate.displayVersion("1.1.3"))
        assertEquals("", Vpn5gAppUpdate.displayVersion(null))
    }

    @Test
    fun test_asset_picker_prefers_playstore_universal() {
        val release = GithubRelease(
            tagName = "v1.1.4",
            assets = listOf(
                GithubReleaseAsset(
                    name = "VPN5G_1.1.4-fdroid_universal.apk",
                    browserDownloadUrl = "https://example.invalid/fdroid.apk",
                ),
                GithubReleaseAsset(
                    name = "VPN5G_1.1.4_universal.apk",
                    browserDownloadUrl = "https://example.invalid/playstore.apk",
                ),
            ),
        )
        assertEquals(
            "https://example.invalid/playstore.apk",
            Vpn5gAppUpdate.pickPlaystoreApkAsset(release)?.browserDownloadUrl,
        )
    }

    @Test
    fun test_asset_picker_falls_back_to_any_universal() {
        val release = GithubRelease(
            tagName = "v1.1.4",
            assets = listOf(
                GithubReleaseAsset(
                    name = "VPN5G_1.1.4-fdroid_universal.apk",
                    browserDownloadUrl = "https://example.invalid/fdroid.apk",
                ),
            ),
        )
        assertEquals(
            "https://example.invalid/fdroid.apk",
            Vpn5gAppUpdate.pickPlaystoreApkAsset(release)?.browserDownloadUrl,
        )
    }

    @Test
    fun test_asset_picker_null_when_no_universal_apk() {
        val release = GithubRelease(
            tagName = "v1.1.4",
            assets = listOf(
                GithubReleaseAsset(
                    name = "VPN5G_1.1.4-x86_64.apk",
                    browserDownloadUrl = "https://example.invalid/x86.apk",
                ),
            ),
        )
        assertNull(Vpn5gAppUpdate.pickPlaystoreApkAsset(release))
        assertNull(Vpn5gAppUpdate.pickPlaystoreApkAsset(null))
        assertNull(Vpn5gAppUpdate.pickPlaystoreApkAsset(GithubRelease(tagName = "v1.1.4")))
    }

    @Test
    fun test_latest_release_json_parses() {
        val json = """
            {
              "tag_name": "v1.1.4",
              "name": "VPN 5G 1.1.4",
              "assets": [
                {
                  "name": "VPN5G_1.1.4_universal.apk",
                  "browser_download_url": "https://github.com/nefrin2love/vpn5g-android/releases/download/v1.1.4/VPN5G_1.1.4_universal.apk",
                  "size": 88527069
                },
                {
                  "name": "VPN5G_1.1.4-fdroid_universal.apk",
                  "browser_download_url": "https://github.com/nefrin2love/vpn5g-android/releases/download/v1.1.4/VPN5G_1.1.4-fdroid_universal.apk",
                  "size": 88527045
                }
              ]
            }
        """.trimIndent()
        val release = JsonUtil.fromJsonSafe(json, GithubRelease::class.java)
        assertEquals("v1.1.4", release?.tagName)
        assertEquals(
            "https://github.com/nefrin2love/vpn5g-android/releases/download/v1.1.4/VPN5G_1.1.4_universal.apk",
            Vpn5gAppUpdate.pickPlaystoreApkAsset(release)?.browserDownloadUrl,
        )
        assertTrue(Vpn5gAppUpdate.isNewerVersion(release?.tagName, "1.1.3"))
    }
}
