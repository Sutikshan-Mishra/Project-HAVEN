#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Copies built packs into the APK as bundled starter maps.

    python3 tools/mappack/bundle_starter.py in-overview in-up-lko-unnao-knp

Files are copied to app/src/main/assets/maps/ with region-prefixed names, and
assets/maps/bundled.json is written in the app's InstalledIndex format. Keep the total small;
the README suggests a budget of about 25 MB.
"""
import hashlib
import json
import pathlib
import shutil
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "maps"
OUT = HERE / "out"


def sha256(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main(ids):
    if not ids:
        raise SystemExit(__doc__)
    regions = {r["id"]: r for r in json.loads((HERE / "regions.json").read_text())["regions"]}
    ASSETS.mkdir(parents=True, exist_ok=True)
    packs, total = [], 0
    for rid in ids:
        src = OUT / rid
        meta = json.loads((src / "meta.json").read_text())
        comps = []
        for ctype, name in (("tiles", "tiles.pmtiles"), ("places", "places.json"), ("poi", "poi.json")):
            p = src / name
            if not p.exists():
                continue
            dest = ASSETS / f"{rid}-{name}"
            shutil.copyfile(p, dest)
            total += dest.stat().st_size
            comps.append({"type": ctype, "file": dest.name, "bytes": dest.stat().st_size, "sha256": sha256(dest)})
        r = regions[rid]
        packs.append({
            "regionId": rid, "name": r["name"], "version": meta["version"], "bbox": r["bbox"],
            "dataDate": meta["dataDate"], "attribution": "© OpenMapTiles © OpenStreetMap contributors", "license": "ODbL-1.0",
            "components": comps, "installedAtEpochMs": int(time.time() * 1000), "bundled": True,
        })
    (ASSETS / "bundled.json").write_text(json.dumps({"formatVersion": 1, "packs": packs}, ensure_ascii=False, indent=1) + "\n")
    print(f"Bundled {len(packs)} pack(s), {total / 1e6:.1f} MB into {ASSETS}")


if __name__ == "__main__":
    main(sys.argv[1:])
