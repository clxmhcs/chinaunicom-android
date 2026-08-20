# ChinaUnicom Android

Android-native migration of the existing ChinaUnicom iOS application.

## Migration contract

- iOS is the **business behavior baseline**.
- iOS is the **visual baseline** for app-owned UI.
- Android is implemented natively with Kotlin/Jetpack Compose; Swift source is not mechanically translated.
- China Unicom protocol semantics, parsers, cache rules, account grouping, refresh gates and error/session rules must be migrated before feature UI is considered complete.
- Real credentials, cookies, `token_online`, passwords, verification codes and identity data must never be committed.

## Current stage

`Android-M1 — Project Skeleton + Design System` is `PASS / CLOSED`.

Real verification evidence:

- GitHub Actions run `32327051021`
- `gradle :app:assembleDebug --stacktrace` = success
- commit status `android-m1-build` = success
- verification commit `7ce1c771716e4994f611895b79a60540a63af8d7`

`NEXT = Android-M2 — Core Data Models Migration`

M0 is closed for progression by explicit migration decision; the missing real iOS light/dark screenshot set remains deferred and mandatory before M7 visual-parity acceptance.

See:

- [`docs/migration/M0_BASELINE.md`](docs/migration/M0_BASELINE.md)
- [`docs/migration/M1_BASELINE.md`](docs/migration/M1_BASELINE.md)
- [`docs/migration/MIGRATION_RULES.md`](docs/migration/MIGRATION_RULES.md)
