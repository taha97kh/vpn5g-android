# VPN 5G v1 — changes (2026-11-20)

Fork of v2rayNG (commit 740f0ba) as **VPN 5G** — native Android VPN app,
package `com.taha97kh.vpn5g`, version 1.0.0 (versionCode 1). Kotlin package
stays `com.v2ray.ang`; namespace stays `com.v2ray.ang`. LICENSE (GPL-3.0)
kept intact; the fork credit is in the About screen
("بر پایه‌ی v2rayNG (GPL-3.0)") and in this file.

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

Default sources (in order): HF `live_configs.json`, HF `sub.txt`, GitHub
primary `0xRadikal/Free-v2ray-Configs` (`verified/configs.txt`, ~1,438
lines, verified nodes), GitHub fallback `barry-far/V2ray-Config`
(`All_Configs_Sub.txt`, ~8,882 lines, unverified pool). Extra sources may
come from `app_config.json` (`config_sources`, deduped).

**Cap:** the two GitHub files are large (344 KB / 2.1 MB), so each
contributes at most **300 lines per refresh**, taken as an evenly-spaced
spread across the whole file — not just the top — so one refresh samples
the entire source. The HF `live_configs.json` list (41 entries) is always
fully ping-tested. All fetching/parsing runs on Dispatchers.IO.

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
