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

`Android-M2 — Core Data Models Migration` is `PASS / CLOSED` on branch `migration/android-m2-models`.

M2 adds a UI-independent `core:model` module derived directly from the frozen iOS model sources, preserves source-owned model semantics and adds source-parity unit tests.

Real M2 verification evidence:

- GitHub Actions run `32328061772`
- `gradle :core:model:testDebugUnitTest :app:assembleDebug --stacktrace` = success
- commit status `android-m2-models` = success
- verification commit `17e4e44248544b1254aff360561f84fcc197b484`
- minimum supported Android version is now frozen at Android 11 / API 30

`NEXT = Android-M3 — Quota / Remaining Parser Migration + Golden Tests`

M0 is closed for progression by explicit migration decision; the missing real iOS light/dark screenshot set remains deferred and mandatory before M7 visual-parity acceptance.

See:

- [`docs/migration/M0_BASELINE.md`](docs/migration/M0_BASELINE.md)
- [`docs/migration/M1_BASELINE.md`](docs/migration/M1_BASELINE.md)
- [`docs/migration/M2_BASELINE.md`](docs/migration/M2_BASELINE.md)
- [`docs/migration/MIGRATION_RULES.md`](docs/migration/MIGRATION_RULES.md)
