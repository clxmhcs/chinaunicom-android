# Android-M9 — Other Business

## Status

`M9_RESULT = IN_PROGRESS`

Substages:

- `M9-A_RESULT = PASS / CLOSED` — 我的订单
  - `M9-A1_RESULT = PASS / CLOSED` — order list client + M5 credential lifecycle + in-memory pagination store
  - `M9-A2_RESULT = PASS / CLOSED` — unified order refresh setting + business/renewal detail core
  - `M9-A3_RESULT = PASS / CLOSED` — rough functional entry/list/detail wiring
  - `M9-A4_RESULT = PASS / CLOSED` — real-device functional validation
- `M9-B_RESULT = IN_PROGRESS` — 我的套餐
  - `M9-B1_RESULT = PASS / CLOSED` — model reuse + crypto + client + M5 lifecycle + disk cache + store + settings
  - `M9-B2_RESULT = PASS / CLOSED` — rough functional app wiring
  - `M9-B3_RESULT = IN_PROGRESS` — real-device functional validation
- `M9-C_RESULT = NOT_STARTED` — 已订业务
- `M9-D_RESULT = NOT_STARTED` — 积分
- `M9-E_RESULT = NOT_STARTED` — 账单
- `M9-F_RESULT = NOT_STARTED` — 返费 / 赠费
- `M9-G_RESULT = NOT_STARTED` — 资费专区
- `M9-H_RESULT = NOT_STARTED` — 视频彩铃

Minimum supported Android version remains **Android 11 / API 30**.

Final visual parity remains deferred until the later page-by-page visual pass.

## Frozen current iOS My Order source truth

These hashes are synchronized to the current uploaded iOS source archive used for M9 work.

| iOS source | SHA-256 | Role |
| --- | --- | --- |
| `Models/MyOrderModels.swift` | `98894d0ca315051f2a0308e1ab915fbdae57fa687d2e29ff2a701be58a92ec69` | order/page/action models and derived semantics |
| `Models/MyOrderDetailModels.swift` | `23eda6099dc019282c96d638690cd3b17f1deb090ccc5e11ae8a419700261bef` | business/renewal detail models and bridge payload parsing truth |
| `Services/MyOrderClient.swift` | `cab67b9db094332de492b0d459a744db7655e43dafceb60d96ba4c7a9a072cb5` | order list request/session/parser truth |
| `Stores/MyOrderStore.swift` | `9c70c5338595062b0502bc922ca1ea378ad4ebb8a8fe19680a330fa982622f8d` | account-scoped pagination and entry refresh truth |
| `Stores/MyOrderDetailStore.swift` | `275af0c317dacc75dd32293c0767c1aed59b04832a8be27e7f8e81b0a072450a` | supported detail loading state truth |
| `Views/MyOrderView.swift` | `4dd5a41424ffb7191f15d8895653eefce4d649f8e3c76d37aa6249d513d70fde` | account/search/filter/load-more interaction truth |
| `Views/MyOrderDetailView.swift` | `eaf72ec821087b4aba4afb5a259b9c95108966a1c800ca285877eb6acba0a032` | hosted business/renewal detail destination truth |
| `Views/OtherBusinessView.swift` | `2824ba88c2ea509ce6e3dc6cbb95794a4cd71db366dccf3967c4fc646cef2214` | Other Business entry ordering truth |

## M9-A accepted boundary

### A1 — real list core

`core:model/MyOrderModels.kt` from M2 remains the sole Android list-model authority. Android does not create a My Order disk cache because the iOS `MyOrderStore.swift` does not persist one.

`UnicomMyOrderClient` preserves the source list contract, including the real `newQueryOrder` form request, page size 15, lowercase-MD5 mobile identifier, source `iphone_c@12.1400` header semantics, Set-Cookie mutation merging, response aliases, JSON-string `respData` compatibility, and one existing-M4 session-activation retry on expiry.

`MyOrderAccountCredentialLifecycle` reads only M5 `CredentialStore`, saves renewed credentials immediately and strips `updatedCredentials` before ordinary business state receives the result.

Accepted A1 implementation: `514e3d2005a4253385beff010834c85d3c76d037`.
Android M9 run `32966913358` — complete success.

### A2 — refresh setting and detail core

The single tolerant `SettingsRepository` owns `orders.refreshOnEntry`, default `true`.

The current iOS detail core supports only `business`, `renewal`, and explicit `unsupported`; Android does not invent a payment-order fallback. Cookie access remains behind M5 and is not stored in ordinary detail state.

