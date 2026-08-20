# Android-M1 — Project Skeleton + Design System

## Scope

M1 establishes only the Android application shell and source-derived visual foundation. It does **not** migrate Unicom networking, account credentials, parsers, persistence, Widget, notifications or business data.

## Toolchain freeze

- Android Gradle Plugin: `9.2.1`
- Gradle: `9.5.0`
- JDK: `17`
- Kotlin/Compose compiler plugin: `2.3.21`
- Compose BOM: `2026.06.01`
- compileSdk: `37`
- targetSdk: `36`
- minSdk: `26`
- Activity Compose: `1.13.0`
- Lifecycle: `2.10.0`
- Navigation Compose: `2.9.8`

`targetSdk 36` is intentional for M1. The project compiles against API 37 while Android 17 target-behavior adoption remains a later reviewed migration decision.

The original M1 CI evidence below was produced with AGP `9.3.0`. On 2026-08-20, the project pin was moved to `9.2.1`, the latest version supported by the current Android Studio installation. Gradle `9.5.0`, Kotlin, SDK levels and application behavior were unchanged; the model/parser/network unit tests and `:app:assembleDebug` were revalidated after the compatibility-only change.

## M1 implementation

- `app` application module
- `core:design` design-system module
- five root destinations in the frozen iOS order:
  1. 流量
  2. 语音
  3. 综合业务
  4. 其它业务
  5. 设置
- Navigation Compose root navigation
- source-derived brand color, page spacing, card radii and 36sp major hierarchy
- light/dark theme with dynamic color intentionally disabled
- no `INTERNET` permission and no Unicom network dependency/client

## Visual rule

The M1 shell may use provisional Android mappings for iOS semantic system colors because the real runtime screenshot set was explicitly deferred. Those provisional values are marked in `ChinaUnicomColors.kt` and must be reviewed against the iOS screenshot set before M7 closes.

The root tab icons are temporary local text glyphs in M1. They are not accepted as final icon parity and must be replaced by traceable app-owned vector assets before M7 visual acceptance.

## Build verification — 2026-08-20

Build verification was performed on branch `migration/android-m1-skeleton` through Draft PR #1.

### Runner recovery and isolation

Early PR runs failed before any workflow step could start. A shell-only `runner-probe` was added to distinguish hosted-runner allocation failures from Android build failures. The same pre-step failure was reproduced on Ubuntu and macOS, and later disappeared when the hosted runner became available again.

Once runner execution recovered, `runner-probe` completed successfully and the Android build pipeline was allowed to proceed.

### Android 17 / API 37 SDK discovery

The current SDK repository does not expose the required platform as `platforms;android-37`. CI therefore uses `sdkmanager --list --channel=3` to discover the actual API 37 package.

The successful verification run observed:

- `platforms;android-37.0`
- `platforms;android-37.1`
- later 37.2 preview packages
- `build-tools;37.0.0`

M1 selected and installed `platforms;android-37.0`, which satisfied `compileSdk = 37`.

### Dependency correction

The first real Gradle run reached dependency resolution and correctly exposed one M1 configuration error:

`androidx.navigation:navigation-compose:2.10.0`

was not available as a stable artifact. M1 was corrected to the current stable Navigation release used by this project:

`androidx.navigation:navigation-compose:2.9.8`

No business behavior or UI architecture was changed by this correction.

### Final real build

Verification commit:

`7ce1c771716e4994f611895b79a60540a63af8d7`

GitHub Actions run:

`32327051021`

Results:

- `runner-probe` = `success`
- Checkout = `success`
- JDK 17 setup = `success`
- Gradle 9.5 setup = `success`
- Android SDK tools setup = `success`
- API 37 package discovery = `success`
- API 37 platform installation = `success`
- `gradle :app:assembleDebug --stacktrace` = `success`
- published commit status `android-m1-build` = `success`
- failure guard step = `skipped`, as expected
- `assemble-debug` job conclusion = `success`

The GitHub commit status for the verification commit is also:

`android-m1-build = success`

This is the required real Android build evidence for M1 closure.

No runtime screenshots were required for this build-verification step.

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
- [x] Draft PR #1 exists for real CI verification
- [x] real API 37 SDK package identified and installed
- [x] invalid Navigation dependency corrected to stable `2.9.8`
- [x] real `:app:assembleDebug` execution succeeded
- [x] machine-readable `android-m1-build=success` observed

`M1_RESULT = PASS / CLOSED`

## Deferred visual debt

The real iOS screenshot set remains deferred to the M7 visual-parity gate by explicit migration decision. M1 closure does not waive R2: iOS remains the visual truth for app-owned UI.

## Next stage gate

M2 is now authorized.

`NEXT = Android-M2 — Core Data Models Migration`
