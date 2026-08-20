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

`Android-M3 — Quota / Remaining Parser Migration + Golden Tests` is `PASS / CLOSED`.

`Android-M4 — HTTP / Cookie / Session Core` is `AUTOMATED_PASS / REAL_PARITY_PENDING` on branch `migration/android-m4-network`.

M4 adds the UI-independent `core:network` module for HTTP retry policy, explicit Cookie/Set-Cookie mutation, session-expiry detection, `appId/token_online` reactivation, quota API, balance API and Remaining unlimited-response normalization.

M4 automated acceptance is complete at verification commit `107a3806cdc0ce7d745fb7ea4f9a3dff5db5d649`: `:core:network:testDebugUnitTest`, the existing M3 parser tests, integrated `:app:assembleDebug`, and commit status `android-m4-network=success` all passed in GitHub Actions run `32331797633`.

M4 is not closed yet. M4-F must perform sanitized real iOS/Android same-account quota/balance/session parity before M5 Login + Security Storage is authorized. Raw Cookie, appId, token_online, password, SMS/captcha or authenticated response bodies must never be committed or uploaded as evidence.

M0 is closed for progression by explicit migration decision; the missing real iOS light/dark screenshot set remains deferred and mandatory before M7 visual-parity acceptance.

See:

- [`docs/migration/M0_BASELINE.md`](docs/migration/M0_BASELINE.md)
- [`docs/migration/M1_BASELINE.md`](docs/migration/M1_BASELINE.md)
- [`docs/migration/M2_BASELINE.md`](docs/migration/M2_BASELINE.md)
- [`docs/migration/M3_BASELINE.md`](docs/migration/M3_BASELINE.md)
- [`docs/migration/M4_BASELINE.md`](docs/migration/M4_BASELINE.md)
- [`docs/migration/MIGRATION_RULES.md`](docs/migration/MIGRATION_RULES.md)
