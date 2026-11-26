plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.animewave.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.animewave.app"
        minSdk = 23
        targetSdk = 35
        // Every cloud build gets a higher number so phones and TVs see it as an update.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.$versionCode"
        // Optional subtitle service key (CI secret WYZIE_API_KEY); without it subtitles come from the stream service.
        buildConfigField("String", "WYZIE_KEY", "\"${System.getenv("WYZIE_API_KEY") ?: ""}\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Stream player (same engine as Movio): page sniffing, ad blocking, offline cache.
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.media3:media3-database:1.8.0")
    implementation("androidx.media3:media3-datasource:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real org.json; Android's is a stub in unit tests
}
