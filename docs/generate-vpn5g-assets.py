#!/usr/bin/env python3
"""Regenerate the VPN 5G bundled data assets.

Outputs (committed under V2rayNG/app/src/main/assets/vpn5g/):
  - ip_country_ranges.txt : merged IPv4 ranges, one line per range:
    "<startInt> <endInt> <CC>" sorted by startInt. Source: ipdeny
    all-zones (per-country CIDR lists, public data).
  - fallback_servers.json : snapshot of the live server list
    [{"uri": ..., "country": ...}] taken from the current live sources
    (live_configs.json fully + an evenly-spaced 300-line spread of each
    GitHub source, the same cap the app uses per refresh).

Run from the repo root:  python3 docs/generate-vpn5g-assets.py
Requires internet. The fallback snapshot is a point-in-time copy: refresh
it whenever the live list changes materially. Regenerating the GeoIP table
is only needed when allocations change (monthly is plenty).
"""
import base64
import json
import os
import tarfile
import urllib.request

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO_ROOT, "V2rayNG", "app", "src", "main", "assets", "vpn5g")
WORK = os.path.join("/tmp", "vpn5g-asset-gen")

IPDENY_URL = "https://www.ipdeny.com/ipblocks/data/countries/all-zones.tar.gz"
LIVE_JSON = "https://taha97kh-vpn5g-app.static.hf.space/live_configs.json"
GITHUB_SOURCE_1 = "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/configs.txt"
GITHUB_SOURCE_2 = "https://raw.githubusercontent.com/barry-far/V2ray-Config/main/All_Configs_Sub.txt"
# v1.1.6: censored-network bundle, best-scored bundle, 30-min refresh,
# and best-by-test (base64-wrapped — sample_lines decodes it).
GITHUB_SOURCE_3 = "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/iran.txt"
GITHUB_SOURCE_4 = "https://raw.githubusercontent.com/morpheusadam/v2ray-config/main/subs/bundles/best.txt"
GITHUB_SOURCE_5 = "https://raw.githubusercontent.com/Alirewa/V2ray-Configs/main/config.txt"
GITHUB_SOURCE_6 = "https://raw.githubusercontent.com/MatinGhanbari/v2ray-configs/main/subscriptions/v2ray/super-sub.txt"
GITHUB_SOURCES = (
    (GITHUB_SOURCE_1, "gh1.txt"),
    (GITHUB_SOURCE_2, "gh2.txt"),
    (GITHUB_SOURCE_3, "gh3.txt"),
    (GITHUB_SOURCE_4, "gh4.txt"),
    (GITHUB_SOURCE_5, "gh5.txt"),
    (GITHUB_SOURCE_6, "gh6.txt"),
)
CONFIG_SCHEMES = ("vmess://", "vless://", "trojan://", "ss://")
PING_CAP = 300


def fetch(url, dest):
    print(f"fetch {url}")
    urllib.request.urlretrieve(url, dest)


def gen_geoip():
    import ipaddress

    os.makedirs(WORK, exist_ok=True)
    archive = os.path.join(WORK, "all-zones.tar.gz")
    fetch(IPDENY_URL, archive)
    ranges = []
    with tarfile.open(archive) as tf:
        for m in tf.getmembers():
            if not m.name.endswith(".zone"):
                continue
            cc = os.path.basename(m.name)[:-5].upper()
            if len(cc) != 2 or not cc.isalpha():
                continue
            f = tf.extractfile(m)
            if f is None:
                continue
            for line in f.read().decode("utf-8", "ignore").splitlines():
                line = line.strip()
                if not line or "/" not in line:
                    continue
                try:
                    net = ipaddress.ip_network(line, strict=False)
                except ValueError:
                    continue
                if net.version != 4:
                    continue
                ranges.append((int(net.network_address), int(net.broadcast_address), cc))
    print(f"raw ranges: {len(ranges)}")
    ranges.sort()
    merged = []
    for s, e, cc in ranges:
        if merged and merged[-1][2] == cc and s <= merged[-1][1] + 1:
            merged[-1] = (merged[-1][0], max(merged[-1][1], e), cc)
        else:
            merged.append((s, e, cc))
    print(f"merged ranges: {len(merged)}")
    out = os.path.join(OUT_DIR, "ip_country_ranges.txt")
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(out, "w") as fh:
        for s, e, cc in merged:
            fh.write(f"{s} {e} {cc}\n")
    print(f"wrote {out} ({os.path.getsize(out)} bytes)")


def sample_lines(path, cap):
    with open(path, encoding="utf-8", errors="ignore") as fh:
        text = fh.read()
    lines = [t for t in (line.strip() for line in text.splitlines())
             if t and t.startswith(CONFIG_SCHEMES)]
    if not lines:
        # Base64-wrapped subscription (MatinGhanbari super-sub): decode
        # and retry, mirroring Vpn5gSync.decodeSubscriptionBody.
        try:
            compact = "".join(text.split())
            decoded = base64.b64decode(compact).decode("utf-8", "ignore")
            lines = [t for t in (line.strip() for line in decoded.splitlines())
                     if t and t.startswith(CONFIG_SCHEMES)]
        except Exception:
            lines = []
    if len(lines) <= cap:
        return lines
    return [lines[(i * len(lines) // cap)] for i in range(cap)]


def gen_fallback():
    os.makedirs(WORK, exist_ok=True)
    entries = {}
    live_path = os.path.join(WORK, "live_configs.json")
    fetch(LIVE_JSON, live_path)
    with open(live_path, encoding="utf-8") as fh:
        for e in json.load(fh):
            uri = (e.get("uri") or "").strip()
            if uri:
                entries[uri] = (e.get("country") or "").strip() or "بین‌الملل"
    for url, name in GITHUB_SOURCES:
        path = os.path.join(WORK, name)
        fetch(url, path)
        for uri in sample_lines(path, PING_CAP):
            entries.setdefault(uri, "بین‌الملل")
    print(f"snapshot entries: {len(entries)}")
    out = os.path.join(OUT_DIR, "fallback_servers.json")
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(
            [{"uri": u, "country": c} for u, c in entries.items()],
            fh, ensure_ascii=False, separators=(",", ":"),
        )
    print(f"wrote {out} ({os.path.getsize(out)} bytes)")


if __name__ == "__main__":
    gen_geoip()
    gen_fallback()
    print("done")
