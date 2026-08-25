# Android-M6 — Persistence / Repository / Global State

## Status

`M6_RESULT = IN_PROGRESS`

Completed substages:

- `M6-A_RESULT = PASS / CLOSED`
- `M6-B_RESULT = PASS / CLOSED`
- `M6-C_RESULT = PASS / CLOSED`

Minimum supported Android version remains **Android 11 / API 30**.

## Frozen iOS source truth

| iOS source | SHA-256 | M6 role |
| --- | --- | --- |
| `ChinaUnicom/Services/PersistenceStore.swift` | `a2d688590deb7f599807f50758469757b4e37f2934f3e6a90d8086930682e07a` | accounts/settings ordinary persistence |
| `ChinaUnicom/Services/AppStore.swift` | `363468df319315f6df986b21e8b1b66219982fb7e9d2b90fc7fb8f40580902b3` | account restore/save/rollback and refresh orchestration |
| `ChinaUnicom/Models/AppRefreshLogicPolicyModels.swift` | `ad1ed089d7c3079cabbbe38702d0d51758e5b3c979b4686711603fe408d23f22` | refresh-policy schema/defaults/tolerant decode |
| `ChinaUnicom/Services/AppRefreshLogicPolicyStore.swift` | `f73c1ad1487a09f7e7c7822be6f3495a75e6da9949b06dee933573f0d9aff1fb` | refresh-policy persistence/change-domain behavior |
| `ChinaUnicom/Views/DashboardView.swift` | `651a183aaedb418e333dbaf624352b4926d915ec8757b5ef404fca215af58810` | cold-launch / foreground / policy-change quota triggers |

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

App production wiring after M6-B:

`AndroidAccountMetadataStores` -> `DefaultAccountRepository` + `LoginAccountLifecycleProvider` + `AndroidQuotaRefreshRuntimeStore` -> `QuotaRefreshCoordinator` -> `ProductionUnicomRepository` -> `StateFlow<UnicomAppState>`.

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
- source quota defaults are:
  - `automaticRefreshEnabled = true`;
  - `refreshOnColdLaunch = true`;
  - `refreshOnForeground = true`;
  - `minimumIntervalMinutes = 10`;
  - `accountGapSeconds = 2`;
- tolerant decode applies defaults per missing or wrongly typed quota field;
- missing/malformed whole policy data falls back to source defaults;
- valid documents older than the current schema are rewritten to the current schema during load;
- quota values are not restricted to the Settings UI picker choices at persistence time;
- runtime refresh logic clamps `minimumIntervalMinutes` to at least one minute and `accountGapSeconds` to at least zero seconds;
- saving a policy identifies changed domains; quota change notification is emitted only when quota actually differs;
- source first-bootstrap inherits a legacy iOS `settings.autoRefreshOnLaunch` value only when no dynamic policy has ever been stored.

The Android migration has no pre-M6 Android equivalent of that legacy iOS `autoRefreshOnLaunch` key, so it does not fabricate a cross-platform legacy value. A missing Android policy starts from the source quota defaults.

### Android implementation

Module added:

- `data:settings`
  - `SettingsRepository`;
  - `DefaultSettingsRepository`;
  - `quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy>`;
  - `AppRefreshLogicPolicyCodec`;
  - `RefreshLogicPolicyStorage` abstraction;
  - `SharedPreferencesRefreshLogicPolicyStorage`;
  - `AndroidSettingsRepositories.refreshLogic(context)` production factory.

Persistence behavior:

- the source-equivalent key `chinaunicom.appRefreshLogic.policy.v1` is used inside app-private SharedPreferences;
- schema version is advanced to `3` for valid legacy documents;
- malformed whole JSON falls back to default quota policy;
- missing/wrong-typed quota fields independently fall back to source defaults;
- saving writes all five quota fields;
- unknown top-level policy domains are preserved rather than erased, so later Balance/Widget/business policy migration can extend the same document safely;
- a failed SharedPreferences `commit()` does not publish an unpersisted policy through the SettingsRepository StateFlow;
- saving an unchanged policy reports `changed = false` while still persisting, matching source save/change-notification separation.

M6-B remains the single `QuotaRefreshPolicy` model owner. M6-C does not introduce a second policy model. Release wiring creates one SettingsRepository and supplies:

`QuotaRefreshPolicyProvider { settingsRepository.loadQuotaRefreshPolicy() }`

to `QuotaRefreshCoordinator`, so every automatic-refresh decision and refresh-all gap resolution reads the persisted policy instead of the fixed source-default provider.

The settings document is ordinary non-secret data. Cookie, appID, token_online, passwords, SMS codes and captcha tokens remain excluded, and `android:allowBackup="false"` remains enforced.

`quotaRefreshPolicy` StateFlow provides the bottom-layer change stream required by the later Settings UI. The app already has the M6-B `.POLICY_CHANGE` trigger; wiring the future visual editor to save and dispatch that active-screen trigger belongs to the later Settings/UI stage and is not claimed here.

### M6-C regression evidence

Implementation head `7b1192ff073633e37deb98751bbe20a1f8a7fca4` passed all PR workflows:

- Android M1 Build run `32828912444` = success;
- Android M2 Models run `32828912450` = success;
- Android M3 Parsers run `32828912436` = success;
- Android M4 Network run `32828912538` = success;
- Android M5 Login Security run `32828912454` = success;
- Android M6 Persistence Refresh run `32828912452` = success.

M6-C tests and gates verify:

- missing settings -> exact source quota defaults = PASS;
- corrupt JSON -> exact source quota defaults = PASS;
- missing/wrong-typed quota fields -> per-field defaults = PASS;
- valid legacy schema -> schemaVersion 3 rewrite = PASS;
- unknown top-level domains survive migration/save = PASS;
- all five quota fields round-trip = PASS;
- unchanged policy reports no domain change = PASS;
- failed settings persistence does not publish candidate state = PASS;
- Release coordinator policy provider is backed by SettingsRepository = PASS;
- ordinary settings layer declares no credential fields and no `AccountCredentials` dependency = PASS;
- `data:settings` minSdk 30 = PASS;
- `:data:settings:testDebugUnitTest` = PASS;
- `:data:refresh:testDebugUnitTest` = PASS;
- all prior core/data tests = PASS;
- `:app:assembleDebug` = PASS;
- `:app:assembleRelease` = PASS.

`M6-C_RESULT = PASS / CLOSED`

### Explicitly not claimed by M6-C

- balance refresh-policy persistence/migration and App Group-equivalent synchronization;
- widget schedule synchronization/reload behavior;
- ordered-business/video-ring/receipt/order/package/bill/rebate/integral policy domains;
- Settings visual editor;
- BalanceRepository;
- SharedBalanceCacheStore representative-account / interval / scope / lease / forced-vs-automatic gate semantics;
- UI visual parity.

These remain subsequent migration stages/substages.

## Screenshot requirement

M6-A, M6-B and M6-C require no real-device screenshots. If a later M6 runtime step requires real-account evidence, the exact pages/states will be requested before that step begins.

## Next

`NEXT = Android-M6-D — BalanceRepository + Shared Balance Gate`
