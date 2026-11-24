package com.v2ray.ang.handler.vpn5g

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the v1.1.15 anti-tamper helpers (pure Kotlin): the
 * SHA-256 fingerprint, the expected-key match, the emulator signals
 * and the root signals. The device-only parts — reading the real
 * signing certificate and the real Build fields — are NOT unit-tested
 * here; they are device checks (Not run). */
class Vpn5gTamperCheckTest {

    @Test
    fun test_sha256_hex_matches_known_vectors() {
        // SHA-256("abc") and SHA-256("") — standard test vectors.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Vpn5gTamperCheck.sha256Hex("abc".toByteArray(Charsets.UTF_8)),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Vpn5gTamperCheck.sha256Hex(ByteArray(0)),
        )
        // Lowercase hex, 64 chars.
        val hex = Vpn5gTamperCheck.sha256Hex("VPN5G".toByteArray(Charsets.UTF_8))
        assertEquals(64, hex.length)
        assertEquals(hex, hex.lowercase())
    }

    @Test
    fun test_signature_matches_expected_key_only() {
        val expected = "961bb669119259f2bd1d8cbaeb2d43da46295ba188428844ca247e180fc076aa"
        assertTrue(Vpn5gTamperCheck.signatureMatches(expected))
        // Case and surrounding whitespace do not matter.
        assertTrue(Vpn5gTamperCheck.signatureMatches(expected.uppercase()))
        assertTrue(Vpn5gTamperCheck.signatureMatches("  $expected  "))
        assertFalse(Vpn5gTamperCheck.signatureMatches("00" + expected.substring(2)))
        assertFalse(Vpn5gTamperCheck.signatureMatches(null))
        assertFalse(Vpn5gTamperCheck.signatureMatches(""))
        assertFalse(Vpn5gTamperCheck.signatureMatches("   "))
    }

    @Test
    fun test_real_device_is_not_an_emulator() {
        assertFalse(
            Vpn5gTamperCheck.isEmulatorBuild(
                "samsung/a52xq/a52xq:13/TP1A.220624.014/A525FXXU5DVK3:user/release-keys",
                "SM-A525F",
                "samsung",
                "a52xq",
                "qcom",
                "release-keys",
            )
        )
        assertFalse(Vpn5gTamperCheck.isEmulatorBuild(null, null, null, null, null, null))
    }

    @Test
    fun test_emulator_signals() {
        // Generic fingerprint.
        assertTrue(
            Vpn5gTamperCheck.isEmulatorBuild("generic/sdk/generic:11/RSR1.201013.001/1:userdebug/test-keys", "sdk", "Google", "sdk", "ranchu", "test-keys")
        )
        assertTrue(
            Vpn5gTamperCheck.isEmulatorBuild("unknown", "Pixel", "Google", "pixel", "qcom", "release-keys")
        )
        // SDK-built model.
        assertTrue(
            Vpn5gTamperCheck.isEmulatorBuild("google/sdk/generic:11/RSR1.201013.001/1:userdebug/test-keys", "google_sdk", "Google", "sdk", "ranchu", "test-keys")
        )
        assertTrue(
            Vpn5gTamperCheck.isEmulatorBuild("google/sdk/generic:11/RSR1.201013.001/1:userdebug/test-keys", "Emulator", "Google", "sdk", "ranchu", "test-keys")
        )
        assertTrue(
            Vpn5gTamperCheck.isEmulatorBuild("google/sdk/generic:11/RSR1.201013.001/1:userdebug/test-keys", "Android SDK built for x86", "Google", "sdk", "ranchu", "test-keys")
        )
        // Genymotion.
        assertTrue(
            Vpn5gTamperCheck.isEmulatorBuild("vbox86p/vbox86p/vbox86p:11/RSR1.201013.001/1:userdebug/test-keys", "Custom Phone", "Genymotion", "vbox86p", "vbox86", "test-keys")
        )
        // Goldfish / ranchu hardware.
        assertTrue(
            Vpn5gTamperCheck.isEmulatorBuild("google/sdk/generic:11/RSR1.201013.001/1:userdebug/test-keys", "sdk", "Google", "sdk", "goldfish", "release-keys")
        )
        // Test-keys tags alone are a signal.
        assertTrue(
            Vpn5gTamperCheck.isEmulatorBuild("samsung/a52xq/a52xq:13/TP1A.220624.014/A525FXXU5DVK3:user/release-keys", "SM-A525F", "samsung", "a52xq", "qcom", "test-keys")
        )
    }

    @Test
    fun test_is_rooted_checks_known_su_paths() {
        assertFalse(Vpn5gTamperCheck.isRooted { false })
        assertTrue(Vpn5gTamperCheck.isRooted { it == "/system/bin/su" })
        assertTrue(Vpn5gTamperCheck.isRooted { it == "/sbin/su" })
        assertTrue(Vpn5gTamperCheck.isRooted { it == "/data/local/xbin/su" })
        assertFalse(Vpn5gTamperCheck.isRooted { it == "/system/bin/sh" })
    }
}
