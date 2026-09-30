// SPDX-License-Identifier: AGPL-3.0-or-later
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from a gitignored keystore.properties, with an env-var
// fallback for CI. Absent either, release builds simply stay unsigned rather than
// failing the whole configuration phase.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

// Every build that reaches a phone must carry a higher versionCode than the one installed,
// or Android refuses the update — and Play refuses any upload not above the last one, for
// good. The code is the commit time of HEAD, in seconds since 2026-01-01 UTC: it is the same
// on the laptop and in CI, rises with every new commit, and unlike a commit count it does
// not fall when history is rebased or squashed (a rebase gives commits a new, later time).
// It stays below Play's limit of 2 100 000 000 until the 2090s. CI additionally refuses a
// code that is not above the last tagged build.
val versionEpochSeconds = 1_767_225_600L  // 2026-01-01T00:00:00Z
val buildCode: Int = providers.exec { commandLine("git", "log", "-1", "--format=%ct", "HEAD") }
    .standardOutput.asText.map { it.trim().toLongOrNull() ?: versionEpochSeconds }
    .map { (it - versionEpochSeconds).coerceIn(1L, 2_100_000_000L).toInt() }
    .getOrElse(1)

// The version people see, in the store and under About. Raised by hand before a release;
// every build in between shares it and is told apart by its versionCode.
val appVersionName: String = providers.gradleProperty("appVersionName").get()

android {
    namespace = "io.github.leostumpf.positioninginfo"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.leostumpf.positioninginfo"
        minSdk = 26
        targetSdk = 36
        versionCode = buildCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val storeFilePath = signingValue("storeFile", "POSITIONINGINFO_STORE_FILE")
    if (storeFilePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "POSITIONINGINFO_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "POSITIONINGINFO_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "POSITIONINGINFO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    lint {
        // CI runs lintRelease: errors — such as an API newer than minSdk called without a
        // version check — fail the build; warnings are reported only.
        abortOnError = true
        checkReleaseBuilds = true
        // targetSdk stays at the level Google Play requires until the next one has been
        // tested on its own: raising it changes how Android treats the app.
        disable += "OldTargetApi"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented UI tests (app/src/androidTest), run on a device or emulator with
    // ./gradlew connectedDebugAndroidTest; nothing of this reaches the release build.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// A signed release lands in releases/ when that path exists — on the laptop it is a link to
// the local Wi-Fi share, so each build is ready for the phone. In CI it does not exist, and
// nothing is copied.
val releaseShare = rootProject.file("releases")
val copyReleaseToShare = tasks.register<Copy>("copyReleaseToShare") {
    description = "Copies the signed release APK into releases/ (the local share), if present."
    onlyIf { releaseShare.exists() && android.signingConfigs.findByName("release") != null }
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*-release.apk") }
    into(releaseShare)
    rename { "positioning-info-$appVersionName-$buildCode.apk" }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyReleaseToShare) }

// For CI, which names and tags the build after it: prints "<versionName> <versionCode>".
tasks.register("printVersion") {
    description = "Prints the versionName and versionCode this checkout builds."
    val line = "$appVersionName $buildCode"
    doLast { println(line) }
}
