# Toolchain and versions

Checked on 2026-10-28 against official release pages (developer.android.com, kotlinlang.org,
GitHub releases). Re-check before upgrading.

| Item | Version | Why |
|---|---|---|
| Android Gradle Plugin | 9.4.0 | Latest stable. Compiles Kotlin itself ("built-in Kotlin"), so `org.jetbrains.kotlin.android` is not applied. |
| Gradle (wrapper) | 9.6.0 | Minimum and default for AGP 9.4. |
| JDK | 17+ (CI uses 21) | AGP 9 needs 17; Gradle 9 needs 17. |
| Kotlin | 2.4.21 | Latest stable; placed on the root buildscript classpath, as the AGP 9 docs describe, so the Compose and serialization plugins match it. |
| KSP | 2.3.11 | Latest stable; 2.3.x supports AGP 9 built-in Kotlin. Used for Room. |
| Compose BOM | 2026.09.00 | |
| Material Icons Extended | 1.7.8 | The final release of this artifact; it is no longer in the BOM. |
| Room | 2.8.5 | Latest stable 2.x. Room 3 is a separate migration. |
| WorkManager | 2.12.0 | Raised its minSdk to 24. |
| DataStore | 1.3.0 | |
| Navigation Compose | 2.10.2 | String routes. |
| Lifecycle | 2.11.0 | |
| Activity Compose | 1.13.0 | |
| Core / Core Splashscreen | 1.19.1 / 1.2.0 | |
| MapLibre Native Android | 13.5.3 | Local PMTiles via `pmtiles://file://…` (supported since 11.7.0). |
| kotlinx.serialization / coroutines | 1.11.0 / 1.11.0 | |

## SDK levels

- **minSdk 26** (Android 8.0): required by Room 2.8 (23) and WorkManager 2.12 (24); 26 adds
  notification channels and adaptive icons used directly by the app.
- **compileSdk / targetSdk 36** (Android 16). AGP 9.4 supports up to API 37; moving to 37 should be
  done together with a review of Android 17 behaviour changes.

## Things to verify on first build

The project was written in an environment without access to Google's Maven repository, so the
following could not be compiled there and should be confirmed on the first real build:

- AGP 9 new DSL accepts every block in `app/build.gradle.kts` (`androidResources.noCompress`, `testOptions.unitTests.isReturnDefaultValues`).
- KSP 2.3.11 with Kotlin 2.4.21 under AGP 9 built-in Kotlin.
- MapLibre 13.x API names used in `OfflineMap.kt` (`org.maplibre.android.*`).
