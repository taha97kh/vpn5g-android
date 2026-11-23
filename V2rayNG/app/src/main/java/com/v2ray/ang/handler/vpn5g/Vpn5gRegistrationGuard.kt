package com.v2ray.ang.handler.vpn5g

/**
 * One account per email AND one per IP (v1.1.9).
 *
 * The server-side rule lives in `supabase/vpn5g_registrations.sql`:
 * table `public.vpn5g_registrations` with UNIQUE(email) and
 * UNIQUE(ip), reachable only through the SECURITY DEFINER RPC
 * functions `check_registration` / `register_account`. This file is
 * the pure client side: it interprets the `check_registration`
 * response (a JSON array of `{"kind": "email" | "ip"}` rows) into an
 * outcome and the stable error key for that outcome. Pure Kotlin,
 * no Android APIs, covered by JVM unit tests
 * ([Vpn5gRegistrationGuardTest]).
 *
 * A response that is not a JSON array is unparseable (null outcome):
 * the caller fails CLOSED and never signs up on an unchecked
 * registration. When both the email and the IP are taken, the email
 * outcome wins — the message tells the user to log in with their
 * password instead of blaming the internet connection.
 */
object Vpn5gRegistrationGuard {

    enum class CheckOutcome { OK, EMAIL_TAKEN, IP_TAKEN }

    /**
     * The `kind` values in a `check_registration` response body, or
     * null when the body is not a JSON array at all. An empty array
     * (neither taken) parses to an empty set.
     */
    fun parseKinds(body: String): Set<String>? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("[")) return null
        return Regex("\"kind\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(trimmed)
            .map { it.groupValues[1] }
            .toSet()
    }

    /**
     * The registration outcome for a `check_registration` response
     * body, or null when the body cannot be parsed (fail closed).
     */
    fun outcome(body: String): CheckOutcome? {
        val kinds = parseKinds(body) ?: return null
        return when {
            kinds.contains("email") -> CheckOutcome.EMAIL_TAKEN
            kinds.contains("ip") -> CheckOutcome.IP_TAKEN
            else -> CheckOutcome.OK
        }
    }

    /** Stable error key for a taken outcome; null when free. Pure. */
    fun errorKey(outcome: CheckOutcome): String? = when (outcome) {
        CheckOutcome.OK -> null
        CheckOutcome.EMAIL_TAKEN -> "email_already_registered"
        CheckOutcome.IP_TAKEN -> "ip_already_registered"
    }
}
