# app_config.json — remote configuration contract (VPN5G IRBOT v1.1)

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

The app shows the card only when `enabled` is true and `text` is
non-blank, and only until the user dismisses it (the dismissal is stored
locally by id, or by text when no id is published). The image is loaded
with Coil; a failed image hides the image slot and the text still shows.
The admin sets/clears the announcement from the Telegram bot (admin
panel → 📢 تبلیغات و اطلاع‌رسانی → 📣 اطلاعیه اپ VPN5G) and publishes
it to this file; the app picks it up on the next refresh (≤ 5 minutes)
with no app update.

### `codes` — discount / gift codes

Created and updated by the ADMIN through the Telegram bot. The app
validates an entered code against this list (case-insensitive, trimmed)
and records the redemption locally; the BOT is the authority — it applies
the benefit centrally and decrements `remaining_uses`.

| Field | Type | Meaning |
|---|---|---|
| `code` | string | Code as the user types it, e.g. `"WELCOME10"`. |
| `type` | string | `"discount"` or `"gift"`. |
| `discount_percent` | integer \| null | Discount percent (1–100) when type is `"discount"`. |
| `gift_days` | integer \| null | Gift duration in days when type is `"gift"`. |
| `max_uses` | integer \| null | Total allowed redemptions, or null for unlimited. |
| `remaining_uses` | integer \| null | Redemptions left, as last published by the bot. Null = unknown/unlimited. `0` means exhausted. |

### `plans` — subscription plans

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Stable id, e.g. `"monthly"`, `"yearly"`. |
| `period` | string | `"monthly"` or `"yearly"` (drives the card layout; yearly gets the "most popular" badge and is the default selection). |
| `name_fa` | string \| null | Display name in Persian, e.g. `"ماهانه"`. Falls back to the period label. |
| `price_toman` | integer \| null | Price in Toman. Null hides the card. |

Plans with a missing id, an unknown period, or no price are ignored by the
app. The yearly plan is the default selection; the user picks a plan, taps
"ادامه", then chooses a payment method.

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
    "id": "ann-1790000000"
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
    { "code": "GIFT7", "type": "gift", "gift_days": 7, "max_uses": 50, "remaining_uses": 50 }
  ],
  "plans": [
    { "id": "monthly", "period": "monthly", "name_fa": "ماهانه", "price_toman": 150000 },
    { "id": "yearly", "period": "yearly", "name_fa": "سالانه", "price_toman": 1200000 }
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
  "trial_hours": 24
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
   array (code, type, discount percent or gift days, max uses,
   remaining uses).
3. **Redeem handler**: a `/start=redeem_<CODE>` deep-link handler that
   validates the code again server-side, applies the discount/gift, and
   decrements `remaining_uses` (the app only validates locally and links
   the user to the bot).
4. **Admin commands for plans**: add / edit / remove entries in the `plans`
   array (id, period, Persian name, price in Toman).
5. **24h free trial on membership**: the bot's membership flow must likewise
   offer 24 hours free (same as the app's trial flag).
6. **Bank gateway credentials**: the user will send the gateway details
   (gateway URL, merchant id, callback) to the bot later; the bot stores
   them and publishes them in `payment_gateway` (with `enabled: true`)
   once configured. Until then the gateway stays coming-soon in the app.
7. **Purchase completion**: the bot applies every purchase, trial, and
   promo-code benefit centrally and updates `subscription_status` /
   `remaining_uses` so the app shows the result on the next refresh.
