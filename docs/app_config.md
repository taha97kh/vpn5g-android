# app_config.json — remote configuration contract (VPN 5G v1)

The app fetches this file on every sync (5-minute home-screen refresh and
every app launch) from:

```
https://taha97kh-vpn5g-app.static.hf.space/app_config.json
```

The file **does not exist on the server yet**. Until the bot side publishes
it, the app behaves as if the file returned 404: it uses the cached copy when
one exists, otherwise the built-in defaults below. A missing or malformed file
never breaks the app — sync continues with the defaults.

## How the bot side writes it

The bot (admin side) publishes this JSON to the Hugging Face static Space
next to `live_configs.json` / `sub.txt`, e.g. by uploading the file or by a
small admin command that writes it. Any change takes effect on the next app
refresh (≤ 5 minutes) with no app update. Keep the file small (< 10 KB) and
valid JSON; the app ignores unknown fields.

## Fields

| Field | Type | Required | Default when absent | Meaning |
|---|---|---|---|---|
| `free_countries` | array of strings | no | `["بین‌الملل", "فرانسه", "آلمان"]` | Countries whose servers are free. Names are matched after stripping the flag-emoji prefix, so `"فرانسه"` matches the live list label `"🇫🇷 فرانسه"`. Every other country in the live list is shown locked. |
| `notice` | string \| null | no | `null` | Short announcement shown at the top of the home screen (e.g. maintenance). `null` or absent hides it. |
| `config_sources` | array of strings (URLs) | no | `[]` | Extra config sources fetched in addition to the built-in defaults. Each URL must return plain-text config URIs, one per line (`vmess://`, `vless://`, `trojan://`, `ss://`). Duplicates are de-duplicated; a failing source is skipped. |
| `min_version` | string \| null | no | `null` | Reserved for v2 (minimum app version that may use the configs). v1 reads it but does not enforce it. |
| `subscription_status` | object \| null | no | `null` | The subscription status shown on the Subscription screen. `null` shows "no subscription". See below. |

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

## Built-in default config sources

When `config_sources` is absent, the app fetches these four sources:

| Source | URL | Notes |
|---|---|---|
| HF live list | `https://taha97kh-vpn5g-app.static.hf.space/live_configs.json` | 41 curated entries; always fully ping-tested. |
| HF subscription | `https://taha97kh-vpn5g-app.static.hf.space/sub.txt` | Plain-text URI lines. |
| GitHub (primary) | `https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt` | Verified nodes, ~1,438 lines; auto-updates every 15 min. |
| GitHub (fallback) | `https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt` | Large unverified pool, ~8,882 lines. |

Ping-test cap: the two GitHub files are large (344 KB / 2.1 MB), so each
contributes at most **300 lines per refresh**, taken as an evenly-spaced
spread across the file (not just the top). The HF live list is always fully
tested. Dead configs (TCP connect fails) are dropped and never stored.

## Full example

```json
{
  "free_countries": ["بین‌الملل", "فرانسه", "آلمان"],
  "notice": "سرورهای جدید اضافه شد — لیست را به‌روزرسانی کنید",
  "config_sources": [
    "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt",
    "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt"
  ],
  "min_version": "1.0.0",
  "subscription_status": {
    "plan_name": "ماهانه",
    "remaining_days": 18,
    "remaining_volume_gb": 42.5
  }
}
```

## Defaults when the file is missing (404)

```json
{
  "free_countries": ["بین‌الملل", "فرانسه", "آلمان"],
  "notice": null,
  "config_sources": [],
  "min_version": null,
  "subscription_status": null
}
```

plus the four built-in config sources above.
