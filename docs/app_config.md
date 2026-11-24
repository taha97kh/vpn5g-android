# app_config.json — remote configuration contract (VPN5G IRBOT v1.1.15)

The app fetches this file on every sync (5-minute home-screen refresh and
every app launch) from:

```
https://taha97kh-vpn5g-app.static.hf.space/app_config.json
```

The file **does not exist on the server yet**. Until the bot side publishes
it, the app behaves as if the file returned 404: it uses the cached copy when
one exists, otherwise the built-in defaults below. A missing or malformed file
never breaks the app — sync continues with the defaults.

**Everything commercial is managed through the bot.** Subscription plans,
prices, discount/gift codes, the bank gateway config, and the 24h free-trial
flag are ALL published here by the bot side; the admin manages them from the
bot's admin panel. The app never hardcodes a plan, price, code, or gateway
value — when the bot publishes nothing for one of these, the app shows no
cards for it (plans), marks the gateway as coming soon, or rejects the code.

## How the bot side writes it

The bot (admin side) publishes this JSON to the Hugging Face static Space
next to `live_configs.json` / `sub.txt`, e.g. by uploading the file or by a
small admin command that writes it. Any change takes effect on the next app
refresh (≤ 5 minutes) with no app update. Keep the file small (< 10 KB) and
valid JSON; the app ignores unknown fields.

## Fields

| Field | Type | Required | Default when absent | Meaning |
|---|---|---|---|---|
| `free_countries` | array of strings | no | — | IGNORED since v1.1.1 (kept only so old files still parse). The free tier is now the **50 fastest-pinging configs** after each sync/ping, whichever countries they are in; everything else is premium-locked. |
| `notice` | string \| null | no | `null` | Short announcement shown at the top of the home screen (e.g. maintenance). `null` or absent hides it. |
| `announcement` | object \| null | no | `null` | Admin-published app announcement (v1.1.5): a dismissible card on the home screen. `null` or absent hides it. See below. |
| `config_sources` | array of strings (URLs) | no | `[]` | Extra config sources fetched in addition to the built-in defaults. Each URL must return plain-text config URIs, one per line (`vmess://`, `vless://`, `trojan://`, `ss://`). Duplicates are de-duplicated; a failing source is skipped. |
| `min_version` | string \| null | no | `null` | Reserved for v2 (minimum app version that may use the configs). v1 reads it but does not enforce it. |
| `subscription_status` | object \| null | no | `null` | The subscription status shown on the Subscription screen. `null` shows "no subscription". See below. |
| `codes` | array of objects \| null | no | `null` | Admin-managed discount/gift codes. `null` or absent means no codes — every entered code is rejected as unknown. See below. |
| `plans` | array of objects \| null | no | `null` | Admin-managed subscription plans (monthly/yearly) with prices in Toman. `null` or absent means no plan cards — the Premium screen shows only the comparison table and the bot purchase button. See below. |
| `payment_gateway` | object \| null | no | `null` | Direct bank payment gateway settings. `null` or absent means the gateway row is shown as coming soon and routes to the bot. See below. |
| `trial_enabled` | boolean \| null | no | `false` | Whether the 24h free trial is offered on the plans. |
| `trial_hours` | integer \| null | no | `24` | Free-trial length in hours (24 per the product rule). |
| `ads` | object \| null | no | `null` | **(v1.1.15)** Interstitial + user-submitted ads: admin interstitial, user ads with schedule/caps, and per-model prices. `null` or absent means NO ad is ever shown. See below. |

### `subscription_status`

| Field | Type | Meaning |
|---|---|---|
| `plan_name` | string \| null | Display name of the plan, e.g. `"ماهانه"`. |
| `remaining_days` | integer \| null | Whole days left on the subscription. |
| `remaining_volume_gb` | number \| null | Remaining traffic in gigabytes. |

The app displays exactly these values and never invents numbers: if a field
is absent, its row is hidden. (Note: in v1 this is a single global status
published by the bot side, not a per-user lookup — per-user status needs the
v2 backend.)

