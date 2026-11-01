plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.arvinderss.lancam"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arvinderss.lancam"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // RootEncoder (encoders + Camera2) and its RTSP server module.
    // If the build fails to resolve, check the latest versions on jitpack.io.
    implementation("com.github.pedroSG94.RootEncoder:library:2.8.1")
    implementation("com.github.pedroSG94:RTSP-Server:1.4.3")
}
