# ChinaUnicom Android

Android-native migration of the existing ChinaUnicom iOS application.

## Migration contract

- iOS is the **business behavior baseline**.
- iOS is the **visual baseline** for app-owned UI.
- Android is implemented natively with Kotlin/Jetpack Compose; Swift source is not mechanically translated.
- China Unicom protocol semantics, parsers, cache rules, account grouping, refresh gates and error/session rules must be migrated before feature UI is considered complete.
- Real credentials, cookies, `token_online`, passwords, verification codes and identity data must never be committed.
- Minimum supported Android version: **Android 11 / API 30**.

## Current stage

`Android-M1 — Project Skeleton + Design System` is `PASS / CLOSED`.

`Android-M2 — Core Data Models Migration` is `PASS / CLOSED`.

`Android-M3 — Quota / Remaining Parser Migration + Golden Tests` is `PASS / CLOSED` on branch `migration/android-m3-parsers`.

M3 adds a UI-independent `core:parser` module derived from the frozen iOS `QuotaParser.swift`, `RemainingQueryParser.swift` and pure formatting rules. Five required quota Golden fixtures plus a full Remaining fixture are sanitized and frozen with expected-output projections.

Real M3 verification evidence:

- GitHub Actions run `32330224609`
- `gradle :core:parser:testDebugUnitTest :app:assembleDebug --stacktrace` = success
- `BUILD SUCCESSFUL in 2m 50s`
- commit status `android-m3-parsers` = success
- verification commit `af2171e03cbc75e94041efc7961af110ef70fc7d`

`NEXT = Android-M4 — HTTP / Cookie / Session Core`

M0 is closed for progression by explicit migration decision; the missing real iOS light/dark screenshot set remains deferred and mandatory before M7 visual-parity acceptance.

See:

- [`docs/migration/M0_BASELINE.md`](docs/migration/M0_BASELINE.md)
- [`docs/migration/M1_BASELINE.md`](docs/migration/M1_BASELINE.md)
- [`docs/migration/M2_BASELINE.md`](docs/migration/M2_BASELINE.md)
- [`docs/migration/M3_BASELINE.md`](docs/migration/M3_BASELINE.md)
- [`docs/migration/MIGRATION_RULES.md`](docs/migration/MIGRATION_RULES.md)
