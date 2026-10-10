plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.schachhelfer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.schachhelfer"
        minSdk = 30          // maximumWindowMetrics braucht API 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    // OpenCV und Stockfish kommen in den nächsten Blöcken dazu
}
