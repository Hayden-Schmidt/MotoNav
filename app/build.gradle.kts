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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.motonav.app"
        minSdk = 26 // Android 8.0 — NotificationListenerService baseline; revisit per PRD open question
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
    }
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

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
