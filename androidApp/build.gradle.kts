plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.playPublisher)
}

android {
    namespace = "org.neteinstein.snap2sheet"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.neteinstein.snap2sheet"
        minSdk = 24
        targetSdk = 36
        // .github/workflows/release.yml sets these from the Actions run number, so every release gets a
        // unique, increasing versionCode without editing this file. Local builds use the defaults.
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("APP_VERSION_NAME") ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Distribution channels, built by .github/workflows/release.yml:
    // - "github": the sideloaded APK published on GitHub Releases.
    // - "playstore": the app bundle uploaded to the Play Console (see the `play { }` block below).
    // Both share the application id; the Play build is re-signed by Play App Signing, so a device
    // switching from the sideloaded APK to the store install has to uninstall first.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
        }
        create("playstore") {
            dimension = "distribution"
        }
    }

    // Release signing comes from CI secrets (KEYSTORE_BASE64 decoded to KEYSTORE_FILE, plus
    // KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD - see README.md → Android releases). Without them
    // the release build falls back to debug signing so `assembleRelease` still works on any machine.
    val keystoreFile = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The only flavor Gradle Play Publisher may upload (it is off for everything else, see `play { }`).
    playConfigs {
        register("playstore") {
            enabled.set(true)
        }
    }
}

// Gradle Play Publisher: off by default and enabled for the "playstore" flavor only, so the "github"
// flavor can never be uploaded to the Play Console. `publishPlaystoreReleaseBundle` reads the
// ANDROID_PUBLISHER_CREDENTIALS env var (a Play service-account JSON key) and uploads to the
// "internal" track unless PLAY_TRACK overrides it, so nothing reaches production without a manual
// promotion in the Play Console. Release notes: src/playstore/play/release-notes/.
play {
    enabled.set(false)
    track.set(System.getenv("PLAY_TRACK")?.takeIf { it.isNotBlank() } ?: "internal")
    defaultToAppBundles.set(true)
}

dependencies {
    implementation(projects.composeApp)
    implementation(compose.preview)
    implementation(libs.androidx.activity.compose)
    debugImplementation(compose.uiTooling)
}
