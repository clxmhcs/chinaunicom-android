# Android-M6 — Persistence / Repository / Global State

## Status

`M6_RESULT = IN_PROGRESS`

Completed substages:

- `M6-A_RESULT = PASS / CLOSED`
- `M6-B_RESULT = PASS / CLOSED`

Minimum supported Android version remains **Android 11 / API 30**.

## Frozen iOS source truth

| iOS source | SHA-256 | M6 role |
| --- | --- | --- |
| `ChinaUnicom/Services/PersistenceStore.swift` | `a2d688590deb7f599807f50758469757b4e37f2934f3e6a90d8086930682e07a` | accounts/settings ordinary persistence |
| `ChinaUnicom/Services/AppStore.swift` | `363468df319315f6df986b21e8b1b66219982fb7e9d2b90fc7fb8f40580902b3` | account restore/save/rollback and refresh orchestration |
| `ChinaUnicom/Models/AppRefreshLogicPolicyModels.swift` | `ad1ed089d7c3079cabbbe38702d0d51758e5b3c979b4686711603fe408d23f22` | quota automatic-refresh policy defaults and eligibility |
| `ChinaUnicom/Views/DashboardView.swift` | `651a183aaedb418e333dbaf624352b4926d915ec8757b5ef404fca215af58810` | cold-launch / foreground automatic-refresh triggers |

M6 follows the migration contract from `迁移总纲.txt`: establish `AccountRepository`, `SettingsRepository`, `QuotaRepository`, `BalanceRepository`, `RefreshCoordinator` and `AppState` before feature UI work. Shared balance lease/refresh semantics are a later M6 substage and must not be simplified to a cache-presence check.

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

`AndroidAccountMetadataStores.accounts(context)` -> `DefaultAccountRepository` -> `ProductionUnicomRepository`.

The former Release `PendingProductionUnicomRepository` placeholder is removed. Debug continues to use `FakeUnicomRepository`; fake fixtures remain absent from main/release production sources.

`FlowViewModel` receives application context through a factory-compatible single-`Application` `AndroidViewModel` constructor, allowing production app-private storage without retaining an Activity.

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

App production wiring now is:

`AndroidAccountMetadataStores` -> `DefaultAccountRepository` + `LoginAccountLifecycleProvider` + `AndroidQuotaRefreshRuntimeStore` -> `QuotaRefreshCoordinator` -> `ProductionUnicomRepository` -> `StateFlow<UnicomAppState>`.

`AndroidQuotaRefreshRuntimeStore` persists only the non-secret `lastRefreshTriggeredAt` epoch-millisecond value in private SharedPreferences. It is not a credential path.

`UnicomRepository` now exposes:

- `appState: StateFlow<UnicomAppState>`;
- manual refresh-all;
- manual single-account refresh;
- automatic-refresh trigger handling.

`FlowViewModel` immediately observes restored AppState, invokes the cold-launch gate separately, and does not replace already-restored content with a synthetic page error when one account refresh fails. The rough flow screen forwards foreground `ON_RESUME` to the foreground refresh gate; this is lifecycle plumbing only, not M7 visual work.

Debug keeps an isolated Fake StateFlow implementation and no fake fixture appears in main/release.

### M6-B regression evidence

Accepted implementation head:

`b10a84372f02a14b186aec182694d692c2ed387c`

All PR workflows passed:

- Android M1 Build run `32826327398` = success;
- Android M2 Models run `32826327362` = success;
- Android M3 Parsers run `32826327232` = success;
- Android M4 Network run `32826327364` = success;
- Android M5 Login Security run `32826327509` = success;
- Android M6 Persistence Refresh run `32826327283` = success.

M6-B tests and gates verify:

- restored-account ordering = PASS;
- automatic refresh no-trigger / 5-minute / 10-minute / clock-rollback policy cases = PASS;
- same-account concurrent manual refresh coalesces to one request = PASS;
- manual trigger timestamp records only when a request actually starts = PASS;
- successful refresh preserves balance and user package placement = PASS;
- quota/RemainingQuery completion timestamps update correctly = PASS;
- network failure keeps old quota and persists lastErrorMessage = PASS;
- account-metadata persistence failure rolls back the network candidate before recording the error = PASS;
- refresh-all filters disabled accounts = PASS;
- refresh-all records one trigger timestamp = PASS;
- refresh-all uses the configured two-second source-default gap = PASS;
- M5 credential lifecycle remains the only credential-refresh boundary = PASS;
- no `AccountCredentials` / Cookie / appID / token_online fields are declared by M6 ordinary state layers = PASS;
- Debug fake isolation = PASS;
- `:data:refresh:testDebugUnitTest` = PASS;
- all prior core/data tests = PASS;
- `:app:assembleDebug` = PASS;
- `:app:assembleRelease` = PASS;
- minimum Android API 30 = PASS.

`M6-B_RESULT = PASS / CLOSED`

### Explicitly not claimed by M6-B

- persisted SettingsRepository / refresh policy editing;
- BalanceRepository;
- SharedBalanceCacheStore semantics;
- representative balance-account selection;
- shared-balance refresh interval / scope / lease / forced-vs-automatic gate;
- shared-balance last-success / next-eligibility / in-flight failure-release semantics;
- UI visual parity.

These remain subsequent M6 substages.

## Screenshot requirement

M6-A and M6-B require no real-device screenshots. If a later M6 runtime step requires real-account evidence, the exact pages/states will be requested before that step begins.

## Next

`NEXT = Android-M6-C — SettingsRepository + persisted refresh policy`
