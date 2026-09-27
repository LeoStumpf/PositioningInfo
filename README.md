# GPS Tools

A minimal GPS speedometer for Android. Open it, see your speed. No ads, no tracking,
no account, no network access at all.

Built because every speedometer on the Play Store had been buried under advertising.

## Current features

Swipe between three screens.

**Speedometer**

- Live speed straight from the GNSS receiver, in km/h, mph or knots (tap the unit to cycle)
- Session maximum and time-weighted average
- Honest status line: fix state, horizontal accuracy, satellites used / visible

**GNSS status**

- Whether the receiver is ready to fix, and why: hot, warm or cold start
- How many satellites the phone holds almanac and ephemeris data for
- Per-constellation breakdown across GPS, GLONASS, Galileo, BeiDou, QZSS, NavIC and SBAS
- Every visible satellite with its signal strength and orbital-data flags

**Signals and accuracy**

- Measured horizontal accuracy of the current fix, kept distinct from the estimate below it
- Expected resolution for the technique actually in use, and why
- Which frequency bands are in use, and whether the phone is running dual-frequency
- Which augmentation system (EGNOS, WAAS, MSAS, GAGAN, …) is overhead, and whether its
  corrections are actually being applied
- Which assistance services the receiver reports, plus its chipset and generation

## Planned

The name is deliberate — the speedometer was the first tool, not the only one. Still to
come: raw `GnssMeasurement` statistics, a sky plot, and trip recording.

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

Location is used only while the app is in the foreground. There is no background location
permission and no foreground service — the receiver is released the moment you leave.

## Building

Requires JDK 17+ and the Android SDK (compileSdk 36).

```sh
./gradlew :app:assembleDebug     # build
./gradlew :app:test              # unit tests
./gradlew :app:installDebug      # install to a connected device
```

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
