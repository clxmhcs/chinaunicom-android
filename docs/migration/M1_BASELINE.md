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

Build verification was attempted on branch `migration/android-m1-skeleton` and Draft PR #1.

### Initial GitHub Actions verification

`.github/workflows/android-m1-build.yml` was configured to:

- configure JDK 17;
- install Gradle 9.5.0 with `gradle/actions/setup-gradle`;
- install Android API 37 and Build Tools 36.0.0;
- run `gradle :app:assembleDebug --stacktrace`;
- publish a commit status named `android-m1-build` as success/failure.

The PR-triggered workflow run `32326003597` failed. Its `assemble-debug` job failed before any workflow step was recorded. Re-running that job produced the same zero-step failure. Job log retrieval returned `BlobNotFound`, so there is no Gradle/Kotlin/compiler log for either attempt.

### Hosted-runner isolation probe

To determine whether Android source/configuration caused the failure, commit `b6f6b88730f90679331367ca6df41b62d513a329` added a `runner-probe` job using explicit `ubuntu-24.04` with one shell-only step:

```text
echo "RUNNER_PROBE=PASS"
uname -a
```

This probe has no checkout, Java, Gradle, Android SDK or third-party Action dependency.

PR workflow run `32326192456` produced:

- `runner-probe`: `failure`
- recorded steps: none
- `assemble-debug`: `skipped` because it depends on `runner-probe`

Therefore the build gate is currently blocked **before GitHub assigns/starts a usable hosted runner job**. This evidence rules out the current Android Kotlin/Compose sources as the cause of these specific workflow failures because the isolated shell-only probe never reached its first command.

GitHub's public status page was checked during this investigation and reported **All Systems Operational**, with Actions marked Operational. That does not identify the account/repository-specific cause; it only means there was no declared platform-wide Actions outage at the time of the check.

### Local fallback path

The available local execution environment was also checked:

- Java: OpenJDK 21 is present;
- Gradle: not installed;
- Android SDK / `sdkmanager`: not installed;
- outbound DNS/download access is unavailable in that execution environment, so the official Android/Gradle toolchain cannot be provisioned there during this stage.

Therefore the local fallback cannot perform a real Android build in the current environment.

### Interpretation

This is **not** evidence that the Android sources fail compilation. It is also **not** acceptable evidence of a successful build.

The verification blocker is now classified as:

`BUILD_EXECUTION_BLOCKER = GITHUB_HOSTED_RUNNER_JOB_NOT_STARTING`

The exact repository/account cause (for example hosted-runner availability, Actions account/billing/quota policy, or another runner-allocation restriction) cannot be determined from the currently exposed GitHub connector because repository Actions billing/runner-allocation settings are not available through it.

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
- [x] Draft PR #1 exists for real CI verification
- [x] hosted-runner probe isolates the current failure before workflow steps begin
- [ ] real `:app:assembleDebug` execution result observed

`M1_RESULT = BLOCKED_BY_GITHUB_HOSTED_RUNNER_EXECUTION`

## Closure rule

M1 may be changed to `PASS / CLOSED` only after one of the following produces a real successful `:app:assembleDebug` result:

1. GitHub Actions can start a hosted runner and the M1 workflow completes successfully; or
2. the project is built with a local Android toolchain and the successful build output is recorded.

M2 must not be declared started before this gate closes unless the project owner explicitly changes the stage gate.
