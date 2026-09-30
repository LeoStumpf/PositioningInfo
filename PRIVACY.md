# Privacy policy — Positioning Info

*Effective 30 September 2026. Applies to the Android app Positioning Info
(`io.github.leostumpf.positioninginfo`), published by Leo Stumpf.*

**In short: Positioning Info collects nothing. It has no internet access, so nothing it measures can
leave your phone through the app. What it keeps stays in the app's private storage on your phone,
and you can delete all of it at any time.**

## No collection, no sharing

The app has no `INTERNET` permission. It contains no analytics, advertising, crash-reporting or
tracking code, and no account. The developer receives no data of any kind from the app: not your
location, not usage statistics, not crash reports, not device identifiers. Nothing is sold or
shared with anyone.

## What the app reads on your phone, and why

| Access | Used for |
|---|---|
| **Precise location** (GNSS receiver, and Android's network and fused location) | Everything the app shows: speed, position, satellites, accuracy, and the comparison of GNSS with network positioning. Only while the app is on screen — or while you have switched on background mode, see below. |
| **Nearby Wi-Fi networks and mobile cells** (names, hardware addresses, signal strength) | Shown on the Wi-Fi & cell page as the inputs of network positioning. Displayed only, never stored. |
| **Sensors** (air pressure, magnetic field, orientation) | Barometric altitude, the compass check and the compass mode of the sky plot. Not stored. |
| **Network state** (whether a data connection exists) | To tell whether Android can download satellite assistance data. The app itself uses no network. |
| **Location extra commands** | The cold-start and assistance-download buttons on the GNSS page. |
| **Notifications** | The notification shown while background mode runs. |

The app never asks for "allow all the time" (background) location access.

**Background mode** is off by default. It runs only after you switch it on and have been told what
it does. While it runs, a permanent notification with a Stop button is shown, and the app keeps
using your location with the screen off. Swiping the app away or pressing Stop ends it.

## What is stored on your phone

Only in the app's private storage, where other apps cannot read it:

- **A recorded trip**, if you record one: for each point the time, coordinates, altitude, speed and
  accuracy.
- **The last 20 times to first fix**: date, duration and start type. No coordinates.
- **Your speed unit** setting.

Everything else — history graphs, sky paths, the signal map, the accuracy test, calibrations — is
held in memory only and gone when the app closes.

Android's cloud backup and device-to-device transfer are switched off for this app, so this data is
not copied to Google's backup or to another phone.

**Deleting it:** *Settings › Data on this phone* (gear button on the speed page, or on the About
page) lists everything the app keeps and deletes it with one button. Uninstalling the app also
deletes all of it.

## When data leaves the app — only because you ask

- **GPX export:** a trip is written to a file only when you export it, to a place you choose. What
  happens to that file afterwards is up to you.
- **Copy:** coordinates or values you copy go to the clipboard.
- **Links** on the About page open in your browser.

## What Android does on its own

Some features rely on services built into Android, which run under your phone's own settings and
privacy policies, not this app's:

- **Network and fused location** are Android's location services (on most phones Google's). When
  location accuracy / Wi-Fi and cell positioning are on in your phone's settings, those services may
  send Wi-Fi and cell data to their provider to compute a position. The app only reads the result.
- **Satellite assistance data (A-GNSS):** the assistance-download button asks Android to fetch
  satellite orbit data from its assistance server. Android makes that download, not the app.

## Children

The app is not directed at children and collects no data from anyone.

## Changes

Changes to this policy are published in this file; its history is in the
[source repository](https://github.com/LeoStumpf/PositioningInfo/commits/main/PRIVACY.md).

## Contact

Questions about this policy: open an issue at
<https://github.com/LeoStumpf/PositioningInfo/issues>.
