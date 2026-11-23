package com.v2ray.ang.handler.vpn5g

/**
 * Supabase Auth configuration for VPN5G IRBOT (v1.1.8).
 *
 * Project "vpn5g" exists (Frankfurt eu-central-1, free plan, ref
 * jsqvjpmqfmgfgzcycngs). Email auth is enabled with "Allow new users
 * to sign up"; "Confirm email" is OFF (turned off by Aref
 * 2026-11-23), so a direct signup returns a session immediately.
 * All other providers are disabled. [Vpn5gAuthService] calls the
 * real GoTrue REST endpoints with the anon key in the apikey header:
 * POST /auth/v1/signup (direct signup, scenario 1), POST
 * /auth/v1/token?grant_type=password (password login, scenario 4),
 * POST /auth/v1/otp + POST /auth/v1/verify (6-digit code, scenario
 * 2), POST /auth/v1/recover + PUT /auth/v1/user (recovery link,
 * scenario 3). The anon key is Supabase's public client key by
 * design (safe to ship in the app); the service-role key and the
 * database password must NEVER be put here.
 *
 * Dashboard items still needed for the flows to actually work
 * (app-side code cannot do them):
 *  1. Custom SMTP with sender VPN5G.IRBOT@gmail.com (a Gmail app
 *     password for that mailbox goes into Supabase's SMTP settings,
 *     never into this app): without it the OTP code emails may not
 *     arrive at all, and the built-in email service is rate-limited.
 *  2. Authentication → URL Configuration → Redirect URLs must
 *     contain `vpn5g://auth/callback` — otherwise GoTrue refuses
 *     the custom redirect and the recovery link opens the browser
 *     instead of returning to the app.
 *  3. If "Confirm email" is ever turned back ON, the Magic Link
 *     email template must contain the {{ .Token }} variable — the
 *     default template sends a link, not a 6-digit code, and the
 *     app's code field cannot use a link.
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
