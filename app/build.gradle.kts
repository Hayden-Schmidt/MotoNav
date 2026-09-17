import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) load(FileInputStream(propsFile))
}

android {
    namespace = "com.motonav.app"
    // compileSdk/targetSdk bumped 35 -> 36 for Navigation SDK 7.7.0+ (requires target API 36).
    // See docs/RESEARCH_NOTES.md "Technical requirements" for the full requirement list and
    // docs/MotoNav_GCP_SETUP.md for the API key this dependency needs before it'll run.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.motonav.app"
        // minSdk 26 already clears the Navigation SDK's own minimum (API 24) — kept at 26 since
        // that's still our NotificationListenerService baseline (legacy path, not yet removed).
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Navigation SDK ships resource strings for every supported language by default;
        // restrict to English during development per Google's own build-time guidance.
        resConfigs("en")
        multiDexEnabled = true

        // Navigation SDK API key — read from local.properties (gitignored, same pattern as the
        // MOTONAV_KEYSTORE_* entries above). Not using Google's Secrets Gradle Plugin to avoid
        // an extra build dependency; manifestPlaceholders achieves the same "never committed"
        // outcome. See docs/MotoNav_GCP_SETUP.md for how to obtain and add this key.
        manifestPlaceholders["MAPS_API_KEY"] =
            keystoreProps.getProperty("MOTONAV_MAPS_API_KEY", "")
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProps.getProperty("MOTONAV_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = keystoreProps.getProperty("MOTONAV_KEYSTORE_PASSWORD")
                keyAlias = "motonav"
                keyPassword = keystoreProps.getProperty("MOTONAV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required for Navigation SDK 7.7.0+ Java 8 API usage.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Navigation SDK bundles its own Maps SDK — any transitive dependency pulling in the
// standalone Play Services Maps SDK must exclude it to avoid duplicate-class build failures.
configurations.all {
    exclude(group = "com.google.android.gms", module = "play-services-maps")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation SDK — see docs/RESEARCH_NOTES.md for the adoption decision and
    // docs/MotoNav_GCP_SETUP.md for the API key this requires before it will run.
    // Version 7.9.0 is current as of this pin (Aug 2026) — re-check release notes before bumping:
    // https://developers.google.com/maps/documentation/navigation/android-sdk/release-notes
    implementation("com.google.android.libraries.navigation:navigation:7.9.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
