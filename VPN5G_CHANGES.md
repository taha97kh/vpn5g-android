# VPN 5G v1 — changes (2026-11-20)

Fork of v2rayNG (commit 740f0ba) as **VPN 5G** — native Android VPN app,
package `com.taha97kh.vpn5g`, version 1.0.0 (versionCode 1). Kotlin package
stays `com.v2ray.ang`; namespace stays `com.v2ray.ang`. LICENSE (GPL-3.0)
kept intact; the fork credit is in the About screen
("بر پایه‌ی v2rayNG (GPL-3.0)") and in this file.

## v1.1.1 (2026-11-20, versionCode 3)

- **App name is "VPN 5G" everywhere**: launcher label (main + fdroid, all
  locales) and in-app title. `app_name` was still "v2rayNG" in the
  `values-fa` locale (the user's locale) and 8 other locales, plus the dev
  / pre-release flavours. The About-screen credit is reworded to
  "بر پایه‌ی نرم‌افزار آزاد (GPL-3.0)" (no name). "v2rayNG" remains only
  in `LICENSE` and source-code attribution comments; package names
  unchanged.
- **Server rows hide config names**: each row shows flag + country name
  (GeoIP; globe + "نامشخص" when unknown) + ping, with the small subtitle
  "VPN5G_iRBot". Applies to the main list, search results, Auto row,
  Recommended row, and all tabs. Nothing remark-derived is displayed.
- **Free tier = 50 fastest configs**: after each sync/ping the 50
  fastest-pinging configs are free (`Vpn5gConstants.FREE_SERVER_COUNT`,
  `Vpn5gSync.selectFreeGuids`); everything else is premium-locked. The
  3-free-countries model and `free_countries` in `app_config.json` are
  retired (the field still parses, but is ignored). Premium-table counts
  are per-server live counts. 30-minute session timer unchanged.
- **Fast switch on dead configs**: a config with no ping is never tried
  first; a tap or connect resolves to the next best free server in a
  different country immediately, with a brief notice. Tunnel-check
  failure switches the same way (no fixed 4 s wait — polls for the
  restart instead). Per-session attempt bound max-3 kept.
- **Real registration via Supabase OTP (passwordless)**: Gmail → real
  6-digit code (`auth/v1/otp`) → code entry (`auth/v1/verify`) → logged
  in. Recovery uses the same OTP flow. No password anywhere. Config in
  `Vpn5gAuthConfig` (`SUPABASE_URL`, `SUPABASE_ANON_KEY`); the
  "به‌زودی — نیاز به سرور دارد" block shows only when the config is
  absent. Dashboard still needs: Magic Link email template must contain
  `{{.Token}}`, and custom SMTP for VPN5G.IRBOT@gmail.com.
- **Footer**: "Made by AREF KH / 2026 - 1405" at the bottom of the home
  screen (above the bottom nav) and the Me/Profile screen. The only
  place a personal name appears.
- **Connect animation**: "4ref.ai" sits on the circular power button;
  while connecting a ring spins around the circle; on connect the ring
  stops, the text briefly catches fire (orange/red glow + slight
  scale-up) and fades away, leaving the normal connected state.
- **Error fixes**: `bestFreeServer` no longer falls back to a no-ping
  server; `refresh()` clears `isLoading` on failure; GeoIP caches only
  non-UNKNOWN results; service start failure shows a short Persian
  message instead of raw core text; payment-gateway `!!` made a safe
  call.

## v1.1.4 (2026-11-22, versionCode 6)

Comprehensive redesign, bug fixes and professional upgrade. **One
reinstall is required**: this release is signed with the new fixed key
of the VPN5G GitHub account (the 1.1.3 key lives only in the suspended
account's secrets), so 1.1.3 users uninstall once; from 1.1.4 on,
updates install in place again. Release build: `assemblePlaystoreRelease`
(`build-release.yml`, artifact `vpn5g-release-apk`), `debuggable=false`,
same fixed key as Debug. In-app updater now points at
`github.com/VPN5G/vpn5g-android`.

- **One-button automatic connect (§1)**: the power button runs the whole
  chain — real internet check (active network + a short HTTPS probe),
  then the fastest free server with a measured ping, then the transport
  MTU profile, then the service. The home shows only six plain states
  (checking internet / finding server / connecting / creating tunnel /
  connected / retrying); no technical error is ever shown. **"Connected"
  appears only after real traffic passed through the tunnel** (exit-IP
  check): the old "service running" and "ping OK" shortcuts are gone, an
  UNKNOWN verdict now counts as a failure (retry), and the tunnel is
  re-verified every 60 s while connected — a dead tunnel drops and the
  drop detector fails over instead of showing "connected". Returning to
  the app re-reads the daemon state.
- **No advanced settings in the UI (§2)**: the protocol picker, the
  routing screen and the "advanced settings" link to the upstream
  MainActivity are removed (bottom nav is now Home / Premium / Guide /
  Me). The engine still supports every protocol; it picks and manages
  protocol, MTU, DNS and routing itself.
- **Self-contained engine (§3)**: unchanged — bundled Xray Core + TUN
  libraries, VpnService, no other VPN app needed.
- **Security fixes (§4)**: `debuggable=false` in Release; Release is
  signed with the fixed key (new `release` signing config, same
  keystore file + `VPN5G_KEYSTORE_*` env vars as Debug); `allowBackup`
  is now `false` (no backup of sensitive data); cleartext is off
  (manifest `usesCleartextTraffic=false` + network security config
  `cleartextTrafficPermitted=false`); user-installed CAs are no longer
  trusted by default (system CAs only). The local HTTP/SOCKS inbounds
  never set a listen address, so Xray binds its default 127.0.0.1 —
  loopback-only, verified by code review. No secrets in the APK, no
  credentials or configs in logs (the validator logs only reason
  counts, never URIs). Third-party library versions
  (`libs.versions.toml`): AGP 9.5.1, Kotlin 2.4.21, JUnit 6.1.3, MMKV
  1.3.17, Gson 2.15.0, OkHttp 5.5.1, coroutines 1.11.0, lifecycle 2.11.0,
  CameraX 1.6.2, Compose BOM 2026.09.00, Coil 3.6.3, desugar 2.1.5. No
  CVE scan was possible offline — that is a CI/networked follow-up, not
  claimed here.
- **Server-system fix (§5)**: every config is validated before ping or
  display — only VLESS / VMess / Trojan / Shadowsocks; non-empty host;
  port 1–65535; well-formed IPv6 literals; UUID passwords for
  VLESS/VMess; non-empty passwords for Trojan/Shadowsocks; TLS requires
  SNI; REALITY requires public key + short id; gRPC requires a service
  name; WebSocket/XHTTP path defaults to `/`. Dedupe is by server
  identity (`scheme://host:port:uuid-or-password`), not raw URI text.
  Bundled fallback re-validated: **641 → 524 entries** (71 duplicates
  removed; 46 invalid: 29 REALITY without key/sid, 8 non-UUID
  passwords, 4 gRPC without service name, 3 TLS without SNI, 1 port 0,
  1 unparseable). A server that fails tunnel verification stays out of
  the auto picker for 6 hours.
- **DNS / MTU / UDP / routing (§6)**: MTU follows the active transport
  (Wi-Fi 1500, cellular 1400) instead of forcing 1500 everywhere; DNS
  is in-tunnel only (Global TUN mode, no split-DNS path); UDP is never
  blocked by VPN5G — any stored "block UDP 443" (QUIC) rule is stripped
  from the stored routing ruleset before connect. Note: the upstream
  routing presets still ship that rule, but VPN5G never imports them
  and the sanitizer removes it from any stored ruleset.
- **Stable connection + auto-recovery (§7)**: Wi-Fi/mobile handover
  (existing `NetworkMonitor`), IP change, screen off, background return,
  Xray crash and DNS loss go through the existing drop detector and
  bounded failover (max 2 per session, different country first); the
  60-second re-verification and the on-resume state re-read are new.
- **Professional minimal UI (§8)**: home keeps logo, status, big
  Connect/Disconnect, selected server and the 30-minute countdown;
  status and the header pill come from the connect state machine.
  There is no data-usage row: no traffic-stats API exists in the
  codebase, so none is invented.
- **Brand watermarks (§9)**: `VPNCRACKNET`, `VPN5G_IRBOT`, `AREF_5G`
  drawn faint (alpha ~0.07–0.08, slightly rotated) behind the power
  button, the update button and the four primary subscription buttons.
  Decorative only: no semantics (TalkBack never reads them), no touch
  handling, no readability impact.
- **Independence from side services (§10)**: the bundled fallback and
  the local cache are unchanged; when the config service is unreachable
  the app shows the last good list (or the fallback on first launch)
  and marks itself offline.
- **Tests (§11)**: 30 new JVM tests (`Vpn5gServerValidatorTest`,
  `Vpn5gAutoConnectTest`) cover every validation branch, the state
  machine (including "CONNECTED only from CREATING_TUNNEL on
  VERIFIED"), the best-server picker and the failed-server TTL store.
  They run in CI (`testPlaystoreDebugUnitTest` in `build-release.yml`).
  Device/server tests (real tunnel, DNS, IPv4/IPv6, TCP, UDP,
  QUIC/HTTP3, handover, screen off, dead-server switch, CPU/RAM,
  multi-device) were NOT run on this server — they need Aref's phone
  and live servers; see the 1.1.4 delivery report for the full list.

## v1.1.5 (2026-11-23, versionCode 7)

- **Root-cause fix — connect never switched to the next server (§2)**:
  in 1.1.4 the verify-loop failover emitted `startService(newGuid)`
  while the old core was still running; `CoreServiceManager` refused
  ("Core already running") and the service then stopped itself, so the
  VPN died instead of switching and the state stranded in RETRYING.
  Failover (and the manual switch while running) now goes through the
  daemon's clean restart path (`LauncherManager.restartServiceOrStart`:
  stop → 500 ms → start with the new server), and the verify job waits
  out the stop/start cycle instead of dying on it (`failoverRestartInProgress`
  guards both the verify-job cancel and the drop detector). The drop
  detector is unchanged as the second failover path.
- **Connect state machine (§2)**: 10 states now — IDLE,
  CHECKING_INTERNET, FINDING_SERVER, CONNECTING, CREATING_TUNNEL,
  VERIFYING_TUNNEL (new — "checking internet from inside the VPN"),
  SWITCHING_SERVER (new — failover / manual switch),
  CONNECTED, RETRYING, FAILED (new). CONNECTED is emitted only from
  VERIFYING_TUNNEL on VERIFIED (real in-tunnel request + DNS check,
  unchanged criterion from 1.1.4). GIVE_UP lands in FAILED except from
  CONNECTED → IDLE (30-minute timer / clean stop never shows an error).
  A failed connect/retry shows the precise cause and stays
  Disconnected; bounded retries (8 verification attempts per chain,
  v1.1.5 refinement), no infinite loops; failed servers stay excluded
  for 6h (verified through the new flows — the TTL store from 1.1.4 is
  now consulted by the manual resolver as well as the auto picker).
- **Manual server picker (§3)**: the "سرورهای رایگان" row is now a real
  two-level dropdown — countries with flag + server count + best ping,
  then that country's free servers with health ("سالم" / "بدون پینگ")
  and real ping. A tap validates the pick exactly like auto
  (exists, free, has ping, not failed in the last 6h, structurally
  valid) and persists it (`KEY_MANUAL_SERVER_GUID`) for the next
  connect. A bad pick shows the precise reason and offers "بازگشت به
  خودکار"; a manual pick that fails mid-session ends the session with
  the reason — it is never silently replaced. The "خودکار" row works
  independently (one tap connects; its chevron opens an info panel
  with a return-to-auto action while manual is active). Picking auto
  again clears the stored manual GUID.
- **Expandable sections (§4)**: new reusable `Vpn5gExpandableSection`
  (smooth expand/collapse animation, closable, accessible chevron as
  its own semantic node) now wraps every option: free servers, auto,
  subscription status, comparison, plans, payment, promo, account,
  subscription, guide sections and FAQ. No dead/demo menus — every
  control works.
- **Guide tab (§5)**: the bottom-nav "مرورگر" tab (which opened the
  About screen) is now "راهنما" with a matching help icon
  (`Vpn5gHelpIcon`). The Guide screen holds all 20 Persian sections
  from the spec plus a separate FAQ (8 questions, tap to reveal the
  answer). All text describes only what the app really does — including
  the honest OTP note that codes are sent by the email service and may
  be delayed until the SMTP backend is set up.
- **Me page (§6)**: only three buttons, exactly `VPNCRACKNET` /
  `VPN5G_IRBOT` / `Aref_5G` in that order, with the real links
  (channel, bot, support) and no extra text under them. Account and
  subscription blocks are expandable; version/build date stay in a
  separate app-info block below the buttons.
- **Theme (§8)**: dark teal-green/black theme unchanged; big connect
  button; status text now covers all 10 connect states; dropdown
  animations smooth; full RTL; Persian font unchanged. Advanced
  settings stay out of the UI; the engine keeps all capabilities.
- **OTP states (§7 — Android side only)**: code-sent note + resend
  button with a 60-second countdown, and a 5-wrong-codes lockout that
  requires a fresh code (policy in `Vpn5gOtpPolicy`, unit-tested).
  The backend is NOT done: the Supabase custom-SMTP setup still needs
  Aref to create the Gmail app password for VPN5G.IRBOT@gmail.com
  himself — no fake sending was implemented and §7 is not marked
  complete. See the 1.1.5 delivery report.
- **Re-audit (§9)**: manifest, cleartext, CA trust, backup, logging,
  and release signing re-checked against the 1.1.4 list — no new
  issues; the only findings are pre-existing dead code carried over
  (`Vpn5gProtocol.kt` + test, `KEY_PROTOCOL_FILTER`, `Vpn5gGlobeIcon`),
  left untouched as out of scope.
- **Independence (§10)**: no dependence on any other VPN app — the
  app still uses only VpnService + its internal Xray engine; config
  sources and email are the only external services, and a down service
  produces a precise error, never a fake state.
- **Tests (§11)**: `Vpn5gAutoConnectTest` updated for the new table
  (happy path through VERIFYING_TUNNEL, GIVE_UP, SWITCHING, FAILED
  retry, VERIFIED-only-from-VERIFYING_TUNNEL); new
  `Vpn5gManualServerTest` (resolve branches) and
  `Vpn5gOtpPolicyTest` (60 s cooldown + 5-attempt lockout). They run
  in CI (`testPlaystoreDebugUnitTest` in `build-release.yml`). Device/
  server tests (real connect, failover, manual pick, DNS leak, IPv4/
  IPv6, TCP, UDP, QUIC/HTTP3, Wi-Fi, mobile data, handover, screen
  off, disconnect/reconnect, relaunch, CPU/RAM, multi-device) were
  NOT run on this server — they need Aref's phone and live servers;
  see the 1.1.5 delivery report for the full list.
- **Watermarks removed entirely (2026-11-23, same 1.1.5, field
  test)**: alpha 0.02 did NOT hide them on a real device — the three
  house names were still readable behind the Me buttons and inside the
  power circle. `Vpn5gBrandWatermark` is deleted, with all 6 call
  sites (update button, power circle, 4 subscription buttons); nothing
  of it is drawn anywhere anymore. (Note: the Me buttons never had a
  watermark call site in any release — the field-test screenshot
  suggests the installed build on the phone was not this commit; the
  removal guarantees no build can render them.)
- **Tunnel verification hardened (same 1.1.5, field test)**: a manual
  France pick (TCP ping 82 ms) failed verification with a single
  5-second probe fetch — one slow first fetch through a cold core
  killed a server that may have been healthy. `verify()` now retries
  the exit-IP fetch in up to 3 rounds inside a 15 s budget (7 s per
  fetch, 500 ms between rounds), trying the primary IP API
  (`api.ip.sb`) and then an ipify fallback each round; the verdict
  logic is extracted as pure `evaluateExitIp()` (unit-tested). A truly
  dead server still fails every round and ends FAILED. The manual
  failure still ends the session (never silently replaced), but the
  failed connect now shows ONE error card: the generic FAILED card is
  suppressed when the manual-failure card (reason + return-to-auto)
  is shown, instead of the same reason stacked twice.
- **Open question — the France servers themselves**: the two
  `afrcloud22.mmv.kr` entries are a Cloudflare Worker front whose
  WebSocket upgrade returned 403 "WebSocket Connection Blocked" from
  this server's egress (egress-specific, not proven for Iran), and
  the REALITY entry cannot be handshake-tested from here at all. If
  Auto mode on the phone also fails every server, the verify path
  above was the cause; if Auto connects, the France backends behind
  the front are dead and the entries should be dropped from
  `live_configs.json`.
- **Refinement — never disconnect during the connect attempt, switch
  fast (§2, same 1.1.5)**: the connect chain now tries up to 8 servers
  before FAILED (was 3; bounded, each attempt has its own timeouts and
  the 6h failed store keeps dead servers out). The drop-detector
  debounce is 800 ms (was a fixed 3,000 ms) — intentional restarts are
  already excluded by `suppressAutoFailover`, so the wait only covers a
  transient daemon hiccup. RETRYING is now a connecting state (cyan
  ring, "در حال تلاش مجدد برای اتصال…") instead of reading as a
  failure; the drop detector still switches through SWITCHING_SERVER,
  never through Disconnected/IDLE. Unchanged on purpose: the daemon's
  500 ms restart wait (the guard against the 1.1.4 "core already
  running" refusal) and the 1,500 ms pre-verify settle (a shorter wait
  risks false UNKNOWN skips of healthy servers). Once VERIFIED,
  CONNECTED is shown immediately — no post-verification waiting.
- **Version label (same 1.1.5)**: the home-screen footer now shows
  "ورژن 1.1.5" from `BuildConfig.VERSION_NAME` (never hardcoded, so it
  follows every future bump); the Me-page app-info block keeps its
  version/build-date lines.
- **Announcement system via the Telegram bot (same 1.1.5)**: the
  `app_config.json` contract gains an `announcement` object (`enabled`,
  `text`, `image_url`, `id`); the home screen shows it as a dismissible
  card (text + optional Coil image) under the notice, and nothing when
  there is none. Dismissal is persisted by announcement id (or text) —
  a republished version is shown again. The image needs a network
  fetcher, so `coil-network-okhttp` (same version as `coil-compose`) is
  now a dependency — without it Coil 3 cannot load remote images at
  all. Bot side: admin panel →
  📢 تبلیغات و اطلاع‌رسانی → 📣 اطلاعیه اپ VPN5G sets/clears the text
  and image (a public URL, or a photo uploaded to the HF Space as
  `announcement.jpg`) and publishes the merged `app_config.json` to the
  Space (`services/app_config_publisher.py`, HF write token in
  `HF_TOKEN`). End-to-end NOT verified here (this server's bot is OFF
  by standing order; the live copy runs on another host) — deployment
  steps are in the delivery report.

## v1.1.6 (2026-11-23, versionCode 8)

Aref tested the fixed 1.1.5 on his phone: it still never connects and
no server tunnels — "تمام کانفیگ‌ها فیک هستند و هیچ‌کدام وصل نمی‌شوند".
Auto mode fails too, so this is not one dead server; the pool itself
has to grow with real, live sources.

- **Four new real GitHub config sources** — every URL below was fetched
  from this server on 2026-11-23, parsed, and sample-checked (TCP
  connect to host:port). All four join the default source list and are
  sampled like the other GitHub files (300-line spread per refresh);
  the hard rule stands: a config with no ping is never stored or shown.
  - `morpheusadam/v2ray-config` — `subs/bundles/iran.txt` (censored-network
    bundle, REALITY / XTLS-Vision first): 2,817 of 3,000 lines parse,
    15/15 sampled TCP connects.
  - `morpheusadam/v2ray-config` — `subs/bundles/best.txt` (only sources
    scoring 85+, every proxy proved with a real TLS tunnel; generated
    2026-11-22): 1,998 of 2,000 lines parse, 15/15 sampled TCP connects.
  - `Alirewa/V2ray-Configs` — `config.txt` (2,000 configs, refreshed
    every 30 minutes): 1,986 of 2,000 lines parse, 15/15 sampled TCP
    connects.
  - `MatinGhanbari/v2ray-configs` — `subscriptions/v2ray/super-sub.txt`
    (best configs by test results, 200 entries, base64-wrapped): 200 of
    200 lines parse after decoding, 15/15 sampled TCP connects.
  - Kept: his own HF `live_configs.json` + `sub.txt` (41 entries, 12/12
    sampled TCP connects from here) and both existing GitHub sources —
    Radikal `verified/configs.txt` (1,318 lines, all parse, 15/15 TCP;
    header says tested 2026-11-23 02:13 UTC by a real proxied request)
    and barry-far `All_Configs_Sub.txt` (9,033 of 9,120 lines parse,
    15/15 TCP).
  - Not added: `mahdibland/V2RayAggregator` `sub/sub_merge.txt`
    (4,428 of 4,515 lines parse, 15/15 TCP) — a big unverified pool
    duplicating barry-far's role, and its repo was last updated
    2026-09-24; `Epodonios/v2ray-configs` `All_Configs_Sub.txt` — 404,
    the file is gone.
  - Honest limit: TCP reachability from this server is not proof a
    server tunnels from Iran — the phone's own ping + tunnel check is
    still the judge, and only a real connect test on his phone settles
    whether the bigger pool fixes the failure.
- **Base64 subscription bodies now decode** (`Vpn5gSync.decodeSubscriptionBody`,
  pure, unit-tested): a body with no config-URI line is tried as base64
  and used only if it decodes to URI lines. This unblocks the new
  MatinGhanbari source — and fixes a real bug: our own HF `sub.txt`
  is base64-wrapped, so before this change it contributed **zero**
  configs (parseUriLines found no scheme line in the blob). The HF
  `live_configs.json` path is untouched (JSON, parsed as before).
- **Me page buttons renamed (Aref's order, 2026-11-23)** — same three
  destinations, new exact labels in this order: «کانال کانفیگ رایگان»
  (channel @VPNCRACKNET), «ربات هوشمند» (bot @VPN5G_IRBot),
  «پشتیبانی آنلاین» (support @Aref_5G). The strings changed in
  `values/strings.xml`; the Guide screen's support section reuses the
  same three strings, so its labels match.
- **Tests**: new `Vpn5gConfigSourcesTest` (default list contents + HTTPS-only,
  capped-source classification, base64 decode incl. line-broken blobs,
  garbage/non-URI passthrough). They run in CI
  (`testPlaystoreDebugUnitTest` in `build-release.yml`). Device/server
  tests (real connect, failover, manual pick, DNS, IPv4/IPv6, TCP, UDP,
  QUIC/HTTP3, handover, screen off) were NOT run on this server — they
  need Aref's phone and live servers.

## v1.1.7 (2026-11-23, versionCode 9)

Aref tested 1.1.6 on his phone: still nothing connects — «هیچ‌کدام وصل
نشدند». The screenshot shows the app stuck on «در حال اتصال...» with a
RED dialog: the current node uses an UNENCRYPTED connection and
connecting to such nodes is NOT possible with Xray core 26.2.6 and
above; it says it moves to the next server. The free-servers row also
shows a manual France pick with a ~1 ms ping — Aref says the pings are
FAKE («پینگ‌ها فیک بوده») or the tunnel is not formed properly.

- **The red dialog, diagnosed (proven from code, not guessed).** The
  dialog is upstream v2rayNG's `toast_allow_insecure_deprecated`
  (`values-fa/strings.xml`, exact Persian text matches the screenshot).
  It fires in `LauncherManager.startContextService` when a config has
  `insecure == true` and no pinned CA (`pinnedCA256` empty): it shows
  the toast, copies it to the clipboard — and the service start
  CONTINUES (no return/throw). Then the core refuses the config: the
  bundled Xray core is v26.11.9, and `allowInsecure` was REMOVED in
  26.2.6 (a config setting it fails to load with `The feature
  "allowInsecure" has been removed`; use `pcs`/`vcn` instead). From
  26.7.11 the core also refuses VLESS without TLS/REALITY/post-quantum
  encryption to a public address. So the service never reports running
  and the 1.1.6 chain dies silently: the verify loop only runs after the
  service starts, and a stop with no active session returns the state
  to IDLE with no error — the toast's "moves to the next server" does
  NOT happen (there is no failover from a service-start failure). The
  user sees the red dialog, then nothing.
- **Encrypted-only filter — the main fix.** `Vpn5gServerValidator`
  now rejects any config that is not provably encrypted, at sync time
  (never stored, never shown), at import time (deep links), and at
  connect time (an unencrypted pick is skipped before the core ever
  sees it, so the red dialog can never appear):
  - `insecure=1` without a pinned CA (`pcs`) → rejected (the exact
    red-dialog node; a pinned CA is the core's replacement and is
    accepted);
  - VLESS needs TLS, REALITY, or a non-"none" post-quantum
    `encryption` value; VMess needs a real AEAD cipher — only scy
    "none" without TLS/REALITY is rejected (with TLS the transport
    encrypts even when scy is "none"; scy "auto" is AES-128-GCM and is
    accepted);
  - Trojan needs TLS or REALITY and an empty `flow` (Trojan + `flow`
    is a hard core error);
  - Shadowsocks needs an AEAD cipher (`aes-128/256-gcm`,
    `chacha20-poly1305` / `chacha20-ietf-poly1305`,
    `xchacha20-poly1305`, `2022-blake3-*`); stream ciphers
    (`aes-*-cfb`, `rc4-md5`), "none" and plain are rejected;
  - SOCKS / HTTP / plain nodes were already rejected as unsupported.
  - Per-source survival after filtering (re-checked 2026-11-23 from
    this server, Python replication of the validator over the same
    samples the app takes — the HF lists in full, a 300-line spread of
    each GitHub file; the HF live list and `sub.txt` are the same 41
    entries, counted twice below):
    - HF `live_configs.json` / `sub.txt`: 24 of 41 survive (59%) each;
    - Radikal: 256 of 300 (85%); barry-far: 253 of 300 (84%);
    - morpheusadam iran: 219 of 300 (73%); morpheusadam best: 153 of
      300 (51%); Alirewa: 164 of 300 (55%); MatinGhanbari: 148 of 200
      (74%);
    - total: 1,241 of 1,782 sampled (70%) survive; the new filter
      drops 158 of the 1,399 the old validator kept (11%) — mostly
      unencrypted VLESS/Trojan (100), `insecure=1` (20) and weak SS
      ciphers (38); no Trojan `flow` was seen. The weakest sources
      (morpheusadam best 51%, Alirewa 55%) still yield 150+ usable
      entries each, so no source is empty and no replacement source
      was needed. The bundled fallback snapshot is NOT regenerated:
      `loadBundledFallback` runs the same validator at import, so the
      filter applies to it automatically.
- **Auto must not end until connected (Aref's order: «تا زمانی که وصل
  نشده کانفینگ اتومات تمام نشود»).** The fixed 8-attempt cap is REMOVED
  from the auto chain (both `MAX_TUNNEL_ATTEMPTS` and the 2-attempt
  post-connect drop-failover cap are gone). The chain now keeps trying
  the next pinged free server until one VERIFIES as a real tunnel or
  the user disconnects. It is bounded sanely, not by a count: one pass
  over the whole pinged free list, then a list re-refresh and continue
  (the 6h failed store keeps dead servers out, so a refresh can only
  add untried servers); when a refresh yields no untried candidate the
  chain waits 60 s in RETRYING and refreshes again — never a FAILED
  state while the user has not disconnected, and the UI stays in a
  connecting state the whole time (new string
  `vpn5g_refreshing_servers`: «لیست سرورها تمام شد؛ در حال
  به‌روزرسانی لیست و تلاش دوباره…»). Per-attempt timeouts, the 6h
  failed-server exclusion and the 30-minute session timer still apply.
  The two "gave up" strings (`vpn5g_tunnel_failed_all`,
  `vpn5g_autofailover_gave_up`) are removed — no code path uses them
  any more. Boundary, stated plainly: a daemon restart failure (the
  service itself fails to come back after a switch) still ends the
  chain with a precise error — that is a service failure, not a server
  failure, and the user re-taps connect.
- **Real ping (the 1 ms France ping was fake).** The TCP-connect ping
  only proves a TCP handshake completed; on a filtered network a
  middlebox answers the handshake in-transit while the real protocol
  handshake never reaches the server — so a dead server can show
  1 ms and the free tier (fastest by TCP time) filled with servers
  that can never tunnel. Now, after every refresh, the 24 fastest
  TCP-pinged candidates are put through a REAL through-the-proxy test
  (`Vpn5gRealPing`, reusing upstream's own `RealPingWorkerService`
  with `onlyTcp = false`: a throwaway core per server, a real request
  to the delay-test URL through it). Only servers that pass it get a
  shown ping, the free tier, or an auto pick; the stored ping of a
  verified server becomes the REAL delay (not the TCP time), and a
  server that fails the real test has its ping withdrawn (-1). A
  TCP-only ping is never displayed as a number anywhere (home rows,
  Auto row, country best-ping, manual subtitle, current-server ping
  all show "—" until a real ping exists). Evidence is keyed by server
  identity (scheme://host:port:identity), not GUID, so a proven server
  keeps its real ping across refreshes and the probe only re-tests new
  candidates. The probe is skipped while the VPN is running (no
  throwaway core next to the live one) and is bounded by a 45 s budget;
  it is a measurement only and never decides the core lifecycle.
  Honest limit: a real through-the-proxy delay proves the proxy
  handshake works — it does not prove the tunnel carries traffic from
  Iran; only the tunnel verification (exit IP + DNS) and a real connect
  on his phone prove that.
- **Xray core version: no upgrade.** The bundled core is already the
  newest: `AndroidLibXrayLite` is tagged v26.11.9 and the Xray-core
  latest release is also v26.11.9. No dependency bump was made — the
  fix is the filter, not a core upgrade (and a speculative core upgrade
  would risk the build, which only CI can check).
- **Tests**: validator tests updated for the new rules (plain VLESS,
  VMess scy none, Trojan no-TLS / with flow, insecure=1 with and
  without a pinned CA, weak vs AEAD SS ciphers, VLESS post-quantum
  encryption) plus new `Vpn5gRealPingTest` (probe-batch selection:
  fastest unproven win, already-proven not re-tested, empty cases)
  and updated `Vpn5gAutoConnectTest` / `Vpn5gTopFreeTest` (a 1 ms
  TCP-only ping never wins the pick or a free slot). They run in CI
  (`testPlaystoreDebugUnitTest` in `build-release.yml`). Device/server
  tests (real connect, failover, manual pick, real-ping probe on a live
  network, DNS, IPv4/IPv6, TCP, UDP, QUIC/HTTP3, handover, screen off)
  were NOT run on this server — they need Aref's phone and live servers.
  The §7 email tests are still blocked on the Gmail app password (see
  v1.1.5).

## Created files

### New code — `V2rayNG/app/src/main/java/com/v2ray/ang/handler/vpn5g/`
- `Vpn5gConstants.kt` — endpoints (HF `live_configs.json`, `sub.txt`,
  `app_config.json`), bot/channel/support links, default free countries
  (بین‌الملل، فرانسه، آلمان — top-3 by live server count, tie at 1 broken
  toward آلمان), both GitHub config sources (see below), 30-min session
  limit, MMKV keys, deep-link schemes, country-label normalization
  (strips the flag-emoji prefix, e.g. "🇫🇷 فرانسه" → "فرانسه", for matching;
  display keeps the original label).
- `Vpn5gModels.kt` — `LiveConfigEntry`, `AppConfigRemote`,
  `SubscriptionStatusRemote`, `Vpn5gSyncResult`, `Vpn5gServerUi`,
  `Vpn5gCountryUi`. All remote-model fields are nullable: Gson allocates
  without the Kotlin constructor, so absent JSON fields stay null.
- `Vpn5gRemoteConfig.kt` — fetches + parses `app_config.json` (10s
  timeout), caches raw JSON in MMKV SETTING `vpn5g_app_config_json`,
  returns cached/defaults on any failure. Never throws.
- `Vpn5gSync.kt` — `refresh()` on Dispatchers.IO: fetches app config, then
  all config sources, parses URIs, TCP-pings each candidate
  (`SpeedtestManager.socketConnectTime`, 1500 ms, max 8 concurrent), drops
  every dead config, parses survivors with `AngConfigManager.parseConfig`,
  sets `profile.country`, records ping, and REPLACES the "VPN 5G" group via
  `MmkvManager.saveServerProfiles(append=false)`. If every fetch fails, the
  last good list is kept untouched and `offline=true` is reported. Also
  `loadGroupedServers()` (free first, then locked, ping-sorted),
  `importUri()` / `importText()` for deep links and files.
- `Vpn5gTimer.kt` — 30-minute auto-disconnect: unique WorkManager work
  (`vpn5g_30min_disconnect`, REPLACE) + `Vpn5gDisconnectWorker` which stops
  the VPN via `LauncherManager` and posts a notification (channel
  "VPN 5G", no reconnect action — reconnect happens from the home screen).
  Connect-start timestamp in MMKV SETTING `vpn5g_connected_at` drives the
  live countdown.

### New UI — `V2rayNG/app/src/main/java/com/v2ray/ang/ui/vpn5g/`
- `Vpn5gMainActivity.kt` — LAUNCHER activity + `Vpn5gHomeViewModel`
  (StateFlow UI state; running state from the daemon broadcast, same
  channel as `MainRepository`; 5-minute auto-refresh ticker; 1-second
  countdown ticker; initial refresh on launch) + `Vpn5gHomeScreen`:
  header (pulsing logo, status), big one-tap connect/disconnect button,
  current server + ping + 30-min countdown, notice card, offline banner,
  country sections (free first, locked rows open a dialog with a bot
  button), house-ad promo card for the bot between free and locked
  sections, pull-to-refresh (`PullToRefreshBox`), bottom navigation
  (Home / Guide / Subscription / About) with slide+fade `AnimatedContent`
  transitions, and an "advanced settings" link to `MainActivity`. Connect
  flow mirrors `MainActivity.requestServiceStart` exactly (VpnService
  permission → `LauncherManager.startService(guid)`). Deep-link/file import
  in `onCreate`/`onNewIntent` (config URI → parse + save; file/content URI
  → read text → `importBatchConfig`, toast with counts).
- `Vpn5gMotion.kt` — shared motion language (see "Animation" below):
  `Vpn5gAnimatedBackground`, `Vpn5gPulsingLogo`, `Vpn5gStaggeredItem`,
  palette constants.
- `Vpn5gGuideScreen.kt` — Persian numbered guide (connect, import a
  config, dead server, 30-minute auto-disconnect).
- `Vpn5gAboutScreen.kt` — maker عارف خیراندیش (@4ref.ai), channel
  @VPNCRACKNET, bot @VPN5G_IRBot, support @Aref_5G (all open with
  ACTION_VIEW), version + code, build date (`BuildConfig.BUILD_TIME`),
  v2rayNG/GPL-3.0 credit.
- `Vpn5gSubscriptionScreen.kt` — shows ONLY what `app_config.json`
  publishes (plan name, remaining days, remaining volume); "no
  subscription" state + bot button otherwise. The days bar is measured
  against a labelled 30-day cycle; volume is text only (no total is
  published). No numbers are invented.
- `Vpn5gAuthScreens.kt` — registration + password-recovery screens
  (email field, send-code button, 6-digit code field, confirm button).
  UI-ONLY in v1: real validation (email format, 6-digit code) but the
  buttons show an honest "به‌زودی — نیاز به سرور دارد (نسخه‌ی بعد)"
  notice — no backend yet (Supabase Auth in v2; see `docs/email-otp.md`).

### CI + docs (repo root)
- `.github/workflows/build-debug.yml` — NEW (does not touch `build.yml`):
  push to master + workflow_dispatch; mirrors `build.yml` (checkout with
  submodules, setup-android cmdline-tools 14742923, NDK 30.0.16248370 +
  same sed, libhevtun cache/build/copy, `AndroidLibXrayLite` tag,
  `libv2ray.aar` download, Temurin 21), then `./gradlew assembleDebug`,
  uploads the universal debug APK as `vpn5g-debug-apk`. No signing
  secrets anywhere.
- `docs/app_config.md` — the `app_config.json` contract: fields, types,
  full example JSON, defaults when 404, how the bot side writes it, and
  the built-in default config sources.
- `docs/email-otp.md` — exact Persian email texts for the registration
  code and the password-recovery code, plus the Supabase Auth note (v2;
  needs Aref's support email, still to be provided).
- `VPN5G_CHANGES.md` — this file.

## Modified files

- `V2rayNG/app/build.gradle.kts` — `applicationId` →
  `com.taha97kh.vpn5g` (namespace/package untouched), `versionCode` 760 →
  1, `versionName` "2.4.0" → "1.0.0", APK names `v2rayNG_…` →
  `VPN5G_…`, added `buildConfigField("String", "BUILD_TIME",
  "\"2026-11-20\"")`. Both flavors kept. No new dependencies.
- `V2rayNG/app/src/main/AndroidManifest.xml` — `Vpn5gMainActivity` is now
  the single LAUNCHER (MAIN/LAUNCHER/LEANBACK); the LAUNCHER filter was
  removed from `MainActivity` (kept exported, QS-tile filter + shortcuts,
  reachable from the home screen as advanced settings). Added
  intent-filters on `Vpn5gMainActivity`: ACTION_VIEW for schemes `vmess`,
  `vless`, `trojan`, `ss`; ACTION_VIEW for `content:`/`file:` URIs with
  `text/plain` + `.*\.txt` and `application/json` + `.*\.json`.
  `android:roundIcon="@mipmap/ic_launcher"` added on `<application>`.
- `V2rayNG/app/src/main/res/values/strings.xml` — `app_name` →
  "VPN 5G"; ~70 new `vpn5g_*` keys (Persian defaults; fa locale falls back
  to these — no `values-fa` duplicates were created, per the brief).
- `V2rayNG/app/src/main/res/values/ic_launcher_background.xml` —
  background `#FFFFFF` → `#0F172A`.
- `V2rayNG/app/src/main/java/com/v2ray/ang/dto/entities/ProfileItem.kt` —
  added `var country: String = ""` (Gson-safe default; set by the sync
  from the live list, "بین‌الملل" for subscription-line entries).
- `V2rayNG/app/src/main/java/com/v2ray/ang/handler/AngConfigManager.kt` —
  `parseConfig` changed from `private` to public (one word + comment) so
  `Vpn5gSync` can parse single URIs; no logic changed.
- `V2rayNG/app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
  and `ic_launcher_round.png` — replaced with the site icon
  (`~/workspace/vpn5g-app/icon-512.png`, shield + "5G" + bolt on dark
  navy), scaled with PIL to 144 / 216 / 288 / 384 / 512 px.
- Deleted: `mipmap-anydpi-v26/ic_launcher.xml`,
  `mipmap-anydpi-v26/ic_launcher_round.xml`, and all
  `mipmap-*/ic_launcher_foreground.png` (old v2rayNG foreground). The
  icon is NOT used as an adaptive-icon foreground: its rounded corners
  are baked in and would double-round. The full-bleed legacy icon is now
  used on all API levels; `android:icon` and `android:roundIcon` both
  point at it.

## Config sources + ping-test cap

Default sources (in order): HF `live_configs.json`, HF `sub.txt`,
GitHub `0xRadikal/Free-v2ray-Configs` (`verified/configs.txt`, ~1,318
lines, verified nodes), GitHub `barry-far/V2ray-Config`
(`All_Configs_Sub.txt`, ~9,120 lines, unverified pool), and since
v1.1.6: GitHub `morpheusadam/v2ray-config` (`subs/bundles/iran.txt`,
censored-network bundle, ~3,000 lines), the same repo's
`subs/bundles/best.txt` (2,000 lines, sources scoring 85+),
GitHub `Alirewa/V2ray-Configs` (`config.txt`, 2,000 lines, refreshed
every 30 min), and GitHub `MatinGhanbari/v2ray-configs`
(`subscriptions/v2ray/super-sub.txt`, 200 best-by-test configs,
base64-wrapped — decoded by `Vpn5gSync.decodeSubscriptionBody`).
Extra sources may come from `app_config.json` (`config_sources`,
deduped).

**Cap:** the GitHub files are large, so each contributes at most
**300 lines per refresh**, taken as an evenly-spaced spread across the
whole file — not just the top — so one refresh samples the entire
source. The HF `live_configs.json` list (41 entries) is always fully
ping-tested. All fetching/parsing runs on Dispatchers.IO.

**Hard rule enforced:** a config whose TCP ping fails (< 0) is never
stored or shown — `Vpn5gSync` filters before `saveServerProfiles`.

## Animation (login/entry + home)

`Vpn5gMotion.kt` + use in `Vpn5gAuthScreens.kt` / `Vpn5gMainActivity.kt`:
- Staggered springy entry: logo, fields and buttons animate in one after
  another via `AnimatedVisibility` + `slideInVertically` (spring,
  `dampingRatio = 0.7f`) + `fadeIn`, ~100 ms stagger per element.
- Animated gradient background: dark navy (`#0f172a` → `#1e293b` with a
  `#2563eb` tint) whose middle/bottom stops slowly shift via
  `infiniteTransition` + `animateColor` (6–8 s cycles, reverse).
- Floating/pulsing logo: infinite scale 1.0 ↔ 1.06 + gentle vertical drift
  on a 2 s cycle.
- Screen transitions: `AnimatedContent` with slide + fade between the
  home/guide/subscription/about/auth destinations.
- Lightweight by design: only Compose `animate*` / `infiniteTransition` /
  `AnimatedVisibility` (render-thread driven), no new dependencies (no
  Lottie), no heavy blur/shadow animation. Animations are purely
  presentational — they never gate or slow config fetching, ping, or
  connecting.

## Notes / caveats

- **Not run (no compiler here):** `:app:compilePlaystoreDebugKotlin`,
  `:app:assemblePlaystoreDebug`, `:app:testPlaystoreDebugUnitTest`,
  `:app:lintPlaystoreDebug`, and any emulator/device check. No Android SDK
  or Gradle is available in this environment, and no network build or
  GitHub push was attempted (no auth here). The code was validated by
  re-reading every new/changed file against the existing APIs it calls,
  plus `python3 -m xml.etree` parsing of the manifest and `strings.xml`
  (both parse) and `json.tool` validation of both JSON examples in
  `docs/app_config.md` (both valid). A real build (`./gradlew
  assembleDebug` locally or the new `build-debug.yml` workflow) is still
  required before release.
- **Locale rule deviation (per brief):** the repo guide asks for every
  locale to be updated per changed key; the brief explicitly ordered the
  new `vpn5g_*` keys only in `values/strings.xml` (Persian defaults, fa
  falls back) with no `values-fa` duplicates. Followed the brief.
- **Icon / maskable:** `icon-maskable-512.png` is byte-identical to
  `icon-512.png` (verified with `cmp`) — it is NOT a true maskable icon
  with safe-zone padding, so maskable support is best-effort in v1. The
  icon's rounded corners are baked in; that is why it is used only as the
  legacy full-bleed icon, not as an adaptive foreground.
- **`ProfileItem.country` + Gson:** new saves always include the field.
  Profiles stored by an older upstream install lack it; Gson allocates
  without the Kotlin constructor, so the field would read null at runtime
  for those legacy rows (only code reading `country` is the VPN 5G group,
  which the sync always rewrites).
- **Subscription status is global in v1:** `app_config.json` publishes
  one status for the whole app, not a per-user lookup. Per-user status
  needs the v2 backend.
- **iOS:** Aref asked for iPhone too (2026-11-20 ~04:09). This deliverable
  is Android v1 only. iOS needs a macOS/Xcode build + an Apple Developer
  account ($99/year) for distribution (or AltStore sideload with limits)
  — not attempted here.
- **Not touched:** `~/workspace/telegram-bot` (bot stays OFF per standing
  order), any keystore/passwords (never read or used), `build.yml`,
  `MainActivity`/`MainScreen` (kept intact as advanced settings),
  `LICENSE`.

## Local build status (2026-11-20, coordinator — NOT compiled to APK)

- **Android SDK installed locally** (`~/android-sdk`): cmdline-tools
  14742923, platform-tools r37.0.3, platform android-37
  (`platform-37.0_r01.zip` — the public repo XML served here is stale
  and lists no android-37 package), build-tools 37.0.0, NDK
  30.0.16248370, JDK 21, Gradle 9.8.2. `libv2ray.aar` v26.11.9
  (60,437,926 bytes, matches the `AndroidLibXrayLite` submodule tag)
  is in `V2rayNG/app/libs/`.
- **HEV native libs BUILT successfully** for all 4 ABIs
  (`V2rayNG/app/libs/<abi>/libhev-socks5-tunnel.so` +
  `libhevsockstun.so`). Required `git submodule update --init
  --recursive` first (the nested `hev-socks5-tunnel/third-part/*`
  submodules were empty).
- **Gradle build BLOCKED by the sandbox, not by the code:** every Java
  process's outbound TCP is intercepted here (`other_tcp` = Deny).
  Evidence: a Java client to a Java server on 127.0.0.1, a Java client
  to a Python server on 127.0.0.1, and a Java client to the egress
  proxy all receive the sandbox message instead of connecting
  ("Other TCP connections is turned off ... switch other_tcp from
  Deny to Ask"); Gradle's daemon client then fails with
  "Unexpected type tag 109" ('m' = first byte of that message) /
  "Connection reset by peer". Python/curl loopback and proxy traffic
  work fine — only Java TCP is intercepted. The Gradle daemon
  protocol needs Java→Java TCP on loopback, so **no Gradle build can
  run in this sandbox as configured**.
- **Unblock paths:** (A, recommended) Aref forks `2dust/v2rayNG`,
  renames the fork to `vpn5g-android`, grants the Muse GitHub App
  access — `.github/workflows/build-debug.yml` then builds the debug
  APK on GitHub Actions (no local sandbox limits; upstream's own
  `build.yml` run 2026-11-18 on this exact commit succeeded).
  (B) The user enables the permission in Muse settings → Permissions
  → Direct network protocols → `other_tcp` Deny → Ask, then the local
  build can be retried here with `~/workspace/vpn5g-app/build-local.sh`.
- **Tree is CI-clean:** the `ndkVersion` line the local build script
  injected into `V2rayNG/app/build.gradle.kts` was reverted — the CI
  workflow injects it itself at build time, and a duplicate would
  fail the build.

## v1.1.8 (2026-11-23, versionCode 10)

Aref turned "Confirm email" OFF in the Supabase panel and asked for
the full auth rewrite: four scenarios, no contradictions, no more
"Too many requests". Raw GoTrue REST (OkHttp) is kept — no new
dependency, no supabase-kt SDK.

- **Scenario 1 — direct signup/login, no code.** POST
  /auth/v1/signup {"email", "password"} (supabase.auth.signUp). With
  "Confirm email" OFF the response carries a session and the user is
  signed in immediately, no code screen. With it ON (later) the
  response has a user but no session: the app falls back to the OTP
  code screen (one sequential OTP send, never parallel with signup).
  An already-registered email is not a dead end: the screen switches
  to password login with a message instead of an error. Interpretation
  recorded: signUp REQUIRES a password — GoTrue has no passwordless
  signup — so the signup screen asks for a password + confirmation.
- **Scenario 2 — OTP login/signup.** Unchanged endpoints (POST
  /auth/v1/otp create_user=true, POST /auth/v1/verify type=email),
  same 6-digit code screen, same 60-second resend cooldown with a
  visible countdown and the 5-wrong-code lockout (Vpn5gOtpPolicy).
- **Scenario 3 — forgot password, link flow.** POST /auth/v1/recover
  {"email", "redirect_to": "vpn5g://auth/callback"}
  (supabase.auth.resetPasswordForEmail) sends the recovery LINK — not
  a code, and the app never falls back to OTP here. The link returns
  to the app through a new intent-filter on Vpn5gMainActivity
  (scheme `vpn5g`, host `auth`; the activity is already singleTask
  and already routes intents through handleDeepLink/onNewIntent), its
  fragment is parsed by the new pure Vpn5gRecoveryLink (type=recovery
  + access/refresh tokens), and the app opens a new-password screen;
  PUT /auth/v1/user {"password": ...} (Authorization: Bearer
  <access_token from the link>, supabase.auth.updateUser) sets the
  new password and signs the user in with the link's tokens.
- **Scenario 4 — password login.** POST
  /auth/v1/token?grant_type=password (supabase.auth.signInWithPassword)
  returns the session directly. A wrong email/password is a friendly
  Persian message, never a crash.
- **"Too many requests" root cause, fixed.** The old screen could
  fire an OTP send with no cooldown guard and no in-flight lock: a
  double tap — or a mode switch followed by another send within 60
  seconds — hit Supabase's one-send-per-60-seconds limit. Every send
  now goes through ONE guarded entry point per mode: a request
  already in flight is ignored, a send inside the 60-second cooldown
  is refused with the rate-limit message instead of being fired, and
  every send button is disabled while its request is in flight. No
  error path leaves the UI stuck loading, and nothing is auto-retried:
  a 429 shows how long to wait.
- **Error handling.** The service never throws; every failure is an
  AuthResult.Error with a stable key, mapped to a Persian string from
  res/values/strings.xml: 429 → "rate_limited", wrong credentials →
  "invalid_credentials", already registered → "email_already_registered",
  short password → "weak_password", bad/expired recovery link →
  "recovery_link_invalid", wrong/expired code →
  "code_wrong_or_expired" (unchanged), network failure →
  "network_error". New keys are JVM-tested in Vpn5gAuthErrorKeyTest;
  the recovery-link parser is JVM-tested in Vpn5gRecoveryLinkTest;
  Vpn5gOtpPolicyTest is unchanged and still green (CI).
- **Dashboard steps the user must still do (app code cannot do
  them).** (1) Authentication → URL Configuration → Redirect URLs:
  add `vpn5g://auth/callback` — without it GoTrue refuses the
  custom redirect and the recovery link opens the browser instead of
  returning to the app. (2) Custom SMTP for VPN5G.IRBOT@gmail.com is
  STILL not set up (blocked on Aref creating the Gmail app password
  himself): until it is, OTP code emails may not arrive at all — the
  code still supports OTP, it never fakes a send. (3) If "Confirm
  email" is ever turned back ON, the Magic Link template must contain
  `{{ .Token }}` for the code flow.
- **Not verified on a device.** `:app:testPlaystoreDebugUnitTest` /
  `:app:compilePlaystoreDebugKotlin` cannot run in this sandbox
  (local Gradle is blocked); CI is the only check. The recovery deep
  link, the four screens, and the signup-no-session fallback need
  Aref's phone to test for real.

## v1.1.9 (2026-11-23, versionCode 11)

Aref ordered: no signup/login CODE inside the app anymore — registration
only with email + password, and one user per IP and email so nobody can
dodge the limit with a different email. OTP is removed entirely.

- **OTP removed.** The code screen, the code buttons, `sendLoginOtp` /
  `verifyOtp`, `Vpn5gOtpPolicy` (+ its test) and all OTP-only strings
  are deleted. No code is sent, asked for, or verified anywhere in the
  app. OTP login was removed together with OTP signup: the code screen
  served both, and codes are exactly the bypass he wants closed.
  Password recovery via the emailed LINK stays — it is not a signup
  code, and it is the only way for users who registered with a code
  earlier to set a password and log in (see below).
- **Registration is email + password only.** Signup is POST
  /auth/v1/signup {"email", "password"}; with "Confirm email" OFF the
  session comes back immediately, no code. If "Confirm email" is ever
  turned back ON, a signup that returns a user without a session now
  reports `email_not_confirmed` ("ایمیلت هنوز تایید نشده؛ لینک تایید
  را از ایمیلت باز کن و بعد با رمز عبورت وارد شو") — the user taps the
  confirmation LINK in the email, then logs in with the password.
- **One account per IP + email (server-enforced).** New file
  `supabase/vpn5g_registrations.sql`: table
  `public.vpn5g_registrations` (id, email, ip, created_at;
  UNIQUE(email), UNIQUE(ip)), RLS enabled, NO direct anon access —
  reachable only through two SECURITY DEFINER functions
  (`check_registration` returns rows 'email'/'ip' for taken values;
  `register_account` inserts and returns true, false on a unique
  violation instead of raising), both `SET search_path = public`,
  `GRANT EXECUTE ... TO anon`. Signup flow in the app: (1) fetch the
  device's public IP directly (no proxy — signup happens before any
  VPN connection), primary https://api.ipify.org?format=json,
  fallback https://api.ip.sb/geoip (the same endpoint
  Vpn5gTunnelCheck uses); if neither answers, signup fails CLOSED
  with a network error and is never attempted. (2) POST
  /rest/v1/rpc/check_registration {p_email, p_ip} — email taken →
  the existing "already registered, log in with your password"
  message; IP taken → new key `ip_already_registered` ("با این
  اینترنت قبلاً یک حساب ساخته شده است؛ هر اینترنت فقط یک حساب").
  An unparseable check response also fails CLOSED. (3) Only then
  signUp. (4) After a successful signup, POST
  /rest/v1/rpc/register_account records the pair; if that record call
  fails, the session is still real and returned (logged only) — the
  table's UNIQUE(ip) is the backstop against two signups racing past
  the check. The guard applies ONLY to registration, never to password
  login or recovery. Pure mapping lives in the new
  `Vpn5gRegistrationGuard` (JVM-tested in
  `Vpn5gRegistrationGuardTest`); IP parsing is JVM-tested in
  `Vpn5gAuthErrorKeyTest` (OTP cases removed).
- **Shared-IP tradeoff, stated honestly.** The rule keys on the
  public IP, so it cannot tell two people apart on the same
  internet: a second person on the same family Wi-Fi — or on the
  same mobile carrier CGNAT address — cannot register while the
  first account exists. That is the intended effect of the rule
  Aref ordered, not a bug: one internet connection, one account.
  (Logging in from a shared IP is unaffected; only registration is
  gated. A user whose IP changes — new mobile data session, another
  Wi-Fi — can register from the new IP.)
- **Earlier OTP users.** Anyone who registered with a code in
  1.1.7/1.1.8 has an account but no password: they set one via
  «فراموشی رمز عبور» (recovery link → new-password screen), then
  log in with email + password. No migration was needed in the app.
- **Dashboard steps the user must still do (app code cannot do
  them).** (1) Run `supabase/vpn5g_registrations.sql` once:
  Supabase dashboard → SQL Editor → New query → paste the whole
  file → Run. Until it is run, EVERY signup fails closed with a
  network error (the RPC functions do not exist yet). (2) The
  redirect URL `vpn5g://auth/callback` (Authentication → URL
  Configuration → Redirect URLs) is still required for the recovery
  link. (3) Custom SMTP for VPN5G.IRBOT@gmail.com is STILL not set
  up (blocked on Aref creating the Gmail app password himself):
  recovery-link emails go through the built-in service until then.
- **Error handling.** The service never throws; the rate-limit
  string no longer mentions codes ("درخواست‌های زیادی فرستاده شده؛
  یک دقیقه صبر کنید و دوباره تلاش کنید"). Every result still sets
  a terminal phase (no stuck loading).
- **Not verified on a device.** `:app:testPlaystoreDebugUnitTest` /
  `:app:compilePlaystoreDebugKotlin` / `:app:assemblePlaystoreDebug`
  cannot run in this sandbox (local Gradle is blocked); CI is the
  only check. The guard flow (IP fetch → check → signup → record),
  the recovery link, and the three screens need Aref's phone to test
  for real. The SQL file has not been run against the live project
  yet — Aref runs it in the dashboard (step 1 above).

## v1.1.10 (2026-11-23, versionCode 12)

Aref asked: the signup IP check must not depend on any third-party
site — only on his own Supabase project ("نمیشه جوری درست کنی وابسته
به هیچ سایتی نباشه جز خودت gmail?"). ipify and api.ip.sb are gone
from the signup path.

- **Migration to a new Supabase project.** The app now points at
  project `atvcpfnnmwguhjvhbbfj` ("vpn5g.irbot@gmail.com's
  Project", eu-west-1) instead of the old `jsqvjpmqfmgfgzcycngs`
  project: the old project is owned by a different account that
  Aref cannot reach, so the registrations SQL Aref ran landed in
  the new project and the old project's `check_registration` never
  existed (live probe: 404 PGRST202). **All users must re-register
  — accounts from the old project do not carry over, including
  Aref's.** The new project already has the `vpn5g_registrations`
  table + `check_registration`/`register_account` RPCs, the
  `get-client-ip` function deployed (JWT ON), email provider ON
  with Confirm email OFF, and redirect `vpn5g://auth/callback`
  listed. The IP check now goes through the project's own Edge
  Function; no ipify/api.ip.sb anywhere in the signup path.
- **Home header brand.** The home screen header now reads "TAHA
  VPN 5G" (new non-translatable string `vpn5g_home_brand`; the
  launcher label, notifications and backup names still use
  `app_name` = "VPN 5G"). Footer "Made by AREF KH" and the
  connect-circle "4ref.ai" are unchanged.
- **IP lookup is Supabase-only now.** New Edge Function
  `supabase/functions/get-client-ip` (Deno, `Deno.serve`): reads the
  caller's public IP from `x-forwarded-for` (first entry),
  `cf-connecting-ip`, then `x-real-ip`, and answers
  `{"ip": "<ip>"}`; 400 `{"error": "no ip"}` when no header yields
  one. JWT verification stays at the default (ON); the app sends the
  anon key as the Bearer token, exactly as for the REST/RPC calls.
  Nothing is logged or stored by the function. The app's
  `fetchPublicIp()` now GETs `/functions/v1/get-client-ip` with the
  anon key and parses the same `{"ip": ...}` body. The ipify primary
  and the api.ip.sb fallback are deleted from the signup path — no
  reference to either remains in the auth code. (Vpn5gTunnelCheck
  still uses api.ip.sb for exit-IP verification; that is a different
  feature, untouched.)
- **Deploy order matters.** The function MUST be deployed before
  users update to 1.1.10: until it is, signup fails closed with a
  network error (the function answers 404). Steps are in
  `supabase/functions/get-client-ip/README-FA.txt`: Supabase
  dashboard → Edge Functions → create/deploy function `get-client-ip`
  → paste `index.ts` → Deploy; verify with the curl command in the
  README (anon key as a placeholder only). The registrations SQL and
  the redirect URL from v1.1.9 are unchanged.
- **Tests.** The ipify/ip.sb parse tests are replaced by Edge
  Function response tests (IPv4, IPv6, missing-ip → null, error
  body → null). Vpn5gRecoveryLinkTest untouched.
- **Live-verified before shipping.** The Edge Function and the
  `check_registration` RPC were probed live against the new
  project with the new anon key before release: the function
  answers 200 `{"ip": ...}` and the RPC answers 200 `[]` for an
  unregistered email/IP. **Not verified on a device.**
  `:app:testPlaystoreDebugUnitTest` /
  `:app:compilePlaystoreDebugKotlin` / `:app:assemblePlaystoreDebug`
  cannot run in this sandbox (local Gradle is blocked); CI is the
  only check. The guard flow (function → check → signup → record)
  needs his phone to test for real.

## v1.1.11 (2026-11-23, versionCode 13)

Aref ordered (2026-11-23 20:55): people who bought a subscription
must use the powerful servers with NO cutoff and NO time limit, and
if the net/tunnel drops anywhere, the app must quickly connect to
another tunnel. Free users keep the 30-minute timer.

- **Per-user subscription (new file `Vpn5gSubscriptionStatus.kt`).**
  The app reads the signed-in user's OWN row from
  `public.vpn5g_registrations` with the user's own access token
  (RLS: `auth.uid() = user_id`). Subscriber = `subscription_status`
  `active` AND `subscription_expires_at` in the future. The
  `user.id` is now captured from the signup/token response
  (`Vpn5gAuthSession.userId`, null for pre-1.1.11 sessions) and
  linked to the row with `set_own_user_id()` after every successful
  sign-in (best effort — a failure never blocks sign-in; rows
  registered before the migration get linked on the next sign-in,
  or by the admin from the dashboard). The result is cached in MMKV
  (`KEY_USER_SUBSCRIPTION_JSON`) and refreshed on app start, on
  every list refresh, and on connect. A failed fetch never clears
  the cache; sign-out does.
- **Subscribers: unlimited, full pool.** No 30-minute disconnect
  work is scheduled, the home countdown shows «بدون محدودیت
  زمانی», and the power-button ring stays full. `freeGuidsFor()`
  marks EVERY real-pinged server as pickable for a subscriber (no
  20-config cap), sorted fastest first — the same `isFree` flag the
  auto picker, manual validation and failover already gate on, so
  the whole pool opens at once. Free users keep the exact current
  behavior (top-20 free, 30-minute timer). The Premium screen shows
  the per-user plan (name looked up from the published plans) and
  remaining days when subscribed, and «اشتراک شما تمام شده است»
  when an active row already expired.
- **Fast in-session failover (watchdog).** While CONNECTED, a light
  single-fetch exit-IP probe (`Vpn5gTunnelCheck.probeOnce`, 5 s
  budget) runs every 20 s; two consecutive FAILED probes drop the
  tunnel and the existing drop detector fails over to the next-best
  healthy server (different country first) with the brief
  "switching server" state — never a disconnect screen. One failed
  probe is forgiven as a blip. The 60-second full re-verification
  still runs alongside as the deeper check. A stop after 29 minutes
  is no longer treated as "the timer ended" for subscribers (they
  have no timer), so long sessions still fail over.
- **Bounded on a dead network.** The drop failover now gives up
  with a clear error («هیچ سرور سالمی در دسترس نیست؛ اینترنت را
  بررسی کنید و دوباره وصل شوید») after 3 no-candidate refresh
  rounds (60 s backoff each) instead of looping forever when the
  network is dead. When candidates exist the chain still never
  gives up (v1.1.7).
- **Signed-in account UI (Aref, 2026-11-23 21:20).** Once the
  user is signed in with email, the «ورود با ایمیل» option no
  longer appears anywhere: the Premium screen's bottom sign-in
  button is replaced by an account block — the user's email, their
  own remaining credit (plan + remaining days for subscribers,
  «اشتراک شما تمام شده است» for an expired row, «اشتراکی ثبت نشده
  است» otherwise) and a «خروج از حساب» button (direct sign-out,
  same as the Me screen — no confirmation, matching the existing
  sign-out). The Me screen's subscription section now also prefers
  the per-user row over the bot-published global status, so a
  signed-in subscriber sees their own remaining days there too.
  Signed out, both screens show the sign-in option exactly as
  before.
- **Live-verified before shipping.** The subscription SQL was run in
  the new project 2026-11-23 21:14 ("Success. No rows returned");
  probed live with the anon key: the table answers 401/42501
  (exists, no anon access — as designed) and `set_own_user_id`
  answers 200 `false` unauthenticated (function exists; no JWT, so
  nothing to link). **Not verified on a device:** the end-to-end
  subscriber path (admin activates a plan for an email → the app
  shows unlimited + full pool on the next sign-in) needs his phone
  and a real activated account. `:app:testPlaystoreDebugUnitTest` /
  `:app:compilePlaystoreDebugKotlin` cannot run in this sandbox
  (local Gradle is blocked); CI is the only check.
- **Tests.** New `Vpn5gSubscriptionStatusTest`: active+future /
  active+expired / cancelled / none / missing expiry, remaining
  days, row parsing (incl. Z and +03:30 ISO forms), subscriber vs
  free pool selection, `user.id` extraction.
