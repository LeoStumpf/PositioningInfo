# Store listing — Positioning Info (English, en-US)

Text for **Grow users › Store presence › Main store listing**. Copy each block as it is; the limits
are Play's, and the counts are checked.

## App name (max 30)

```
Positioning Info
```

## Short description (max 80)

```
Speedometer, satellites and accuracy: what your phone's GNSS really knows.
```

## Full description (max 4000)

```
Positioning Info shows everything your phone's satellite receiver knows, and says plainly what it doesn't. No ads, no account, no tracking, and no internet access at all: every figure is measured or computed on your phone.

SPEED
• Live speed straight from the GNSS receiver, in km/h, mph or knots
• The receiver's own uncertainty under the reading; a stale reading dims and is then withdrawn instead of frozen
• Maximum, average and a speed plot since the last reset

TRIP
• Record a track: distance, moving time, average and maximum speed
• Ascent and descent from the barometer where the phone has one, with an elevation profile
• Export as GPX to a file you choose

POSITION
• Coordinates as decimal degrees, DMS, UTM, MGRS, Plus Code and Maidenhead locator
• Height above sea level and above the ellipsoid, barometric altitude and vertical speed
• Accuracy test: leave the phone still and see how far the position really wanders (CEP50, CEP95)

GNSS & SKY
• "Why no fix?" walks through everything a fix depends on and names what is missing
• Hot, warm or cold start: the orbital data the receiver holds, and how long the first fix took
• Every satellite of GPS, Galileo, GLONASS, BeiDou, QZSS, NavIC and SBAS, with signal strength and acquisition stage
• Sky plot with each satellite's path and where it is heading, a compass mode and a map of where buildings block the sky

SIGNAL & ACCURACY
• Every accuracy Android reports, and the fix rate
• Dual-frequency (L1/L5) detection, satellite geometry (DOP), and whether EGNOS, WAAS and other augmentation services are in use

RECEIVER INTERNALS
• Jamming and spoofing indicators from the receiver's gain control
• Receiver clock, NMEA output and decoded GPS navigation messages, where the phone passes them on

WI-FI & MOBILE NETWORK
• The network and fused positions next to GNSS, with their real error
• Cell towers and Wi-Fi access points in range

Each page explains its terms in a built-in glossary.

BACKGROUND MODE (OPTIONAL)
Normally the app uses location only while it is on screen. For a trip with the screen off you can switch on background mode: it shows a permanent notification with a Stop button and ends when you close the app. The app never asks for "allow all the time" location access.

PRIVACY
Nothing leaves your phone. The app keeps only a recorded trip, the last 20 first-fix times and your speed unit, and deletes all of it with one button under "Data on this phone".

OPEN SOURCE
Free software under the GNU AGPL v3. Source code: github.com/LeoStumpf/PositioningInfo

The app was written by Claude, Anthropic's AI model, under the developer's direction and testing; the README on GitHub explains how.

What your phone shows depends on its receiver: some phones report neither dual-frequency signals nor navigation messages, and the app says so where that is the case.
```

## Release notes for 1.1.0 (max 500)

```
First release on Google Play.
• Speed, trip recording with GPX export, coordinates in six formats and barometric altitude
• Satellite status, "Why no fix?", sky plot and signal map
• Accuracy, dual-frequency and augmentation, receiver internals, Wi-Fi and cell positioning
• Optional background mode for recording with the screen off
No ads, no tracking, no internet access.
```

## Graphics

| Asset | File | Size |
|---|---|---|
| App icon | `docs/brand/play-icon-512.png` | 512 × 512 PNG |
| Feature graphic | `docs/brand/play-feature-1024x500.png` | 1024 × 500 PNG |
| Phone screenshots | `docs/store/phone/01…06-*.png` | 1080 × 1920 (9:16) |
| 7-inch tablet screenshots | `docs/store/tablet7/01…06-*.png` | 1215 × 2160 (9:16) |
| 10-inch tablet screenshots | `docs/store/tablet10/01…06-*.png` | 2688 × 1512 (16:9) |

Upload the screenshots in file-name order: speed, sky plot, "Why no fix?", trip, signal &
accuracy, position. Play needs at least 2 phone screenshots; with 4 or more of at least 1080 px
at 16:9 or 9:16, the app is also eligible for promotion in Play's recommendations. That is why
the tablet shots are padded to exactly 9:16 or 16:9 with the app's own black background.

The screenshots show the real app on emulators fed with a scripted drive and sky (debug-only demo
mode, see `CLAUDE.md`), because an emulator's own GNSS reports no satellite positions. The tablet
launcher's taskbar is cropped off.
