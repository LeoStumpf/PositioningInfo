// SPDX-License-Identifier: AGPL-3.0-or-later
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
// or Android refuses the update. The commit count on the branch rises with every merge and
// is the same on the laptop and in CI (which checks out the full history for it).
val commitCount: Int = providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
    .standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.getOrElse(1)

android {
    namespace = "io.github.leostumpf.positioninginfo"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.leostumpf.positioninginfo"
        minSdk = 26
        targetSdk = 36
        versionCode = commitCount
        versionName = "1.0.$commitCount"
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
}

// A signed release lands in releases/ when that path exists — on the laptop it is a link to
// the local Wi-Fi share, so each build is ready for the phone. In CI it does not exist, and
// nothing is copied.
val releaseShare = rootProject.file("releases")
val copyReleaseToShare by tasks.registering(Copy::class) {
    description = "Copies the signed release APK into releases/ (the local share), if present."
    onlyIf { releaseShare.exists() && android.signingConfigs.findByName("release") != null }
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("*-release.apk") }
    into(releaseShare)
    rename { "positioning-info-1.0.$commitCount.apk" }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyReleaseToShare) }
