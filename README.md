# Project HAVEN

**India's very own emergency utility app** · Developed by Sutikshan Mishra

Project HAVEN is an offline-first emergency and survival toolkit for Android. It puts SOS
shortcuts, first aid, survival and food guidance, signalling tools, field instruments, a private
notebook and offline maps in one clean app that keeps working without mobile data.

> HAVEN is not affiliated with or endorsed by the Government of India, and it does not replace
> emergency services or medical care. In an emergency, call **112**.

---

## Features

| Area | What it does | Offline |
|---|---|---|
| **SOS** | Call 112 (opens the dialler), live coordinates with accuracy, copy/share, prepared SMS to emergency contacts you review before sending | Calls/SMS need a SIM and network |
| **Emergency contacts** | Pick from Android contacts (no contacts permission) or enter manually; primary contact | Yes |
| **Medical profile** | Optional, every field optional, stored only on the device, delete any time | Yes |
| **Safety check-in** | Timer that alerts you if you don't check in; offers to prepare messages, never sends anything itself; survives reboot | Yes |
| **Signal & Light** | Flashlight, Morse code (flash, sound, screen) with adjustable speed and gaps, SOS prosign, preview mode, siren, full-screen light | Yes |
| **First Aid** | 27 searchable articles in 6 categories, step-by-step, with "do not", warning signs and sources | Yes |
| **Survival Handbook** | 30 articles: water, shelter and weather, navigation and signalling, supplies, personal safety, preparedness checklists | Yes |
| **Emergency Cookbook** | 17 recipes from Indian pantry staples with filters (no cooking, limited water, limited fuel, vegetarian), food-safety guides, ration and water planner | Yes |
| **Field tools** | Compass (magnetic/true), GPS coordinates (decimal/DMS), distance and bearing, speed and altitude, ruler and calipers (calibratable), protractor, spirit level, unit converter, calculator, battery | Yes |
| **Field Notebook** | Notes with autosave, search, pin, favourites, tags, sort, attach location, share, JSON export/import with duplicate protection | Yes |
| **Offline maps** | MapLibre rendering of installed PMTiles packs, place search and emergency places when the pack includes them, resumable and verified pack downloads | After download |
| **Settings** | Dark (default) / Light / System theme, permissions overview, storage and safe cleanup, privacy, about and licences | Yes |

