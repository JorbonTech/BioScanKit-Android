plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.bioscankit.android"
version = "0.1.0"

android {
    namespace = "com.bioscankit.android.settings"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":design"))
    // 云收藏区块只用到同步状态契约，不引入任何远端实现。
    api(project(":cloudsync"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

// 推荐应用清单与图标只有一份，放在 BioScanKit-iOS/Shared 下由双端共用。
// 这里在构建时同步到本模块的 assets，避免复制两份数据。
val sharedDir = rootProject.file("../BioScanKit-iOS/Shared")
val syncSharedAssets by tasks.registering(Copy::class) {
    from(sharedDir) {
        include("RecommendedApps*.json")
        include("RelatedRoutes.json")
        include("Icons/*.png")
    }
    // 落到 assets/bioscan_shared 下，与 RecommendedAppsLoader 读取路径保持一致。
    into(layout.buildDirectory.dir("generated/sharedAssets/bioscan_shared"))
}

android.sourceSets.getByName("main").assets.srcDir(
    layout.buildDirectory.dir("generated/sharedAssets")
)

tasks.named("preBuild") { dependsOn(syncSharedAssets) }
