# Android-M2 — Core Data Models Migration

## Scope

M2 ports the iOS domain/data model layer needed by later parser, network, repository and UI stages. It deliberately does **not** implement China Unicom HTTP, Cookie/session behavior, login, persistence stores, parser execution, Compose feature pages, Widget or automation.

The Android model layer is isolated in `core:model` and has no Compose dependency and no China Unicom network dependency.

## iOS source truth

M2 was derived directly from `chinaunicom-ios-main.zip` and the following frozen source files:

| iOS source | SHA-256 | Top-level struct/enum declarations | Android target |
| --- | --- | ---: | --- |
| `ChinaUnicom/Models/AppModels.swift` | `84bdd7c16e37a39b914827a02f11ef1d4e1c58a76335c5cd423247bb053133fa` | 26 | `AppModels.kt` |
| `ChinaUnicom/Models/RemainingQueryModels.swift` | `86b4ac132697b0439cda1ebc583e0036dcebf6fdec3c845d4dd6a6941be38a46` | 15 | `RemainingQueryModels.kt` |
| `ChinaUnicom/Models/PhoneBillModels.swift` | `6669e13f8f5fd0444922f3b654b9f1c5f504e9c01b0089f7ab221264d155c72c` | 8 | `PhoneBillModels.kt` |
| `ChinaUnicom/Models/MyPackageModels.swift` | `945a3b33d30872d72430e833b5f1bbabedf08bda769419d3267c3195b3628b40` | 8 | `MyPackageModels.kt` |
| `ChinaUnicom/Models/MyOrderModels.swift` | `98894d0ca315051f2a0308e1ab915fbdae57fa687d2e29ff2a701be58a92ec69` | 6 | `MyOrderModels.kt` |
| `ChinaUnicom/Models/OrderedBusinessModels.swift` | `d003575500b18bcffe3defa112ebc9931c40ccecd20e158ffeda347b52fa9121` | 5 | `OrderedBusinessModels.kt` |
| `ChinaUnicom/Models/IntegralModels.swift` | `c42a2819035525eca03412e222130aac048e73948a538d5f568aca31fa194cb0` | 8 | `IntegralModels.kt` |

The declaration counts are source-audit counts, not a claim that every Swift declaration maps one-to-one to a Kotlin type: `RemainingQueryModels.swift` also contains storage/calculation declarations that are intentionally deferred below.

## Model semantics ported in M2

### Core account/quota models

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
- unavailable/frozen balance detail models
- `BalanceRefreshState`
- `RefreshState`
- `DisplayUnit`
- `AppSettings`

The model-owned computed behavior from the `UnicomAccount` extension is also ported: resource-kind overrides, flow/voice cross-conversion, voice-resource deduplication, ambiguous-resource grouping, package sorting, automatic summary grouping, summary aggregation, primary/secondary selection and hidden/visible filtering.

### Remaining-query models

The data declarations through `DailyUsageBaseline` are ported, including safe usage values, unlimited resolution, member IDs and the schema version constant.

### Phone bill / package / order / ordered-business / integral models

The M2 Kotlin models preserve source-owned computed behavior such as:

- `BillMonth` key/title/subtitle;
- flattened bill items;
- displayed month-fee fallback;
- order type classification and display fields;
- HTTP(S)-only order action URL normalization;
- ordered-business total count;
- integral `yearMonth`, score selection, detail-query cache keys and error messages.

## Intentional M2 deferrals

The following source declarations are **not** silently moved into `core:model` because they are not pure model responsibilities:

- `DailyUsageBaselineStore` -> M6 storage/persistence stage;
- `DailyUsageBaselineUsageCalculator` -> M3/M6 business calculation integration;
- `DailyUsageBaselineStoreError` -> introduced with the Android storage implementation in M6;
- `AppRefreshLogicPolicyModels.swift` -> M6 refresh-policy/state stage;
- `MyOrderDetailModels.swift` -> M9-A order-detail stage;
- `TariffZoneRegionCatalog.swift` -> M9-G tariff-zone stage;
- capture-related model/controller code -> M14.

Kotlin persistence encoding annotations are also deferred. M2 freezes domain field names/types/raw enum values and model behavior; M6 will define the authoritative Android persistence/serialization representation so the domain layer does not prematurely couple itself to a storage technology.

## Platform types

- Swift `UUID` -> `java.util.UUID`
- Swift `Date` -> `java.time.Instant`
- Swift `URL` -> `java.net.URI`
- Swift optional -> nullable Kotlin type
- Swift `struct` -> Kotlin `data class`
- Swift raw-value enum -> Kotlin `enum class` with explicit `rawValue`
- Swift associated-value `RefreshState.failed(String)` -> Kotlin sealed-state `Failed(message)`

## Android 11 floor correction

The migration contract requires Android 11 as the minimum supported system. M1 historically had `minSdk = 26`; M2 corrects the authoritative project floor to:

`minSdk = 30`

The `app`, `core:design` and new `core:model` modules all use API 30 as the minimum from this stage onward.

## M2 tests

`CoreModelParityTest` covers source-derived model semantics, including:

- finite/unlimited traffic fraction rules;
- iOS keyword-based automatic summary grouping;
- forced flow -> voice conversion;
- bill-month key/title rules;
- integral year-month/cache-key rules;
- D15 order classification and operation semantics;
- safe remaining-query values and default settings.

These tests contain no real account credentials or personal data.

## Real CI verification — 2026-08-20

Verification commit:

`17e4e44248544b1254aff360561f84fcc197b484`

GitHub Actions run:

`32328061772`

Job:

`96303242926` (`model-test-and-build`)

The runner discovered and used:

`ANDROID_API_37_PLATFORM_PACKAGE=platforms;android-37.0`

The authoritative verification command was:

`gradle :core:model:testDebugUnitTest :app:assembleDebug --stacktrace`

Observed results:

- `:core:model:compileDebugKotlin` = success
- `:core:model:compileDebugUnitTestKotlin` = success
- `:core:model:testDebugUnitTest` = success
- `:app:compileDebugKotlin` = success
- `:app:assembleDebug` = success
- Gradle result = `BUILD SUCCESSFUL in 2m 36s`
- `78 actionable tasks: 78 executed`
- workflow job conclusion = `success`
- commit status `android-m2-models` = `success`
- failure guard step = skipped, as expected

This closes both the model-unit-test gate and the integrated Android application build gate.

## Acceptance gates

- [x] `core:model` module exists
- [x] model module has no Compose dependency
- [x] model module has no Unicom networking dependency
- [x] seven frozen iOS M2 source files fingerprinted
- [x] core account/quota models ported
- [x] remaining-query data models ported
- [x] phone-bill models ported
- [x] my-package models ported
- [x] my-order list models ported
- [x] ordered-business models ported
- [x] integral models ported
- [x] source-owned computed model semantics ported
- [x] model parity unit tests added
- [x] project minimum corrected to Android 11 / API 30
- [x] `:core:model:testDebugUnitTest` succeeds in GitHub Actions
- [x] `:app:assembleDebug` succeeds with `core:model` integrated
- [x] commit status `android-m2-models=success` observed

`M2_RESULT = PASS / CLOSED`

## Screenshot requirement

No iOS or Android real-device screenshots are required for M2. Visual parity remains an M7 gate.

## Next stage gate

M2 acceptance is complete. M3 is authorized.

`NEXT = Android-M3 — Quota / Remaining Parser Migration + Golden Tests`