**Not in V1:** offline turn-by-turn routing (see [Routing](#maps-and-routing)), on-device AI,
camera Morse decoding and Bluetooth messaging.

## Requirements

- Android 8.0 (API 26) or newer. Targets Android 16 (API 36).
- Flash, GPS, compass, accelerometer and barometer are all optional. Each tool shows a clear
  "unavailable" state when the hardware is missing.

## Build locally

You need **JDK 17+** (21 recommended) and the **Android SDK** with platform 36. Android Studio
(a version that supports AGP 9.4) provides both.

```bash
git clone <your-repository-url> ProjectHAVEN
cd ProjectHAVEN
echo "sdk.dir=$ANDROID_HOME" > local.properties   # if not using Android Studio
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Optional build properties (in `~/.gradle/gradle.properties` or with `-P`):

| Property | Purpose |
|---|---|
| `haven.repositoryUrl` | Shown as "Source code" in About. Hidden when empty. |
| `haven.mapCatalogUrl` | Default map catalog (`https://…/catalog.json`). Users can also set one in Map downloads. |

## Install the debug APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and open it (allow installs from that source when asked). The
debug build is signed with the Android debug key only. It is not a production release.

## Run tests

```bash
./gradlew testDebugUnitTest            # JVM unit tests (runs in CI)
./gradlew connectedDebugAndroidTest    # Compose UI and Room tests, needs a device or emulator
python3 tools/content/build_guides.py --check   # bundled content is valid and up to date
```

Unit tests cover Morse encoding and timing, unit conversion, the calculator, geographic
distance and bearing, speed estimation, notes (editing rules, search, sorting, import/export),
guide parsing and search on the real bundled files, recipe filters, checklist and theme
persistence, map manifest validation, checksums, the download state machine, pack install and
rollback, and map style generation.

## GitHub Actions

`.github/workflows/android.yml` runs on pushes and pull requests to `main` and on manual
dispatch. It validates the Gradle wrapper, sets up JDK 21 and Gradle caching, checks the
bundled content, runs `testDebugUnitTest`, builds `assembleDebug`, and uploads the APK as the
`project-haven-debug-apk` artifact (Actions → run → Artifacts). Test reports are kept on every
run and build reports on failure. If your default branch is not `main`, edit the two branch lists.

`.github/workflows/instrumented.yml` is a manual workflow that runs the instrumented tests on an
emulator. It uses one third-party action; review and pin it before use.

No signing keys or secrets are used or required.

## Maps and routing

**Rendering is not routing.** HAVEN draws maps from vector tiles stored on the phone. Turn-by-turn
navigation needs a separate routing graph and engine, which V1 does not ship. Every region
therefore says **"Offline routing not available for this region"**, and the app never draws an
invented route. See [docs/ROUTING.md](docs/ROUTING.md) for the integration plan.

What each pack can contain, and what it enables:

| Component | File | Enables |
|---|---|---|
| Vector tiles | `tiles.pmtiles` (OpenMapTiles schema) | Map display |
| Places | `places.json` | Offline place search |
| Emergency POIs | `poi.json` | Nearby hospitals, clinics, pharmacies, police, fire stations |
| Routing graph | engine-specific | Nothing yet in V1 |

**Where packs come from.** HAVEN does not assume any public host. Packs are built from
OpenStreetMap with the pipeline in `tools/mappack/` (Planetiler + pyosmium), described in
[docs/MAP_PACKS.md](docs/MAP_PACKS.md), then published with a `catalog.json` on any static HTTPS
host (GitHub Releases work). Set the catalog URL at build time or in the app. Downloads resume
after interruption, every file is checked for size, format and SHA-256 before install, and a
working pack is never replaced until the new one has passed verification.

**Starter coverage.** The pipeline defines an India overview pack and a Lucknow–Unnao–Kanpur pack,
and `tools/mappack/bundle_starter.py` can ship them inside the APK. They are **not included in
this repository** because they must be generated from current OSM data (see the docs for why).

Map labels need glyph files generated by `tools/mappack/build_glyphs.sh`. Without them, maps
render without text.

## Permissions and privacy

| Permission | Used for | Asked |
|---|---|---|
| Location | Coordinates, SOS messages, true north, speed, maps | When you open a location feature |
| Notifications | Check-in alerts | When you start a check-in (Android 13+) |
| Alarms and reminders | On-time check-in alerts | Optional, from the check-in screen |
| Internet | Map catalog and pack downloads you start | Never prompted |
| Boot completed | Re-arming an active check-in after restart | Never prompted |

No contacts, camera, phone or SMS permission is requested: contacts are picked one at a time
through Android's picker, the torch uses the camera service without camera access, and calls and
messages open the system apps for you to confirm.

There is no account, analytics or tracking. Notes, contacts and the medical profile are stored in
app-private storage and excluded from cloud backup and device transfer. HAVEN does not add its own
encryption; data relies on Android's device encryption and your screen lock. Location is read only
while a location screen is visible.

## Offline limits

- Calls and SMS need a working SIM and network. Opening the messaging app does not send a message.
- GPS needs a view of the sky; without mobile data the first fix can take longer.
- The check-in alert can be delayed by battery saving, and precise timing needs "Alarms and reminders".
- Screen ruler and calipers are approximate until calibrated against a known object.
- Bundled guides have **not yet been clinically reviewed**; each article says so. See
  [docs/CONTENT_REVIEW.md](docs/CONTENT_REVIEW.md).

## Project layout

```
app/src/main/java/app/projecthaven/
  domain/      Pure Kotlin logic (Morse, units, calculator, geo, notes, guides, maps, SOS)
  data/        Android-backed repositories (DataStore, Room, sensors, location, torch, audio,
               check-in alarms, map packs and downloads)
  ui/          Compose theme, components, navigation and screens
app/src/main/assets/guides/   Bundled guide JSON (generated by tools/content)
tools/content/                Guide source text, builder and validator
tools/mappack/                Map-pack pipeline
docs/                         Toolchain, maps, routing, content review
```

## Licensing and attribution

- Source code: **GPL-3.0-or-later**, see [LICENSE](LICENSE) and [NOTICE](NOTICE).
- Third-party libraries, fonts and data keep their own licences: see
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- Map data: © OpenStreetMap contributors, available under the ODbL. Map tiles use the
  OpenMapTiles schema (© OpenMapTiles).
- The Project HAVEN name and logo are not covered by the GPL: see [BRANDING.md](BRANDING.md).

## Known limitations and future work

- The Android build and CI are configured but were not compiled in the environment this was
  written in; see [docs/IMPLEMENTATION_REPORT.md](docs/IMPLEMENTATION_REPORT.md).
- Offline routing, camera Morse decoding, Bluetooth peer messaging and on-device question
  answering are planned for V2.
- Map packs and label glyphs must be generated and hosted before maps are usable.
- English only.
