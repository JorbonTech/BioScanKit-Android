# BioScanKit Android

BioScan 应用家族共用的 Jetpack Compose 组件库，与 `BioScanKit-iOS` 的 6 个 pod 一一对应。

## 模块

| 模块 | 对应 iOS Pod | 内容 |
| --- | --- | --- |
| `:design` | `BioScanDesign` | 自适应主题、颜色、导航样式、本地化 |
| `:settings` | `BioScanSettings` | 设置页、语言选择、会员卡、推荐应用 |
| `:capture` | `BioScanCapture` | 相机页、裁剪编辑、识别处理页 |
| `:paywall` | `BioScanPaywall` | 付费页 UI、额度账本、识别准入控制 |
| `:analytics` | `BioScanAnalytics` | PostHog 封装、事件归一、付费漏斗 |
| `:cloudsync` | `BioScanCloudSync` | 收藏云同步抽象（后端由宿主注入） |

宿主 App 负责识别引擎、导航、凭据、商品 ID、持久化和品牌资源。

## 依赖关系

```
design  ← settings, capture, paywall
analytics（独立）
cloudsync（独立）
```

`design` 是唯一的 UI 基础层，其余 UI 模块通过 `api(project(":design"))` 传递依赖。

## 接入

```kotlin
// settings.gradle.kts
include(":bioscankit-design")
project(":bioscankit-design").projectDir = file("../../BioScanKit-Android/design")
// 其余模块同理，按需声明
```

```kotlin
// app/build.gradle.kts
implementation(project(":bioscankit-design"))
implementation(project(":bioscankit-settings"))
implementation(project(":bioscankit-paywall"))
implementation(project(":bioscankit-analytics"))
```

## 推荐应用数据

推荐应用清单与图标**只保留一份**，位于 `../BioScanKit-iOS/Shared/`，与 iOS 共用。
`:settings` 模块在 `preBuild` 阶段把它们同步到 `build/generated/sharedAssets/` 并注册为
assets 源，无需手工复制。读取入口：

```kotlin
RecommendedAppsLoader.loadFromAssets(
    context = context,
    excludingCurrentAppId = "natureear",
    destinationUrlKey = "googlePlayURL",
)
```

清单同时包含 `appStoreURL` 与 `googlePlayURL`，两端各取所需。

## 验证

```sh
./gradlew assembleDebug
./gradlew test
```
