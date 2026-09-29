# Third-party notices

Positioning Info's own code is released under the GNU Affero General Public License, version 3
or later (see [`LICENSE`](LICENSE)); that licence covers Positioning Info's code only. The app
is built on third-party open-source libraries, all of them permissive and compatible with the
AGPL. This file collects their attributions.

The same list, with the full licence texts, is inside the app: **About page (the last one) →
Open-source licences**. The licence texts ship in the APK under
`app/src/main/assets/licenses/`.

No proprietary library is included — no Google Play Services, Firebase, analytics, crash
reporting or advertising SDK. The app has no internet permission.

## Bundled libraries

| Project | Licence | Copyright |
|---|---|---|
| AndroidX / Jetpack — Compose (UI, foundation, animation, runtime, Material 3, Material ripple and icons), Activity, Lifecycle, SavedState, DataStore, Core, Collection, Annotation, Arch Core, Autofill, Concurrent, CustomView, Emoji2, Graphics, Interpolator, ProfileInstaller, Startup, Tracing, VersionedParcelable | Apache-2.0 | © The Android Open Source Project |
| Kotlin standard library and runtime | Apache-2.0 | © JetBrains s.r.o. and Kotlin Programming Language contributors |
| kotlinx.coroutines | Apache-2.0 | © JetBrains s.r.o. and contributors |
| kotlinx.serialization | Apache-2.0 | © JetBrains s.r.o. and contributors |
| JetBrains Java Annotations | Apache-2.0 | © JetBrains s.r.o. |
| Okio | Apache-2.0 | © Square, Inc. |
| Guava ListenableFuture | Apache-2.0 | © The Guava Authors |
| JSpecify | Apache-2.0 | © The JSpecify Authors |
| Protocol Buffers (repackaged inside AndroidX DataStore) | BSD-3-Clause | © Google Inc. |

Full texts: [Apache License 2.0](app/src/main/assets/licenses/Apache-2.0.txt),
[BSD-3-Clause (Protocol Buffers)](app/src/main/assets/licenses/protobuf-BSD-3-Clause.txt).

## Not shipped in the app

Build and test tools are not part of the APK: Gradle, the Android Gradle Plugin and the Kotlin
compiler (Apache-2.0), JUnit 4 (EPL-1.0) and kotlinx-coroutines-test (Apache-2.0) for the unit
tests, and the GitHub Actions used by CI.

The app uses Android's own system services (location providers, sensors, telephony, Wi-Fi) and
the fonts every Android phone ships with; none of these are bundled.

## Every bundled component

The exact list, as resolved for the release build (`./gradlew :app:dependencies
--configuration releaseRuntimeClasspath`), with the licence each one declares in its published
metadata:

