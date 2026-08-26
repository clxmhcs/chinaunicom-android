# Android-M9 — Other Business

## Status

`M9_RESULT = IN_PROGRESS`

Substages:

- `M9-A_RESULT = IN_PROGRESS` — 我的订单
  - `M9-A1_RESULT = PASS / CLOSED` — order list client + M5 credential lifecycle + in-memory pagination store
  - `M9-A2_RESULT = NOT_STARTED` — unified order refresh setting + order detail core
  - `M9-A3_RESULT = NOT_STARTED` — rough functional entry/list/detail wiring
  - `M9-A4_RESULT = NOT_STARTED` — real-device functional validation
- `M9-B_RESULT = NOT_STARTED` — 我的套餐
- `M9-C_RESULT = NOT_STARTED` — 已订业务
- `M9-D_RESULT = NOT_STARTED` — 积分
- `M9-E_RESULT = NOT_STARTED` — 账单
- `M9-F_RESULT = NOT_STARTED` — 返费/赠费
- `M9-G_RESULT = NOT_STARTED` — 资费专区
- `M9-H_RESULT = NOT_STARTED` — 视频彩铃

Minimum supported Android version remains **Android 11 / API 30**.

Visual refinement remains deferred until the later page-by-page visual pass.

## Frozen iOS My Order source truth

| iOS source | SHA-256 | Role |
| --- | --- | --- |
| `Models/MyOrderModels.swift` | `41c664d7a58bd3fe09bedfa5ace7c7a19da66d1148740ee9bd16b352ed6cfa7e` | order/member/action/page models and derived presentation semantics |
| `Models/MyOrderDetailModels.swift` | `c5136fb54822c912b756f2cfa7d322bdc0f71c9a649fecac730cec3e10f90d5c` | hosted-order-detail snapshot/section/field/parser truth |
| `Services/MyOrderClient.swift` | `840dbef92c5a21ff0105fe6393115e4feacdb9aa192220877424ce4a96829634` | order list endpoint, request, cookie/session and parser truth |
| `Stores/MyOrderStore.swift` | `57fac1215064343421a200dea535b4c6ea6b89d9fe3dbc5681800ccae5f2e7de` | account-scoped list pagination and entry-refresh behavior |
| `Stores/MyOrderDetailStore.swift` | `1884d4d7864fbaf330c3096e6da0dbb6790b6a6ec38921cbc05c55b44f44ab33` | detail loading/fallback state truth |
| `Views/MyOrderView.swift` | `55cdfa1bd8bfabc54528050a11687be73b0bbb5d95d2dc7fdf051cdf09c4799c` | list interaction / load-more behavior truth |
| `Views/MyOrderDetailView.swift` | `ba0079686575c253fd0a1bbb1cc0336dad9ec0b865b131031017eaf88d13cea0` | detail functional destination truth |
| `Views/OtherBusinessView.swift` | `44ebb6c64a1729f0781d02078328c16932096b6f327762d2f0cd0002adf6a8fc` | Other Business entry ordering / My Order entry truth |

## M9-A1 accepted boundary

### Existing source-aligned model authority

`core:model/MyOrderModels.kt` already existed from M2 and is reused as the sole My Order model authority. M9-A1 deliberately did not create a duplicate list-model hierarchy.

### Real order-list client

`UnicomMyOrderClient` implements the source-derived order-list contract:

- endpoint: `https://m.client.10010.com/mobileservicequery/order/newQueryOrder`;
- POST `application/x-www-form-urlencoded` with a millisecond `timestamp` query parameter;
- `current_page = max(1, page)`;
- `page_size = max(1, pageSize)`;
- `loginNumber = lowercase MD5(digits-only mobile)`;
- default page size `15`;
- 20-second transport timeout;
- source headers including `Origin=https://img.client.10010.com`, matching Referer / Accept-Language, and iPhone-like `unicom{version:iphone_c@12.1400}` user-agent semantics;
- Set-Cookie mutations are merged through the existing M4 `UnicomCookieCodec`;
- `respCode` / `code`, `respDesc` / `dsc` / `message`, `timeYear`, and `respData` source fields are preserved;
- `respData` accepts either an array or a JSON string containing an array;
- `hasMore` is true only when returned order count is at least the requested page size;
- session-expiry recovery reuses existing M4 `UnicomAPIClient.activateSession(...)`, then retries the order request once.

### M5 credential lifecycle

`MyOrderAccountCredentialLifecycle` is the only secure request boundary for M9-A1:

- reads `AccountCredentials` only from the existing M5 `CredentialStore`;
- executes the My Order request through a validator/client boundary;
- immediately saves renewed Cookie/appID/token_online to the same M5 store;
- strips `updatedCredentials` before returning the business result to ordinary M9 state.

No Cookie, token, password, SMS code or real account fixture is committed to Git.

### Source-equivalent in-memory store — no invented disk cache

The iOS `MyOrderStore.swift` does **not** persist an order-list disk cache. Android therefore intentionally does not create one.

`DefaultMyOrderStore` preserves the relevant source semantics:

- one `StateFlow<MyOrderStoreState>`;
- changing active account clears prior account orders/paging state before loading the new account;
- initial load and manual refresh replace page 1;
- load-more requests advance `nextPage` only after success;
- `loadMoreIfNeeded` triggers only for the current last order;
- duplicate order IDs are removed while preserving first-seen order order;
- stale asynchronous results cannot overwrite a newer account generation;
- initial and load-more loading states remain distinct;
- prior business data is not written to disk.

The iOS `orders.refreshOnEntry` default is `true`. M9-A1 exposes that behavior through `MyOrderEntryRefreshPolicy` with source-default `true`, but **persistent integration into the single Android `SettingsRepository` is intentionally deferred to M9-A2** rather than creating a second settings authority.

## Accepted implementation / CI

Accepted implementation head:

`514e3d2005a4253385beff010834c85d3c76d037`

Android M9 Other Business run `32966913358` completed successfully:

- M9-A1 source/static boundary — success;
- My Order request/header/MD5/Cookie tests — success;
- session-expiry → existing M4 activation → retry test — success;
- M5 renewed-credential save-and-strip test — success;
- My Order entry-policy/pagination/dedup/account-switch tests — success;
- all existing core/data/app unit-test regression — success;
- Debug assembly — success;
- Release assembly — success;
- `android-m9-my-order-core` status publication — success;
- failure gate — skipped as expected after successful verification.

## Deferred from M9-A1

M9-A1 intentionally does not yet implement:

- persistence of `orders.refreshOnEntry` in the existing tolerant `SettingsRepository`;
- hosted My Order detail parser/store/WebView bridge;
- Other Business Compose entry and My Order list/detail screens;
- real-device My Order functional evidence;
- final visual parity.

These are ordered into M9-A2 → M9-A4 so bottom/business behavior remains complete before page refinement.

## Next

`NEXT = Android-M9-A2 — My Order Settings / Detail Core`
