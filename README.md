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

`Android-M8-A — Comprehensive Business Foundation` is `PASS / CLOSED`.

M8-A freezes the source-derived ordered-business, phone-bill and integral models/network contracts and extends the single tolerant `SettingsRepository` with their refresh policies while preserving M5 Keystore as the only credential authority.

`Android-M8-B — Ordered Business Client + Cache + Store` is `PASS / CLOSED`.

M8-B implements the real ordered-business allocation/query/recovery flow, source `iphone_c@12.1300` semantics, M5 credential renewal, app-private `AtomicFile` cache and source-equivalent multi-account refresh/store rules.

`Android-M8-C — Phone Bill Client + Cache + Store` is `PASS / CLOSED`.

M8-C implements the real phone-bill months/detail flow, parser version `4`, `iphone_c@9.0100` activation recovery, M5 credential renewal, 13-month window, current/history cache policy, historical member-based sharing and same-month serialization. Accepted implementation head `8649aa57ecb970fe423956e6adaf31285abee2fa` passed run `32924827574`.

`Android-M8-D — Integral Client + Cache + Store` is `PASS / CLOSED`:

- implements the real `activity.10010.com` integral balance/month/detail requests with source `ZXGS97000017640,003`;
- preserves `IntegralSnapshot` parser version `1`, section/detail query and cache-key semantics;
- validates `c_mobile` / `u_account` against the selected mobile before any integral carrier request to prevent cross-account data leakage;
- reuses the existing M4 `/mobileService/onLine.htm` activation path for source-equivalent cookie/login expiry and routes renewed credentials through M5 `CredentialStore` before stripping them from ordinary state;
- adds app-private `AtomicFile` persistence at `integral/integral-snapshots.json` with `fd.sync()` and no credential material;
- preserves monthly day-2 08:00 Asia/Shanghai refresh cycles, fixed 24-hour mode, manual-only mode, check-on-entry and clock-rollback refresh behavior;
- keeps overview and per-query detail caches separate; a successful overview refresh clears stale details while detail cache writes remain query-keyed;
- preserves a prior successful overview if network or disk persistence fails, while a detail disk-write failure keeps the newly fetched detail in memory and exposes an error;
- accepted implementation head `c091cb87036fa620bc0289312ec08e122e106ed9` passed the dedicated M8-D static gate, integral client/lifecycle/store tests, all existing core/data/app unit-test regression, Debug assembly and Release assembly in run `32926493346`.

M8-A/M8-B/M8-C/M8-D contain no final visual refinement. Final comprehensive-page visual parity remains deferred until the later page-by-page visual pass.

`NEXT = Android-M8-E — Comprehensive Root Aggregation / Entries / Final Functional Closure`

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
