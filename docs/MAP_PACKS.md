# Map packs

HAVEN renders maps only from data stored on the phone. A **map pack** is one region's data plus a
manifest entry in a **catalog**. Nothing is fetched from any tile server at run time.

## Components

| Type | File | Format | Used for |
|---|---|---|---|
| `tiles` (required) | `tiles.pmtiles` | PMTiles v3, OpenMapTiles schema vector tiles | Map display |
| `places` | `places.json` | `haven-places` JSON | Offline place search |
| `poi` | `poi.json` | `haven-places` JSON with kinds hospital, clinic, pharmacy, police, fire_station | Emergency places |
| `routing` | engine-specific | Must name `engine` and `engineVersion` | Not used in V1 (see ROUTING.md) |

A region is only shown as able to do something when the matching component is installed and
verified.

## Catalog format

```json
{
  "format": "haven-map-catalog",
  "formatVersion": 1,
  "generated": "2026-10-20",
  "regions": [{
    "id": "in-up-lko-unnao-knp",
    "name": "Lucknow · Unnao · Kanpur",
    "version": "2026.10.1",
    "bbox": [80.15, 26.30, 81.15, 27.05],
    "dataDate": "2026-10-20",
    "attribution": "© OpenMapTiles © OpenStreetMap contributors",
    "license": "ODbL-1.0",
    "components": [
      {"type": "tiles", "file": "tiles.pmtiles", "url": "in-up-lko-unnao-knp/tiles.pmtiles",
       "bytes": 12345678, "sha256": "<64 hex chars>"}
    ]
  }]
}
```

Rules the app enforces (`ManifestValidator`): safe file names only (no paths), `https` URLs (or
paths relative to the catalog), SHA-256 and size for every file, a tiles component, a valid
bounding box and version. Invalid regions are hidden and reported.

## Download and install

1. The user picks a region. The manifest entry is saved as a job.
2. A WorkManager job downloads each file to `files/maps/staging/<id>/<version>/`, using HTTP
   `Range` requests to resume. It runs only on a network (optionally Wi-Fi only) and when storage is
   not low, and retries with backoff.
3. Each file is checked for exact size, PMTiles magic header and SHA-256.
4. The verified directory is moved into `files/maps/packs/<id>/<version>/`, `installed.json` is
   rewritten atomically, and only then is the previous version deleted.

A failed or cancelled download never changes the installed pack. Partial data can be resumed or
discarded.

## Building packs

```bash
pip install osmium                      # pyosmium, for places and POIs
tools/mappack/build_pack.sh in-up-lko-unnao-knp 2026.10.1
tools/mappack/build_pack.sh in-overview 2026.10.1
python3 tools/mappack/make_catalog.py   # writes tools/mappack/out/catalog.json
```

`build_pack.sh` downloads Planetiler from its GitHub releases, which in turn downloads the
Geofabrik extract named in `regions.json`, OSM water polygons and Natural Earth. Expect several GB
of downloads and temporary disk, and 8 GB of RAM for the India overview.

Rough sizes to plan for: a 100 × 80 km urban region to zoom 14 is typically tens of megabytes; an
India overview to zoom 7 is smaller. Measure your own output.

## Importing your own map

Maps → Downloads → **Import a map file** lets anyone use a map they already have, with no catalog or
internet. Pick a `.pmtiles` file from phone storage; HAVEN copies it into app storage, checks the
PMTiles v3 header, reads the bounding box, name and attribution from the file, and installs it like
any downloaded region. It appears under "On this device" and can be deleted from there.

- The archive must hold **vector tiles (MVT) in the OpenMapTiles layout**, which is what
  `tools/mappack/build_pack.sh` (Planetiler) produces. Other layouts (for example Protomaps) and raster
  tiles are rejected with an explanation instead of drawing a blank map.
- Raw OpenStreetMap files (`.osm`, `.osm.pbf`, `.pbf`) are source data, not tiles. Convert them on a
  computer first, e.g. `java -jar planetiler.jar --osm-path=your.osm.pbf --output=your.pmtiles`,
  then import the `.pmtiles`.
- Imported maps give map display only. Place search and emergency places need a catalog pack.
- You are responsible for the licence of data you import; keep the source's attribution.

## Hosting

Upload everything in `tools/mappack/out/` to a static HTTPS host, keeping the directory layout,
and set the catalog URL:

- at build time: `-Phaven.mapCatalogUrl=https://<host>/catalog.json`, or
- in the app: Maps → Downloads → Map source.

GitHub Releases are suitable: upload the files as release assets and run `make_catalog.py
--url-prefix https://github.com/<owner>/<repo>/releases/download/<tag>/`, since release assets
cannot contain folders (rename files `<id>-tiles.pmtiles` etc., or host the folder elsewhere).

Do not point the app at third-party tile or extract servers whose terms do not allow this use.

## Bundling starter maps in the APK

```bash
python3 tools/mappack/bundle_starter.py in-overview in-up-lko-unnao-knp
```

This copies the built files to `app/src/main/assets/maps/` and writes `bundled.json`. PMTiles are
stored uncompressed in the APK (`androidResources.noCompress`). Keep the total small (about 25 MB
is a reasonable budget for a utility app); lower `maxzoom` in `regions.json` if needed.

The repository does **not** include generated packs: they must be built from current OSM data, and
they carry ODbL obligations that the publisher must meet (below).

## Licensing obligations (ODbL)

- **Attribution**: maps show "© OpenStreetMap contributors" and "© OpenMapTiles". Keep these.
- **Derived databases** (`places.json`, `poi.json`, any routing graph) are ODbL. If you distribute
  them, offer them under the ODbL and keep them available (for example alongside the catalog).
- **Produced works** (rendered vector tiles) may be distributed with attribution; state that the
  data is © OpenStreetMap contributors and available under the ODbL.
- The app's GPL licence does not cover the map data.

## Boundaries

The style does not draw international (admin level 2) boundaries. OpenStreetMap shows boundaries
as they are on the ground, which can differ from India's official depiction. If you add
international boundaries, use a dataset that meets the requirements that apply to publishing maps
in India.

## Labels

Text labels need SDF glyphs. Run `tools/mappack/build_glyphs.sh` (needs `cargo install
build_pbf_glyphs`) to generate `app/src/main/assets/glyphs/Inter Regular/*` and
`Inter SemiBold/*`. Without them, maps render without labels.
