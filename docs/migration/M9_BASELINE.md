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
  - `M9-B2_RESULT = NOT_STARTED` — rough functional app wiring
  - `M9-B3_RESULT = NOT_STARTED` — real-device functional validation
- `M9-C_RESULT = NOT_STARTED` — 已订业务
- `M9-D_RESULT = NOT_STARTED` — 积分
- `M9-E_RESULT = NOT_STARTED` — 账单
- `M9-F_RESULT = NOT_STARTED` — 返费/赠费
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

`UnicomMyOrderClient` preserves the source list contract, including:

- `https://m.client.10010.com/mobileservicequery/order/newQueryOrder`;
- form POST, page size 15, `loginNumber = lowercase MD5(digits-only mobile)`;
- source headers / `iphone_c@12.1400` semantics;
- Set-Cookie mutation merging;
- response field aliases and JSON-string `respData` compatibility;
- existing M4 session activation and one retry on login/session expiry.

`MyOrderAccountCredentialLifecycle` reads only M5 `CredentialStore`, saves renewed credentials immediately and strips `updatedCredentials` before business state receives the result.

Accepted A1 implementation: `514e3d2005a4253385beff010834c85d3c76d037`.
Android M9 run `32966913358` — complete success.

### A2 — refresh setting and detail core

The single tolerant `SettingsRepository` owns `orders.refreshOnEntry`, default `true`.

The detail core follows the **current** iOS source and supports only:

- `business` hosted detail;
- `renewal` hosted detail;
- explicit `unsupported` for other order types.

It does not invent a payment-order fallback. Cookie access remains behind M5 and is not stored in ordinary detail state.

Primary A2 implementation: `0b5101e752cc46f87cf38c93c57ff5f7e3e2200d`.
Accepted verified A2 head: `d9c864fb3675320a62ed29b765117088b696b173`.
Android M9 run `32973038127` — complete success.

### A3 — rough installable app wiring

A3 wires:

- `其它业务 -> 我的订单`;
- production persisted account selection;
- source-equivalent entry refresh;
- manual refresh, local search and kind filtering;
- automatic list-end pagination plus manual load-more fallback;
- business/renewal hosted detail route only when a supported detail action exists;
- Android WebView restricted to `10010.com` / subdomains;
- M5 Cookie injection only for the hosted request, followed by targeted cookie-name expiry on destruction.

No Cookie/token is put into navigation, Compose state, ordinary disk state or logs.

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
- per the frozen acceptance scope, no artificial business/renewal order was manufactured solely to obtain a detail screenshot.

Therefore `M9-A_RESULT = PASS / CLOSED`.

## Frozen iOS My Package source truth

| iOS source | SHA-256 | Role |
| --- | --- | --- |
| `Models/AppRefreshLogicPolicyModels.swift` | `ad1ed089d7c3079cabbbe38702d0d51758e5b3c979b4686711603fe408d23f22` | three-state `PageEntryRefreshMode` and MyPackage policy defaults |
| `Models/MyPackageModels.swift` | `945a3b33d30872d72430e833b5f1bbabedf08bda769419d3267c3195b3628b40` | MyPackage snapshot/activity/rule/broadband/member/fetch-result models |
| `Services/MyPackageCrypto.swift` | `40217661d2a24a22d57a4153de4083a094db4c8695f39fdf93c1c6b0281de912` | member payload URL/Base64/AES-128-CBC/zero-padding contract |
| `Services/MyPackageClient.swift` | `bfe48b27c2fd475b2b04e513560ea6fa6f4d7b61605267a5f6fc7394789d8c5a` | primary and optional enhancement endpoint/session/parser truth |
| `Stores/MyPackageStore.swift` | `92be9421cbe1e92057d3a87f226a78bdd8ce938d4f3d45d7ba956af78f9df1c3` | per-account cache and refresh behavior truth |
| `Views/MyPackageView.swift` | `d7e7d9fafc630d7b41216e01c1df77ac810c908d3e42a889d334895e724eebe0` | functional page content/interaction truth for B2/B3 |
| `Views/OtherBusinessView.swift` | `2824ba88c2ea509ce6e3dc6cbb95794a4cd71db366dccf3967c4fc646cef2214` | `我的套餐` entry truth |

## M9-B1 accepted boundary

