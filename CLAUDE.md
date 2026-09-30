# CLAUDE.md

Guidance for Claude Code in this repository. The app, its features and the build are described
in [README.md](README.md); this file covers how to work on it.

## Status

- Android app "Positioning Info" (`io.github.leostumpf.positioninginfo`), version 1.1.0, not yet
  published. Preparing for Google Play: the privacy policy is [PRIVACY.md](PRIVACY.md), and the
  Play Console answers are in [docs/play-console.md](docs/play-console.md).
- Installed only on the maintainer's phone. Formats of stored files may change without a
  migration until the first store release. After that, every change to `TripCsv` or `TtffEntry`
  needs a new format header and a migration.
- Store listing: texts in [docs/store-listing.md](docs/store-listing.md), screenshots in
  `docs/store/`, and the step-by-step upload in [docs/play-upload-guide.md](docs/play-upload-guide.md).
- Open work: move UI texts into string resources for localisation (only the notification's are
  resources so far).

## Workflow rules

- Commit and push straight to `main` after every finished task. No branches, no pull requests,
  and no need to ask first.
- Every commit runs `.githooks/pre-commit`, which runs `./gradlew detekt lintRelease`. Fix every
  finding. Never add a baseline, a blanket suppression or `--no-verify`. A local `@Suppress` with
  a comment giving the reason is fine where a rule really does not fit.
- Before committing code changes, run the unit tests: `./gradlew testDebugUnitTest`.
- UI tests (`connectedDebugAndroidTest`) run **only on the emulator**, never on the maintainer's
  phone (`09291JEC226042`, usually connected). Set `ANDROID_SERIAL=emulator-5554` for the run.
  Start the emulator with
  `~/Android/Sdk/emulator/emulator -avd zc-shot-phone -no-window -no-audio -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect`
  as a background task.
- Do not run `assembleRelease` casually: it copies the signed APK into `releases/`, a shared
  folder, and can overwrite files there. CI builds the release artifacts.
- Commit messages: a short subject, then a body saying what changed and why.

## Code conventions

- Layers: `data/` wraps Android sources as flows. `domain/` is plain, immutable Kotlin (events
  return new instances) and holds the logic and the unit tests. `ui/` holds one package per
  page: a `…UiState` built from the domain and a screen that only draws it.
- Domain code returns values or enums, never display sentences; the `ui` layer words them. Never
  parse display text back into data.
- `PositioningInfoViewModel` owns the single GNSS session and delegates to small classes
  (`SpeedSession`, `NetworkSession`, `FirstFixSession`, `AnalysisSession` →
  `ReceiverAnalysis`, `SkyAnalysis`, `AccuracyTest`, `TripRecorder`). Pages collect only their
  own state. Nothing is published while the app is off screen.
- Compose: `modifier` is the first optional parameter. List sections are `LazyListScope`
  extension functions. Canvas drawing goes in named `DrawScope` functions. Colours come from
  `Palette`, text styles from `ui/theme`.
- Detekt limits apply: at most 60 lines and complexity 14 per function, and 120 characters per
  line. Split code into named steps rather than raising the limits. Numbers in logic get named
  constants; layout sizes inside composables may stay literal.
- Every public class and function has a KDoc saying what it is for, in plain British English:
  units, what null means, and any threading or side effects. Comments explain why, not what.
- SDK checks: helpers that guard API levels carry `@ChecksSdkIntAtLeast`, so lint can see the
  check. minSdk is 26, target 36, compile 37.
- Every Kotlin file starts with `// SPDX-License-Identifier: AGPL-3.0-or-later`.
- No `INTERNET` permission, ever. No analytics, no crash reporting, no backups; data stays on the
  phone and can be cleared from *Data on this phone*.

## Store screenshots (demo mode)

An emulator's GNSS reports only a few satellites with no positions. For screenshots, debug builds
have a demo mode: `app/src/debug/.../demo/ScriptedDemoSource.kt` scripts a drive south of Munich
under a multi-constellation, dual-frequency sky. It is loaded by name through `data/DemoMode.kt`,
so release builds contain neither the class nor a way to switch it on.

1. Use the AVDs `zc-shot-phone` (1080×1920), `shot-tablet7` (1200×1920) and `shot-tablet10`
   (2560×1600). Run at most two at a time, because memory is tight.
2. Install the debug APK. Put the status bar in demo mode:
   `adb shell settings put global sysui_demo_allowed 1`, then the `com.android.systemui.demo`
   broadcasts for clock 10:42, full battery and no notifications.
3. Launch with `adb shell am start -n io.github.leostumpf.positioninginfo.debug/io.github.leostumpf.positioninginfo.MainActivity --ez demo true`.
4. Start a trip recording, then wait about 6 minutes, so the plot, the elevation profile and the
   sky paths fill in. The sky runs five times faster than the clock.
5. Capture the Speed, Trip, GNSS & sky (top and sky plot) and Signal pages with
   `adb exec-out screencap -p`.

## Commands

```sh
./gradlew testDebugUnitTest            # unit tests
./gradlew detekt lintRelease           # what the pre-commit hook runs
./gradlew detekt -PdetektAutoCorrect   # auto-fix formatting findings
./gradlew assembleDebug                # debug APK (.debug suffix, installs beside the release)
./gradlew -q printVersion              # versionName and versionCode
```

Before running autocorrect, make sure no hand-written line is over 120 characters. Otherwise
ktlint wraps it badly.
