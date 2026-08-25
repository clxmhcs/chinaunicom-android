# Android-M6 — Persistence / Repository / Global State

## Status

`M6_RESULT = PASS / CLOSED`

Completed substages:

- `M6-A_RESULT = PASS / CLOSED`
- `M6-B_RESULT = PASS / CLOSED`
- `M6-C_RESULT = PASS / CLOSED`
- `M6-D_RESULT = PASS / CLOSED`
- `M6-E_RESULT = PASS / CLOSED`

Minimum supported Android version remains **Android 11 / API 30**.

## Frozen iOS source truth

| iOS source | SHA-256 | M6 role |
| --- | --- | --- |
| `ChinaUnicom/Services/PersistenceStore.swift` | `a2d688590deb7f599807f50758469757b4e37f2934f3e6a90d8086930682e07a` | accounts/settings ordinary persistence |
| `ChinaUnicom/Services/AppStore.swift` | `363468df319315f6df986b21e8b1b66219982fb7e9d2b90fc7fb8f40580902b3` | account restore/save/rollback and refresh orchestration |
| `ChinaUnicom/Services/AppStoreBalance.swift` | `0c226bdce2f6fba2243ea92f3f0545cafba2d57fa9781d25ba22390c339879a6` | shared-balance groups, representative selection, automatic/manual refresh orchestration |
| `ChinaUnicom/Services/UnicomNetworking.swift` | `3f1e31c6f1b367ac8119cd536f1cf7cbaa4109e033f3e8242627ac0aac910a2e` | `SharedBalanceCacheStore` scope/interval/lease/cache semantics |
| `ChinaUnicom/Services/UnicomBalanceClient.swift` | `a306e5664e669cd5d09dfc1a97cd32f95b2adc6925b45d81c04b5f864b3c5c65` | balance API result and credential-renewal boundary |
| `ChinaUnicom/Models/AppRefreshLogicPolicyModels.swift` | `ad1ed089d7c3079cabbbe38702d0d51758e5b3c979b4686711603fe408d23f22` | refresh-policy schema/defaults/tolerant decode |
| `ChinaUnicom/Services/AppRefreshLogicPolicyStore.swift` | `f73c1ad1487a09f7e7c7822be6f3495a75e6da9949b06dee933573f0d9aff1fb` | refresh-policy persistence/change-domain behavior |
| `ChinaUnicom/Views/DashboardView.swift` | `651a183aaedb418e333dbaf624352b4926d915ec8757b5ef404fca215af58810` | cold-launch / foreground / policy-change quota triggers |

M6 follows the migration contract from `迁移总纲.txt`: establish `AccountRepository`, `SettingsRepository`, `QuotaRepository`, `BalanceRepository`, `RefreshCoordinator` and `AppState` before feature UI work. All six named boundaries now exist in the Android production data path, and the shared-balance lease/freshness gate has been migrated without simplifying it to a cache-presence check.

## M6-A — account metadata persistence + production repository foundation

### iOS persistence behavior frozen for this substage

`PersistenceStore.swift` stores ordinary account metadata separately from credentials:

- account metadata file: `Application Support/ChinaUnicom/accounts.json`;
- JSON date strategy: ISO-8601;
- save uses atomic file replacement;
- unreadable/malformed/missing account metadata restores as an empty account list;
- `AppStore` sorts restored accounts by `sortOrder`;
- credential Cookie/appID/token_online are not fields in `accounts.json`;
- credentials remain in Keychain and are associated by account UUID.

Android does not copy the iOS filesystem path. The Android equivalent is app-private storage plus an atomic-write primitive while preserving the same data/security boundary.

### Android implementation

Modules added:

- `core:storage`
  - `AccountMetadataStore`;
  - `AccountMetadataJsonCodec`;
  - `AndroidFileAccountMetadataStore` using `android.util.AtomicFile`;
  - app-private `persistence/accounts.json`;
  - complete `UnicomAccount` metadata round-trip including flow/voice packages, RemainingQuerySnapshot, balance detail, display preferences and summary groups;
  - ISO-8601 `Instant` strings and UUID strings;
  - no `AccountCredentials`, Cookie, appID or token_online fields.
