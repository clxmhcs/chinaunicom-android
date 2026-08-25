# Android-M7 — Flow / Voice Feature UI

## Status

`M7_RESULT = IN_PROGRESS`

Current substage:

- `M7-A_RESULT = IMPLEMENTED / CI_PENDING`

Minimum supported Android version remains **Android 11 / API 30**.

## Frozen iOS source truth

| iOS source | SHA-256 | M7-A role |
| --- | --- | --- |
| `ChinaUnicom/Views/DashboardView.swift` | `651a183aaedb418e333dbaf624352b4926d915ec8757b5ef404fca215af58810` | flow root, refresh-all, balance entry, account list |
| `ChinaUnicom/Views/VoiceDashboardView.swift` | `281c6d6ecf75b914761a688c25de6f3685a77b19fc38a721c8dd697860118528` | voice root using the same quota refresh result |
| `ChinaUnicom/Views/RemainingQueryView.swift` | `7c6a4b8af91c2e6f75a51b68d809cc903778eedc00fd3b26422c1cd770111425` | remaining snapshot/category/member/package disclosure behavior |

## M7-A functional scope

M7-A intentionally wires business data and interactions before visual parity. It must not redesign or duplicate the M4-M6 data authorities.

Source-derived behavior frozen for this substage:

- flow root lists every restored account and exposes refresh-all;
- account-level flow refresh uses the existing production quota refresh path;
- home balance is shown from the M6 balance state and can be manually refreshed;
- voice data is not independently refreshed; it is consumed from the same quota refresh result as flow;
- voice root lists the same accounts and their visible voice resources;
- account package name, balance, update time, error state and visible flow/voice resources are driven by production state;
- flow remaining detail consumes `RemainingQuerySnapshot` when available;
- flow remaining packages are grouped as general / exclusive / other;
- a category displays at most two packages while collapsed and supports `查看更多` / `收起` when more than two packages exist;
- member masked numbers and voice snapshot summary are available in the functional remaining detail;
- flow and voice detail views can switch between persisted accounts without creating another repository;
- one activity-level `FlowViewModel` is shared by the flow and voice root tabs;
- foreground/cold-launch quota and balance lifecycle tasks remain M6-owned and must not be duplicated per tab.

## Android implementation

M7-A changes are limited to the app/UI composition layer:

- `FlowUiState.Content` now carries the production `UnicomAppState` and `BalanceRepositoryState` directly;
- `FlowViewModel` combines the quota/AppState and balance StateFlows from one production `UnicomRepository`;
- `ChinaUnicomApp` owns one `FlowViewModel` and passes it to both flow and voice root destinations;
- lifecycle foreground/background handling is lifted to the root so tab changes do not create duplicate automatic balance loops;
- `FlowHomeScreen` renders real account/balance/quota data and exposes refresh-all, account refresh, balance refresh, account selection and remaining detail;
- `VoiceDashboardScreen` renders the same account state and visible voice packages but deliberately exposes no independent refresh action;
- remaining detail renders source-derived flow categories and collapsed/expanded package lists;
- app version is `0.7.0-m7a`;
- no new network endpoint, credential store or production repository is introduced.

## Visual parity boundary

M7-A does **not** claim final spacing, typography, card styling, gradients, symbols, dark-mode appearance or screenshot parity. Current UI may remain rough while the functional path is completed.

Real-device screenshots are **not required for M7-A implementation**. Before the later M7 visual-parity acceptance, request the exact iOS/Android evidence pages. At minimum that later evidence is expected to include:

- flow root;
- voice root;
- remaining detail in collapsed and expanded states;
- light and dark mode where the iOS source has app-owned visual differences.

## CI

`.github/workflows/android-m7-build.yml` verifies the shared ViewModel/state boundary, source-derived flow/voice actions, no independent voice refresh, remaining-category disclosure behavior, API 30, fake-data isolation, all prior core/data unit tests, app unit tests and Debug/Release assembly.

## Next

`NEXT = wait for M7-A implementation CI; only after PASS decide the next functional M7 substage. Visual polish remains deferred.`
