# Android-M6 — Persistence / Repository / Global State

## Status

`M6_RESULT = IN_PROGRESS`

Completed substages:

- `M6-A_RESULT = PASS / CLOSED`

Minimum supported Android version remains **Android 11 / API 30**.

## Frozen iOS source truth

| iOS source | SHA-256 | M6 role |
| --- | --- | --- |
| `ChinaUnicom/Services/PersistenceStore.swift` | `a2d688590deb7f599807f50758469757b4e37f2934f3e6a90d8086930682e07a` | accounts/settings ordinary persistence |
| `ChinaUnicom/Services/AppStore.swift` | `363468df319315f6df986b21e8b1b66219982fb7e9d2b90fc7fb8f40580902b3` | account restore/save/rollback and refresh orchestration |

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

`AndroidAccountMetadataStores.accounts(context)` -> `DefaultAccountRepository` -> `ProductionUnicomRepository` -> existing `UnicomRepository` UI envelope.

The former Release `PendingProductionUnicomRepository` placeholder is removed. Debug continues to use `FakeUnicomRepository`; fake fixtures remain absent from main/release production sources.

`FlowViewModel` receives application context through a factory-compatible single-`Application` `AndroidViewModel` constructor, allowing production app-private storage without retaining an Activity. This is dependency plumbing only; M7 still owns visual/UI parity.

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

The M6 job additionally verified:

- `Verify M6-A persistence and production repository boundary` = PASS;
- `AtomicFile` app-private `persistence/accounts.json` boundary = PASS;
- ISO-8601 `Instant` and UUID codec guards = PASS;
- complete metadata codec tests, including RemainingQuerySnapshot/balance/display/summary data, = PASS;
- ordinary metadata credential-field/`AccountCredentials` exclusion = PASS;
- Release `PendingProductionUnicomRepository` absence = PASS;
- Debug fake isolation = PASS;
- `android:allowBackup="false"` = PASS;
- `core:storage` and `data:account` minSdk 30 = PASS;
- `:core:model:testDebugUnitTest` = PASS;
- `:core:parser:testDebugUnitTest` = PASS;
- `:core:network:testDebugUnitTest` = PASS;
- `:core:security:testDebugUnitTest` = PASS;
- `:core:login:testDebugUnitTest` = PASS;
- `:core:storage:testDebugUnitTest` = PASS;
- `:data:account:testDebugUnitTest` = PASS;
- `:app:assembleDebug` = PASS;
- `:app:assembleRelease` = PASS;
- commit status `android-m6-persistence` = success.

`M6-A_RESULT = PASS / CLOSED`

### Explicitly not claimed by M6-A

- automatic/manual quota refresh orchestration;
- SettingsRepository;
- BalanceRepository;
- SharedBalanceCacheStore semantics;
- representative balance account selection;
- refresh cooldown/lease/in-flight gates;
- StateFlow AppState ownership;
- UI parity.

These remain subsequent M6 substages.

## Screenshot requirement

M6-A requires no real-device screenshots. If a later M6 runtime step requires real-account evidence, the exact pages/states will be requested before that step begins.

## Next

`NEXT = Android-M6-B — Production quota refresh orchestration + AppState`
