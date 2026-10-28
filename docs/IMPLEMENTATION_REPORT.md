# Implementation report (V1, 2026-10-28)

## Verification status

| Check | Result |
|---|---|
| Domain unit tests (78 tests: Morse, units, calculator, geo, sensors/speed, notes rules, guide search, recipe filters, ration planner, SOS message, check-in, manifest validation, SHA-256/PMTiles checks, download state machine, capabilities, place search) | **Run and passing** with Kotlin 2.4.21, using a minimal JUnit 4 stand-in because JUnit jars could not be downloaded in the build environment. In Gradle they run with real JUnit. |
| Android-layer unit tests (DataStore persistence, notes repository import/export, bundled content parsing, map-pack install/rollback, catalog parsing, map style) | Written, **not run**: need AndroidX and kotlinx-serialization from Maven. |
| Compose UI and Room instrumented tests | Written, **not run**: need a device or emulator. |
| `./gradlew assembleDebug` | **Not run.** Google Maven, Maven Central and the Gradle plugin portal were blocked in the build environment. |
| GitHub Actions | Workflow written and YAML-validated; **not executed**. |
| Bundled guide content | Built and validated by `tools/content/build_guides.py` (structure, required sections, 112 guidance, references). Medical review **pending**. |
| Map catalog generator | Run on a synthetic test pack: correct sizes, SHA-256 and relative URLs. |
| Places extractor (pyosmium) | **Not run**: pyosmium could not be installed in the build environment. |
| Syntax check of all app sources | All files were passed through kotlinc; the remaining errors trace back to the AndroidX, Compose, MapLibre and kotlinx-serialization classes that could not be downloaded. Some genuine issues found this way were fixed. |

**Expect the first Gradle build to need small fixes** (for example an API detail or import),
because the Android code could not be compiled here.

## Feature status

| Feature | Status |
|---|---|
| Splash and branding, bottom navigation, Settings with persistent theme | Complete |
| SOS: 112 dialler shortcut, live location, copy/share, contacts, prepared SMS | Complete (sending is always done by the user in the messaging app) |
| Emergency contacts, medical profile | Complete |
| Safety check-in with reboot restore | Complete; on-time alerts need the "Alarms and reminders" permission on Android 12+ |
| Flashlight, Morse (flash/sound/screen, speed and gaps, SOS, preview), siren, screen light | Complete; signals stop when leaving the screen |
| Compass, coordinates, distance, speed and altitude, ruler, calipers, protractor, level, converter, calculator, battery | Complete |
| Field Notebook with autosave, search, tags, pin, favourites, sort, location, share, export/import | Complete |
| First Aid (27), Survival Handbook (30), Cookbook (17 recipes + 5 safety guides + planner), search, bookmarks, checklists | Complete; **content pending clinical review** |
| Map rendering of installed packs, places search, emergency places, download manager (resume, verify, cancel, retry, delete, update) | Complete in code; needs hosted packs |
| Bundled India overview and Lucknow–Unnao–Kanpur maps | **Not included**: pipeline provided, data must be generated |
| Map labels | Need glyphs from `tools/mappack/build_glyphs.sh` |
| Offline routing and turn-by-turn | **Not available**; explicit "not available" state, integration interface and plan in ROUTING.md |

## Setup still required

1. Build once with the Android SDK (or push to GitHub to run CI) and fix anything the compiler reports.
2. Build map packs (`tools/mappack/build_pack.sh`), generate the catalog, host it over HTTPS and set
   `haven.mapCatalogUrl` or enter the URL in the app. Optionally bundle the starter packs.
3. Generate label glyphs.
4. Have the guide content reviewed (docs/CONTENT_REVIEW.md).
5. Set `haven.repositoryUrl` once the repository exists.
