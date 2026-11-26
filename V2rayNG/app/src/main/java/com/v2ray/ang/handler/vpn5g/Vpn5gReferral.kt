package com.v2ray.ang.handler.vpn5g

import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_PENDING_REFERRER_CODE
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_REFERRAL_CODE
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_REFERRAL_CODE_EMAIL

/**
 * Share-app referral (v1.1.30, Aref's order): every user gets a
 * personal share link; when someone joins through it, BOTH sides
 * receive 1 GB of gift usage on ALL servers. The crediting itself
 * is server-side (the guard/bot owns the grant — see the report);
 * the app side owns:
 *
 *  - a stable per-user referral code (generated once, stored in
 *    MMKV, regenerated when the signed-in email changes);
 *  - the share link: the bot deep link `?start=ref_<code>` (works
 *    without the app installed — the bot credits there) — the app
 *    deep link `vpn5g://ref/<code>` is accepted too;
 *  - capturing an incoming referrer code (deep link) and holding
 *    it until the new user registers, then reporting it to the
 *    server (`record_referral` RPC, best-effort — a failure never
 *    blocks signup).
 *
 * The link building/parsing rules are pure and JVM-tested; MMKV is
 * the only storage touch point.
 */
object Vpn5gReferral {

    /** Gift volume both sides receive, in GB (display + guard contract). */
    const val GIFT_GB = 1

    /** Referral code alphabet (unambiguous chars only, no 0/O/1/I). */
    private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Referral code length. */
    const val CODE_LENGTH = 8

    /** True when [code] has the referral-code shape. Pure. */
    fun isValidCode(code: String?): Boolean {
        val normalized = code?.trim()?.uppercase().orEmpty()
        return normalized.length == CODE_LENGTH &&
            normalized.all { it in CODE_ALPHABET }
    }

    /** Normalizes a code (trim + upper-case), or null when invalid. Pure. */
    fun normalizeCode(code: String?): String? =
        code?.trim()?.uppercase()?.takeIf { isValidCode(it) }

    /**
     * Generates a referral code from [random] (injectable for
     * tests). Pure given the random source.
     */
    fun generateCode(random: kotlin.random.Random = kotlin.random.Random.Default): String {
        return buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) {
                append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
            }
        }
    }

    /** Bot deep link carrying the referral code (the shareable link). Pure. */
    fun shareLink(code: String): String = "$BOT_URL?start=ref_$code"

    /** App deep link carrying the referral code. Pure. */
    fun appLink(code: String): String = "vpn5g://ref/$code"

    /**
     * Parses a referral code out of an incoming link: the app deep
     * link (`vpn5g://ref/<code>`) or a bot-style `?start=ref_<code>`
     * parameter. Null when the link carries no valid code. Pure.
     */
    fun parseIncomingLink(link: String?): String? {
        if (link.isNullOrBlank()) return null
        val text = link.trim()
        // vpn5g://ref/<code>
        Regex("(?i)vpn5g://ref/([A-Za-z0-9]+)").find(text)?.let { match ->
            normalizeCode(match.groupValues[1])?.let { return it }
        }
        // ?start=ref_<code> (bot deep link shared back into the app)
        Regex("(?i)[?&]start=ref_([A-Za-z0-9]+)").find(text)?.let { match ->
            normalizeCode(match.groupValues[1])?.let { return it }
        }
        return null
    }

    // -- MMKV storage -----------------------------------------------------------

    /**
     * The signed-in user's own referral code: the stored one when
     * it belongs to [email], otherwise a fresh code (stored with
     * the email). Null while signed out (no code to share yet).
     */
    fun ownCode(email: String?): String? {
        val normalizedEmail = Vpn5gEmailPolicy.normalize(email.orEmpty())
        if (normalizedEmail.isBlank()) return null
        val storedEmail = MmkvManager.decodeSettingsString(KEY_REFERRAL_CODE_EMAIL)
        val storedCode = MmkvManager.decodeSettingsString(KEY_REFERRAL_CODE)
        if (storedEmail == normalizedEmail && isValidCode(storedCode)) {
            return normalizeCode(storedCode)
        }
        val fresh = generateCode()
        MmkvManager.encodeSettings(KEY_REFERRAL_CODE, fresh)
        MmkvManager.encodeSettings(KEY_REFERRAL_CODE_EMAIL, normalizedEmail)
        return fresh
    }

    /** Stores an incoming referrer code until registration reports it. */
    fun storePendingReferrer(code: String) {
        normalizeCode(code)?.let {
            MmkvManager.encodeSettings(KEY_PENDING_REFERRER_CODE, it)
        }
    }

    /** The pending referrer code captured from a deep link, or null. */
    fun pendingReferrer(): String? =
        normalizeCode(MmkvManager.decodeSettingsString(KEY_PENDING_REFERRER_CODE))

    /** Clears the pending referrer code (after it was reported). */
    fun clearPendingReferrer() {
        MmkvManager.encodeSettings(KEY_PENDING_REFERRER_CODE, "")
    }
}
