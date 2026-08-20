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

## M1 build-verification attempt — 2026-08-20

Build verification was attempted on branch `migration/android-m1-skeleton`.

### GitHub Actions path

`.github/workflows/android-m1-build.yml` now:

- configures JDK 17;
- installs Gradle 9.5.0 with `gradle/actions/setup-gradle`;
- installs Android API 37 and Build Tools 36.0.0;
- runs `gradle :app:assembleDebug --stacktrace`;
- publishes a commit status named `android-m1-build` as success/failure so the connected GitHub client can read the result without requiring a PR.

Trigger commit: `102c254a353198ffc284da98b1609abf760b36eb`.

Repeated commit-status reads returned no status entry. Therefore no successful or failed `assembleDebug` result is currently observable from the GitHub execution path.

### Local fallback path

The available execution environment was checked as a fallback:

- Java: OpenJDK 21 is present;
- Gradle: not installed;
- Android SDK / `sdkmanager`: not installed.

Therefore the local fallback cannot perform a real Android build without provisioning a full Android build toolchain.

### Interpretation

This is **not** evidence that the Android sources fail compilation. It is also **not** acceptable evidence of a successful build. The verification gate remains unresolved because a real build has not produced an observable result.

No runtime screenshots are required for this verification step.

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
- [x] build-verification workflow can publish a machine-readable commit status when executed
- [ ] real `:app:assembleDebug` execution result observed

`M1_RESULT = BLOCKED_PENDING_REAL_BUILD_EXECUTION`

## Closure rule

M1 may be changed to `PASS / CLOSED` only after one of the following produces a real successful `:app:assembleDebug` result:

1. GitHub Actions executes the M1 workflow and `android-m1-build=success` is observed; or
2. the project is built with a local Android toolchain and the successful build output is recorded.

M2 must not be declared started before this gate closes unless the project owner explicitly changes the stage gate.
