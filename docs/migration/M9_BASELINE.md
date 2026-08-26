# Android-M9 — Other Business

## Status

`M9_RESULT = IN_PROGRESS`

Substages:

- `M9-A_RESULT = IN_PROGRESS` — 我的订单
  - `M9-A1_RESULT = PASS / CLOSED` — order list client + M5 credential lifecycle + in-memory pagination store
  - `M9-A2_RESULT = PASS / CLOSED` — unified order refresh setting + order detail core
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

The iOS `orders.refreshOnEntry` default is `true`. M9-A1 exposed that behavior through `MyOrderEntryRefreshPolicy` with source-default `true`; persistence was intentionally deferred to M9-A2.

### M9-A1 accepted implementation / CI

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

## M9-A2 accepted boundary

### One persistent order-refresh setting authority

M9-A2 extends the existing tolerant `SettingsRepository` rather than creating a My Order-specific settings store:

- `OrderRefreshPolicy(refreshOnEntry = true)` matches the frozen iOS source default;
- `orderRefreshPolicy`, `loadOrderRefreshPolicy()` and `saveOrderRefreshPolicy(...)` live beside the existing quota/balance/ordered-business/phone-bill/integral policies;
- the `orders` JSON domain is decoded with tolerant defaults and merged without replacing unrelated refresh-policy domains;
- malformed or older policy payloads continue to fall back through the existing codec/migration path;
- `SettingsMyOrderEntryRefreshPolicy` makes the M9 list store consume this single persisted authority.

The cold-launch key typo found by CI was corrected in commit `410bed13264a02a04269007fa81c2a7004a738ec`; this was a constant-name correction only and did not change the intended refresh behavior.

### Platform-neutral order-detail core

M9-A2 adds the business/detail layer while still deferring the Android WebView/Compose destination itself to M9-A3:

- `MyOrderDetailRequestFactory` derives the detail mode and order identifier from the frozen iOS action URL semantics;
- renewal orders recognize renewal/broad-order detail URLs and business/storefront orders recognize the `omo.10010.com` detail path;
- unsupported order types fail explicitly instead of inventing a payment/detail fallback;
- encoded `%26` / `%3D` query fragments are normalized before extracting `orderNo`, `orderId` or `serviceType`;
- the business ready gate is `omo.10010.com` + `dbh-evaluate-fe`;
- the renewal ready gate is `upayxx.10010.com` + `broadordersdetail`, excluding `broadordersdetailinit`;
- business detail uses `/udbh/rest/portal/qryEvaluateOrderInfoByOrderId` with `sourcePage = CJ_SOU_20000` and conditionally requests `/udbh/rest/portal/querySubProducts` with `pageSize = 100` for `businessType == 120`;
- renewal detail uses `/npfwap/NpfMobAppQuery/broadRenewalOrderHandle/broaRenewalInfo` and preserves the source default `serviceType = 29` when the action does not supply one;
- `MyOrderDetailParser` maps the business and renewal bridge payloads into Android model content while preserving server errors/empty-detail failures;
- `MyOrderDetailStore` keeps the cookie out of ordinary state, requires the M5-owned credential lifecycle before hosted loading, and rejects stale bridge results whose request ID no longer matches the active request.

### Credential boundary

`MyOrderDetailCredentialLifecycle` remains in `core:login` and obtains the Cookie only through the existing M5 `CredentialStore`. M9-A2 does not persist or expose carrier credentials in business state, Compose state, tests or Git fixtures.

### CI hardening discovered during A2

The A2 full-regression gate exposed three compatibility/coverage defects and they were fixed before acceptance:

- `5d9b17aa8b5673ca5f24a90c1d5924dc710f69a8` updated the balance-test fake to implement the newly extended `SettingsRepository` contract;
- `ab93aa1e49c6374ac494e5f17f16f495e313ddd2` expanded the M9 workflow path coverage to include `app/**` and `data/balance/**`, so downstream settings consumers and the upcoming App wiring cannot bypass the M9 gate;
- `d9c864fb3675320a62ed29b765117088b696b173` added `core:security` only to the `data:myorder` **test** classpath because the test fake implements `CredentialStore`; the production module dependency boundary was not widened.

### M9-A2 accepted implementation / CI

Primary A2 implementation commit:

`0b5101e752cc46f87cf38c93c57ff5f7e3e2200d`

Accepted verified repository head:

`d9c864fb3675320a62ed29b765117088b696b173`

Android M9 Other Business run `32973038127` completed successfully:

- `Verify M9-A1/A2 My Order source boundary` — success;
- `Run M9-A2 regression` — success;
- `Publish M9-A2 status` — success;
- failure gate — skipped as expected;
- complete job — success.

Therefore `M9-A2_RESULT = PASS / CLOSED`.

## Deferred after M9-A2

M9-A2 intentionally does not yet implement:

- Other Business Compose entry for My Order;
- My Order account selector/list/pagination UI wiring;
- Android WebView adapter for the frozen detail bridge contract;
- business/renewal order-detail functional destination rendering;
- real-device My Order functional evidence;
- final visual parity.

These remain ordered as M9-A3 → M9-A4. Final app-owned visual refinement stays deferred until the later page-by-page pass.

## Next

`NEXT = Android-M9-A3 — My Order Rough Functional Entry / List / Detail Wiring`
