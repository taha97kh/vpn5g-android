package com.v2ray.ang.handler.vpn5g

/**
 * Registration email policy (v1.1): signup accepts ONLY @gmail.com
 * addresses. Disposable / temporary-mail domains are rejected too.
 *
 * Pure Kotlin, no Android APIs, so it is covered by JVM unit tests
 * (Vpn5gEmailPolicyTest). The disposable blocklist is defense in depth:
 * no disposable domain can pass the gmail.com rule, but the list stays
 * so the rule survives any future relaxation of the domain requirement.
 */
object Vpn5gEmailPolicy {

    enum class Check { OK, INVALID_FORMAT, NOT_GMAIL, DISPOSABLE }

    /** Well-known disposable / temporary-mail domains. */
    val DISPOSABLE_DOMAINS: Set<String> = setOf(
        "mailinator.com",
        "guerrillamail.com",
        "guerrillamailblock.com",
        "tempmail.com",
        "temp-mail.org",
        "tempmailo.com",
        "10minutemail.com",
        "yopmail.com",
        "sharklasers.com",
        "getnada.com",
        "maildrop.cc",
        "trashmail.com",
        "dispostable.com",
        "fakeinbox.com",
        "throwawaymail.com",
        "mohmal.com",
        "mintemail.com",
        "mytemp.email",
        "emailondeck.com",
        "mailnesia.com",
        "spamgourmet.com",
        "pokemail.net",
        "tempail.com",
        "tmail.com",
        "inboxbear.com",
        "tempinbox.com",
        "mailcatch.com",
        "jetable.org",
        "anonymbox.com",
        "binkmail.com",
        "e4ward.com",
        "kurzepost.de",
    )

    /**
     * Canonical form of an email (v1.1.25): trimmed + lowercased.
     * Proven live 2026-11-25: the guard's `get_subscription_status`
     * is CASE-SENSITIVE — "Taha@gmail.com" returned {"status":"none"}
     * while "taha@gmail.com" returned the active sub_12 — so the app
     * normalizes EVERY email it sends or stores (login, signup,
     * subscription lookup, code redeem, registration, ad records)
     * and case can never matter app-side, whatever the server does.
     * Pure, JVM-tested.
     */
    fun normalize(rawEmail: String): String = rawEmail.trim().lowercase()

    /** Checks [rawEmail] against the policy. Never throws. */
    fun check(rawEmail: String): Check {
        val email = normalize(rawEmail)
        val at = email.lastIndexOf('@')
        if (at <= 0 || at == email.length - 1) return Check.INVALID_FORMAT
        val local = email.substring(0, at)
        val domain = email.substring(at + 1).lowercase()
        if (local.isBlank() || domain.isBlank() || !domain.contains('.') ||
            email.contains(' ') || email.contains("..")
        ) {
            return Check.INVALID_FORMAT
        }
        if (domain in DISPOSABLE_DOMAINS) return Check.DISPOSABLE
        if (domain != "gmail.com") return Check.NOT_GMAIL
        return Check.OK
    }
}