- `data:account`
  - `AccountRepository`;
  - `DefaultAccountRepository`;
  - restored accounts sorted by `sortOrder`;
  - validated login seed -> source-equivalent new-account metadata mapping;
  - first flow = primary, next two flows = secondary, remaining flows/voice = detail-only;
  - newly created balance fields start empty;
  - `lastUpdatedAt` and RemainingQuerySnapshot timestamp use account-creation completion time;
  - default summary groups are generated from the authoritative M2 account model;
  - account removal reindexes `sortOrder`.

Release app wiring:

`AndroidAccountMetadataStores.accounts(context)` -> `DefaultAccountRepository` -> production state/repository path.

The former Release `PendingProductionUnicomRepository` placeholder is removed. Debug continues to use `FakeUnicomRepository`; fake fixtures remain absent from main/release production sources.

### Security boundary

M6-A ordinary metadata persistence must never become a second credential store:

- Cookie/appID/token_online remain M5 Keystore-only;
- passwords, SMS codes and captcha resultToken remain transient;
- account UUID is the only association key between ordinary metadata and secure credentials;
- `android:allowBackup="false"` remains required;
- malformed account metadata never triggers credential fallback or plaintext recovery.

### M6-A regression evidence

Implementation head `f4e821451849476ef4ad2213f2850988e3b854d7` passed all PR workflows:

- Android M1 Build run `32545493612` = success;
- Android M2 Models run `32545493582` = success;
- Android M3 Parsers run `32545493539` = success;
- Android M4 Network run `32545493535` = success;
- Android M5 Login Security run `32545493532` = success;
- Android M6 Persistence Repository run `32545493586` = success.

`M6-A_RESULT = PASS / CLOSED`

## M6-B — production quota refresh orchestration + AppState

### iOS refresh behavior frozen for this substage

The production quota-refresh path preserves these source semantics:

- restored accounts are immediately available before a network refresh completes;
- manual single-account refresh is mutually exclusive per account;
- refresh-all uses the same account-level exclusion and adds a global refresh-all exclusion;
- refresh-all includes enabled accounts only;
- a real manual single-account refresh records one quota-refresh trigger timestamp;
- refresh-all records one trigger timestamp for the whole batch rather than once per account;
- refresh-all processes accounts sequentially with a default two-second inter-account gap;
- automatic quota refresh defaults to enabled, cold-launch enabled, foreground enabled, minimum interval 10 minutes;
- no previous trigger means eligible; a clock rollback also means eligible;
- a successful refresh preserves balance state and user display configuration while updating quota/remaining resources;
- empty refreshed package name does not erase an existing package name;
- RemainingQuerySnapshot completion time is normalized to the refresh completion time;
- existing resource-kind overrides propagate across equivalent flow/voice resources;
- voice summary groups use identity hints to survive backend package-ID changes;
- a network failure keeps prior quota data and records `lastErrorMessage`;
- cancellation resets the account refresh state to idle without persisting a failure;
- credential renewal remains owned by M5 and happens before M6 ordinary metadata persistence.

### Android implementation

Module added:

- `data:refresh`
  - `QuotaRefreshCoordinator`;
  - `UnicomAppState` exposed through `StateFlow`;
  - `QuotaRefreshPolicy` source defaults;
  - `QuotaRefreshRuntimeStore` boundary;
  - `LoginQuotaRefreshClient` adapter to M5 `LoginAccountLifecycle.refreshValidatedQuota`;
  - account-level `Mutex` values keyed by account UUID;
  - a global refresh-all `Mutex`;
  - sequential enabled-account refresh with configurable gap;
  - automatic cold-launch / foreground / policy-change eligibility checks;
  - source-equivalent refreshed-account merge, resource-kind synchronization and voice-summary stabilization;
  - persistence failure rollback so a network result is not left published when account metadata could not be committed.

