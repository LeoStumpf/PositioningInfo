# Positioning Info

**What your phone's GNSS receiver and sensors know — and what they don't.** Ad-free, open source.

Everything your phone's GNSS receiver knows, on eight pages: a glanceable speedometer, trip
recording, coordinates and altitude, satellite status and a sky plot, accuracy and geometry,
receiver internals (interference, clock, NMEA), Wi-Fi and mobile-network positioning, and an About page. No ads, no tracking,
no account, and no network access at all — every figure is measured or computed on the phone.

Started as a speedometer called "GPS Tools", because every speedometer on the Play Store had
been buried under advertising. It was renamed once it had become more than that — and because
"GPS Tools" was already taken, including as a registered mark.

> **Transparency:** Positioning Info was "vibe-coded" with Claude (Anthropic's AI model), which wrote
> the code under human direction. See [Who wrote this](#who-wrote-this).

App ID: `io.github.leostumpf.positioninginfo`

## Who wrote this

**Claude wrote it** — Anthropic's model (Claude Opus 5.5, working in Claude Code), in
conversation over a few days in September 2026. All of it: about 19 400 lines of Kotlin
(15 600 in the app, 3 700 in tests), the in-app glossary texts, this README and the commit messages. The
visual design ("Instrument Black") was drafted by Claude as well. Some of the maths and parsing
modules were written by Claude sub-agents working in parallel and then merged. Every commit
carries `Co-Authored-By: Claude Opus 5.5`.

Leo Stumpf set the problem and made every decision that needed a person: what the app should
show, that it never gets internet access, that background operation is opt-in and explained,
what to reorganise, and what to ship. He ran every build on a real phone and he answers for the
result, which is why the git author line is his. **He does not claim to have written the
code.** That is the reason this section exists — it is here to inform, not to excuse or limit
anything; using a model to build software is neither novel nor a problem.

What stands behind the code: 289 unit tests and UI tests that walk the app on an emulator; detekt
and Android lint with no findings, enforced before every commit; reference values checked independently rather
than recalled (UTM against a separate implementation, GPS navigation-message fields against
IS-GPS-200); and every feature exercised on a Pixel 4a. What does *not* stand behind it is a
line-by-line human review, and some paths could not be tried on real hardware — the jamming
and spoofing indicators have never met a jammer, and that phone does not pass navigation
messages on. Judge it on the code and on whether the readings hold up.

## Current features

Swipe between eight screens. Each detail screen has a collapsible glossary of the terms it uses.

**Speedometer**

- Live speed straight from the GNSS receiver, in km/h (default), mph or knots — chosen under the
  gear button, so a stray touch never changes it
- The receiver's own speed uncertainty under the reading, and SIMULATED when a mock-location app
  is at work
- Session maximum and time-weighted average, a speed plot since the last reset, and a reset button
- Honest status line: fix state, horizontal accuracy, satellites used / visible
- Background mode button: keep running with the screen off, shown by a notification

**Trip**

- Records a track, saved on the phone point by point, so it survives the app being closed. The
  screen stays on while recording; with background mode on it also records with the screen off
- Distance, moving time, maximum and moving-average speed, and an elevation profile
- Ascent and descent from the barometer when the phone has one (else GNSS height), with a
  hysteresis against altitude noise; a switch of height source is never counted as a climb
- Exports GPX to a file you choose

**Position**

- Coordinates as decimal, DMS, UTM, MGRS, Plus Code and Maidenhead locator
- Height above sea level (from the chip's geoid model), above the ellipsoid, and the geoid
  height in between
- Barometric altitude calibrated against GNSS, derived sea-level pressure, vertical speed
- Accuracy test: leave the phone still and see the real spread (CEP50, CEP95, 2DRMS) against
  the accuracy the receiver claims

**GNSS & sky**

- **"Why no fix?"** — walks the chain a fix depends on (location on, real receiver, battery saver, data for
  assistance, satellites heard, usable signals, orbits, geometry, search time) and names the first link that fails
- Each satellite's acquisition stage (code lock → bit sync → frame sync → time decoded), with a detail sheet
  that also translates its raw measurement: signal code, strength at the chip and the loss before it,
  time uncertainty as metres of range, carrier-phase state and inter-signal bias
- The last 30 minutes as graphs (satellites in fix, signal, accuracy) and a log of recent times to first fix
- Whether the receiver has a fix right now, and separately how fast its next start would be (hot, warm or cold)
- How many satellites the phone holds almanac and ephemeris data for
- Per-constellation breakdown across GPS, GLONASS, Galileo, BeiDou, QZSS, NavIC and SBAS
- Every visible satellite with its signal strength and orbital-data flags
- How long this session's first fix actually took, to check the hot / warm / cold verdict
- How far the phone's clock is from GNSS time and from network time, and how good the network's time is
- The phone settings around positioning, read-only: precise or approximate access, Wi-Fi and cell
  positioning, Wi-Fi and Bluetooth scanning, battery saver, automatic time and time zone
- Cold start (clear aiding data) and A-GNSS download, to watch the difference assistance makes
- Sky plot of every satellite, right above the satellite list, with its path so far and a
  15-minute projection, estimated offline from its recent motion
- Satellites about to set, and which appeared or were lost
- Compass mode that turns the plot with the phone, corrected for magnetic declination
- Compass trust: measured magnetic field against the World Magnetic Model, to catch a disturbed compass
- Signal map: signal strength by direction over time, showing where buildings block the sky

**Signals and accuracy**

- Every accuracy Android reports — horizontal, vertical, speed, direction of travel, the fix's
  timestamp — and the actual fix rate
- Measured horizontal accuracy of the current fix, kept distinct from the estimate below it
- Expected resolution for the technique actually in use, and why
- Which frequency bands are in use, and whether the phone is running dual-frequency
- Which augmentation system (EGNOS, WAAS, MSAS, GAGAN, …) is overhead, and whether its
  corrections are actually being applied
- Satellite geometry (PDOP, HDOP, VDOP, TDOP), computed and as the chip reports it
- Which assistance services and chip features the receiver reports (antenna corrections; from
  Android 14 also satellite positions, blocklist, low power, geofencing, path corrections, …), plus its
  chipset and generation

**Receiver internals**

- Jamming and spoofing indicators from the receiver's gain control and signal strengths
- Receiver clock: oscillator frequency error, discontinuities, leap seconds
- The chip's NMEA output: fix type, DOP, error ellipse, sentence statistics
- GPS navigation messages decoded where the chip passes them on: health, week, leap-second
  announcements, ionosphere model, almanac pages

**Wi-Fi & mobile network**

- GNSS, network and Android's fused position side by side, with each one's distance from the GNSS fix
- Position from Wi-Fi and cell towers, its claimed accuracy and its real error against GNSS
- Every location provider the phone offers apps (gps, network, fused, passive, vendor ones) with what
  each declares: accuracy and power class, what it needs, what it reports
- The raw inputs: cells (with timing-advance distance) and Wi-Fi access points in range

**About**

- What the app is, its licence (AGPL-3.0), a link to the source and the open-source licences
- Data on this phone: everything the app keeps, cleared with one button

The interface ("Instrument Black") is monochrome on true black, with colour reserved for
state and constellations, set in the fonts every Android phone already has.

## How it works

Speed comes from the platform `LocationManager` GPS provider, which reports the receiver's
own Doppler-derived velocity. The app deliberately avoids Google Play Services' fused
location provider: it is proprietary, blends in non-GNSS sources, and cannot supply the
satellite-level detail and raw measurements this app shows.

### Almanac and ephemeris

The GNSS status screen answers the question "why is my fix taking so long?".

A receiver needs two kinds of orbital data. The **almanac** is coarse, covers the whole
constellation, is broadcast by every satellite and stays valid for weeks — without it the
receiver has no idea which satellites are overhead and must search blindly. The
**ephemeris** is precise, describes a single satellite, and expires after a few hours; no
fix can be computed without one.

That gives the three classic states, which the screen names directly: **hot** (ephemeris
for four or more satellites, so a fix takes seconds), **warm** (orbital data held but not
enough of it, roughly half a minute), and **cold** (almost nothing, and a full almanac
download takes up to 12 minutes).

Not every phone publishes both flags — many report the almanac faithfully but never the
ephemeris. A device claiming zero ephemeris while it is demonstrably computing a fix is
contradicting itself, so the app withholds just that one figure and says why, rather than
blaming the sky for a driver limitation or hiding the almanac data that *is* valid.

For the same reason, a receiver that is actively using four or more satellites is reported
as ready on the strength of what it is doing, not what its flags claim.

### Resolution

Two numbers appear on the signals screen and they are deliberately not the same thing. The
first is the horizontal accuracy the receiver reports for the fix you actually have. The
second is what the technique currently in play typically achieves under an open sky —
single constellation, multi-constellation, augmented, dual frequency. Indoors the first is
routinely ten times worse than the second, which is the point: an estimate of what the
hardware is capable of is not a measurement of what it just did.

Dual frequency is the single biggest lever. A receiver hearing the same satellite on L1 and
L5 can measure the ionospheric delay and cancel it, instead of estimating it from a
broadcast model — which is otherwise the largest remaining source of error. The bands are
identified from the carrier frequency each satellite reports, so this reflects what is
being received right now rather than what the spec sheet claims.

### Details that matter

Three things the app does that cheap speedometers usually do not:

- **It never invents a speed.** If the receiver reports no velocity, the display shows `--`
  rather than differentiating consecutive positions, which is what makes other apps jitter.
- **It respects the noise floor.** A stationary receiver still reports a few tenths of a
  metre per second; anything below the receiver's own stated accuracy reads as zero.
- **It admits when the fix is stale.** Fix age is tracked on the monotonic clock; a reading
  older than three seconds dims, and one older than ten seconds is withdrawn entirely.

Location is used while the app is on screen, and the receiver is released the moment you
leave — unless you switch **background mode** on. That runs a location foreground service with
a permanent notification and a Stop button, so trips and measurements continue with the screen
off. It is off by default, explained before it is enabled, and ends when the app is swiped away.
The app never asks for "allow all the time" location access.

Everything stays on the phone — Android's cloud backup and device-to-device transfer are
switched off for this app. Stored there: a recorded trip, the last 20 times to first fix, and the
speed unit. Everything else — history, sky paths, signal map, accuracy test, calibrations — lives in
memory only. **Settings › Data on this phone** (gear on the speed page, and on the About page) lists
it all and clears it with one button.

The full [privacy policy](PRIVACY.md) is linked from the About page and the store listing.

Permissions beyond location: `ACCESS_WIFI_STATE` and `CHANGE_WIFI_STATE` (list access points and
ask for a fresh Wi-Fi scan), `ACCESS_NETWORK_STATE`
(tell whether a data connection exists for the system's A-GNSS download — read-only, no network
use), `ACCESS_LOCATION_EXTRA_COMMANDS` (cold start / A-GNSS request), and the foreground-service and
notification permissions for opt-in background mode. There is no `INTERNET` permission.

## Code

Kotlin with Jetpack Compose, in one Gradle module (`app`) plus `baselineprofile`, which never
ships. Under `app/src/main/java/io/github/leostumpf/positioninginfo/`:

- `data/` — one class per Android source (location, GNSS status, raw measurements and navigation
  messages, NMEA, sensors, cells, Wi-Fi, system settings) turning platform callbacks into Kotlin
  flows, and the two files the app writes (`TripStore`, `TtffLogStore`).
- `domain/` — the GNSS knowledge as plain, immutable Kotlin with no Android UI: almanac status,
  "why no fix?", DOP, coordinate formats, NMEA and GPS navigation-message decoding, interference
  checks, trip statistics, sky tracking. Almost all of the unit tests are here.
- `ui/` — `PositioningInfoViewModel` owns the one GNSS session and hands the pages' parts to
  small classes (`SpeedSession`, `NetworkSession`, `FirstFixSession`, `AnalysisSession` with
  `ReceiverAnalysis`, `SkyAnalysis`, `AccuracyTest` and `TripRecorder`). One package per page, each
  with a `…UiState` built from the domain and a screen that only draws it; shared components in
  `ui/common`, colours and type in `ui/theme`.
- `background/` — the opt-in foreground service; `settings/` — the stored speed unit.

Domain code returns values, not sentences; the pages word them. English only for now: the texts
are in the code, not yet in string resources (except the notification's).

## Building

Requires JDK 17+ and the Android SDK (compileSdk 37; targetSdk 36, the level Google Play requires).

```sh
./gradlew :app:assembleDebug     # build
./gradlew :app:test              # unit tests
./gradlew :app:connectedDebugAndroidTest  # UI tests on a connected device or emulator
./gradlew detekt lintRelease     # the linters, as CI and the pre-commit hook run them
./gradlew detekt -PdetektAutoCorrect  # let detekt fix formatting findings itself
./gradlew :app:installDebug      # install to a connected device
./gradlew :app:bundleRelease     # the bundle (AAB) for Google Play
```

**Linting:** detekt (Kotlin style via ktlint, complexity, naming, documentation and Compose
conventions; `config/detekt/detekt.yml`, `.editorconfig`) and Android lint. A pre-commit hook in
`.githooks/` runs both and refuses the commit while they report anything; the first Gradle build
installs it (`git config core.hooksPath .githooks`). Findings are fixed, not baselined.

**Baseline profile:** `app/src/release/generated/baselineProfiles/baseline-prof.txt` tells Android
which code to compile ahead of time (startup and the first page swipes), so the first launches are
not interpreted. It is generated on an emulator or device (API 28+) and committed; regenerate it
after larger changes with `ANDROID_SERIAL=<device> ./gradlew :app:generateBaselineProfile`
(module `baselineprofile`, which never ships).

The release app ID is `io.github.leostumpf.positioninginfo`; debug builds add `.debug`, so both can
be installed side by side.

**Versions:** `versionCode` is the commit time of `HEAD` in seconds since 2026-01-01 UTC, so every
newer commit installs over the previous build, on the phone and on Google Play alike, and a rebase
never lowers it (Play refuses any upload not above the last one). `versionName` is set by hand as
`appVersionName` in `gradle.properties` and raised before each store release. `./gradlew -q
printVersion` shows both. (Builds up to `1.0.35` were numbered by commit count.)

**CI** (`.github/workflows/ci.yml`) runs the unit tests and lint, the UI tests on an emulator (a
separate job), and builds a signed release on every
push to `main`: the APK, the bundle (AAB) for Google Play and R8's mapping file are kept as a workflow
artifact for 30 days (Actions → CI → latest run → Artifacts). Each signed build of `main` is tagged
`build-<versionCode>`, so every distributed build maps to its exact source.
It is signed with the upload key from three repository secrets — `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_PASSWORD` — which forks never see.

**Local release folder:** if `releases/` exists (gitignored — on the maintainer's laptop a link to a
local share), `./gradlew assembleRelease` also copies the signed APK there as
`positioning-info-<versionName>-<versionCode>.apk`.

Release builds read signing credentials from a `keystore.properties` at the repo root
(gitignored) or from the `POSITIONINGINFO_STORE_FILE`, `POSITIONINGINFO_STORE_PASSWORD`,
`POSITIONINGINFO_KEY_ALIAS` and `POSITIONINGINFO_KEY_PASSWORD` environment variables.

## Licence

Copyright © 2026 Leo Stumpf

This program is free software: you can redistribute it and/or modify it under the terms of
the GNU Affero General Public License as published by the Free Software Foundation, either
version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the [GNU Affero General Public License](LICENSE) for more details.

The code, the app icon and the store graphics are all covered by the AGPL. Only the name
"Positioning Info" is reserved: forks are welcome, but please give them a different name.

The app bundles third-party open-source libraries, all under permissive licences (Apache-2.0,
BSD-3-Clause) compatible with the AGPL; see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
The same list, with the full licence texts, is in the app under **About → Open-source licences**.
