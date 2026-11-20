package com.v2ray.ang.handler.vpn5g

/**
 * Supabase Auth configuration for VPN5G IRBOT (v1.1.1).
 *
 * Project "vpn5g" exists (Frankfurt eu-central-1, free plan, ref
 * jsqvjpmqfmgfgzcycngs). Email auth is enabled with "Allow new users to
 * sign up" and "Confirm email" on; all other providers are disabled.
 * [Vpn5gAuthService] calls the real GoTrue REST endpoints (auth/v1/otp to
 * send the 6-digit code, auth/v1/verify to check it) with the anon key in
 * the apikey header. The anon key is Supabase's public client key by
 * design (safe to ship in the app); the service-role key and the database
 * password must NEVER be put here.
 *
 * Two dashboard items are still needed for codes to actually arrive
 * (app-side code cannot do them):
 *  1. Authentication → Email Templates → "Magic Link": the template must
 *     contain the {{ .Token }} variable — the default template sends a
 *     link, not a 6-digit code, and the app's code field cannot use a link.
 *  2. Custom SMTP with sender VPN5G.IRBOT@gmail.com (a Gmail app password
 *     for that mailbox goes into Supabase's SMTP settings, never into this
 *     app): the built-in email service is rate-limited and only delivers
 *     reliably to project members.
 */
object Vpn5gAuthConfig {
    /** Supabase project URL for "vpn5g". */
    const val SUPABASE_URL = "https://jsqvjpmqfmgfgzcycngs.supabase.co"

    /** Supabase anon/public key for "vpn5g". */
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpzcXZqcG1xZm1nZmd6Y3ljbmdzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTUxNzg4ODgsImV4cCI6MjExMDc1NDg4OH0.ZfRnA0GznbVE9zVZ8ndKSq04W-9AB4RJTKErXkxB7ZA"

    /** OTP sender mailbox (agreed 2026-11-20). Codes are sent from this address. */
    const val OTP_SENDER_EMAIL = "VPN5G.IRBOT@gmail.com"

    /** OTP sender display name. */
    const val OTP_SENDER_NAME = "VPN5G IRBOT"

    /** True once a Supabase project has been created and pasted above. */
    fun isConfigured(): Boolean =
        SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank()
}
