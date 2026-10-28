#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Writes a HAVEN map catalog (catalog.json) for packs built by build_pack.sh.

Layout expected under OUT_DIR (default tools/mappack/out):
    <region-id>/tiles.pmtiles      required
    <region-id>/places.json        optional (haven-places)
    <region-id>/poi.json           optional (haven-places, emergency kinds)
    <region-id>/meta.json          written by build_pack.sh: version, dataDate

Component URLs are relative to the catalog, so the whole directory can be uploaded as-is
(for example as GitHub release assets or to any static HTTPS host).

    python3 tools/mappack/make_catalog.py [--out DIR] [--regions regions.json]
"""
import argparse
import hashlib
import json
import pathlib
import re
import sys
from datetime import date

HERE = pathlib.Path(__file__).resolve().parent
ID = re.compile(r"^[a-z0-9][a-z0-9-]{1,62}$")
VERSION = re.compile(r"^[0-9]+(\.[0-9]+){0,3}$")
ATTRIBUTION = "© OpenMapTiles © OpenStreetMap contributors"
LICENSE = "ODbL-1.0"
COMPONENTS = [("tiles", "tiles.pmtiles"), ("places", "places.json"), ("poi", "poi.json")]


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def is_pmtiles_v3(path):
    with open(path, "rb") as f:
        head = f.read(8)
    return len(head) == 8 and head[:7] == b"PMTiles" and head[7] == 3


def build(out_dir, regions_file, url_prefix=""):
    regions = json.loads(regions_file.read_text())["regions"]
    entries, problems = [], []
    for r in regions:
        rdir = out_dir / r["id"]
        if not rdir.is_dir():
            continue
        if not ID.match(r["id"]):
            problems.append(f"{r['id']}: invalid id")
        meta_path = rdir / "meta.json"
        if not meta_path.exists():
            problems.append(f"{r['id']}: missing meta.json (run build_pack.sh)")
            continue
        meta = json.loads(meta_path.read_text())
        if not VERSION.match(meta["version"]):
            problems.append(f"{r['id']}: invalid version {meta['version']}")
        components = []
        for ctype, name in COMPONENTS:
            p = rdir / name
            if not p.exists():
                if ctype == "tiles":
                    problems.append(f"{r['id']}: missing {name}")
                continue
            if ctype == "tiles" and not is_pmtiles_v3(p):
                problems.append(f"{r['id']}: {name} is not a PMTiles v3 archive")
            components.append({
                "type": ctype,
                "file": name,
                "url": f"{url_prefix}{r['id']}/{name}",
                "bytes": p.stat().st_size,
                "sha256": sha256(p),
            })
        entries.append({
            "id": r["id"],
            "name": r["name"],
            "description": r.get("description"),
            "version": meta["version"],
            "bbox": r["bbox"],
            "dataDate": meta["dataDate"],
            "attribution": ATTRIBUTION,
            "license": LICENSE,
            "components": components,
        })
    if problems:
        raise SystemExit("\n".join(problems))
    return {"format": "haven-map-catalog", "formatVersion": 1, "generated": date.today().isoformat(), "regions": entries}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=pathlib.Path, default=HERE / "out")
    ap.add_argument("--regions", type=pathlib.Path, default=HERE / "regions.json")
    ap.add_argument("--url-prefix", default="", help="Prefix for component URLs, e.g. a release download URL ending in /")
    args = ap.parse_args()
    catalog = build(args.out, args.regions, args.url_prefix)
    (args.out / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
    print(f"Wrote {args.out / 'catalog.json'} with {len(catalog['regions'])} region(s)")


if __name__ == "__main__":
    sys.exit(main())
