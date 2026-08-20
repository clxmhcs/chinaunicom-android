# Android-M0 — Model Manifest

The iOS model layer is the data-contract baseline for Android M2/M3. Android may rename implementation-only types only when equivalence remains explicit and testable.

## Core account/quota/settings models

Source: `ChinaUnicom/Models/AppModels.swift`.

Primary migration models:

- `UnicomAccount`
- `QuotaResourceStatus`
- `ShareScope`
- `CarryForwardScope`
- `FlowPackage`
- `VoicePackage`
- `PackageDisplayPreference`
- `ResourceDisplayKind`
- `AmbiguousResourceGroup`
- `FlowSummaryGroup`
- `VoicePackageIdentityHint`
- `VoiceSummaryGroup`
- `FlowSummary`
- `QuotaType`
- `PackageCategory`
- `DisplayPlacement`
- `AccountCredentials`
- `QuotaFetchResult`
- `BalanceFetchResult`
- `UnavailableBalanceDetail`
- `UnavailableLimitItem`
- `FrozenBalanceItem`
- `BalanceRefreshState`
- `RefreshState`
- `DisplayUnit`
- `AppSettings`

## Remaining-query models

Source: `Models/RemainingQueryModels.swift`.

- member role/member usage
- flow category and per-package detail
- voice package/snapshot
- SMS package/snapshot
- complete remaining-query snapshot
- daily usage baseline and usage calculator

The Android parser tests must preserve these source semantics for shared resources, directional/general resources, unlimited resources, rollover/carry-forward and member-level usage.

## Refresh-policy models

Source: `Models/AppRefreshLogicPolicyModels.swift`.

The source has separate policy objects for quota, balance, ordered business, video ring, electronic receipt, orders, package, phone bill, integral/tariff and related domains. They are migration behavior, not UI preferences to be discarded.

## Business models

| iOS file | Domain | Target stage |
| --- | --- | --- |
| `PhoneBillModels.swift` | bill months, summaries, items, snapshots | M8/M9 |
| `OrderedBusinessModels.swift` | ordered-business relationship data | M8/M9 |
| `IntegralModels.swift` | points snapshot/month/detail/query | M8/M9 |
| `MyOrderModels.swift` | order list/business order data | M9 |
| `MyOrderDetailModels.swift` | order-detail and renewal-detail data | M9 |
| `MyPackageModels.swift` | package activities/resources/members/snapshot | M9 |
| `TariffZoneRegionCatalog.swift` | tariff region catalog | M9 |

## Cross-cutting persistent/widget models

Important structures are also declared outside `Models/` and must not be missed:

- shared-balance scope/cache/lease/result types in `UnicomNetworking.swift`
- widget configuration/slot/resource/refresh types in `WidgetConfigurationStore.swift`
- dual-widget configuration and shortcut cache types in `WidgetDualConfigurationStore.swift`
- dual-widget snapshots and quota payloads in `WidgetDualSnapshotStore.swift`
- electronic-receipt cache/index/storage/migration types in electronic-receipt source files

The model families above plus their concrete source-file references form the M0 model inventory. Every Android M2 type must remain traceable to its source type/file or carry a documented platform-only exception.

## Android M2 acceptance rule

M2 is not complete merely when Kotlin classes compile. At minimum:

1. every core source model has an explicit Android equivalent or a documented reason it is platform-only;
2. JSON/date/ID/default-value behavior used by persistence or API parsing is preserved;
3. sensitive credentials are not logged or embedded in fixtures;
4. parser-facing equality can be asserted in M3 Golden Tests.
