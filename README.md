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

`Android-M6 — Persistence / Production Repository / Refresh / Shared Balance Gate` is `PASS / CLOSED`.

M6 includes:

- ordinary account metadata persistence separated from M5 Keystore credentials;
- `AccountRepository`, `SettingsRepository`, `QuotaRepository`, `BalanceRepository`, `QuotaRefreshCoordinator` and `StateFlow<UnicomAppState>`;
- cold-launch/foreground/manual quota refresh orchestration, per-account/global mutual exclusion and persisted quota refresh policy;
- M5-owned credential renewal for quota and balance;
- source-equivalent `SharedBalanceCacheStore` freshness, persistent lease/in-flight protection, failure retry cooldown and financial representative selection;
- one shared account-persistence serialization boundary for quota/balance metadata updates;
- Debug Fake Repository isolation and complete Debug/Release regression coverage.

`Android-M7 — Flow / Voice Feature UI` is `PASS / CLOSED` **for migration progression**.

M7-A functional flow/voice wiring is `PASS / CLOSED`:

- one root-scoped `FlowViewModel` consumes the production M6 `AppState` and `BalanceState` for both root tabs;
- flow root renders real persisted accounts, balance, package/quota data, update/error state and supports refresh-all, per-account refresh and manual home-balance refresh;
- voice root renders the same account state and visible voice packages but intentionally has no independent refresh action;
- flow remaining detail consumes `RemainingQuerySnapshot`, groups general/exclusive/other flow packages and implements source-derived two-item collapsed disclosure with `查看更多` / `收起`;
- flow and voice detail can switch persisted accounts without creating another repository or refresh authority;
- root lifecycle handling prevents duplicate quota/balance automatic work across tab changes;
- app version is `0.7.0-m7a` and minimum Android remains API 30;
- accepted implementation head passed M1/M2/M3/M4/M5/M6/M7 workflows, app unit tests and Debug/Release builds.

Per the explicit project priority, **M7 final visual parity is deferred**. M7 closure here means functional/data/interaction migration is complete enough to progress to later business stages; it does not claim final app-owned spacing, card styling, typography, gradients, icons, progress bars or light/dark screenshot parity. Those will be refined page-by-page after the remaining business migration is complete.

`Android-M8 — Comprehensive Business` is `IN_PROGRESS`.

`Android-M8-A — Comprehensive Business Foundation` is `PASS / CLOSED`:

- reuses the existing source-aligned ordered-business, phone-bill and integral model files instead of introducing duplicate business types;
- freezes source-derived endpoints and typed network contracts for ordered business, phone bill and integral;
- preserves phone-bill parser version 4 and integral parser version 1 semantics;
- extends the single tolerant `SettingsRepository` document with source-equivalent ordered-business, phone-bill and integral refresh-policy defaults;
- preserves unknown settings domains and existing quota/balance settings while keeping Android 11 / API 30 support;
- introduces no additional credential persistence path: M5 Keystore remains the credential authority.

`Android-M8-B — Ordered Business Client + Cache + Store` is `PASS / CLOSED`:

- implements the real `mxx.client.10010.com` allocation/query flow and `loginxx.10010.com/mobileService/onLine.htm` recovery path;
- preserves source `iphone_c@12.1300` session fields, Cookie mutation propagation, parser section structure and stable item IDs;
- routes renewed Cookie/appID/token_online through the existing M5 `CredentialStore` and strips them before ordinary M8 state/cache;
- adds app-private Android `AtomicFile` snapshot persistence at `ordered-business/ordered-business-snapshots.json`;
- adds a multi-account `OrderedBusinessStore` with cachePreferred / refreshWhenExpired / everyEntry / manualOnly policies, same-account duplicate suppression, serial refresh-all gap, orphan reconciliation, old-cache retention on failure and warning state when local save fails;
- keeps Android 11 / API 30 and `allowBackup=false` security boundaries unchanged;
- accepted implementation head passed M1–M8 workflows, M8-B dedicated client/session/cache/store tests and Debug/Release assembly.

M8-A/M8-B contain no visual refinement. Final comprehensive-page visual parity remains deferred until the later page-by-page visual pass.

`NEXT = Android-M8-C — Phone Bill Client + Cache + Store`

M0 is closed for progression by explicit migration decision. Deferred real iOS/Android screenshot evidence remains mandatory when the final page-by-page visual parity pass begins.

See:

- [`docs/migration/M0_BASELINE.md`](docs/migration/M0_BASELINE.md)
- [`docs/migration/M1_BASELINE.md`](docs/migration/M1_BASELINE.md)
- [`docs/migration/M2_BASELINE.md`](docs/migration/M2_BASELINE.md)
- [`docs/migration/M3_BASELINE.md`](docs/migration/M3_BASELINE.md)
- [`docs/migration/M4_BASELINE.md`](docs/migration/M4_BASELINE.md)
- [`docs/migration/M4_F_REAL_PARITY.md`](docs/migration/M4_F_REAL_PARITY.md)
- [`docs/migration/M5_BASELINE.md`](docs/migration/M5_BASELINE.md)
- [`docs/migration/M6_BASELINE.md`](docs/migration/M6_BASELINE.md)
- [`docs/migration/M7_BASELINE.md`](docs/migration/M7_BASELINE.md)
- [`docs/migration/M8_BASELINE.md`](docs/migration/M8_BASELINE.md)
- [`docs/migration/MIGRATION_RULES.md`](docs/migration/MIGRATION_RULES.md)
