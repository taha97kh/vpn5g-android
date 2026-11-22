package com.v2ray.ang.handler.vpn5g

/**
 * OTP resend + wrong-code policy (v1.1.5 §7, Android side only).
 *
 * Supabase accepts at most one OTP send per 60 seconds per email (a faster
 * resend comes back `rate_limited`), and a code is only 6 digits, so a
 * wrong code is usually a typo — but after too many wrong tries the user
 * must take a fresh code instead of guessing forever. All decisions here
 * are pure Kotlin so they are covered by JVM unit tests in
 * Vpn5gOtpPolicyTest; the ViewModel only stores the counters.
 *
 * The backend itself is still blocked on the Supabase custom-SMTP setup
 * (a Gmail app password for VPN5G.IRBOT@gmail.com, created by Aref
 * himself): until then codes may not arrive at all, and no client-side
 * policy can fix that. Nothing here fakes a send.
 */
object Vpn5gOtpPolicy {

    /** Seconds between two code sends for the same email. */
    const val RESEND_COOLDOWN_SECONDS = 60L

    /** Wrong-code tries before a fresh code is required. */
    const val MAX_WRONG_CODE_ATTEMPTS = 5

    /** True when a resend is allowed now. Pure. */
    fun canResend(lastSentMs: Long, nowMs: Long): Boolean {
        if (lastSentMs <= 0L) return true
        return nowMs - lastSentMs >= RESEND_COOLDOWN_SECONDS * 1000L
    }

    /** Whole seconds left until a resend is allowed (0 when allowed). Pure. */
    fun resendWaitSeconds(lastSentMs: Long, nowMs: Long): Long {
        if (lastSentMs <= 0L) return 0L
        val leftMs = RESEND_COOLDOWN_SECONDS * 1000L - (nowMs - lastSentMs)
        return (leftMs / 1000L).coerceAtLeast(0L)
    }

    /** True once the wrong-code limit is reached. Pure. */
    fun isLockedOut(wrongAttempts: Int): Boolean {
        return wrongAttempts >= MAX_WRONG_CODE_ATTEMPTS
    }
}