### Existing model authority

M2 already contains source-aligned `core:model/MyPackageModels.kt`; M9-B1 reuses it and does not introduce a duplicate model hierarchy.

### Real MyPackage client and crypto

`UnicomMyPackageClient` implements the current iOS network contract:

- primary endpoint: `https://mxx.client.10010.com/servicequerybusiness/queryPackage/myPackage`;
- optional resource enhancement: `/servicequerybusiness/queryPackage/myResourceDetails`, types `1` and `2`;
- optional member enhancement: `/servicequerybusiness/queryPackage/myMemberMobile`, `chooseflag=1`;
- optional pretty-number enhancement: `/servicequerybusiness/queryPackage/myPrettyNumber`;
- `Origin=https://imgxx.client.10010.com`, matching package-page referer and `unicom{version:iphone_c@12.1400}` semantics;
- primary package failure fails the query, while non-session failure of resource/member/pretty enhancement does **not** discard a valid primary package;
- login/session expiry anywhere in the flow reuses existing M4 `activateSession(...)` and retries once;
- Set-Cookie mutations are merged and renewed credentials are routed back through M5.

Member payload crypto follows the source sequence exactly:

1. URL-percent decode;
2. Base64 decode;
3. AES-128-CBC with key/IV `#user3ExtraInfo6`;
4. no PKCS padding;
5. trim trailing zero bytes;
6. parse `myNumbers` member groups.

Android uses the API-30-safe `URLDecoder.decode(String, String)` overload.

### M5 credential lifecycle

`MyPackageAccountCredentialLifecycle`:

- reads credentials only from the existing M5 `CredentialStore`;
- executes the validated MyPackage request;
- saves renewed Cookie/appID/token immediately to M5 storage;
- strips `updatedCredentials` before returning the business result.

No raw credential material exists in `data:mypackage` business/cache state.

### Per-account disk cache

Unlike My Order, iOS MyPackage **does** persist a cache. Android therefore adds `AndroidMyPackageDiskCache` with:

- account UUID -> `MyPackageCacheRecord` mapping;
- schema version `1`;
- snapshot + `fetchedAt`;
- app-private files directory;
- Android `AtomicFile` replacement and `fd.sync()` before finish-write;
- no Cookie/token/password/SMS material.

A cache write failure preserves the freshly fetched in-memory snapshot and exposes a warning instead of converting a valid carrier response into total failure.

### Source-equivalent refresh policy

The unique `SettingsRepository` now owns `myPackage` using source-equivalent `PageEntryRefreshMode` with exactly three modes:

- `everyEntry` — default, query whenever the page is entered after cache restoration;
- `refreshWhenExpired` — default validity 30 minutes; clock rollback is treated as expired;
- `manualOnly` — restore cache only; if absent, show the source-equivalent manual-query message.

Manual refresh always performs network work. Policy changes can be applied immediately to the active account. Unknown settings domains remain preserved by tolerant JSON merging.

### M9-B1 accepted implementation / CI

Accepted B1 implementation head:

`2a6cc6a3dc03f0d1f8ca4979863c06470c1b3d0b`

Android M9 Other Business run `33030539003` completed successfully:

- My Order permanent regression boundary — success;
- M9-B1 MyPackage static/source/security boundary — success;
- AES/member crypto test — success;
- primary-success/optional-enhancement-failure client behavior test — success;
- MyPackage cache/store policy tests — success;
- SettingsRepository MyPackage default/persistence/unknown-domain tests — success;
- all existing core/data/app regression tests — success;
- Debug assembly — success;
- Release assembly — success;
- `android-m9-other-business` status publication — success;
- failure gate — skipped as expected;
- complete job — success.

Android M2 Models run `33030538968` also completed successfully on the same B1 head, including app assembly.

Therefore `M9-B1_RESULT = PASS / CLOSED`.

## Deferred after M9-B1

B1 intentionally does not yet provide:

- an active `其它业务 -> 我的套餐` Android destination;
- Android account selection / package content rendering;
- Android manual-refresh control and active policy UI wiring for the page;
- real-device MyPackage evidence;
- final visual parity.

These remain ordered as **M9-B2 -> M9-B3**. Visual refinement stays deferred.

## Next

`NEXT = Android-M9-B2 — My Package Rough Functional App Wiring`