### `announcement`

| Field | Type | Meaning |
|---|---|---|
| `enabled` | boolean \| null | True when the announcement is live. |
| `text` | string \| null | Announcement text (Persian). Blank hides the card. |
| `image_url` | string \| null | Optional public image URL; null/blank = text only. |
| `id` | string \| null | Stable id for one announcement version; the bot regenerates it whenever the text or image changes, so an edited announcement is shown again even to users who dismissed the previous one. |
| `link_url` | string \| null | **(v1.1.15)** Optional link: tapping the announcement text or image opens this URL in the browser. Null/blank = no link (the card is not tappable). |

The app shows the card only when `enabled` is true and `text` is
non-blank, and only until the user dismisses it (the dismissal is stored
locally by id, or by text when no id is published). The image is loaded
with Coil; a failed image hides the image slot and the text still shows.
When `link_url` is set, the text + image area is one tappable node that
opens the link; the dismiss button stays a separate action.
The admin sets/clears the announcement from the Telegram bot (admin
panel → 📢 تبلیغات و اطلاع‌رسانی → 📣 اطلاعیه اپ VPN5G) and publishes
it to this file; the app picks it up on the next refresh (≤ 5 minutes)
with no app update.

### `codes` — discount / gift codes

Created and updated by the ADMIN through the Telegram bot.
**(v1.1.15) The app validates an entered code SERVER-side first** (RPC
`validate_and_redeem_code` — see "Server-side RPCs" below): the server
checks existence / already-used / expiry / remaining_uses and marks the
code used atomically. The list below is ONLY the fallback when the RPC
is unreachable — the app then validates against it (case-insensitive,
trimmed) and records the redemption locally. The BOT is the authority —
it applies the benefit centrally and decrements `remaining_uses`.

