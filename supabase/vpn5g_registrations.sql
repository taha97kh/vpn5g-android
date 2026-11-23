-- VPN5G registrations: one account per email AND one account per IP.
--
-- Dashboard steps (run once, Supabase project "vpn5g"):
--   1. Supabase dashboard → SQL Editor → New query.
--   2. Paste this whole file → Run.
--   3. Confirm the output: the table exists and both functions exist
--      (Table Editor → vpn5g_registrations; Database → Functions).
--
-- What this enforces (v1.1.9): a signup is accepted only when NEITHER
-- the email NOR the public IP is already registered. The app checks
-- first (check_registration) and records the pair after a successful
-- signup (register_account). The UNIQUE(ip) constraint is the backstop
-- against two signups racing past the check at the same time.
--
-- Security: row-level security is ON and anon/authenticated have NO
-- direct access to the table (no SELECT/INSERT policy). The table is
-- reachable ONLY through the two SECURITY DEFINER functions below,
-- which run as postgres (the role that runs this file in the SQL
-- Editor) and can only do exactly what they say: check, or insert.
-- The functions are executable by anon (the app calls them with the
-- public anon key); they never return emails or IPs, only whether a
-- value is already taken.

CREATE TABLE IF NOT EXISTS public.vpn5g_registrations (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email text NOT NULL,
    ip text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (email),
    UNIQUE (ip)
);

ALTER TABLE public.vpn5g_registrations ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.vpn5g_registrations FROM anon, authenticated;

-- Returns one row 'email' if the email is registered, one row 'ip'
-- if the IP is registered (both rows when both are taken, no rows
-- when neither is). Read-only.
CREATE OR REPLACE FUNCTION public.check_registration(p_email text, p_ip text)
RETURNS TABLE(kind text)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT 'email'::text
    WHERE EXISTS (
        SELECT 1 FROM public.vpn5g_registrations WHERE email = p_email
    )
    UNION ALL
    SELECT 'ip'::text
    WHERE EXISTS (
        SELECT 1 FROM public.vpn5g_registrations WHERE ip = p_ip
    );
$$;

-- Records one (email, ip) pair after a successful signup. Returns
-- true on insert; on a unique violation (a race lost against another
-- signup) returns false instead of raising, so the caller can still
-- treat the already-created session as signed in.
CREATE OR REPLACE FUNCTION public.register_account(p_email text, p_ip text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.vpn5g_registrations (email, ip)
    VALUES (p_email, p_ip);
    RETURN true;
EXCEPTION WHEN unique_violation THEN
    RETURN false;
END;
$$;

REVOKE ALL ON FUNCTION public.check_registration(text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.register_account(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.check_registration(text, text) TO anon;
GRANT EXECUTE ON FUNCTION public.register_account(text, text) TO anon;
