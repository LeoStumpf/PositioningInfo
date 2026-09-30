// SPDX-License-Identifier: AGPL-3.0-or-later
// Generates the app's baseline profile: which code startup and the first page swipes run,
// so Android compiles it ahead of time instead of interpreting it on the first launches.
//
//   ANDROID_SERIAL=<emulator> ./gradlew :app:generateBaselineProfile
//
// needs an emulator or device (API 28+); the result lands in
// app/src/main/generated/baselineProfiles and is committed. Nothing of this module ships.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    autoCorrect = providers.gradleProperty("detektAutoCorrect").isPresent
}

android {
    namespace = "io.github.leostumpf.positioninginfo.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
    detektPlugins(libs.detekt.ktlint)
}