`AndroidQuotaRefreshRuntimeStore` persists only the non-secret `lastRefreshTriggeredAt` epoch-millisecond value in private SharedPreferences. It is not a credential path.

### M6-B regression evidence

Accepted implementation head `b10a84372f02a14b186aec182694d692c2ed387c` passed:

- Android M1 Build run `32826327398` = success;
- Android M2 Models run `32826327362` = success;
- Android M3 Parsers run `32826327232` = success;
- Android M4 Network run `32826327364` = success;
- Android M5 Login Security run `32826327509` = success;
- Android M6 Persistence Refresh run `32826327283` = success.

M6-B gates cover restored ordering, 10-minute/clock-rollback eligibility, per-account/global mutual exclusion, enabled-account filtering, one batch trigger timestamp, two-second gap, successful merge preservation, network failure preservation, persistence rollback, M5 credential isolation, Debug fake isolation, all core/data tests, Debug/Release assembly and API 30.

`M6-B_RESULT = PASS / CLOSED`

## M6-C — SettingsRepository + persisted quota refresh policy

### iOS settings behavior frozen for this substage

`AppRefreshLogicPolicyStore.swift` is the sole saved refresh-policy entry point. For the quota domain used by M6-B:

- storage key is `chinaunicom.appRefreshLogic.policy.v1`;
- current `AppRefreshLogicPolicy.currentSchemaVersion` is `3`;
- source quota defaults are automatic=true, cold-launch=true, foreground=true, minimum interval 10 minutes, account gap 2 seconds;
- tolerant decode applies defaults per missing or wrongly typed quota field;
- missing/malformed whole policy data falls back to source defaults;
- valid documents older than the current schema are rewritten to the current schema during load;
- runtime refresh logic clamps minimum interval to at least one minute and account gap to at least zero seconds;
- source first-bootstrap inherits a legacy iOS `settings.autoRefreshOnLaunch` value only when no dynamic policy has ever been stored.

The Android migration has no pre-M6 Android equivalent of that legacy iOS key, so it does not fabricate a cross-platform legacy value.

### Android implementation

Module added:

- `data:settings`
  - `SettingsRepository` / `DefaultSettingsRepository`;
  - quota and balance policy StateFlows;
  - `AppRefreshLogicPolicyCodec`;
  - `RefreshLogicPolicyStorage` abstraction;
  - private SharedPreferences production storage.

Quota policy persistence uses the source-equivalent key and schema version, tolerantly migrates valid legacy documents, preserves unknown top-level domains, does not publish failed writes, and remains non-secret. Release `QuotaRefreshCoordinator` reads persisted SettingsRepository policy instead of fixed defaults.

### M6-C regression evidence

Implementation head `7b1192ff073633e37deb98751bbe20a1f8a7fca4` passed all PR workflows:

- Android M1 Build run `32828912444` = success;
- Android M2 Models run `32828912450` = success;
- Android M3 Parsers run `32828912436` = success;
- Android M4 Network run `32828912538` = success;
- Android M5 Login Security run `32828912454` = success;
- Android M6 Persistence Refresh run `32828912452` = success.

`M6-C_RESULT = PASS / CLOSED`

## M6-D — BalanceRepository + Shared Balance Gate

### iOS balance/shared-gate behavior frozen for this substage

`AppStoreBalance.swift` + `SharedBalanceCacheStore` require:

- default shared balance refresh interval = **60 minutes**;
- automatic freshness requires the same local calendar day and an interval-valid successful cache entry;
- a new local day invalidates the previous day's automatic freshness even when the raw elapsed interval is short;
- automatic refresh first consumes a fresh shared cache and performs no network request;
- forced/manual refresh bypasses freshness, but never bypasses a live in-flight lease;
- persistent lease default = **2 minutes**;
- lease duration is clamped to **15 seconds ... 10 minutes**;
- only the caller holding the matching lease token can complete or fail that refresh;
- a failed refresh releases its lease and preserves the last successful balance entry;
- scope/member topology changes invalidate obsolete cache and lease state;
- successful/fresh-cache consumption clears the automatic failure-attempt cooldown;
- a failed/incomplete automatic attempt remains persisted and enforces the balance policy `failureRetryMinutes` cooldown;
- balance policy source defaults are automatic refresh enabled, interval 60 minutes, failure retry 15 minutes;
- valid legacy balance interval 15 is migrated to 60;
- shared balance is not a simple `if cache != nil return cache` cache.