Primary A2 implementation: `0b5101e752cc46f87cf38c93c57ff5f7e3e2200d`.
Accepted verified A2 head: `d9c864fb3675320a62ed29b765117088b696b173`.
Android M9 run `32973038127` — complete success.

### A3 — rough installable app wiring

A3 wires `其它业务 -> 我的订单`, production persisted account selection, entry/manual refresh, search/filter, pagination, supported business/renewal detail, and a WebView limited to `10010.com` / subdomains. M5 Cookie injection is transient and followed by targeted expiry; no credential material enters navigation, Compose state, ordinary disk state or logs.

Accepted A3 head: `1f32584c9856f0afe84d2b01fc98085efc120e3b`.
Android M9 run `33024157050` — complete success.
Android M2 run `33024157038` — complete success.
Android M8 run `33024157045` — complete success.
Android Main APK run `33024157051` — complete success, artifact upload included.

### A4 — real-device acceptance

Accepted real-device evidence on **2026-08-27** verified the installable production path without exporting credentials or recording unmasked account identifiers in Git:

- `其它业务` displayed `我的订单` as an active destination;
- a persisted production account could be selected;
- the real carrier order list returned populated records with legitimate order category/status, masked business number, channel, time and amount;
- a cancelled order displayed the corresponding cancellation/退订 warning state;
- current returned payment orders exposed no supported detail action and correctly rendered `暂无可用详情`;
- no artificial business/renewal order was manufactured solely for acceptance.

Therefore `M9-A_RESULT = PASS / CLOSED`.

## Frozen iOS My Package source truth

| iOS source | SHA-256 | Role |
| --- | --- | --- |
| `Models/AppRefreshLogicPolicyModels.swift` | `ad1ed089d7c3079cabbbe38702d0d51758e5b3c979b4686711603fe408d23f22` | three-state `PageEntryRefreshMode` and MyPackage policy defaults |
| `Models/MyPackageModels.swift` | `945a3b33d30872d72430e833b5f1bbabedf08bda769419d3267c3195b3628b40` | MyPackage snapshot/activity/rule/broadband/member/fetch-result models |
| `Services/MyPackageCrypto.swift` | `40217661d2a24a22d57a4153de4083a094db4c8695f39fdf93c1c6b0281de912` | member payload URL/Base64/AES-128-CBC/zero-padding contract |
| `Services/MyPackageClient.swift` | `bfe48b27c2fd475b2b04e513560ea6fa6f4d7b61605267a5f6fc7394789d8c5a` | primary and optional enhancement endpoint/session/parser truth |
| `Stores/MyPackageStore.swift` | `92be9421cbe1e92057d3a87f226a78bdd8ce938d4f3d45d7ba956af78f9df1c3` | per-account cache and refresh behavior truth |
| `Views/MyPackageView.swift` | `d7e7d9fafc630d7b41216e01c1df77ac810c908d3e42a889d334895e724eebe0` | functional page content/interaction truth |
| `Views/OtherBusinessView.swift` | `2824ba88c2ea509ce6e3dc6cbb95794a4cd71db366dccf3967c4fc646cef2214` | `我的套餐` entry truth |

## M9-B1 accepted boundary

M2 already contains the source-aligned `MyPackageModels.kt`; M9-B1 reuses that model authority.

`UnicomMyPackageClient` implements the current iOS primary `/queryPackage/myPackage` request and the optional resource/member/pretty-number enhancement requests. Primary failure fails the query; non-session failures of optional enhancement requests do not discard a valid primary package. Session expiry reuses existing M4 activation and retries once. Set-Cookie mutations and renewed credentials continue through M5.

Member crypto follows the source sequence: URL-percent decode -> Base64 decode -> AES-128-CBC with key/IV `#user3ExtraInfo6` -> no PKCS padding -> trailing-zero removal -> member JSON parsing.

`MyPackageAccountCredentialLifecycle` is the secure boundary, and `AndroidMyPackageDiskCache` persists only schema-versioned account UUID -> snapshot/fetchedAt data through app-private `AtomicFile` + `fd.sync()`.

The single `SettingsRepository` owns `myPackage` with exactly the source three entry modes: `everyEntry`, `refreshWhenExpired`, and `manualOnly`; default cache validity for expired mode is 30 minutes. Manual refresh always performs network work.

Accepted B1 implementation: `2a6cc6a3dc03f0d1f8ca4979863c06470c1b3d0b`.
Android M9 run `33030539003` — complete success.
Android M2 run `33030538968` — complete success.

