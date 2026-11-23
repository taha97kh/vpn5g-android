package com.v2ray.ang.handler.vpn5g

/**
 * Supabase Auth configuration for VPN5G IRBOT (v1.1.9).
 *
 * Project "vpn5g" exists (Frankfurt eu-central-1, free plan, ref
 * jsqvjpmqfmgfgzcycngs). Email auth is enabled with "Allow new users
 * to sign up"; "Confirm email" is OFF (turned off by Aref
 * 2026-11-23), so a direct signup returns a session immediately.
 * All other providers are disabled. [Vpn5gAuthService] calls the
 * real GoTrue REST endpoints with the anon key in the apikey header:
 * POST /auth/v1/signup (direct signup, flow 1), POST
 * /auth/v1/token?grant_type=password (password login, flow 3),
 * POST /auth/v1/recover + PUT /auth/v1/user (recovery link,
 * flow 2). OTP is gone (removed v1.1.9): no code is sent, asked
 * for, or verified anywhere. The anon key is Supabase's public
 * client key by design (safe to ship in the app); the service-role
 * key and the database password must NEVER be put here.
 *
 * One account per email AND one per IP (v1.1.9): the rule lives in
 * `supabase/vpn5g_registrations.sql` — table
 * `public.vpn5g_registrations` with UNIQUE(email) and UNIQUE(ip),
 * reachable only through the SECURITY DEFINER RPC functions
 * `check_registration` / `register_account`. The SQL file must be
 * run once in the dashboard (SQL Editor); until it is, every signup
 * fails closed with a network error.
 *
 * Dashboard items still needed for the flows to actually work
 * (app-side code cannot do them):
 *  1. Run `supabase/vpn5g_registrations.sql` in the SQL Editor
 *     (see the file header) — without it no signup can pass the
 *     registration guard.
 *  2. Authentication → URL Configuration → Redirect URLs must
 *     contain `vpn5g://auth/callback` — otherwise GoTrue refuses
 *     the custom redirect and the recovery link opens the browser
 *     instead of returning to the app.
 *  3. Custom SMTP with sender VPN5G.IRBOT@gmail.com (a Gmail app
 *     password for that mailbox goes into Supabase's SMTP settings,
 *     never into this app): without it the recovery-link emails go
 *     through the built-in service and are rate-limited.
 */
object Vpn5gAuthConfig {
    /** Supabase project URL for "vpn5g". */
    const val SUPABASE_URL = "https://jsqvjpmqfmgfgzcycngs.supabase.co"

    /** Supabase anon/public key for "vpn5g". */
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpzcXZqcG1xZm1nZmd6Y3ljbmdzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTUxNzg4ODgsImV4cCI6MjExMDc1NDg4OH0.ZfRnA0GznbVE9zVZ8ndKSq04W-9AB4RJTKErXkxB7ZA"

    /** True once a Supabase project has been created and pasted above. */
    fun isConfigured(): Boolean =
        SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank()
}
