# ChinaUnicom Android

Android-native migration of the existing ChinaUnicom iOS application.

## Migration contract

- iOS is the **business behavior baseline**.
- iOS is the **visual baseline** for app-owned UI.
- Android is implemented natively with Kotlin/Jetpack Compose; Swift source is not mechanically translated.
- China Unicom protocol semantics, parsers, cache rules, account grouping, refresh gates and error/session rules must be migrated before feature UI is considered complete.
- Real credentials, cookies, `token_online`, passwords, verification codes and identity data must never be committed.

## Current stage

`Android-M1 — Project Skeleton + Design System`

Current status: `IMPLEMENTED_PENDING_BUILD_VERIFICATION`.

M0 is closed for progression by explicit migration decision; the missing real iOS light/dark screenshot set remains deferred and mandatory before M7 visual-parity acceptance.

See:

- [`docs/migration/M0_BASELINE.md`](docs/migration/M0_BASELINE.md)
- [`docs/migration/M1_BASELINE.md`](docs/migration/M1_BASELINE.md)
- [`docs/migration/MIGRATION_RULES.md`](docs/migration/MIGRATION_RULES.md)
