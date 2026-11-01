plugins {
    id("com.android.application")
}

android {
    namespace = "com.arvinderss.lancam"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.arvinderss.lancam"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.pedroSG94.RootEncoder:library:2.8.1")
    implementation("com.github.pedroSG94:RTSP-Server:1.4.3")
}