Financial representative priority for an effective group is preserved exactly:

1. enabled `homeBalanceAccountID` when it belongs to the group;
2. enabled group `defaultAccountID`;
3. first enabled account by `sortOrder` that has secure credentials;
4. first enabled account by `sortOrder`.

Source lifecycle nuance is also frozen: when no persisted home-balance account exists during AppStore initialization, iOS calls `initializeHomeBalanceAccountIfNeeded()` and permanently seeds it to the first enabled account by `sortOrder`. Explicitly clearing the home account later allows the fallback representative path to be exercised.

### Android implementation

Module added:

- `data:balance`
  - `BalanceRepository` / `DefaultBalanceRepository`;
  - `SharedBalanceCacheStore`;
  - balance account-group and shared-scope models;
  - automatic / forced / cached / in-flight / unavailable claim outcomes;
  - persisted shared entries, scopes and leases;
  - same-day + interval freshness;
  - failure-attempt cooldown persistence;
  - representative-account selection matching the source priority;
  - foreground automatic balance loop and manual home-balance refresh entry points.

Android shared state:

- app-private directory: `shared-balance`;
- state file: `chinaunicom.balance.shared-cache.v1.json`;
- lock file: `chinaunicom.balance.shared-cache.v1.lock`;
- writes use `android.util.AtomicFile`;
- cross-process transaction exclusion uses a `RandomAccessFile` channel lock;
- balance configuration/attempt metadata use app-private SharedPreferences;
- Android minimum remains API 30.

The production data path is:

`M4 balance API -> M5 Keystore credential lifecycle -> SharedBalanceCacheStore gate -> M6 BalanceRepository/AppState`.

`BalanceAccountCredentialLifecycle` keeps Cookie/appID/token_online inside the M5 secure boundary, persists renewed credentials before returning, and strips credentials from the ordinary M6 result. M6 balance/account/settings persistence never declares an `AccountCredentials` field or a parallel plaintext credential path.

Quota and balance metadata updates share the same account-persistence serialization boundary so concurrent successful writes cannot silently overwrite each other's account-state changes.

### M6-D regression evidence

Accepted implementation head `2bad26e4b8bed0f158f9065bb24461af5f2c5592` passed all PR workflows:

- Android M1 Build run `32832955310` = success;
- Android M2 Models run `32832955514` = success;
- Android M3 Parsers run `32832955425` = success;
- Android M4 Network run `32832955412` = success;
- Android M5 Login Security run `32832955422` = success;
- Android M6 Persistence Refresh run `32832955462` = success.

M6-D CI verifies the shared-balance source boundary, 60-minute interval, 2-minute lease, 15-second/10-minute lease clamp, same-day freshness, automatic/forced behavior, `AtomicFile` + file lock, balance policy migration, production wiring, Keystore isolation, all prior core/data tests, `data:balance` tests, and Debug/Release assembly.

During implementation, CI also caught and closed two harness/dependency issues plus one source-lifecycle test setup issue:

- a new M6-D test helper initially reused the M5 `FakeCredentialStore` top-level class name; the M6-D helper was renamed instead of changing production behavior;
- `BalanceRepositoryTest` required `:data:account` only on its test classpath, so `testImplementation(project(":data:account"))` was added without widening the production dependency graph;
- fallback representative testing now explicitly clears the startup-seeded home-balance account before asserting credential-aware fallback, matching the iOS initialization lifecycle rather than weakening production representative priority.

`M6-D_RESULT = PASS / CLOSED`

