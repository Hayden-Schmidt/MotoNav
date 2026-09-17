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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.motonav.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        multiDexEnabled = true
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
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Ferrostar core only — composeui/maplibreui deliberately skipped, see
    // docs/MotoNav_REBUILD_PLAN_OSM.md Phase A item 2. okhttp is Ferrostar's own HttpClientProvider
    // transport (OkHttpClientProvider); pinned to the version Ferrostar's own POM bundles.
    implementation("com.stadiamaps.ferrostar:core:0.56.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    // Phase C — Valhalla as an Android .so, routing entirely against on-device tiles. Only the
    // engine + config-building module are needed: requests/responses go through routeRaw/heightRaw
    // (plain strings) rather than the strongly-typed valhalla-models request/response classes, so
    // valhalla-models and its osrm-openapi dependency (only needed for that typed path) are
    // skipped. Versions verified current against Maven Central + GitHub releases as of 2026-09-17
    // — see docs/MotoNav_REBUILD_PLAN_OSM.md Phase C item 1.
    implementation("io.github.rallista:valhalla-mobile:0.6.3")
    implementation("io.github.rallista:valhalla-models-config:0.5.2")

    // Stage 3 — GPS speed, and now also Ferrostar's location feed (RideLocationProvider).
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Phase E item 2 — route-selection map only, not a live nav surface (composeui/maplibreui were
    // already skipped from Ferrostar in Phase A for the same reason: we render our own dial).
    // pmtiles:// support has shipped since 11.7.0; verified current on Maven Central as of
    // 2026-09-17 — see docs/MotoNav_REBUILD_PLAN_OSM.md Phase E item 2.
    implementation("org.maplibre.gl:android-sdk:11.11.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
