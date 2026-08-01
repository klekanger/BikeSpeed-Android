# BikeSpeed for Android

A fullscreen speedometer for your bike, styled after 80s-car analog dashboards. Mount your phone on the handlebar, open the app, and ride — no setup, no accounts, no distractions.

This is the Android port of [BikeSpeed for iOS](https://github.com/klekanger/BikeSpeed), with the same features and the same behaviour — every speed filter, auto-pause threshold, and elevation rule matches the iOS app, pinned by a shared behavioural test suite.

## Features

- **Analog speed gauge** — a live GPS-driven needle on a 270° dial, with a configurable max speed and a red danger zone near the top, plus a digital speed readout in the center
- **Trip stats** — average speed, max speed, distance, duration, altitude, climb, descent, and GPS position, all updating live in a 2×2 panel; tap or swipe a cell to page through its readouts (speed cell: average ↔ max; distance cell: distance ↔ duration; altitude cell: altitude ↔ climb ↔ descent ↔ position while a trip is running)
- **Auto-pause** — distance and time pause automatically when you stop and resume the moment you move off, so your average speed doesn't sag at every red light; on by default, and switchable in Settings
- **Elevation & gradient** — total climb and descent over the ride and a live gradient percentage, measured by the barometer for accuracy where available and by GPS otherwise
- **Direction of travel** — an arrow plus compass letters (N, NE, E, SE, S, SW, W, NW) showing which way you're heading, with the name of the street you're currently on below it; the arrow stays live at a standstill by switching to the compass when GPS course goes quiet
- **Route map & GPX export** — every ride records its track; open a saved trip to see it drawn on a Google Map, and export it as a GPX file to share with Strava, Komoot, or any other app
- **GPS signal indicator** — a top-left glyph tinted green/yellow/red for signal quality; when the signal is too poor to trust, speed and distance freeze rather than drift on noise
- **Trip controls** — Start, Pause/Resume, Save, and Reset, so you can pause a ride, save a finished one to your trip log, and reset cleanly for a new one
- **Background recording** — a trip keeps recording when the screen locks or you switch apps, held by a foreground service with a quiet status notification, so you can pocket the phone mid-ride without losing the trip
- **Trip log** — completed trips are saved with date, duration, distance, average/max speed, total ascent/descent, a route map, and height-profile and speed-profile charts plotted against distance; up top, switch between your lifetime totals and personal bests (longest ride, fastest average, top speed, biggest climb)
- **Built for hundreds of rides** — trips are grouped into month sections, newest first, with a date-range filter (All, Last 7 days, Last 30 days, This year); open a trip to delete it if it's one you don't want to keep
- **On-device storage** — your trip log lives in the app's own database on your phone; nothing is uploaded anywhere
- **Settings** — set your own max gauge speed, switch between metric (km/h) and imperial (mph) units, toggle auto-pause, and override the app's display language independent of your device setting
- **Built for the handlebar** — runs fullscreen in portrait and keeps the screen from sleeping while you ride
- **English and Norwegian** — follows your phone's language setting automatically, or pick one explicitly in Settings

## Getting started

1. Mount your phone on your handlebar with a case/mount that keeps the screen visible and portrait-oriented.
2. Open BikeSpeed and allow location access when prompted — this is required for the speedometer, GPS position, and direction indicator to work. Allowing notifications lets the app show its "recording" status while a trip runs in the background; recording works either way.
3. Tap the gear in the control pill at the top-right to set your preferred max gauge speed, units (metric/imperial), auto-pause, and display language before you start riding.
4. Tap **Start** to begin tracking a trip. Average speed, distance, and climb begin accumulating from this point, and recording continues even if the screen locks.
5. Stop at a light and the trip pauses itself (auto-pause), resuming when you ride on, so your average speed stays accurate — no stats are reset. You can also tap **Pause** to stop manually, and **Resume** to continue.
6. When you're done, tap **Pause/Stop** and then **Save** to add the trip to your trip log (open it via the list icon in the same pill), or **Reset** to discard it and start fresh. Reset is only available once the trip is manually paused, so you can't accidentally lose an in-progress ride.

## Building your own copy

BikeSpeed for Android is open source, and everything but one thing builds and runs as-is. That one thing is the Google Maps API key the trip-detail map uses — it's tied to a Google Cloud project, so it can't be shared. To build your own copy:

1. Clone the repo and open it in Android Studio (or build from the command line with `./gradlew assembleDebug`; run the tests with `./gradlew testDebugUnitTest`).
2. In the [Google Cloud Console](https://console.cloud.google.com/), create a project, enable the **Maps SDK for Android**, and create an API key. Dynamic in-app maps are currently a no-charge SKU, but a billing account must be attached to the project.
3. Create a `secrets.properties` file in the repo root (git-ignored, never committed) containing: `MAPS_API_KEY=your-key-here`.
4. Before distributing a build, restrict the key to your app's package name and signing certificate in the Cloud Console.

Without a key the app builds and runs fine — the map on the trip-detail screen just renders no tiles. Everything else, including trip recording, charts, and GPX export, works without it.

## Requirements

- A phone running Android 11 (API 30) or later, with Google Play services (for fused location and the trip-detail map)
- Location permission granted to BikeSpeed (recording continues in the background through a foreground service while a trip is running)
- Optional: a barometer, for pressure-based climb and gradient — without one, climb falls back to GPS altitude
- A GPS signal — speed, direction, and position accuracy depend on GPS reception, so results may be less accurate indoors or in dense urban areas

## Privacy

BikeSpeed has no accounts, no analytics, and no tracking, and it does not collect your data or share it with anyone. Your location is used only to drive what you see on screen. Your trips are stored in the app's own database on your device and nowhere else — there is no cloud sync in the Android version. Deleting the app removes them.

Two things involve Google's services, using the standard Android APIs on your phone:

- To show the name of the street you're on, BikeSpeed asks Android's geocoding service what street a coordinate corresponds to. That lookup sends the coordinate to the geocoding provider (Google, on phones with Play services), subject to [Google's privacy policy](https://policies.google.com/privacy), and happens only occasionally as you move between streets, not continuously. With no network connection the street name is simply left blank and the rest of the app works as usual.
- Opening a saved trip loads its map from Google Maps, which fetches tiles for the area around your route from Google's servers. The live speedometer screen involves no map and sends nothing.

Beyond those, nothing — speed, distance, altitude — is sent off the device automatically, and nothing at all is ever shared with us or any third party.

The one exception is entirely your choice: when you tap **Export GPX** on a saved trip, BikeSpeed hands that ride's track to Android's share sheet so you can send it to another app or service. That only ever happens when you ask for it, with the trip you picked, going where you send it.
