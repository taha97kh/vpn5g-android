package com.v2ray.ang.handler.vpn5g

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.security.MessageDigest

/**
 * Runtime tamper checks (v1.1.15).
 *
 * What it does:
 *  - verifies this APK's own signing certificate at startup against
 *    the fixed release key ([EXPECTED_CERT_SHA256]). A repacked build
 *    (different key) fails the check: privileged features are refused
 *    and the app falls back to the free tier, with a warning — never a
 *    crash. Debug builds are always treated as verified (they use the
 *    debug key by design).
 *  - root / emulator detection is a SOFT signal only: logged, never
 *    blocks anything (a rooted phone or an emulator is not proof of
 *    tampering).
 *
 * HONEST LIMIT: no Android app is 100% uncrackable. The real
 * protection is server-side enforcement (codes and subscriptions are
 * validated by the `validate_and_redeem_code` / `get_subscription_status`
 * RPCs, not by local flags) plus R8 obfuscation plus this check. A
 * determined attacker can still patch the check out of a repacked
 * APK — what they cannot do is make the SERVER grant a subscription
 * the bot never issued.
 */
object Vpn5gTamperCheck {

    /**
     * SHA-256 of the signing certificate of every release APK signed
     * with the fixed vpn5g key (v1.1.4+, verified from the released
     * APKs). A PUBLIC certificate fingerprint, not a secret — it only
     * proves which key signed the APK.
     */
    const val EXPECTED_CERT_SHA256 =
        "961bb669119259f2bd1d8cbaeb2d43da46295ba188428844ca247e180fc076aa"

    /** Known su / superuser paths (soft signal only). */
    private val ROOT_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/app/Superuser.apk",
        "/system/app/SuperSU.apk",
        "/data/local/xbin/su",
        "/data/local/bin/su",
    )

    /** SHA-256 hex of [input] (lowercase). Pure, JVM-tested. */
    fun sha256Hex(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** True when [actualSha256] is the expected release-key fingerprint. Pure. */
    fun signatureMatches(actualSha256: String?): Boolean {
        if (actualSha256.isNullOrBlank()) return false
        return actualSha256.trim().lowercase() == EXPECTED_CERT_SHA256
    }

    /**
     * Pure emulator signal over Build fields (JVM-tested): generic
     * fingerprints, SDK-built models, Genymotion, goldfish/ranchu
     * hardware. Any one match is a signal, not proof.
     */
    fun isEmulatorBuild(
        fingerprint: String?,
        model: String?,
        manufacturer: String?,
        product: String?,
        hardware: String?,
        tags: String?,
    ): Boolean {
        val fp = fingerprint.orEmpty()
        if (fp.startsWith("generic") || fp.startsWith("unknown")) return true
        if (fp.contains("generic") || fp.contains("emulator")) return true
        val m = model.orEmpty()
        if (m.contains("google_sdk") || m.contains("Emulator")) return true
        if (m.contains("Android SDK built for")) return true
        if (manufacturer.orEmpty().contains("Genymotion")) return true
        val p = product.orEmpty()
        if (p.contains("sdk") || p.contains("emulator") || p.contains("google_sdk")) return true
        val h = hardware.orEmpty()
        if (h.contains("goldfish") || h.contains("ranchu")) return true
        if (tags.orEmpty().contains("test-keys")) return true
        return false
    }

    /** Pure root signal: true when any known su path exists. */
    fun isRooted(fileExists: (String) -> Boolean): Boolean {
        return ROOT_PATHS.any { fileExists(it) }
    }

    /** Device emulator signal (soft only). */
    fun isEmulator(): Boolean = isEmulatorBuild(
        Build.FINGERPRINT,
        Build.MODEL,
        Build.MANUFACTURER,
        Build.PRODUCT,
        Build.HARDWARE,
        Build.TAGS,
    )

    /** Device root signal (soft only). */
    fun isRooted(): Boolean = isRooted { File(it).exists() }

    /**
     * Verifies this APK's own signing certificate (Android side).
     * Never throws — any failure to read the signature returns false
     * (unverified), never a crash. Debug builds return true.
     */
    fun checkSignature(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        return try {
            val certs = signingCertBytes(context) ?: return false
            certs.any { bytes -> signatureMatches(sha256Hex(bytes)) }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gTamperCheck: signature check failed", e)
            false
        }
    }

    /**
     * Signing-certificate bytes of this APK, one compatibility
     * function for both API levels. API 28+ uses signingInfo; below
     * 28 the deprecated signatures field is the only source.
     * Removal trigger: minSdk >= 28.
     */
    @Suppress("DEPRECATION")
    private fun signingCertBytes(context: Context): List<ByteArray>? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
        } else {
            info.signatures?.map { it.toByteArray() }
        }
    }
}
