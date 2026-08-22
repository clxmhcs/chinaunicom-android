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

`Android-M4 — HTTP / Cookie / Session Core` is `PASS / CLOSED`.

M4 closure includes:

- source-derived HTTP/Cookie/session activation and quota/balance clients;
- iOS-equivalent transient retry classification;
- authoritative M2 `UnicomAccount` / `FlowPackage` / `VoicePackage` business-model boundary;
- independent `remainingMB`, unlimited and M3 `FlowFormatter` semantics;
- debug-only fake repository isolation with no fake fixture data in main/release;
- accepted sanitized M4-F real iOS/Android same-account parity evidence;
- final CI gate covering model/parser/network tests plus Debug and Release assembly.

The accepted M4-F evidence is sanitized validation evidence only. Raw Cookie, appId, token_online, password, SMS/captcha, credential archives and authenticated response bodies must never be committed or uploaded as evidence.

`NEXT = Android-M5 — Login + Security Storage`.

M0 is closed for progression by explicit migration decision; the deferred real iOS light/dark screenshot set remains mandatory before M7 visual-parity acceptance.

See:

- [`docs/migration/M0_BASELINE.md`](docs/migration/M0_BASELINE.md)
- [`docs/migration/M1_BASELINE.md`](docs/migration/M1_BASELINE.md)
- [`docs/migration/M2_BASELINE.md`](docs/migration/M2_BASELINE.md)
- [`docs/migration/M3_BASELINE.md`](docs/migration/M3_BASELINE.md)
- [`docs/migration/M4_BASELINE.md`](docs/migration/M4_BASELINE.md)
- [`docs/migration/M4_F_REAL_PARITY.md`](docs/migration/M4_F_REAL_PARITY.md)
- [`docs/migration/MIGRATION_RULES.md`](docs/migration/MIGRATION_RULES.md)
