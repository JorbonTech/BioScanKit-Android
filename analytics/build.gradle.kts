plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

group = "com.bioscankit.android"
version = "0.1.0"

android {
    namespace = "com.bioscankit.android.analytics"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.posthog:posthog-android:3.72.8")
    testImplementation("junit:junit:4.13.2")
}
