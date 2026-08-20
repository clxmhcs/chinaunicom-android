# Android-M1 — Project Skeleton + Design System

## Scope

M1 establishes only the Android application shell and source-derived visual foundation. It does **not** migrate Unicom networking, account credentials, parsers, persistence, Widget, notifications or business data.

## Toolchain freeze

- Android Gradle Plugin: `9.3.0`
- Gradle runtime expected by AGP: `9.5.0`
- JDK: `17`
- Kotlin/Compose compiler plugin: `2.3.21`
- Compose BOM: `2026.06.01`
- compileSdk: `37`
- targetSdk: `36`
- minSdk: `26`
- Navigation Compose: `2.10.0`

`targetSdk 36` is intentional for M1. The project compiles against API 37 for current Compose compatibility while Android 17 target-behavior adoption remains a later reviewed migration decision.

## M1 implementation

- `app` application module
- `core:design` design-system module
- five root destinations in the iOS order:
  1. 流量
  2. 语音
  3. 综合业务
  4. 其它业务
  5. 设置
- Navigation Compose root navigation
- source-derived brand color, page spacing, card radii and 36sp major hierarchy
- light/dark theme with dynamic color intentionally disabled
- no `INTERNET` permission and no network dependency/client

## Visual rule

The M1 shell may use provisional Android mappings for iOS semantic system colors because the real runtime screenshot set was explicitly deferred. Those provisional values are marked in `ChinaUnicomColors.kt` and must be reviewed against the iOS screenshot set before M7 closes.

The root tab icons are temporary local text glyphs in M1. They are not accepted as final icon parity and must be replaced by traceable app-owned vector assets before M7 visual acceptance.

## M1 acceptance gates

- [x] project structure exists
- [x] app and design modules exist
- [x] five root tabs exist in frozen order
- [x] root navigation exists
- [x] Theme exists
- [x] `ChinaUnicomColors.kt` exists
- [x] `ChinaUnicomSpacing/Dimensions` equivalent exists as `ChinaUnicomDimensions.kt`
- [x] `ChinaUnicomShapes.kt` exists
- [x] `ChinaUnicomTypography.kt` exists
- [x] no Unicom business networking introduced
- [ ] Gradle/Android compilation verified by CI or local Android toolchain

`M1_RESULT = IMPLEMENTED_PENDING_BUILD_VERIFICATION`
