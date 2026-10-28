#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds one HAVEN region pack from OpenStreetMap data.
#
#   tools/mappack/build_pack.sh <region-id> <version>
#   e.g. tools/mappack/build_pack.sh in-up-lko-unnao-knp 2026.10.1
#
# Requirements: Java 21+, Python 3 with pyosmium (pip install osmium), curl, jq.
# Planetiler downloads the Geofabrik extract plus Natural Earth and water polygons, so the
# first run needs several GB of disk and a good connection. Output goes to tools/mappack/out/<id>/.
set -euo pipefail

ID="${1:?region id}"
VERSION="${2:?version, e.g. 2026.10.1}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$HERE/work"
OUT="$HERE/out/$ID"
PLANETILER_VERSION="${PLANETILER_VERSION:-0.9.1}"   # check https://github.com/onthegomap/planetiler/releases
JAR="$WORK/planetiler-$PLANETILER_VERSION.jar"

REGION="$(jq -c --arg id "$ID" '.regions[] | select(.id == $id)' "$HERE/regions.json")"
[ -n "$REGION" ] || { echo "Unknown region $ID"; exit 1; }
AREA="$(jq -r .geofabrik <<<"$REGION")"
BBOX="$(jq -r '.bbox | join(",")' <<<"$REGION")"
MAXZOOM="$(jq -r .maxzoom <<<"$REGION")"
WANT_POI="$(jq -r .poi <<<"$REGION")"

mkdir -p "$WORK" "$OUT"
if [ ! -f "$JAR" ]; then
  curl -fL -o "$JAR" "https://github.com/onthegomap/planetiler/releases/download/v$PLANETILER_VERSION/planetiler.jar"
fi

# 1. Vector tiles (OpenMapTiles schema) as PMTiles, clipped to the region.
java -Xmx8g -jar "$JAR" \
  --download --area="$AREA" --bounds="$BBOX" --maxzoom="$MAXZOOM" \
  --download-dir="$WORK/sources" --tmpdir="$WORK/tmp" \
  --output="$OUT/tiles.pmtiles" --force

# 2. Place names and emergency points of interest from the same extract.
PBF="$(ls "$WORK"/sources/*"$AREA"*.osm.pbf | head -n1)"
DATA_DATE="$(date -u -r "$PBF" +%F)"
POI_ARGS=()
[ "$WANT_POI" = "true" ] && POI_ARGS=(--poi "$OUT/poi.json")
python3 "$HERE/extract_places.py" "$PBF" --bbox "$BBOX" --data-date "$DATA_DATE" --places "$OUT/places.json" "${POI_ARGS[@]}"

# 3. Pack metadata for make_catalog.py.
jq -n --arg v "$VERSION" --arg d "$DATA_DATE" '{version: $v, dataDate: $d}' > "$OUT/meta.json"
ls -lh "$OUT"
echo "Next: python3 tools/mappack/make_catalog.py"
