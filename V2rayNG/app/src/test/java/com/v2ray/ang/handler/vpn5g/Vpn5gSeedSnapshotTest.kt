package com.v2ray.ang.handler.vpn5g

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** JVM tests for the bundled seed snapshot (v1.1.20): the asset the
 * fresh-install free list is built from must hold >= 20 verified
 * configs, all parseable by the app's own rules (scheme + vmess JSON
 * shape), with no duplicate server identity. The snapshot is
 * regenerated from the live sources at build time (see
 * tools/refresh-seed-snapshot.py); these tests fail the build when a
 * stale or broken snapshot would ship. */
class Vpn5gSeedSnapshotTest {

    private fun seedFile(): File {
        // Gradle runs unit tests with the module dir (V2rayNG/) as the
        // working directory; tolerate the repo root too.
        val candidates = listOf(
            "app/src/main/assets/vpn5g/fallback_servers.json",
            "V2rayNG/app/src/main/assets/vpn5g/fallback_servers.json",
            "src/main/assets/vpn5g/fallback_servers.json",
        )
        val found = candidates.map { File(it) }.firstOrNull { it.isFile }
        requireNotNull(found) {
            "seed snapshot not found (tried ${candidates.joinToString()})"
        }
        return found
    }

    private val uriRegex = Regex("\"uri\"\\s*:\\s*\"([^\"]+)\"")

    private fun seedUris(): List<String> {
        val text = seedFile().readText()
        return uriRegex.findAll(text).map { it.groupValues[1] }.toList()
    }

    @Test
    fun test_seed_has_at_least_20_entries() {
        val uris = seedUris()
        assertTrue(uris.size >= 20, "seed has only ${uris.size} entries, need >= 20")
    }

    @Test
    fun test_seed_entries_are_parseable() {
        val uris = seedUris()
        assertTrue(uris.isNotEmpty())
        val seen = mutableSetOf<String>()
        for (uri in uris) {
            val scheme = uri.substringBefore("://")
            assertTrue(
                scheme in setOf("vless", "vmess", "trojan", "ss"),
                "unknown scheme in seed entry: ${uri.take(24)}",
            )
            if (scheme == "vmess") {
                val raw = uri.removePrefix("vmess://")
                    .substringBefore("#").substringBefore("?")
                val padded = raw + "=".repeat((4 - raw.length % 4) % 4)
                val decoded = try {
                    String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_8)
                } catch (_: IllegalArgumentException) {
                    String(java.util.Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
                }
                assertTrue(decoded.contains("\"add\""), "vmess without add: ${uri.take(20)}")
                assertTrue(decoded.contains("\"port\""), "vmess without port: ${uri.take(20)}")
                assertTrue(decoded.contains("\"id\""), "vmess without id: ${uri.take(20)}")
            }
            // No duplicate server identity (scheme + host:port). For
            // vmess the identity is the whole base64 body (no host in
            // the URI itself), so an exact duplicate still collides.
            val hostPort = uri.substringAfter("://")
                .substringBefore("#").substringBefore("?")
                .substringAfterLast("@")
            val identity = "$scheme://$hostPort"
            assertTrue(seen.add(identity), "duplicate seed server: $identity")
        }
    }
}
