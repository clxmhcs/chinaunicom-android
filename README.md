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

`Android-M5 — Login + Security Storage` is `PASS / CLOSED`.

M5 supplies Android Keystore AES-256-GCM account credentials, SMS login, password/risk-captcha protocol core, Keystore-protected login device identity, quota-before-account credential binding, renewed credential propagation, restart restore and transactional create/delete credential rollback. Password UI remains disabled in parity with current iOS source.

`Android-M6 — Persistence / Production Repository / Refresh / Shared Balance Gate` is `IN_PROGRESS`.

M6-A Account Metadata Persistence + Production Repository Foundation is `PASS / CLOSED`:

- source-equivalent ordinary account metadata persistence separated from M5 Keystore credentials;
- app-private `persistence/accounts.json` using Android `AtomicFile`;
- complete `UnicomAccount` metadata JSON round-trip with ISO-8601 Instants and UUIDs;
- Cookie/appID/token_online/AccountCredentials excluded from ordinary metadata storage;
- `AccountRepository` / `DefaultAccountRepository` restore accounts ordered by `sortOrder`;
- M5 validated-login seed maps to default account metadata, display placements and summary groups;
- Release `PendingProductionUnicomRepository` placeholder removed;
- Debug Fake Repository remains isolated.

M6-B Production Quota Refresh Orchestration + AppState is `PASS / CLOSED`:

- production `QuotaRefreshCoordinator` and `StateFlow<UnicomAppState>`;
- immediate account restore followed by independently gated cold-launch refresh;
- manual single-account and refresh-all account-level mutual exclusion;
- global refresh-all mutual exclusion;
- enabled-account-only sequential refresh-all with source-default two-second gap;
- source-default automatic refresh: enabled, cold launch, foreground, 10-minute minimum interval;
- non-secret refresh-trigger timestamp persists across process recreation;
- M5 remains the credential/renewal boundary for every production quota refresh;
- refreshed quota preserves balance and user display configuration;
- flow/voice override synchronization and voice summary-group identity stabilization survive refreshed resource IDs;
- network failure keeps prior quota and persists lastErrorMessage;
- metadata persistence failure rolls back the network candidate before recording failure;
- `FlowViewModel` observes production AppState and forwards cold-launch/foreground triggers without visual redesign;
- Debug Fake StateFlow remains isolated;
- M1/M2/M3/M4/M5/M6 workflows, `data:refresh` tests, and Debug/Release builds passed on the accepted implementation head.

M6 still does not claim SettingsRepository, BalanceRepository, SharedBalance cache/representative-account/lease semantics or UI parity.

`NEXT = Android-M6-C — SettingsRepository + persisted refresh policy`.

M0 is closed for progression by explicit migration decision; the deferred real iOS light/dark screenshot set remains mandatory before M7 visual-parity acceptance.

See:

- [`docs/migration/M0_BASELINE.md`](docs/migration/M0_BASELINE.md)
- [`docs/migration/M1_BASELINE.md`](docs/migration/M1_BASELINE.md)
- [`docs/migration/M2_BASELINE.md`](docs/migration/M2_BASELINE.md)
- [`docs/migration/M3_BASELINE.md`](docs/migration/M3_BASELINE.md)
- [`docs/migration/M4_BASELINE.md`](docs/migration/M4_BASELINE.md)
- [`docs/migration/M4_F_REAL_PARITY.md`](docs/migration/M4_F_REAL_PARITY.md)
- [`docs/migration/M5_BASELINE.md`](docs/migration/M5_BASELINE.md)
- [`docs/migration/M6_BASELINE.md`](docs/migration/M6_BASELINE.md)
- [`docs/migration/MIGRATION_RULES.md`](docs/migration/MIGRATION_RULES.md)
