package com.v2ray.ang.handler.vpn5g

/**
 * Supabase Auth configuration for VPN5G IRBOT (v1.1).
 *
 * The Supabase project DOES NOT EXIST YET, so both values below are empty
 * placeholders on purpose. Fill them in (no secrets anywhere else in the
 * app) once the project is created:
 *  1. Create a Supabase project, enable Email Auth (OTP + password).
 *  2. In the Supabase dashboard, set the SMTP sender to
 *     VPN5G.IRBOT@gmail.com (a Gmail app password for that mailbox is
 *     required there — it goes into Supabase's SMTP settings, never into
 *     this app or its source).
 *  3. Paste the project URL and anon/public key here.
 *
 * Until then [Vpn5gAuthService] reports NotConfigured and the auth screens
 * say so honestly instead of pretending to send codes. The anon key is a
 * public client key by design (it is safe to ship in the app once the
 * project exists); the service-role key must NEVER be put here.
 */
object Vpn5gAuthConfig {
    /** Supabase project URL, e.g. "https://xyzcompany.supabase.co". Empty until created. */
    const val SUPABASE_URL = ""

    /** Supabase anon/public key. Empty until created. */
    const val SUPABASE_ANON_KEY = ""

    /** OTP sender mailbox (agreed 2026-11-20). Codes are sent from this address. */
    const val OTP_SENDER_EMAIL = "VPN5G.IRBOT@gmail.com"

    /** OTP sender display name. */
    const val OTP_SENDER_NAME = "VPN5G IRBOT"

    /** True once a Supabase project has been created and pasted above. */
    fun isConfigured(): Boolean =
        SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank()
}