Therefore `M9-B1_RESULT = PASS / CLOSED`.

## M9-B2 accepted boundary

B2 wires the B1 core into the installable Android app while intentionally keeping presentation rough:

- `其它业务` now exposes `我的套餐` as an active destination;
- `other/my-package` is a real Navigation route;
- `MyPackageViewModel` owns one `DefaultMyPackageStore` and reuses the existing M5 `CredentialStoreProvider`, B1 credential lifecycle, account-scoped disk cache and single `SettingsRepository` policy authority;
- the page selects from the already-persisted, enabled production mobile accounts and calls the source-equivalent entry load for the chosen account;
- loading, no-cache/manual-only, carrier failure, retained-cache warning and retry states are represented without duplicating network logic in Compose;
- manual `刷新` calls the existing B1 store refresh path;
- the main package section renders product name, month fee, product start time, refresh time and promotion metadata when returned;
- the `移网` resource tab renders source-derived outside-package charging rules;
- the `宽带` resource tab renders broadband resources returned by the package API, including broadband number, package/actual speed, start/end time and tips;
- package description, business rules and month-fee description are retained;
- contract activities, contract rules and non-cancellable prompt are retained;
- member groups preserve the source group-type `05` behavior, primary members, masked numbers, and the source two-member collapsed/`查看更多`/`收起` interaction;
- `查看完整号码` does **not** expose or fabricate a full number; it currently shows the source-derived requirement that full-number access needs SMS verification;
- the pretty-number section appears only when `isPrettyNumber` is true;
- no Cookie, token, password or SMS code enters ViewModel/Compose ordinary state;
- app version is `0.9.0-m9b2`; minimum Android remains API 30.

### Independent broadband-account parity boundary

The frozen iOS MyPackage account selector can also consume saved `BroadbandAccountInfoStore` targets. The current Android account system does not yet contain an equivalent independent broadband-account persistence/onboarding authority.

B2 therefore deliberately **does not invent or fake broadband accounts**. It uses the real persisted mobile-account authority and renders any associated broadband resources returned by the real MyPackage response. Independent saved-broadband-account selection remains a known account-system parity gap and must be completed before full broadband-account parity can be claimed.

### M9-B2 accepted implementation / CI

Accepted B2 implementation head:

`04e56d2bde802d9c32638f01825763ca37e05c1b`

Android M9 Other Business run `33032941768` completed successfully:

- M9-A My Order permanent boundary — success;
- M9-B1 MyPackage permanent core boundary — success;
- M9-B2 rough functional wiring boundary — success;
- all core/data/app regression tests — success;
- Debug assembly — success;
- Release assembly — success;
- `android-m9-other-business` status publication — success;
- failure gate — skipped as expected;
- complete job — success.

Android Main APK Build run `33032941829` completed successfully, including artifact upload.

Accepted installable artifact:

- name: `chinaunicom-debug-apk`;
- artifact id: `9630992097`;
- SHA-256 digest: `1dc4f95fd6b740f2305cac1a5a2fd7be11c3b99c4c548dc9ed50ea4ec5a8df74`;
- source head: `04e56d2bde802d9c32638f01825763ca37e05c1b`.

Therefore `M9-B2_RESULT = PASS / CLOSED`.

## M9-B3 real-device acceptance scope

Use the installable B2 artifact with the user's existing local credentials. No credential export/upload is required.

Required evidence:

1. `其它业务` root showing `我的套餐` as `已接入`;
2. `我的套餐` account-selection page showing at least one saved production account; account identifiers may be masked;
3. one selected real account with the returned main-package section visible, including product name and month fee plus effective/refresh time where returned;
4. the same account's `移网` resource tab showing real outside-package rules or the legitimate carrier-empty state;
5. the same account's `宽带` resource tab showing real associated broadband data when returned, or the legitimate `当前套餐未返回关联宽带资源` state;
6. the `我的合约` section showing real contract data or the legitimate empty state;
7. the `我的成员` section showing real masked members or the legitimate empty state; when more than two ordinary members exist, `查看更多` should expand them;
8. if `我的靓号` appears for the selected account, capture it; if it does not appear, do not manufacture a condition solely for acceptance.

Also press `刷新` once on a selected real account and confirm the page does not crash. A screenshot is only additionally required if refresh produces a warning/error that needs evaluation.

Visual spacing, typography, icons and card polish are explicitly outside B3 acceptance.

## Next

`NEXT = Android-M9-B3 — My Package Real-device Functional Validation`
