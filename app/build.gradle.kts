plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.quietkeeper.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quietkeeper.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google Maps key for live map tiles. Supply via -PMAPS_API_KEY=... or a
        // gradle.properties / local.properties MAPS_API_KEY entry. Falls back to a
        // placeholder so the build compiles without a key (map tiles render blank).
        // TODO: set MAPS_API_KEY for live map tiles.
        manifestPlaceholders["MAPS_API_KEY"] = (project.findProperty("MAPS_API_KEY") as String?) ?: "YOUR_MAPS_API_KEY"

        externalNativeBuild {
            cmake {
                // Project-wide audio constants documented in native sources:
                // sampleRate 48000, mono, frame 6000 samples, ring buffer 30s,
                // pre/post 5s/5s, default threshold 55.0 dB(A), calibration offset 0.0.
                cppFlags += "-std=c++17"
            }
        }
        ndk {
            // Match the emulator/device ABIs we target.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    ndkVersion = "27.1.12297006"
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Monetization: Play Billing, AdMob, DataStore (debug-pro override storage).
    // Location capture + map preview.
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:6.1.2")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("com.google.android.gms:play-services-ads:23.5.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Embedded LAN streaming server for serving saved event WAVs to viewers on the same WiFi.
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    // androidx.test runner/junit/espresso bumped to API 36-compatible versions:
    // older Espresso reflects InputManager.getInstance(), which was removed on
    // recent Android (NoSuchMethodException on the API 36 emulator).
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Compose UI instrumented tests. Reuse the same Compose BOM applied above so
    // the test artifacts match the app's Compose version.
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
