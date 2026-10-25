plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi") // screenshot tests: gradle :app:testDebugUnitTest --tests "*ScreenshotTest"
}
android {
    namespace = "com.animewave.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.animewave.app"
        minSdk = 23
        targetSdk = 35
        // Every cloud build gets a higher number so phones and TVs see it as an update.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.$versionCode"
    }
    signingConfigs {
        // The keystore is encrypted with SIGNING_PASSWORD, a repo secret. Without it (local builds)
        // the default debug key is used, which can't update an installed copy from the cloud.
        create("animewave") {
            storeFile = rootProject.file("signing/animewave-release.jks")
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = "animewave"
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }
    buildTypes {
        // Release = optimised and shrunk by R8; much faster than debug on TV boxes.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(
                if (System.getenv("SIGNING_PASSWORD").isNullOrEmpty()) "debug" else "animewave"
            )
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // Native discovery for pages that expose media links in their public HTML.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real org.json; Android's is a stub in unit tests
}