| Field | Type | Meaning |
|---|---|---|
| `code` | string | Code as the user types it, e.g. `"WELCOME10"`. |
| `type` | string | `"discount"` or `"gift"`. |
| `discount_percent` | integer \| null | Discount percent (1–100) when type is `"discount"`. |
| `gift_days` | integer \| null | Gift duration in days when type is `"gift"`. **Legacy** — prefer `gift_hours` (below); when both are set, `gift_hours` wins. |
| `gift_hours` | integer \| null | **(v1.1.14)** Gift duration in HOURS when type is `"gift"`. The admin duration choices: `1`, `5`, `24`, or `168` (7 days). Wins over `gift_days`. |
| `tier` | string \| null | **(v1.1.14)** Which tier a gift grants: `"gift"` (the middle tier — top 50 servers, no 30-minute cutoff while the gift lasts) or a plan id such as `"sub_12"` (a FULL subscription for that plan's duration). Default: `"gift"` when no plan id is given. |
| `max_uses` | integer \| null | Total allowed redemptions, or null for unlimited. **(v1.1.14)** The admin people-limit (e.g. `5`): how many people may redeem this code in total. |
| `remaining_uses` | integer \| null | Redemptions left, as last published by the bot. Null = unknown/unlimited. `0` means exhausted. **(v1.1.14)** The bot must decrement this on every publish (it checks the `vpn5g_code_redemptions` table) and set `0` once the limit is reached. May be > 1 for multi-use gift codes. |

### `plans` — subscription plans

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Stable id. **(v1.1.14)** The bot's plan ids are `sub_1` (30 days), `sub_3` (90 days), `sub_6` (180 days), `sub_12` (360 days); legacy `monthly`/`yearly` still work. |
| `days` | integer \| null | **(v1.1.14)** Duration in days (30 / 90 / 180 / 360). The app accepts a plan when `days` is set OR the id is a known `sub_*` id OR the period is monthly/yearly. Explicit `days` wins. |
| `period` | string | `"monthly"` or `"yearly"` (legacy; drives the card layout for old plans). |
| `name_fa` | string \| null | Display name in Persian, e.g. `"ماهانه"`. Falls back to the plan id. |
| `price_toman` | integer \| null | Price in Toman. Null hides the card. |

Plans with a missing id, no known duration, or no price are ignored by the
app. The app renders the plans from this config on every app start and on
every manual refresh (see "Bot → app sync" below) — **no app update is needed
for a plan or price change**. The longest plan is the default selection.

### `payment_gateway` — direct bank payment gateway

| Field | Type | Meaning |
|---|---|---|
| `enabled` | boolean \| null | True when the gateway is configured and usable. |
| `url` | string \| null | Gateway checkout URL the app opens in the browser. |
| `merchant_id` | string \| null | Merchant id (informational; the app never sends it anywhere). |
| `callback_url` | string \| null | Callback URL the gateway returns to (informational; the bot owns the flow). |
| `name_fa` | string \| null | Display name, e.g. `"درگاه پرداخت مستقیم بانکی"`. |

The gateway itself is NOT configured yet — the user will provide the
gateway details later via the Telegram bot. While `enabled` is not true or
`url` is blank, the app shows the gateway row as "به‌زودی" and routes the
user to the bot to complete the purchase manually. The app never hardcodes
gateway credentials or test values and never constructs payment requests:
when configured it opens the published checkout URL in the browser and the
bot owns the rest of the flow.

### Free trial (24 hours)

`trial_enabled: true` + `trial_hours: 24` mark the plans as including a
24-hour free trial (shown on every plan card). The app only DISPLAYS the
trial — granting it is bot-side (the bot must offer 24h free on membership,
the same as in the app). The app cannot mint a subscription by itself.

### `ads` — interstitial + user-submitted ads (v1.1.15)

Everything commercial comes from here — the app never invents ad content
or prices. When `ads` is absent or `null`, NO ad is ever shown. The admin
sets everything in the bot panel; the app reads it on every app start and
on every Premium entry (the config is re-fetched there).

**Forced-ad rule (exact):** the FIRST connection in an app session has NO
forced ad. After the user disconnects and connects for the SECOND time,
the full-screen interstitial shows BEFORE the VPN starts, and the VPN
starts ONLY after the ad is fully viewed (countdown completes). Closing
the ad early is an INCOMPLETE view and the connection does NOT start. The
countdown runs on the monotonic clock (`elapsedRealtime`), so changing the
phone's time cannot shorten it. Subscribers skip the forced ad unless
`applies_to_free_only` is `false`.

| Field | Type | Meaning |
|---|---|---|
| `interstitial.enabled` | boolean \| null | True when the admin interstitial is live. |
| `interstitial.duration_seconds` | integer \| null | Countdown in seconds, **1–20**. The app clamps any value into 1..20; absent = 10. Set by the admin in the bot panel. |
| `interstitial.show_from_connect_number` | integer \| null | First connect number that may show a forced ad. Default `2` (connect #1 never shows one). The admin tunes the frequency with this flag. |
| `interstitial.applies_to_free_only` | boolean \| null | Default `true` = free tier only (subscribers and gift tier skip the ad). `false` = the ad applies to everyone. |
| `interstitial.text` | string \| null | Ad text (Persian). |
| `interstitial.image_url` | string \| null | Optional public image URL; null/blank = text only. |
| `interstitial.link_url` | string \| null | Optional link: tapping the ad text or image opens this URL (the tap never ends the countdown). |
| `interstitial.id` | string \| null | Stable id for this ad (used for the impression ledger and the server-side view record). Absent = `"admin-interstitial"`. |
| `user_ads` | array of objects \| null | User-submitted ads (after admin approval). Each entry is shown per its schedule and caps — never more impressions than purchased. See below. |
| `pricing` | object \| null | Per-model prices (Toman), set by the admin in the bot panel. The app only DISPLAYS them; payment is bot-side. |

**`user_ads[]` entry:**

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Stable id (used for the impression ledger and the server-side view/click records). |
| `text` | string \| null | Ad text (Persian). |
| `image_url` | string \| null | Optional public image URL. |
| `link_url` | string \| null | Optional link (tap opens it; the tap never ends the countdown). |
| `model` | string | Display model: `"once"` (1× display), `"ten_times"` (up to 10× at different hours), `"hourly"` (hourly in set windows), `"monthly_daily"` (monthly, 5×/day). The app enforces the model through the caps + windows below; the model name itself is informational. |
| `max_impressions` | integer \| null | Total impressions purchased (the cap). Null = unlimited. The app never shows more than this. |
| `impressions_per_day` | integer \| null | Impressions per day purchased (the per-day cap). Null = unlimited. `5` for the monthly model. |
| `hour_windows` | array of `[start, end]` pairs \| null | Local hours the ad may show, e.g. `[[9, 12], [18, 21]]`. Each window is `[start, end)` — start inclusive, end exclusive — so `[9, 12]` means 09:00–11:59. Absent/empty = any hour. |
| `price_toman` | integer \| null | Final price the user paid (Toman), for the order history. |
| `enabled` | boolean \| null | True when the ad is live. |
| `approved` | boolean \| null | True only after admin approval. Unapproved ads are never shown. |

**Impression accounting:** the app keeps a local ledger (ad id → total +
per-day counts, persisted) so the caps hold even offline, and records
every completed view (`record_ad_view`) and click (`record_ad_click`)
server-side. A due user ad is picked before the admin interstitial. User
ads use the same admin-set interstitial duration (1–20 s).

### Buy deep link + unique payment amounts (v1.1.15, G)

The buy-subscription flow passes the selected plan to the bot via deep
link so the bot can generate the UNIQUE payable amount for that order:

```
https://t.me/VPN5G_IRBot?start=buy_<plan_id>
```

Plan ids: `sub_1` / `sub_3` / `sub_6` / `sub_12` (see `plans`). The
payable amount comes FROM THE BOT, unique per order (non-round, e.g. base
price + unique suffix), and the app must never show a round plan price as
the amount to pay in a payment context — the user pays the bot-provided
unique amount. The app does NOT generate unique amounts (uniqueness must
be coordinated by the bot to avoid collisions). Bot side: generate the
unique amount per pending order, watch Pasargad 6276 deposits, auto-match
by unique amount, auto-activate without admin (see "What the bot side
still needs", item 11).

### Server-side RPCs (v1.1.15)

The app calls these PostgREST RPCs on the NEW Supabase project
(`atvcpfnnmwguhjvhbbfj`); the bot side implements them. Every call is
best-effort from the app: when an RPC is missing or unreachable, the app
falls back to its local behavior (config list / local countdown) and never
hangs or crashes. The app sends only the code / ad id / email — no secrets.

| RPC | Request body | Response | App behavior |
|---|---|---|---|
| `validate_and_redeem_code` | `{"p_code": "<CODE>", "p_email": "<email>"}` | `{"ok": true, "plan": "<plan_id>" \| null, "expires_at": "<ISO-8601>" \| null}` or `{"ok": false, "reason": "not_found" \| "already_used" \| "expired" \| "exhausted" \| "not_yours"}` — the app ALSO accepts the older `{"status": "ok" \| "unknown" \| "already_used" \| "expired" \| "exhausted" \| "not_yours", ...}` shape. (v1.1.17: the real server returns the `{"ok":...}` shape — the 1.1.15/1.1.16 app only parsed `{"status":...}`, so every real answer fell through to the fallback while the server had already marked the code used.) | The server checks existence / already-used / expiry / remaining_uses and marks the code used ATOMICALLY. `ok` + a usable grant activates the gift immediately and unlocks the countries; `ok` without a grant (discount code) shows VALID + the bot deep link. A rejection maps to the exact error message (unknown / already used / expired / exhausted / not yours). `already_used` for this email re-derives the grant from the published entry instead of saying invalid (v1.1.17 recovery). Anything else = `Unavailable` → config-list fallback. |
| `record_ad_view` | `{"p_ad_id": "<id>", "p_email": "<email>"}` | `true` or `{"verified": true}` | Called on countdown completion, to record/verify the view server-side. If the server is unreachable, the local countdown completion still stands — the VPN starts anyway. |
| `record_ad_click` | `{"p_ad_id": "<id>", "p_email": "<email>"}` | `true` or `{"verified": true}` | Called when the user taps ad content (best-effort). The tap never ends the countdown. |
| `get_subscription_status` | `{"p_email": "<email>"}` | `{"status": "active" \| "none" \| "inactive" \| "cancelled", "plan": "<plan_id>" \| null, "expires_at": "<ISO-8601>" \| null}` | The server is the authority for the subscription: the app re-checks it before connecting when the cached tier is premium (bounded to 3 s) and merges it with the cached row on every refresh. A failed/timed-out check keeps the cached tier. |

Bot-side tables: `vpn5g_ad_events` (ad_id, email, event `view`/`click`,
created_at) for the ad records; `vpn5g_code_redemptions` (see item 8)
for code redemptions. The RPCs are `SECURITY DEFINER` with `GRANT
EXECUTE` to anon, like `record_code_redemption`.

## Built-in default config sources

When `config_sources` is absent, the app fetches these eight sources:

| Source | URL | Notes |
|---|---|---|
| HF live list | `https://taha97kh-vpn5g-app.static.hf.space/live_configs.json` | 41 curated entries; always fully ping-tested. |
| HF subscription | `https://taha97kh-vpn5g-app.static.hf.space/sub.txt` | Base64-wrapped URI lines (decoded by the app since v1.1.6). |
| GitHub (primary) | `https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt` | Verified nodes, ~1,318 lines; auto-updates every 15 min. |
| GitHub (fallback) | `https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt` | Large unverified pool, ~9,120 lines. |
| GitHub (censored) | `https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/iran.txt` | Censored-network bundle (REALITY / XTLS-Vision first), ~3,000 lines. Added v1.1.6. |
| GitHub (best) | `https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/best.txt` | Only sources scoring 85+, every proxy proved with a real TLS tunnel; 2,000 lines. Added v1.1.6. |
| GitHub (30-min) | `https://raw.githubusercontent.com/Alirewa/V2ray-Configs/main/config.txt` | 2,000 configs, refreshed every 30 min. Added v1.1.6. |
| GitHub (best-tested) | `https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/main/subscriptions/v2ray/super-sub.txt` | Best configs by test results, 200 entries, base64-wrapped. Added v1.1.6. |

Ping-test cap: the GitHub files are large, so each contributes at most
**300 lines per refresh**, taken as an evenly-spaced spread across the
file (not just the top). The HF live list is always fully tested. Dead
configs (TCP connect fails) are dropped and never stored.

## Full example

```json
{
  "notice": "سرورهای جدید اضافه شد — لیست را به‌روزرسانی کنید",
  "announcement": {
    "enabled": true,
    "text": "اشتراک ویژه با ۲۰٪ تخفیف — فقط تا آخر هفته",
    "image_url": null,
    "link_url": "https://t.me/VPN5G_IRBot?start=buy_sub_1",
    "id": "ann-1790000000"
  },
  "ads": {
    "interstitial": {
      "enabled": true,
      "duration_seconds": 10,
      "show_from_connect_number": 2,
      "applies_to_free_only": true,
      "text": "اشتراک ویژه VPN 5G — همه کشورها، بدون محدودیت",
      "image_url": null,
      "link_url": "https://t.me/VPN5G_IRBot?start=buy_sub_1",
      "id": "ad-welcome"
    },
    "user_ads": [
      { "id": "ad-user-1", "text": "تبلیغ کاربر", "image_url": null, "link_url": null, "model": "ten_times", "max_impressions": 10, "impressions_per_day": null, "hour_windows": [[9, 12], [18, 21]], "price_toman": 50000, "enabled": true, "approved": true }
    ],
    "pricing": { "once_toman": 20000, "ten_times_toman": 150000, "hourly_toman": 100000, "monthly_daily_toman": 900000 }
  },
  "config_sources": [
    "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt",
    "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt"
  ],
  "min_version": "1.0.0",
  "subscription_status": {
    "plan_name": "ماهانه",
    "remaining_days": 18,
    "remaining_volume_gb": 42.5
  },
  "codes": [
    { "code": "WELCOME10", "type": "discount", "discount_percent": 10, "max_uses": 100, "remaining_uses": 87 },
    { "code": "GIFT5H", "type": "gift", "gift_hours": 5, "tier": "gift", "max_uses": 5, "remaining_uses": 5 },
    { "code": "GIFT12M", "type": "gift", "gift_days": 360, "tier": "sub_12", "max_uses": 1, "remaining_uses": 1 }
  ],
  "plans": [
    { "id": "sub_1", "days": 30, "name_fa": "یک ماهه", "price_toman": 150000 },
    { "id": "sub_3", "days": 90, "name_fa": "سه ماهه", "price_toman": 390000 },
    { "id": "sub_6", "days": 180, "name_fa": "شش ماهه", "price_toman": 690000 },
    { "id": "sub_12", "days": 360, "name_fa": "یک ساله", "price_toman": 1200000 }
  ],
  "payment_gateway": {
    "enabled": false,
    "url": null,
    "merchant_id": null,
    "callback_url": null,
    "name_fa": "درگاه پرداخت مستقیم بانکی"
  },
  "trial_enabled": true,
  "trial_hours": 24
}
```

(The prices above are EXAMPLES only — the admin sets the real prices in
the bot; the app never ships with prices.)

## Defaults when the file is missing (404)

```json
{
  "notice": null,
  "announcement": null,
  "config_sources": [],
  "min_version": null,
  "subscription_status": null,
  "codes": null,
  "plans": null,
  "payment_gateway": null,
  "trial_enabled": false,
  "trial_hours": 24,
  "ads": null
}
```

plus the four built-in config sources above.

## What the bot side still needs (admin / other AI)

The app side is done; the bot side must implement the following before the
commerce works end to end:

1. **Publish app_config.json** to the Hugging Face static Space (next to
   `live_configs.json` / `sub.txt`) with the fields above. The
   announcement publisher already exists (v1.1.5): the bot admin action
   merges `announcement` into the published file (see
   `services/app_config_publisher.py`); it needs an HF write token in
   `HF_TOKEN`. The codes / plans / gateway / trial fields are still
   unpublished — the admin actions for them are items 2–6 below.
2. **Admin commands for codes**: add / edit / remove entries in the `codes`
   array (code, type, discount percent or gift hours/days, `tier`, max uses,
   remaining uses). **(v1.1.14)** Gift codes now carry `gift_hours` (1 / 5 /
   24 / 168), `tier` (`"gift"` or a `sub_*` plan id) and a multi-use
   `max_uses` / `remaining_uses`; the bot must decrement `remaining_uses`
   on publish (it checks the `vpn5g_code_redemptions` table — see item 8)
   and set it to `0` once the people-limit is reached.
3. **(v1.1.15) Server-side code validation**: implement the
   `validate_and_redeem_code(p_code, p_email)` RPC (see "Server-side
   RPCs"): it checks existence / already-used / expiry /
   remaining_uses and marks the code used ATOMICALLY, returning
   `{"ok": true, "plan"?, "expires_at"?}` or `{"ok": false,
   "reason": "not_found" | "already_used" | "expired" |
   "exhausted" | "not_yours"}` (the older `{"status": ...}` shape
   is also accepted). The app calls it BEFORE the config-list
   check; until it exists, every call returns `Unavailable` and the
   app falls back to the list. Keep the `/start=redeem_<CODE>`
   deep-link handler too: it validates the code again server-side,
   applies the discount/gift, and decrements `remaining_uses`.
4. **Admin commands for plans**: add / edit / remove entries in the `plans`
   array (id, **days**, Persian name, price in Toman). **(v1.1.14)** Plans
   are now the bot's `sub_1` / `sub_3` / `sub_6` / `sub_12` ids with
   `days` 30 / 90 / 180 / 360 — the app renders whatever the bot
   publishes, on every app start, with no app update.
5. **24h free trial on membership**: the bot's membership flow must likewise
   offer 24 hours free (same as the app's trial flag).
6. **Bank gateway credentials**: the user will send the gateway details
   (gateway URL, merchant id, callback) to the bot later; the bot stores
   them and publishes them in `payment_gateway` (with `enabled: true`)
   once configured. Until then the gateway stays coming-soon in the app.
7. **Purchase completion**: the bot applies every purchase, trial, and
   promo-code benefit centrally and updates `subscription_status` /
   `remaining_uses` so the app shows the result on the next refresh.
8. **(v1.1.14) Redemption records**: create table
   `vpn5g_code_redemptions` (code PK, email, redeemed_at default now) +
   SECURITY DEFINER RPC `record_code_redemption(p_code, p_email)`
   returning true only when newly inserted + GRANT EXECUTE to anon. The
   app calls this RPC best-effort right after every successful redeem
   (gift activation and discount validation); it never fails a redeem.
   **(v1.1.14) Bot → app sync**: the app re-fetches `app_config.json` on
   every app start, every 5 minutes while open, and on manual refresh —
   any change the admin publishes (plans, prices, announcements, codes)
   reaches the app without an app update. No bot change is needed for
   the fetch itself; the publisher must simply republish the file.
9. **(v1.1.14) Support deep link**: add a `start=support` handler to the
   bot — the app's "پشتیبانی آنلاین" button now opens
   `https://t.me/VPN5G_IRBot?start=support` (the bot handles membership
   and routes the user to its support section).
10. **(v1.1.15) Ad system — bot panel**: publish the `ads` object (admin
    interstitial + user ads + pricing) in `app_config.json`; admin
    controls the interstitial duration (1–20 s), the
    `show_from_connect_number` frequency flag, `applies_to_free_only`,
    content, link, and the per-model prices (`pricing`:
    `once_toman` / `ten_times_toman` / `hourly_toman` /
    `monthly_daily_toman`). User-submitted ads: the bot user panel
    lets a user submit text/image/link + display type + count, shows
    the final price preview, takes payment, and tracks order
    status/history; the admin approves/rejects each request, and only
    approved ads are published (with their `model`, `max_impressions`,
    `impressions_per_day`, `hour_windows`, `price_toman`). Never
    publish more impressions than purchased.
11. **(v1.1.15) Unique payment amounts — auto-confirmation**: the buy
    deep link now carries the plan
    (`https://t.me/VPN5G_IRBot?start=buy_<plan_id>`, plan ids
    `sub_1` / `sub_3` / `sub_6` / `sub_12`). For every pending order
    the bot generates a UNIQUE payable amount (non-round, e.g. base
    price + unique suffix), watches Pasargad 6276 deposits, auto-matches
    by the unique amount, and auto-activates the subscription without
    admin — so a payment confirms even when the admin is unavailable.
12. **(v1.1.15) Ad view/click RPCs**: implement `record_ad_view(p_ad_id,
    p_email)` and `record_ad_click(p_ad_id, p_email)` (returning `true`
    or `{"verified": true}`) + table `vpn5g_ad_events` (ad_id, email,
    event `view`/`click`, created_at). The app calls them best-effort;
    a missing RPC never blocks the connect.
13. **(v1.1.15) Subscription-status RPC**: implement
    `get_subscription_status(p_email)` returning `{"status": "active" |
    "none" | ..., "plan"?, "expires_at"?}`. The app re-checks it before
    connecting when the cached tier is premium (3 s bound) and merges it
    with the cached row on every refresh — it is the server-side
    authority for VIP access (the local cache is only a display hint).
