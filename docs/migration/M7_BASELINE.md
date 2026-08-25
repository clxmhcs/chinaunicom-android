# Android-M7 — Flow / Voice Feature UI

## Status

`M7_RESULT = PASS / CLOSED`

Completed substage:

- `M7-A_RESULT = PASS / CLOSED`

Visual parity status:

- `M7_VISUAL_PARITY = DEFERRED_BY_PROJECT_DECISION`

Minimum supported Android version remains **Android 11 / API 30**.

M7 is closed for migration progression because the current project decision is to finish functional/business migration first and polish app-owned UI page-by-page later. This closure therefore means the flow/voice data and interaction contract is functional and regression-closed; it does **not** claim final screenshot/visual parity.

## Frozen iOS source truth

| iOS source | SHA-256 | M7-A role |
| --- | --- | --- |
| `ChinaUnicom/Views/DashboardView.swift` | `651a183aaedb418e333dbaf624352b4926d915ec8757b5ef404fca215af58810` | flow root, refresh-all, balance entry, account list |
| `ChinaUnicom/Views/VoiceDashboardView.swift` | `281c6d6ecf75b914761a688c25de6f3685a77b19fc38a721c8dd697860118528` | voice root using the same quota refresh result |
| `ChinaUnicom/Views/RemainingQueryView.swift` | `7c6a4b8af91c2e6f75a51b68d809cc903778eedc00fd3b26422c1cd770111425` | remaining snapshot/category/member/package disclosure behavior |

## M7-A functional contract

The accepted implementation preserves these source-derived behaviors:

- flow root lists all restored accounts and exposes refresh-all;
- account-level refresh uses the production M6 quota path;
- home balance comes from M6 balance state and can be manually refreshed;
- voice data is **not independently refreshed** and consumes the same quota refresh result as flow;
- voice root lists the same persisted accounts and visible voice resources;
- package name, balance, update time, error state and visible flow/voice resources come from production state;
- flow remaining detail consumes `RemainingQuerySnapshot`;
- remaining flow packages are grouped as general / exclusive / other;
- collapsed category shows at most two packages and supports `查看更多` / `收起` when more than two exist;
- masked member numbers and voice snapshot summary are exposed in the functional remaining detail;
- flow and voice details can switch persisted accounts without creating another repository;
- one root-scoped `FlowViewModel` is shared by the flow and voice tabs;
- cold-launch/foreground quota refresh and balance-loop ownership remain with the existing M6 production repository rather than being duplicated per tab.

## Android implementation

M7-A changes stay in the app/UI composition layer:

- `FlowUiState.Content` carries production `UnicomAppState` and `BalanceRepositoryState`;
- `FlowViewModel` combines `repository.appState` and `repository.balanceState` from one `UnicomRepository`;
- `ChinaUnicomApp` creates one `FlowViewModel` and passes it to both flow and voice destinations;
- root lifecycle handling starts/stops foreground automatic work without tab-level duplication;
- `FlowHomeScreen` renders real account/balance/quota data and exposes refresh-all, account refresh, balance refresh, account selection and remaining detail;
- `VoiceDashboardScreen` renders the same account state and visible voice packages and intentionally exposes no independent refresh action;
- remaining detail implements source-derived category disclosure and collapsed/expanded package lists;
- app version is `0.7.0-m7a`;
- no new network endpoint, credential store or production repository was introduced.

## CI evidence

Accepted implementation head:

`208dcfe7025346039753c2662899cb11dc0eccac`

Passed PR workflows:

- Android M1 Build run `32840713675` = success;
- Android M2 Models run `32840713599` = success;
- Android M3 Parsers run `32840713586` = success;
- Android M4 Network run `32840713731` = success;
- Android M5 Login Security run `32840713709` = success;
- Android M6 Persistence Refresh run `32840713711` = success;
- Android M7 Flow Voice Functional run `32840713662` = success.

The M7 gate verifies shared production ViewModel/state ownership, flow refresh actions, no independent voice refresh, remaining-category collapse/expand behavior, API 30, fake-data isolation, all prior core/data tests, app unit tests and Debug/Release assembly.

The first implementation CI correctly caught invalid explicit Compose `layout.weight` imports in both new dashboard files. Those imports were removed without changing production behavior; the accepted head above then passed all M1-M7 workflows.

## Deferred visual parity

M7 does not yet claim final spacing, card order/width, margins, corner radii, background/gradient, typography, number hierarchy, icons, progress-bar appearance or light/dark screenshot parity. These app-owned visual parameters remain mandatory during the later page-by-page UI refinement pass.

No real-device screenshots are required for the functional M7 closure. When the deferred M7 visual pass begins, request at minimum:

- iOS + Android flow root;
- iOS + Android voice root;
- remaining detail collapsed state;
- remaining detail expanded state;
- corresponding light/dark variants where app-owned visuals differ.

## Next

`NEXT = Android-M8-A — Comprehensive Business Functional Migration (visual polish deferred)`