## M6-E — QuotaRepository boundary + final closure

### Boundary decision

The iOS source has no standalone type named `QuotaRepository`; the source business behavior remains inside `AppStore.refresh(accountID:)`, `refreshAll()` and `shouldAutoRefreshQuota(...)`. M6-E therefore does **not** invent new quota behavior. It adds the Android repository boundary required by the migration architecture while keeping the already accepted M6-B/C coordinator semantics authoritative.

Android `data:refresh` now contains:

- `QuotaRepository` — the app-facing quota-domain data contract;
- `DefaultQuotaRepository` — a thin production delegate to `QuotaRefreshCoordinator`;
- `QuotaRefreshCoordinator` — still the sole quota refresh, locking, refresh-policy, persistence and `UnicomAppState` authority.

The repository contract intentionally exposes only:

- `StateFlow<UnicomAppState>`;
- automatic refresh trigger entry;
- single-account refresh;
- refresh-all.

Automatic eligibility calculation remains inside the coordinator. There is no second StateFlow, second account cache, second mutex set, second refresh policy implementation or second persistence path.

Production wiring is now:

`AccountRepository + SettingsRepository + M5 LoginAccountLifecycle -> QuotaRefreshCoordinator -> DefaultQuotaRepository -> ProductionUnicomRepository`.

`ProductionUnicomRepository` depends on the `QuotaRepository` interface rather than the concrete coordinator. `DefaultBalanceRepository` continues to share the same coordinator-backed AppState mutation authority, so quota and balance still operate on one serialized account state rather than parallel copies.

### M6-E regression evidence

Accepted implementation head `447db4244eecb1b48ef86d75c22628ae14a0c1c8` passed all PR workflows:

- Android M1 Build run `32838010350` = success;
- Android M2 Models run `32838010430` = success;
- Android M3 Parsers run `32838010299` = success;
- Android M4 Network run `32838010288` = success;
- Android M5 Login Security run `32838010315` = success;
- Android M6 Persistence Refresh run `32838010335` = success.

The M6-E gate verifies:

- explicit `QuotaRepository` and `DefaultQuotaRepository` types exist;
- the default repository delegates to `QuotaRefreshCoordinator` rather than duplicating orchestration;
- `ProductionUnicomRepository` no longer references `QuotaRefreshCoordinator` directly;
- Release provider constructs `DefaultQuotaRepository(refreshCoordinator)`;
- `QuotaRepositoryTest` proves repository and coordinator share the exact same StateFlow and that manual/refresh-all operations delegate through the accepted coordinator path;
- all prior M6-D shared-balance/security gates remain active;
- all core/data unit tests pass;
- Debug and Release APK assembly pass;
- minimum Android remains API 30.

`M6-E_RESULT = PASS / CLOSED`

## M6 final closure

The M6 architecture required by `迁移总纲.txt` is now present and production-wired:

- `AccountRepository` — CLOSED;
- `SettingsRepository` — CLOSED;
- `QuotaRepository` — CLOSED;
- `BalanceRepository` — CLOSED;
- `RefreshCoordinator` — CLOSED;
- `AppState` / `StateFlow` / coroutine refresh orchestration — CLOSED;
- Shared Balance scope/interval/lease/forced-vs-automatic/in-flight semantics — CLOSED;
- ordinary metadata versus M5 secure credential separation — CLOSED.

Feature UI, Settings visual editors and high-fidelity visual parity are not M6 requirements and are not being falsely claimed here.

`M6_RESULT = PASS / CLOSED`

## Screenshot requirement

M6-A through M6-E require no real-device screenshots. No real-device screenshot is needed for M6 final closure.

The deferred real iOS light/dark screenshot set remains required before the visual-parity acceptance part of M7. Per the current migration priority, M7 may first proceed with functional/data/interaction wiring while the Android UI remains rough; visual refinement will be handled separately.

## Next

`NEXT = Android-M7-A — Flow + Voice Dashboard Functional Wiring (visual polish deferred)`
