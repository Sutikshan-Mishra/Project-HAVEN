#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Extracts searchable places and emergency points of interest from an OSM extract.

Requires pyosmium (pip install osmium). Reads .osm.pbf or .osm XML.

    python3 extract_places.py INPUT.osm.pbf --bbox W,S,E,N --data-date YYYY-MM-DD \
        --places out/<id>/places.json [--poi out/<id>/poi.json]

Output follows the "haven-places" format read by the app (domain/maps/Places.kt).
Only real OSM records are written; nothing is invented or filled in.
"""
import argparse
import json
import sys

import osmium

PLACE_KINDS = {"city", "town", "village", "suburb", "neighbourhood", "hamlet", "locality"}
EMERGENCY = {
    ("amenity", "hospital"): "hospital",
    ("amenity", "clinic"): "clinic",
    ("amenity", "pharmacy"): "pharmacy",
    ("amenity", "police"): "police",
    ("amenity", "fire_station"): "fire_station",
}
SOURCE = "OpenStreetMap contributors (ODbL)"


def name_of(tags):
    return tags.get("name:en") or tags.get("name")


def address_of(tags):
    parts = [tags.get(k) for k in ("addr:housenumber", "addr:street", "addr:city")]
    joined = ", ".join(p for p in parts if p)
    return joined or None


class Handler(osmium.SimpleHandler):
    def __init__(self, bbox, want_poi):
        super().__init__()
        self.w, self.s, self.e, self.n = bbox
        self.want_poi = want_poi
        self.places, self.poi = [], []

    def inside(self, lat, lon):
        return self.s <= lat <= self.n and self.w <= lon <= self.e

    def add(self, tags, lat, lon):
        if not self.inside(lat, lon):
            return
        name = name_of(tags)
        if not name:
            return
        place = tags.get("place")
        if place in PLACE_KINDS:
            self.places.append({"name": name, "kind": place, "lat": round(lat, 6), "lon": round(lon, 6)})
        if self.want_poi:
            for (k, v), kind in EMERGENCY.items():
                if tags.get(k) == v:
                    rec = {"name": name, "kind": kind, "lat": round(lat, 6), "lon": round(lon, 6)}
                    if address_of(tags):
                        rec["address"] = address_of(tags)
                    phone = tags.get("phone") or tags.get("contact:phone")
                    if phone:
                        rec["phone"] = phone.split(";")[0].strip()
                    self.poi.append(rec)
                    break

    def node(self, n):
        if n.location.valid():
            self.add(n.tags, n.location.lat, n.location.lon)

    def area(self, a):
        # Buildings mapped as areas (most hospitals): use the centre of the outer ring's bounds.
        try:
            for ring in a.outer_rings():
                lats = [nd.lat for nd in ring]
                lons = [nd.lon for nd in ring]
                if lats:
                    self.add(a.tags, (min(lats) + max(lats)) / 2, (min(lons) + max(lons)) / 2)
                break
        except osmium.InvalidLocationError:
            pass


def write(path, records, data_date):
    records.sort(key=lambda r: (r["kind"], r["name"]))
    data = {"format": "haven-places", "formatVersion": 1, "source": SOURCE, "dataDate": data_date, "places": records}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input")
    ap.add_argument("--bbox", required=True)
    ap.add_argument("--data-date", required=True)
    ap.add_argument("--places", required=True)
    ap.add_argument("--poi")
    args = ap.parse_args()
    bbox = [float(x) for x in args.bbox.split(",")]
    h = Handler(bbox, want_poi=bool(args.poi))
    h.apply_file(args.input, locations=True)
    # De-duplicate identical name/kind/position records (nodes inside mapped areas).
    def dedupe(rs):
        seen, out = set(), []
        for r in rs:
            key = (r["name"], r["kind"], round(r["lat"], 4), round(r["lon"], 4))
            if key not in seen:
                seen.add(key)
                out.append(r)
        return out
    write(args.places, dedupe(h.places), args.data_date)
    if args.poi:
        write(args.poi, dedupe(h.poi), args.data_date)
    print(f"{len(h.places)} places, {len(h.poi)} emergency points", file=sys.stderr)


if __name__ == "__main__":
    main()
