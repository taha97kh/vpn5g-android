// get-client-ip: returns the caller's public IP as seen by Supabase.
//
// v1.1.10: the signup one-account-per-IP guard reads the IP from this
// function, so the app depends ONLY on Aref's own Supabase project —
// no ipify.org, no api.ip.sb, no other third-party site. Signup already
// requires Supabase, so this adds no new failure mode.
//
// How it works: Supabase's edge runtime terminates TLS and puts the
// caller's address into the forwarded headers. The first entry of
// `x-forwarded-for` is the real client (the rest are proxies); when
// the request comes through Cloudflare the `cf-connecting-ip` header
// carries it instead; `x-real-ip` is the last fallback. Nothing is
// logged and nothing is stored — the IP is only returned in the
// response body so the app can pass it to `check_registration`.
//
// Auth: JWT verification stays at the Supabase default (ON). The app
// sends the project's anon key as the Bearer token, exactly as it does
// for the REST/RPC calls; the anon key is a valid JWT for this project,
// so the function accepts it. No service-role key is needed here.

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

/** First usable client IP from the forwarded headers, or null. */
function clientIp(req: Request): string | null {
  const forwarded = req.headers.get("x-forwarded-for");
  if (forwarded) {
    const first = forwarded.split(",")[0]?.trim();
    if (first) return first;
  }
  const cf = req.headers.get("cf-connecting-ip")?.trim();
  if (cf) return cf;
  const real = req.headers.get("x-real-ip")?.trim();
  if (real) return real;
  return null;
}

Deno.serve((req: Request): Response => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }
  const headers: Record<string, string> = {
    ...CORS_HEADERS,
    "Content-Type": "application/json",
  };
  const ip = clientIp(req);
  if (!ip) {
    return new Response(JSON.stringify({ error: "no ip" }), {
      status: 400,
      headers,
    });
  }
  return new Response(JSON.stringify({ ip }), { status: 200, headers });
});