| Component | Licence |
|---|---|
| `androidx.activity:activity-compose:1.10.1` | Apache-2.0 |
| `androidx.activity:activity-ktx:1.10.1` | Apache-2.0 |
| `androidx.activity:activity:1.10.1` | Apache-2.0 |
| `androidx.annotation:annotation-experimental:1.4.1` | Apache-2.0 |
| `androidx.annotation:annotation-jvm:1.9.1` | Apache-2.0 |
| `androidx.annotation:annotation:1.9.1` | Apache-2.0 |
| `androidx.arch.core:core-common:2.2.0` | Apache-2.0 |
| `androidx.arch.core:core-runtime:2.2.0` | Apache-2.0 |
| `androidx.autofill:autofill:1.0.0` | Apache-2.0 |
| `androidx.collection:collection-jvm:1.5.0` | Apache-2.0 |
| `androidx.collection:collection-ktx:1.5.0` | Apache-2.0 |
| `androidx.collection:collection:1.5.0` | Apache-2.0 |
| `androidx.compose.animation:animation-android:1.9.1` | Apache-2.0 |
| `androidx.compose.animation:animation-core-android:1.9.1` | Apache-2.0 |
| `androidx.compose.animation:animation-core:1.9.1` | Apache-2.0 |
| `androidx.compose.animation:animation:1.9.1` | Apache-2.0 |
| `androidx.compose.foundation:foundation-android:1.9.1` | Apache-2.0 |
| `androidx.compose.foundation:foundation-layout-android:1.9.1` | Apache-2.0 |
| `androidx.compose.foundation:foundation-layout:1.9.1` | Apache-2.0 |
| `androidx.compose.foundation:foundation:1.9.1` | Apache-2.0 |
| `androidx.compose.material3:material3-android:1.3.2` | Apache-2.0 |
| `androidx.compose.material3:material3:1.3.2` | Apache-2.0 |
| `androidx.compose.material:material-icons-core-android:1.7.8` | Apache-2.0 |
| `androidx.compose.material:material-icons-core:1.7.8` | Apache-2.0 |
| `androidx.compose.material:material-ripple-android:1.9.1` | Apache-2.0 |
| `androidx.compose.material:material-ripple:1.9.1` | Apache-2.0 |
| `androidx.compose.runtime:runtime-android:1.9.1` | Apache-2.0 |
| `androidx.compose.runtime:runtime-annotation-android:1.9.1` | Apache-2.0 |
| `androidx.compose.runtime:runtime-annotation:1.9.1` | Apache-2.0 |
| `androidx.compose.runtime:runtime-saveable-android:1.9.1` | Apache-2.0 |
| `androidx.compose.runtime:runtime-saveable:1.9.1` | Apache-2.0 |
| `androidx.compose.runtime:runtime:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-android:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-geometry-android:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-geometry:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-graphics-android:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-graphics:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-text-android:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-text:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-tooling-preview-android:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-tooling-preview:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-unit-android:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-unit:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-util-android:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui-util:1.9.1` | Apache-2.0 |
| `androidx.compose.ui:ui:1.9.1` | Apache-2.0 |
| `androidx.compose:compose-bom:2025.09.00` | Apache-2.0 |
| `androidx.concurrent:concurrent-futures:1.1.0` | Apache-2.0 |
| `androidx.core:core-ktx:1.17.0` | Apache-2.0 |
| `androidx.core:core-viewtree:1.0.0` | Apache-2.0 |
| `androidx.core:core:1.17.0` | Apache-2.0 |
| `androidx.customview:customview-poolingcontainer:1.0.0` | Apache-2.0 |
| `androidx.datastore:datastore-android:1.1.7` | Apache-2.0 |
| `androidx.datastore:datastore-core-android:1.1.7` | Apache-2.0 |
| `androidx.datastore:datastore-core-okio-jvm:1.1.7` | Apache-2.0 |
| `androidx.datastore:datastore-core-okio:1.1.7` | Apache-2.0 |
| `androidx.datastore:datastore-core:1.1.7` | Apache-2.0 |
| `androidx.datastore:datastore-preferences-android:1.1.7` | Apache-2.0 |
| `androidx.datastore:datastore-preferences-core-android:1.1.7` | Apache-2.0 |
| `androidx.datastore:datastore-preferences-core:1.1.7` | Apache-2.0 |
| `androidx.datastore:datastore-preferences-external-protobuf:1.1.7` | BSD-3-Clause |
| `androidx.datastore:datastore-preferences-proto:1.1.7` | Apache-2.0 |
| `androidx.datastore:datastore-preferences:1.1.7` | Apache-2.0 |
| `androidx.datastore:datastore:1.1.7` | Apache-2.0 |
| `androidx.emoji2:emoji2:1.4.0` | Apache-2.0 |
| `androidx.graphics:graphics-path:1.0.1` | Apache-2.0 |
| `androidx.interpolator:interpolator:1.0.0` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-java8:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common-jvm:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-common:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-livedata-core:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-process:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-android:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4` | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel:2.9.4` | Apache-2.0 |
| `androidx.profileinstaller:profileinstaller:1.4.0` | Apache-2.0 |
| `androidx.savedstate:savedstate-android:1.3.1` | Apache-2.0 |
| `androidx.savedstate:savedstate-compose-android:1.3.1` | Apache-2.0 |
| `androidx.savedstate:savedstate-compose:1.3.1` | Apache-2.0 |
| `androidx.savedstate:savedstate-ktx:1.3.1` | Apache-2.0 |
| `androidx.savedstate:savedstate:1.3.1` | Apache-2.0 |
| `androidx.startup:startup-runtime:1.1.1` | Apache-2.0 |
| `androidx.tracing:tracing:1.2.0` | Apache-2.0 |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | Apache-2.0 |
| `com.google.guava:listenablefuture:1.0` | Apache-2.0 |
| `com.squareup.okio:okio-jvm:3.4.0` | Apache-2.0 |
| `com.squareup.okio:okio:3.4.0` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.9.22` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.22` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-common:2.3.20` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib:2.3.20` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.8.1` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.1` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.7.3` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3` | Apache-2.0 |
| `org.jetbrains:annotations:23.0.0` | Apache-2.0 |
| `org.jspecify:jspecify:1.0.0` | Apache-2.0 |
