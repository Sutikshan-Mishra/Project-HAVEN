# Third-party notices

Project HAVEN's own source code is GPL-3.0-or-later. It uses the components below, each under
its own licence. All licences listed are compatible with distributing the app under GPL-3.0.
Versions are those in `gradle/libs.versions.toml`; check transitive dependencies with
`./gradlew :app:dependencies` before each release.

## Libraries (compiled into the app)

| Component | Licence | Notes |
|---|---|---|
| AndroidX Core, Core Splashscreen, Activity, Lifecycle, Navigation, DataStore, Room, WorkManager | Apache-2.0 | developer.android.com/jetpack/androidx |
| Jetpack Compose (UI, Foundation, Material 3) | Apache-2.0 | |
| Compose Material Icons Extended (icons from Material Design) | Apache-2.0 | |
| Kotlin standard library | Apache-2.0 | |
| kotlinx.coroutines | Apache-2.0 | |
| kotlinx.serialization | Apache-2.0 | |
| MapLibre Native for Android | BSD-2-Clause | Includes further third-party components listed in its own LICENSE file |
| MapLibre Java (GeoJSON), pulled in by MapLibre Native | MIT | |
| OkHttp and Okio, pulled in by MapLibre Native | Apache-2.0 | |

## Fonts (bundled)

| Component | Licence | Files |
|---|---|---|
| Inter 4.1 by Rasmus Andersson and the Inter Project Authors | SIL Open Font License 1.1 | `app/src/main/res/font/inter_*.ttf`; licence in `licenses/Inter-OFL-1.1.txt` |

## Data (not in this repository; produced by `tools/mappack`)

| Data | Licence | Obligations |
|---|---|---|
| OpenStreetMap | Open Database License 1.0 (ODbL) | Attribute "© OpenStreetMap contributors"; derived databases (places.json, poi.json, routing graphs) are themselves ODbL and must be offered under the ODbL; produced works such as rendered tiles must carry attribution. See docs/MAP_PACKS.md. |
| OpenMapTiles vector-tile schema (used by Planetiler's profile) | CC-BY-4.0 (schema) | Attribute "© OpenMapTiles" wherever tiles built with the schema are shown. |
| OSM water polygons (osmdata.openstreetmap.de) | ODbL | As OpenStreetMap. |
| Natural Earth (low-zoom features used by Planetiler) | Public domain | None required; credited as courtesy. |

## Build tools (used, not distributed)

| Tool | Licence |
|---|---|
| Gradle and the Gradle wrapper | Apache-2.0 |
| Android Gradle Plugin, Kotlin Gradle plugin, KSP | Apache-2.0 |
| Planetiler | Apache-2.0 |
| pyosmium / libosmium | BSD-2-Clause / BSL-1.0 |
| build_pbf_glyphs (optional) | BSD-3-Clause |

## Guide content

The first-aid, survival and cookbook text in `tools/content/` and `app/src/main/assets/guides/` was
written for this project and is distributed under the project's GPL-3.0-or-later licence. It cites
published guidance from the organisations listed in each article; it does not reproduce their text.
