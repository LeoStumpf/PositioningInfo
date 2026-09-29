# GPS Tools

Everything your phone's GNSS receiver knows, on eight pages: a glanceable speedometer, trip
recording, coordinates and altitude, satellite status and a sky plot, accuracy and geometry,
receiver internals (interference, clock, NMEA), and network location. No ads, no tracking,
no account, and no network access at all — every figure is measured or computed on the phone.

Started as a speedometer, because every speedometer on the Play Store had been buried under
advertising.

> **Transparency:** GPS Tools was "vibe-coded" with Claude (Anthropic's AI model), which wrote
> the code under human direction. See [Who wrote this](#who-wrote-this).

App ID: `io.github.leostumpf.gpstools`

## Who wrote this

**Claude wrote it** — Anthropic's model (Claude Opus 5.5, working in Claude Code), in
conversation over a few days in September 2026. All of it: about 11 700 lines of Kotlin
including the tests, the in-app glossary texts, this README and the commit messages. The
visual design ("Instrument Black") was drafted by Claude as well. Some of the maths and parsing
modules were written by Claude sub-agents working in parallel and then merged. Every commit
carries `Co-Authored-By: Claude Opus 5.5`.

Leo Stumpf set the problem and made every decision that needed a person: what the app should
show, that it never gets internet access, that background operation is opt-in and explained,
what to reorganise, and what to ship. He ran every build on a real phone and he answers for the
result, which is why the git author line is his. **He does not claim to have written the
code.** That is the reason this section exists — it is here to inform, not to excuse or limit
anything; using a model to build software is neither novel nor a problem.

What stands behind the code: 202 unit tests; reference values checked independently rather
than recalled (UTM against a separate implementation, GPS navigation-message fields against
IS-GPS-200); and every feature exercised on a Pixel 4a. What does *not* stand behind it is a
line-by-line human review, and some paths could not be tried on real hardware — the jamming
and spoofing indicators have never met a jammer, and that phone does not pass navigation
messages on. Judge it on the code and on whether the readings hold up.

The IBM Plex typefaces are by IBM, bundled under the SIL Open Font License 1.1.

## Current features

Swipe between eight screens. Each detail screen has a collapsible glossary of the terms it uses.

**Speedometer**

- Live speed straight from the GNSS receiver, in km/h, mph or knots (tap the unit to cycle)
- Session maximum and time-weighted average, with a reset button
- Background mode button: keep running with the screen off, shown by a notification
- Honest status line: fix state, horizontal accuracy, satellites used / visible

**Trip**

- Records a track while the app is open (the screen stays on), saved on the phone as it goes
- Distance, moving time, maximum and moving-average speed, ascent and descent (barometric
  when the phone has a barometer)
- Exports GPX to a file you choose

**Position**

- Coordinates as decimal, DMS, UTM, MGRS, Plus Code and Maidenhead locator
- Height above sea level (from the chip's geoid model), above the ellipsoid, and the geoid
  height in between
- Barometric altitude calibrated against GNSS, derived sea-level pressure, vertical speed
- Accuracy test: leave the phone still and see the real spread (CEP50, CEP95, 2DRMS) against
  the accuracy the receiver claims

**GNSS status**

- Whether the receiver is ready to fix, and why: hot, warm or cold start
- How many satellites the phone holds almanac and ephemeris data for
- Per-constellation breakdown across GPS, GLONASS, Galileo, BeiDou, QZSS, NavIC and SBAS
- Every visible satellite with its signal strength and orbital-data flags
- How long this session's first fix actually took, to check the hot / warm / cold verdict
- How far the phone's clock is from GNSS time
- Cold start (clear aiding data) and A-GNSS download, to watch the difference assistance makes

**Sky**

- Sky plot of every satellite with its path so far and a 15-minute projection, estimated
  offline from its recent motion
- Satellites about to set, and which appeared or were lost
- Compass mode that turns the plot with the phone, corrected for magnetic declination
- Signal map: signal strength by direction over time, showing where buildings block the sky

**Signals and accuracy**

- Measured horizontal accuracy of the current fix, kept distinct from the estimate below it
- Expected resolution for the technique actually in use, and why
- Which frequency bands are in use, and whether the phone is running dual-frequency
- Which augmentation system (EGNOS, WAAS, MSAS, GAGAN, …) is overhead, and whether its
  corrections are actually being applied
- Satellite geometry (PDOP, HDOP, VDOP, TDOP), computed and as the chip reports it
- Which assistance services the receiver reports, plus its chipset and generation

**Receiver internals**

- Jamming and spoofing indicators from the receiver's gain control and signal strengths
- Receiver clock: oscillator frequency error, discontinuities, leap seconds
- The chip's NMEA output: fix type, DOP, error ellipse, sentence statistics
- GPS navigation messages decoded where the chip passes them on: health, week, leap-second
  announcements, ionosphere model, almanac pages

**Network location**

- Position from Wi-Fi and cell towers, its claimed accuracy and its real error against GNSS
- The raw inputs: cells (with timing-advance distance) and Wi-Fi access points in range
- About: what the app is, its licence (AGPL-3.0) and a link to the source

The interface ("Instrument Black") is monochrome on true black, with colour reserved for
state and constellations, set in IBM Plex (bundled under the SIL Open Font License).

## How it works

Speed comes from the platform `LocationManager` GPS provider, which reports the receiver's
own Doppler-derived velocity. The app deliberately avoids Google Play Services' fused
location provider: it is proprietary, blends in non-GNSS sources, and cannot supply the
satellite-level detail this app is built to grow into.

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

## Building

Requires JDK 17+ and the Android SDK (compileSdk 36).

```sh
./gradlew :app:assembleDebug     # build
./gradlew :app:test              # unit tests
./gradlew :app:installDebug      # install to a connected device
```

The release app ID is `io.github.leostumpf.gpstools`; debug builds add `.debug`, so both can
be installed side by side.

Release builds read signing credentials from a `keystore.properties` at the repo root
(gitignored) or from the `GPSTOOLS_STORE_FILE`, `GPSTOOLS_STORE_PASSWORD`,
`GPSTOOLS_KEY_ALIAS` and `GPSTOOLS_KEY_PASSWORD` environment variables.

## Licence

Copyright © 2026 Leo Stumpf

This program is free software: you can redistribute it and/or modify it under the terms of
the GNU Affero General Public License as published by the Free Software Foundation, either
version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the [GNU Affero General Public License](LICENSE) for more details.

The name "GPS Tools" and the app icon are not covered by the AGPL.
