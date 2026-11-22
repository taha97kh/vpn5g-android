package com.v2ray.ang.handler.vpn5g

import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One asset of a GitHub release.
 *
 * All fields are nullable: Gson allocates these objects without calling the
 * Kotlin constructor, so a field absent from the JSON stays null instead of
 * taking its Kotlin default. Callers must treat null as "not provided".
 */
data class GithubReleaseAsset(
    val name: String? = null,
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String? = null,
    val size: Long? = null,
)

/** A GitHub release as returned by /repos/{owner}/{repo}/releases/latest. */
data class GithubRelease(
    @SerializedName("tag_name")
    val tagName: String? = null,
    val assets: List<GithubReleaseAsset>? = null,
)

/**
 * In-app update check (v1.1.3): compares the installed version with the
 * latest GitHub release and picks the APK asset to download.
 *
 * The check is unauthenticated (public repo, 60 requests/hour per IP) and
 * never throws: any network or parse failure returns null and the caller
 * stays silent. No download ever starts without the user tapping the
 * update button.
 */
object Vpn5gAppUpdate {

    /** Latest-release endpoint for the public repo (no auth needed). */
    const val RELEASES_LATEST_URL =
        "https://api.github.com/repos/VPN5G/vpn5g-android/releases/latest"

    /** The update check runs at most this often (once per day). */
    const val UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    /** Display version of a release tag ("v1.1.4" -> "1.1.4"). */
    fun displayVersion(tag: String?): String {
        if (tag.isNullOrBlank()) return ""
        return tag.trim().trimStart('v', 'V').trim()
    }

    /**
     * True when [latest] is a newer release than [current].
     * Both may carry a leading "v". Unparseable input is never newer.
     */
    fun isNewerVersion(latest: String?, current: String?): Boolean {
        val latestParts = parseVersion(latest) ?: return false
        val currentParts = parseVersion(current) ?: return false
        return compareVersions(latestParts, currentParts) > 0
    }

    /**
     * The APK asset the update button downloads: the playstore universal
     * APK. Falls back to any universal APK when the playstore one is absent
     * (e.g. a release published with only one flavor). Null when the
     * release carries no universal APK at all.
     */
    fun pickPlaystoreApkAsset(release: GithubRelease?): GithubReleaseAsset? {
        val assets = release?.assets.orEmpty()
        return assets.firstOrNull { it.isPlaystoreUniversalApk() }
            ?: assets.firstOrNull { it.isUniversalApk() }
    }

    /** Fetches the latest release, or null on any failure (stays silent). */
    suspend fun fetchLatest(): GithubRelease? = withContext(Dispatchers.IO) {
        try {
            // getUrlContentWithUserAgent sets a User-Agent, which the
            // GitHub API requires (no User-Agent -> 403), and throws on
            // any non-2xx status (e.g. the unauthenticated rate limit).
            val body = HttpUtil.getUrlContentWithUserAgent(
                UrlContentRequest(url = RELEASES_LATEST_URL, timeout = 10_000)
            )
            if (body.isBlank()) return@withContext null
            JsonUtil.fromJsonSafe(body, GithubRelease::class.java)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gAppUpdate: release check failed", e)
            null
        }
    }

    private fun GithubReleaseAsset.isUniversalApk(): Boolean =
        name?.endsWith("_universal.apk", ignoreCase = true) == true

    private fun GithubReleaseAsset.isPlaystoreUniversalApk(): Boolean =
        isUniversalApk() && name?.contains("fdroid", ignoreCase = true) != true

    private data class VersionParts(val numbers: List<Int>, val preRelease: String?)

    private fun parseVersion(raw: String?): VersionParts? {
        val cleaned = raw?.trim()?.trimStart('v', 'V')?.trim() ?: return null
        if (cleaned.isEmpty()) return null
        val core = cleaned.substringBefore('-')
        val preRelease = cleaned.substringAfter('-', "").ifEmpty { null }
        val numbers = core.split('.').map { part ->
            val digits = part.takeWhile { it.isDigit() }
            if (digits.isEmpty()) return null
            digits.toIntOrNull() ?: return null
        }
        return VersionParts(numbers, preRelease)
    }

    private fun compareVersions(a: VersionParts, b: VersionParts): Int {
        val len = maxOf(a.numbers.size, b.numbers.size)
        for (i in 0 until len) {
            val av = a.numbers.getOrElse(i) { 0 }
            val bv = b.numbers.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        // Numerically equal: a pre-release (e.g. "1.1.4-beta") is older
        // than the plain release; two pre-releases compare by name.
        return when {
            a.preRelease == b.preRelease -> 0
            a.preRelease == null -> 1
            b.preRelease == null -> -1
            else -> a.preRelease.compareTo(b.preRelease)
        }
    }
}
