plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

group = "com.bioscankit.android"
version = "0.1.0"

android {
    namespace = "com.bioscankit.android.cloudsync.drive"
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
    // 只依赖 cloudsync 的契约，不把 Google 依赖塞进纯逻辑层。
    api(project(":cloudsync"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // api：宿主 App 需要看到 OkHttpClient 类型（构造参数默认值里用到）。
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
