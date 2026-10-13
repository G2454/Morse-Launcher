# Morse Launcher

An Android home screen that mixes Nothing OS's dot-matrix monochrome look with
iOS-style widgets, dock and app library. Built for the Motorola Edge 40 Neo
(412 × 915 dp, pOLED, 144 Hz) and designed to stay light on battery.

## What's in it

| Area | What it does |
| --- | --- |
| Home | Pages on a 4 × 6 (or 5 × 6) grid, apps, folders, widgets, search pill, 4-app dock. Swipe up for the app library, down for search. |
| Edit mode | Long-press empty space. Static dashed outlines (no jiggle animation), drag to move, drop an app on an app to make a folder, drag between grid and dock, remove badges, page manager. |
| Widgets | Glance (M), Dot clock (S), Dial clock (S), Weather (S, M hourly), Battery (S), Calendar (S next event, M agenda, L month), Music (M), Quick toggles (S), Reminders (S, M) — plus any other app's widgets. |
| App library | Category cards (Suggestions, Recently added, Social, Productivity…) or an A–Z list with a letter rail. |
| Search | Apps, contacts, system settings pages and web search, ranked by match quality and how often you open each app. |
| Icons | 63 single-stroke Morse glyphs for system apps; other apps use Android 13's monochrome icon layer, or their initial in dot-matrix type. "Use original icon" per app. |
| Settings | Grid size, labels, theme (dark/light/auto), accent, icon style, wallpaper (dot grid/plain/system), power options, weather units and city, permissions. Also reachable from the app list as "Morse settings". |

## Battery decisions

* **Dark theme is pure black.** OLED pixels are off. The dot-grid wallpaper is
  drawn once and cached.
* **No live blur.** Folders and sheets sit on a solid dim instead.
* **Nothing animates while idle.** No live wallpaper, no parallax, no looping
  effects, no jiggling icons in edit mode.
* **Motion uses transform and opacity only, ≤ 300 ms.** In low-power motion
  mode (on by default) every overlay is a 120 ms fade and the home window asks
  for 60 Hz instead of 144 Hz.
* **Widgets never poll.**
  * The clock listens to the system's minute tick.
  * Battery uses the system broadcast.
  * Calendar refreshes on provider changes.
  * Music updates on media-session events, and its progress advances one dot at a time.
  * Weather refreshes only when the home screen is shown and the last forecast is older than 30 minutes (15 on Fresh, 60 on Saver).
  * Everything stops collecting when the launcher is in the background.
* **Low battery.** Below 20% (or with Battery Saver on), widgets freeze, show
  "PAUSED · as of", and motion drops to fades.
* **No background work.** There is no WorkManager and no wake locks. Location
  uses the last known position and never starts GPS.

## Project layout

```
core/   Pure Kotlin (no Android): glyphs + SVG path parser, JSON, home layout model
        and all layout operations, default layout, app library grouping, search
        ranking, weather parsing (Open-Meteo), calendar month grid, power policy,
        motion tokens, settings codec, reminders. Fully unit tested.
app/    Android app: Jetpack Compose UI, repositories over system services
        (LauncherApps, AppWidgetHost, MediaSession, CalendarContract, …).
```

Package: `dev.morse.launcher` (debug builds: `dev.morse.launcher.debug`, so both can be installed).

## Building

Requirements: JDK 17 and the Android SDK (platform 35). Then run:

```bash
./gradlew :core:test                 # pure-Kotlin unit tests
./gradlew :app:testDebugUnitTest     # Robolectric + Compose UI tests
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/
./gradlew :app:connectedDebugAndroidTest   # on a device/emulator
```

To install the debug build, run `adb install app/build/outputs/apk/debug/app-debug.apk`.
Then press Home and choose **Morse**, or open *Morse settings* and tap **Make Morse your home screen**.

## CI (GitHub Actions)

`.github/workflows/ci.yml` runs on every push to `main`, on pull requests and on demand.

The **Test and build** job:

1. Validates the Gradle wrapper.
2. Runs the core tests, then the app's Robolectric/Compose tests.
3. Runs lint.
4. Builds the debug APK.
5. Uploads the APK and the test/lint reports as artifacts.

The **Emulator tests** job boots an API 34 emulator and runs the instrumented
smoke tests. It runs on pushes to `main` and when the workflow is started by hand.

Signed release APKs are optional. Add these repository secrets and the workflow
also builds and uploads `app-release.apk`:

| Secret | Value |
| --- | --- |
| `MORSE_KEYSTORE_BASE64` | `base64 -w0 your.jks` |
| `MORSE_KEYSTORE_PASSWORD` | keystore password |
| `MORSE_KEY_ALIAS` | key alias |
| `MORSE_KEY_PASSWORD` | key password |

Without them, release builds are signed with the debug key and the job still passes.

Lint is reported but does not fail the build (`abortOnError = false`); tighten it once the first runs are clean.

## Permissions

Everything except `INTERNET` and `QUERY_ALL_PACKAGES` is optional and requested only when you use the feature.

| Permission | Why |
| --- | --- |
| `INTERNET` | Weather from Open-Meteo (free, no account). |
| `QUERY_ALL_PACKAGES` | A home screen must list every app and widget. |
| `ACCESS_COARSE_LOCATION` | Weather for where you are (optional; a city can be set instead). |
| `READ_CALENDAR` | Calendar widgets. |
| `READ_CONTACTS` | Contacts in search. |
| `ACCESS_NOTIFICATION_POLICY` | Do-not-disturb toggle. |
| `REQUEST_DELETE_PACKAGES` | "Uninstall" in the app menu. |
| Notification access (system setting) | Notification dots and the Music widget. |

## Credits

The fonts are bundled under the SIL Open Font License 1.1 (see `licenses/`):

* [Doto](https://github.com/oliverlalan/Doto)
* [Geist and Geist Mono](https://github.com/vercel/geist-font)

Weather data comes from [Open-Meteo](https://open-meteo.com) (CC BY 4.0).
