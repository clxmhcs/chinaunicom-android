# Android-M0 — Storage / Cache / Security Manifest

This manifest freezes *behavioral storage responsibilities*. Android storage technology may differ, but these responsibilities cannot silently disappear.

## Main account/settings persistence

Source: `Services/PersistenceStore.swift`.

- Accounts: Application Support `ChinaUnicom/accounts.json`
- Account JSON date encoding: ISO-8601
- File write protection on iOS: atomic + complete file protection
- Settings key: `chinaunicom.settings.v1` in standard UserDefaults
- Account identity changes/removal trigger phone-bill cache invalidation/pruning

Android target: app-private durable storage; the exact Room/DataStore/file split is decided during M6, while behavior is preserved.

## Keychain responsibilities

Source: `Services/KeychainStore.swift`.

Keychain service namespaces:

- `com.clxmhcs.chinaunicom.credentials`
- `com.clxmhcs.chinaunicom.permanent-backup`
- `com.clxmhcs.chinaunicom.credential-access`
- `com.clxmhcs.chinaunicom.electronic-receipt-notes`

Backup/account records include:

- `accounts.v1`
- `broadband-accounts.v1`
- `settings.v1`
- `password.v1`
- per-receipt note records keyed by receipt UUID

The credential-access password is stored as a salted repeated SHA-256 digest with `20,000` source-defined rounds, not as plaintext.

Important behavior: the iOS permanent account backup intentionally strips `remainingQuerySnapshot`, because extensions only require base account data and full remaining-query details may contain member/secret-number information.

Android target: Keystore-backed encrypted secret storage. Android uninstall behavior is **not** assumed to match iOS Keychain persistence; any uninstall-survival/cross-device restoration is a separate explicit design decision.

## Shared App Group

Frozen identifier: `group.com.clxmhcs.chinaunicom`.

Used by main app/widget/intent/packet-tunnel paths for shared state on iOS. Android must replace this with explicit app-private/shared-process repository/state contracts rather than emulate an Apple App Group name.

## Shared balance cache and refresh lease

Source: `Services/UnicomNetworking.swift` (`SharedBalanceCacheStore`).

Frozen defaults/constraints:

- default refresh interval: 60 minutes
- default network lease: 2 minutes
- min refresh interval: 1 minute
- max refresh interval: 24 hours
- schema version: 1
- state file: `chinaunicom.balance.shared-cache.v1.json`
- lock file: `chinaunicom.balance.shared-cache.v1.lock`

State contains:

- authoritative balance scopes
- cached entries
- cross-process/in-flight leases
- representative account ID
- refresh source and timestamps

Critical behavior:

- automatic refresh consumes fresh shared cache first;
- only one owner gets the network lease after cache expiry;
- concurrent callers see in-flight state rather than duplicating network requests;
- manual refresh bypasses freshness but still respects an active lease;
- changed/removed account grouping invalidates stale cache and lease state;
- failure releases the lease without deleting the previous successful value;
- successful commit is accepted only while the caller still owns the matching lease.

This is a core M6 invariant and must not be reduced to a simple `if (cache != null)` cache.

## Widget/shared keys

Source: `WidgetConfigurationStore.swift` / `WidgetDualConfigurationStore.swift` / `WidgetDualSnapshotStore.swift`.

Important keys include:

- `chinaunicom.quota.widget.configuration.v1`
- `chinaunicom.quota.widget.networkCredentials.v1` (legacy)
- `chinaunicom.quota.widget.refreshStatus.v1`
- `chinaunicom.quota.widget.lastRefreshAttempt.v1`
- `chinaunicom.quota.widget.lastSuccessfulRefresh.v1`
- `chinaunicom.quota.widget.lastSuccessfulRefreshSource.v1`
- `chinaunicom.quota.widget.compensationDelayMinutes.v1`
- `chinaunicom.quota.widget.refreshRetryDelaySeconds.v1`
- `chinaunicom.quota.widget.snapshot.v1`
- `chinaunicom.quota.widget.dual.configuration.v1`
- `chinaunicom.shortcutNotification.accountIdentityByMobile.v1`
- `chinaunicom.shortcutNotification.accountIdentity.v1` (legacy)
- `chinaunicom.shortcutWidgetQuotaCache.byAccount.v1`

Android M12/M13 may use a different persistence mechanism but must preserve single/dual configuration, source/status timestamps, cache freshness and immediate snapshot-driven update semantics.

## Daily usage baseline

Source: `Models/RemainingQueryModels.swift`.

Key prefixes:

- `chinaunicom.dailyUsageBaseline.v1.`
- `chinaunicom.dailyUsageBaseline.todayUsage.v1.`

The per-account/per-date usage baseline is business logic used to derive daily consumption; it must survive the UI migration.

## Refresh-policy persistence

Source: `Services/AppRefreshLogicPolicyStore.swift`.

- key: `chinaunicom.appRefreshLogic.policy.v1`

Policy-domain changes are published with source-defined changed-domain information. Android should expose an equivalent observable policy update path.

## Domain disk caches

The source contains dedicated disk caches rather than one generic cache, including:

- `PhoneBillDiskCache`
- `OrderedBusinessDiskCache`
- `IntegralDiskCache`

Their TTL/invalidation semantics are migrated with the corresponding domain, not discarded when the UI is rebuilt.

## Electronic receipts

Source: `ElectronicReceiptCore.swift`, `ElectronicReceiptStorage.swift`, `ElectronicReceiptCloudStore*.swift`.

The source manages:

- processed/saved receipt metadata
- PDF validation (`%PDF` content check)
- SHA-256 file hashes
- per-account metadata
- refresh metadata
- H5 month cache
- external/user-selected directory bookmarks
- migration from legacy storage
- receipt notes + Keychain backup
- CloudKit index/bookmark/note synchronization when entitled

CloudKit is an Apple-only dependency and is explicitly cut at M10; local PDF/index/migration behavior must be completed independently of any Android cloud replacement.

## Security acceptance rules

Android migration must verify that:

- Cookie, `token_online`, password and codes are never logged;
- secret records are not plaintext in Git/test fixtures;
- secure storage is separated from general preferences;
- Widget/automation does not receive more credential material than needed;
- account/cache invalidation semantics remain deterministic.
