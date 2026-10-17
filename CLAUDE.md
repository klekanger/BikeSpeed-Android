# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

BikeSpeed for Android — a full port of the iOS BikeSpeed app
(https://github.com/klekanger/BikeSpeed, whose test suite is the behavioural spec): a fullscreen
bike speedometer with an 80s-car-style analog gauge showing live GPS speed, a 2×2 paged stats
panel (average/max speed, direction + street name, distance/duration, altitude/climb/descent/
GPS position with live gradient), Start/Pause/Reset trip controls with auto-pause, background
trip recording via a foreground service, a trip log with monthly sections and lifetime/
personal-best aggregates, and a trip detail screen with a route map, height/speed charts and
GPX export. Localized in English and Norwegian (nb) with an in-app language override.
Portrait only, screen kept awake — designed to run mounted on a bike handlebar.

Jetpack Compose + Material 3 (fixed dark theme, no dynamic color), Room (local-only, with a
dormant `TripSyncEngine` seam for a future REST backend shared with iOS), Google Maps Compose.

## Commands

```
./gradlew testDebugUnitTest    # the full suite (~275 tests, pure JVM + Robolectric)
./gradlew assembleDebug
```

Both must be green after every change. Tests mirror the production layout
(`service/TripManager.kt` → `test/…/service/TripManagerTest.kt`) and are behaviour-level:
they drive `TripManager` by feeding fixes through `TripTestHarness`
(`test/…/fixtures/`), whose `move(meters, seconds)` reads as riding. Two things will
otherwise look like app bugs when they're really the guards working:

- `move(meters = …)` must imply a speed under 120 km/h (33.3 m/s) or the teleport guard
  discards the fix and no distance accrues — `move(meters = 50.0)` at the default one second
  is 180 km/h.
- Time is injected (`NowProvider`/`TestClock`); tests advance the clock and call
  `tripManager.tick()`. **Never add a sleep to make a timing test pass.**

The harness's `Fix.north` divisor (111 100) deliberately overshoots real metres by ~0.1 % so
sampling-interval boundaries (two 5 m hops crossing a 10 m interval) stay off the
floating-point knife edge — see `Fixes.kt` before "correcting" it.

The Maps API key goes in `secrets.properties` at the repo root as `MAPS_API_KEY=…`
(git-ignored; NOT `local.properties`, which Android Studio warns it may regenerate). The
checked-in `local.defaults.properties` keeps a keyless build running — the map just renders
no tiles.

## Architecture (the ported rules — all inherited from iOS, all pinned by tests)

- **Synchronous fix delivery is load-bearing.** `LocationSource` is a plain listener registry
  (`FixListener`), NOT a SharedFlow: `TripManager.consume` runs inside
  `LocationManager.process`, tests assert on the next line, and the auto-pause ordering
  depends on it. Observable *state* is `StateFlow` (synchronous `.value` writes); an accepted
  fix is an *event*.
- **Model layer is pure SI** (m, m/s, s, degrees); only the UI converts, via
  `MeasurementSystem` (explicit constants — the gauge labels KM/H / KM/T (nb) / MPH are
  hand-picked, not platform-formatted).
- **Sentinels → nulls at one boundary.** `platform/LocationFixMapper.kt` is the only place
  Android's `has*()` flags are translated; everything downstream reads "null means unknown".
  It also stamps the fix's timestamp in the *device-clock* domain, deriving the fix's age
  from `elapsedRealtimeNanos` — never "simplify" back to `Location.time`, which lives on the
  GPS clock: a device clock a few seconds off GPS time would stale every fix forever.
- **Distance is haversine (`GeoMath`)**, not `Location.distanceBetween`, so the whole trip
  pipeline runs as plain JVM tests.
- `service/TripManager.kt` is the heart: teleport guard (120 km/h, 3-strike re-anchor),
  jitter floor max(1 m, 0.5×hAcc), auto-pause hysteresis (pause <2 km/h after 3 s on the GPS
  clock, resume ≥3 km/h immediately, 5 s wall-clock watchdog), elevation-source latch
  (barometer preferred with a 10-fix grace, GPS fallback, latched per trip; deadbands
  1 m/3 m; 15 s continuity gap), route track at 10 m / height profile at 25 m, save floor
  5 s + 10 m. Read the iOS `TripManager.swift` doc comments (in the iOS repo above) before
  touching any number. The production 0.5 s driver of `tick()` is `TripManager.startTicker`,
  owned by the *app* scope in `AppContainer` — never move it (back) into a screen: a
  screen-owned loop dies when the rider opens Settings or the trip log, taking the
  auto-pause watchdog and the trip clock with it mid-ride.
- The platform barometer (`platform/BarometerAltitudeSource.kt`) low-passes raw pressure
  (EMA, α 0.1) before anything reads it: iOS hands TripManager CMAltimeter's pre-filtered
  relative altitude, and raw Android transients (~0.12 hPa ≈ 1 m) would otherwise cross the
  1 m deadband and bank phantom climb on a flat gusty ride.
- `service/SettingsStore.kt` uses SharedPreferences (not DataStore — `autoPauseEnabled` is
  read synchronously in the fix path) with `contains()` so an unset auto-pause reads
  default-ON while an explicit off survives. Same key names as iOS.
- **Storage:** `data/TripEntity.kt` mirrors the iOS `StoredTrip` row, payloads as JSON blobs
  via `TripPayloadCoder` — **the shared wire format** (iOS field names, ISO-8601 UTC
  timestamps without fractions, absent-not-null optionals; pinned by golden-JSON tests).
  **No list query may select the blob columns** (`TripScalars` is the projection); `deletedAt`
  filtering is pre-wired in the DAO so soft-delete later touches only the delete path. The
  sync metadata (`updatedAt`/`deletedAt`/`syncedAt`/`remoteId`) is written but read by
  nothing yet — it stays dormant for the future REST backend.
- `TripDataStack.save` **throws** on failure (reset() destroys the only other copy); the
  control bar resets only after the write lands, and Save is **single-flight** (an unguarded
  double-tap would write the ride twice under fresh UUIDs). Every mutation notifies
  `TripSyncEngine`, and `TripDataStackTest` fails if a new mutation path forgets.
- Background recording = `TripRecordingService` (foreground, `location` type), driven
  entirely through `LocationManager.backgroundUpdatesHandler` by TripManager's lifecycle
  calls; **an auto-pause keeps it alive** (its only exit is a fix that can't arrive if GPS
  is released). `START_NOT_STICKY` is deliberate: the trip lives in memory, so a sticky
  restart after process death would be a zombie "Recording trip" notification over nothing,
  with no code path that could ever stop it. Relatedly, MainActivity releases GPS +
  heading/tilt listeners at onStop unless a trip is RUNNING
  (`AppContainer.releaseSensorsUnlessRecording`) and restarts them at onStart.
- GPX: `String.format(Locale.ROOT, …)` is load-bearing — Kotlin's bare `format` is
  locale-aware and nb_NO would emit comma decimals no importer reads. The test runs the
  export under nb_NO to prove the pin.
- Localization: `AppCompatDelegate.setApplicationLocales` (MainActivity must stay an
  `AppCompatActivity`); every `getString` follows the override, so there is no iOS-style
  manual bundle lookup. Norwegian swaps the compass east/west axis (Ø/V) — the single-letter
  resources double as the hemisphere letters.

## Build-system notes (AGP 9)

- **No `org.jetbrains.kotlin.android` plugin** — AGP 9's built-in Kotlin compiles the app.
  The Compose-compiler and serialization plugin versions must match AGP's embedded KGP
  (2.2.10 for AGP 9.1.1 — check the AGP POM when bumping; the `kotlin` version in
  `libs.versions.toml` keys all three).
- `android.disallowKotlinSourceSets=false` in `gradle.properties` is the documented
  suppression for KSP still using the old sourceSets DSL; drop it when KSP migrates.
- **compileSdk 36 is a ceiling until the android-37 platform ships**: core-ktx 1.18.0,
  lifecycle 2.10.0, maps-compose 8.0.1 and navigation-compose 2.9.8 are the newest versions
  that compile against 36 — their successors demand the unpublished 37. Revisit together
  when bumping compileSdk.
- `manifestPlaceholders["MAPS_API_KEY"] = ""` in `defaultConfig` is the fallback for the
  unit-test manifest the secrets plugin doesn't reach.

Application id: `no.lekanger.bikespeed` (what Play knows the app as). Namespace (Kotlin packages, `R`): `no.bikespeed.app`. Min SDK 29 (Android 10).
