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

M4 closure includes source-derived HTTP/Cookie/session behavior, authoritative M2 business models, remaining/unlimited/formatting parity, debug-only Fake Repository isolation, accepted sanitized M4-F real-account evidence, and the final model/parser/network + Debug/Release CI gate.

`Android-M5 — Login + Security Storage` is `IN_PROGRESS`.

M5-A is `PASS / CLOSED` and establishes Android Keystore AES-256-GCM account credential persistence for Cookie + optional appID + optional token_online.

M5-B SMS Login Core is `PASS / CLOSED`:

- source-derived `getSwitch`, `sendRadomNum.htm` and `radomLogin.htm` behavior;
- frozen RSA PKCS#1 v1.5 encryption for mobile and six-digit verification code;
- explicit source-equivalent Cookie accumulation and base Cookie seeding;
- `ECS99998 + type=10` captcha-required state with `channel=smssms`;
- Cookie/appID/token_online/invalidat extraction;
- Keystore-protected persistent login device identity matching the iOS Keychain role;
- M1/M2/M3/M4/M5 CI and Debug/Release builds green.

M5-C Password Login + Risk Captcha is implemented and pending its branch CI gate:

- source-derived `login.htm` + shared `getSwitch` preflight;
- M5-B RSA/device identity/Cookie state reused instead of a parallel login stack;
- password request contract preserves `netWay=wifi`, `isRemberPwd=false`, `keyVersion=2`;
- strict 192-character lowercase-hex preferred appID validation;
- `ECS99999 + type=10` password risk-captcha state with mandatory returned risk `mobile`;
- password-rejected, SMS-verification-required and generic server failures remain distinct;
- password success applies response city before final credential Cookie snapshot;
- password and captcha `resultToken` remain transient and are not persisted.

The current iOS source still explicitly disables its password-login UI; M5-C migrates the already-existing underlying protocol implementation only and does not prematurely enable Android UI.

`NEXT_AFTER_M5_C_PASS = Android-M5-D — Login Integration + Persistence Acceptance`.

M0 is closed for progression by explicit migration decision; the deferred real iOS light/dark screenshot set remains mandatory before M7 visual-parity acceptance.

See:

- [`docs/migration/M0_BASELINE.md`](docs/migration/M0_BASELINE.md)
- [`docs/migration/M1_BASELINE.md`](docs/migration/M1_BASELINE.md)
- [`docs/migration/M2_BASELINE.md`](docs/migration/M2_BASELINE.md)
- [`docs/migration/M3_BASELINE.md`](docs/migration/M3_BASELINE.md)
- [`docs/migration/M4_BASELINE.md`](docs/migration/M4_BASELINE.md)
- [`docs/migration/M4_F_REAL_PARITY.md`](docs/migration/M4_F_REAL_PARITY.md)
- [`docs/migration/M5_BASELINE.md`](docs/migration/M5_BASELINE.md)
- [`docs/migration/MIGRATION_RULES.md`](docs/migration/MIGRATION_RULES.md)
