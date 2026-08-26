# Android-M8 — Comprehensive Business

## Status

`M8_RESULT = IN_PROGRESS`

Substages:

- `M8-A_RESULT = PASS / CLOSED`
- `M8-B_RESULT = PASS / CLOSED`
- `M8-C_RESULT = PASS / CLOSED`
- `M8-D_RESULT = NOT_STARTED`
- `M8-E_RESULT = NOT_STARTED`

Minimum supported Android version remains **Android 11 / API 30**.

Accepted implementation heads:

- M8-A: `896c923f8af877c16d73d94b2a620cb6f291f27c`
- M8-B: `f8d74c6de77b57e729f02597c26306d0097d04e1`
- M8-C: `8649aa57ecb970fe423956e6adaf31285abee2fa`

## Source-derived M8 boundary

The iOS comprehensive root is an aggregation page. It does not actively refresh carrier data. It reads already cached quota/balance/voice data and cached integral points, while ordered business, phone bill and integral use independent clients/stores.

M8 is therefore split as:

1. **M8-A — comprehensive business model / refresh-policy / network-contract foundation — PASS / CLOSED**
2. **M8-B — ordered business client + cache + store — PASS / CLOSED**
3. **M8-C — phone bill client + cache + store — PASS / CLOSED**
4. **M8-D — integral client + cache + store**
5. **M8-E — comprehensive root aggregation / entries / final functional closure**

Visual refinement remains deferred until the later page-by-page visual pass.

## Frozen iOS source truth

| iOS source | SHA-256 | Role |
| --- | --- | --- |
| `Models/OrderedBusinessModels.swift` | `d003575500b18bcffe3defa112ebc9931c40ccecd20e158ffeda347b52fa9121` | ordered-business snapshot/section/item models |
| `Models/PhoneBillModels.swift` | `6669e13f8f5fd0444922f3b654b9f1c5f504e9c01b0089f7ab221264d155c72c` | bill month/snapshot/summary/user/item models |
| `Models/IntegralModels.swift` | `c42a2819035525eca03412e222130aac048e73948a538d5f568aca31fa194cb0` | integral overview/month/detail/query models |
| `Models/AppRefreshLogicPolicyModels.swift` | `ad1ed089d7c3079cabbbe38702d0d51758e5b3c979b4686711603fe408d23f22` | M8 refresh policy defaults and raw enum values |
| `Services/OrderedBusinessClient.swift` | `176c30e4cc54f69ee3cf4db7dbcc6142a8b435aec31f9a6075bc89e54c42be37` | ordered-business request/session/parser truth |
| `Services/OrderedBusinessDiskCache.swift` | `b994d623b119ab7dc765026c9256d75eb1492fca7f9d658676eff0290797e0a0` | ordered-business disk-cache truth |
| `Stores/OrderedBusinessStore.swift` | `bb3955be81e6689cab1f1584e4ecff4fd8fc6a05644e99dffcbc05435d7f0107` | ordered-business state/refresh/cache policy truth |
| `Services/PhoneBillClient.swift` | `f86abeaa228aded4c28a52880d3fa848faee1e65253e04c7ddf30114eeaffb0e` | phone-bill endpoints and session-recovery contract |
| `Services/IntegralClient.swift` | `682c967406a4ca25105d0e7fc14aa1664a9b0cf655a70df275821291ed92a13f` | integral endpoints/source and session-recovery contract |
| `Stores/ComprehensiveBusinessStore.swift` | `988db5155d634ea895a56f0f1b5e9cd8625385eb9bf9a4da8207b73c56b88fb7` | comprehensive root cached-points aggregation |
| `Views/ComprehensiveBusinessView.swift` | `008dcb837f41873be60d098c0c66242122e2cd9ee310a1dfaf4f837f0c02cbdf` | root behavior: aggregate cached data, no proactive network refresh |

## M8-A accepted foundation

Android reuses the repository's source-aligned ordered-business, phone-bill and integral models instead of introducing duplicate model hierarchies.

- ordered business retains snapshot/section/item structure and computed total count;
- phone bill retains parser version `4` and source monetary-string boundary;
- integral retains parser version `1`, source section queries and cache-key semantics;
- the single tolerant schema-version-3 `SettingsRepository` document owns ordered-business, phone-bill and integral refresh policies while preserving unknown top-level domains;
- M5 Keystore remains the only credential authority.

Frozen endpoint roots remain:

- ordered business: `https://mxx.client.10010.com`, recovery via `https://loginxx.10010.com/mobileService/onLine.htm`;
- phone bill: `https://m.client.10010.com`;
- integral: `https://activity.10010.com`, source `ZXGS97000017640,003`.

## M8-B accepted ordered-business semantics

### Real client and session recovery

`UnicomOrderedBusinessClient` implements the source-derived two-step business request:

1. POST `/servicebusiness/newOrdered/provincialAlloc` to `mxx.client.10010.com`;
2. apply allocation `Set-Cookie` mutations to the business Cookie;
3. POST `/servicebusiness/newOrdered/queryOrderRelationship` with `type=1`;
4. parse the ordered-business snapshot.

Business requests preserve the source `Origin` / `Referer` against `imgxx.client.10010.com` and use the existing M4 `UnicomHTTPClient`, `UnicomCookieCodec` and response/session classification instead of a second HTTP stack.

When the business session expires:

- saved `appId` and `token_online` are required;
- recovery uses `https://loginxx.10010.com/mobileService/onLine.htm`;
- source version remains `iphone_c@12.1300`;
- source device/session form fields remain `step=welcom`, `isFirstInstall=1`, `flushkey=1`, source sim-operator and voip-token values;
- device-code lookup is `d_deviceCode -> deviceCode -> devicedId -> UUID fallback`;
- `deviceId` is SHA-256 of deviceCode and `uniqueIdentifier` is `ios` plus its first 32 lowercase hex characters;
- recovery `Set-Cookie`, appId and token_online changes are returned as renewed credentials;
- a second expired business request after successful loginxx recovery fails closed instead of looping.

### Ordered-business parser

The Android parser preserves the source sections:

- 主套餐
- 其他已订产品
- 合约
- 套餐内业务与优惠
- 增值业务
- 宽带/IPTV 产品 · `<number>`
- 功能服务
- 异常或失效业务

Stable item IDs use the source product identifier plus name/start/end when available. Fallback IDs hash canonical primitive source fields. Duplicate IDs inside a section are removed. Snapshot title uses `commdityName` with `commodityName` fallback, and queryTime/fetchedAt are retained.

### M5 credential boundary

`OrderedBusinessAccountCredentialLifecycle` is the only M8-B bridge to account credentials:

- reads `AccountCredentials` from the existing M5 `CredentialStore` by account UUID;
- calls the real ordered-business validator/client;
- immediately persists any renewed credentials back to the same M5 store;
- strips `updatedCredentials` from the result before it enters ordinary M8 store/cache state;
- missing credentials fail before any carrier request.

No ordered-business cache or store serializes Cookie, appID, token_online, password, SMS code or captcha result token.

### Atomic cache

`AndroidOrderedBusinessDiskCache` stores only source-derived snapshots under app-private files:

`filesDir/ordered-business/ordered-business-snapshots.json`

The cache:

- is keyed by account UUID string;
- uses Android `AtomicFile` plus `fd.sync()` before finish-write;
- skips invalid UUID keys while loading;
- fails closed to an empty cache when the file/document cannot be decoded;
- contains no account credential material.

### Store / refresh policy

`DefaultOrderedBusinessStore` owns a single `StateFlow<OrderedBusinessStoreState>` with per-account idle/loading/failed/warning states.

Accepted source behavior:

- cache is loaded once;
- `cachePreferred`: use existing cache, and only auto-query missing cache when `noCacheAutoQuery` is enabled;
- `refreshWhenExpired`: source default validity is 12 hours, time rollback forces refresh, and missing cache still respects `noCacheAutoQuery`;
- `everyEntry`: always query;
- `manualOnly`: never query automatically;
- duplicate concurrent refresh for the same account is suppressed;
- refresh-all is serialized and uses source default `refreshAllAccountGapSeconds=1`;
- network failure preserves the previous successful snapshot and publishes failed state;
- successful network response followed by disk-write failure preserves the new in-memory snapshot and publishes warning state;
- account reconciliation removes orphan cached snapshots and persists the filtered cache;
- cancellation returns affected state to idle / stops the remaining batch without creating another refresh authority.

`AndroidOrderedBusinessStores.create(...)` composes the production M5 credential lifecycle, atomic disk cache, persisted settings policy and store without introducing UI or a second account/session repository.

## M8-C accepted phone-bill semantics

### Real client and session recovery

`UnicomPhoneBillClient` reuses the existing M4 HTTP/Cookie/session stack and implements the source-defined phone-bill requests against `https://m.client.10010.com`:

- POST `/serviceimportantbusiness/phoneBillNew/queryMonths` with the source blank compatibility form fields;
- POST `/serviceimportantbusiness/phoneBillNew/queryDetail` with `month=<BillMonth.key>`;
- 20-second transport timeout;
- source session activation through `/mobileService/onLine.htm` with `appId`, `token_online` and `version=iphone_c@9.0100`;
- activation applies returned `Set-Cookie` mutations to the existing business Cookie and preserves renewed appId/token_online;
- activation is attempted only for source-equivalent session/login/cookie failures and is not used as a generic retry mechanism.

The detail parser keeps `PhoneBillSnapshot.CURRENT_PARSER_VERSION = 4`, source field fallbacks, nested user/account sections, stable item identity and two-decimal monetary normalization.

### M5 credential boundary

`PhoneBillAccountCredentialLifecycle` is the only M8-C credential bridge:

- reads credentials from the existing M5 `CredentialStore` by account UUID;
- performs months/detail requests through the phone-bill client;
- immediately saves any renewed Cookie/appID/token_online back to M5;
- strips `updatedCredentials` before returning data to ordinary M8 state/cache;
- fails before carrier access when credentials are absent.

No phone-bill cache or store persists Cookie, appID, token_online, password, SMS code or captcha material.

### Atomic cache and timing policy

`AndroidPhoneBillDiskCache` stores account/month snapshots at:

`filesDir/phone-bill/phone-bill-snapshots.json`

Accepted persistence behavior:

- account UUID -> month key -> `PhoneBillSnapshot` document shape;
- Android `AtomicFile` with synchronized read-modify-write and `fd.sync()` before finish-write;
- orphan account pruning and per-month removal;
- decode failure fails closed to an empty cache rather than exposing partial data.

`PhoneBillCachePolicy` preserves the source timing rules:

- visible window: current month plus the preceding 12 months (`13` total);
- current-month cache default: `10` minutes;
- historical cache default: `15` days;
- historical expiry is additionally capped by the next monthly recheck boundary, default day `2` at `08:00`;
- month calculations use `Asia/Shanghai`;
- future timestamps and parser-version mismatches are not considered fresh.

### Current-month isolation and historical sharing

Current-month snapshots are account-local. They are accepted only when the snapshot bill membership proves that it belongs to the selected account.

Historical snapshots may be reused across local accounts only through `PhoneBillHistoricalCacheResolver` when all source conditions are satisfied:

- the target/source local mobile identity is uniquely resolvable among local accounts;
- the cached snapshot is fresh for the requested historical month;
- the source identity appears exactly once in the bill-member list;
- the target identity appears exactly once in the bill-member list;
- current-month data is never shared;
- when both own and shared candidates qualify, the newest qualifying snapshot wins.

This sharing is based on the bill-member relationship returned by the phone-bill service; it does not reuse the M6 balance representative/grouping semantics.

### Store / concurrency semantics

`DefaultPhoneBillStore` owns a single `StateFlow<PhoneBillStoreState>` with months, selected/requested/failed month, snapshot and idle/loading/loaded/failed state.

Accepted behavior:

- load prunes orphan accounts and checks a fresh, account-owned current-month cache before carrier access;
- otherwise it fetches the server month list, restricts it to the 13-month window, requires the current month and fetches current detail;
- current-month selection uses only a valid own-account cache;
- historical selection first resolves the best qualifying own/shared cache;
- network writes for the same historical month are globally serialized across store instances by `PhoneBillHistoricalQueryCoordinator`;
- manual historical refresh bypasses the pre-network shared-cache shortcut but still participates in the same-month serialization boundary;
- while a request is active, only the latest queued month-selection intent is retained;
- request failure leaves the previous successful snapshot available;
- successful network data remains available in memory even when disk persistence fails.

`AndroidPhoneBillStores.create(...)` composes the production M5 credential lifecycle, atomic disk cache, Settings-backed policy and existing `AccountRepository` without introducing another account/session authority.

## Security / persistence boundary

- M5 Keystore remains the only account-credential authority;
- ordinary M8 caches contain business snapshots only;
- no Cookie/appID/token_online/password/SMS code/captcha token storage is introduced;
- `android:allowBackup="false"` remains required;
- Android minimum remains API 30.

## Visual / device boundary

M8-A, M8-B and M8-C contain no final visual refinement and require **no real-device screenshots**.

When M8-E later performs comprehensive functional-page acceptance, the exact screenshots needed will be stated before that substep. Final visual parity remains deferred until the page-by-page visual pass.

## CI acceptance

### M8-A

Accepted M8-A implementation head `896c923f8af877c16d73d94b2a620cb6f291f27c` passed every triggered M2-M8 workflow.

### M8-B implementation head

Accepted implementation head `f8d74c6de77b57e729f02597c26306d0097d04e1` passed every triggered M1-M8 workflow:

- M1 Build — run `32851965395` — success;
- M2 Models — run `32851965522` — success;
- M3 Parsers — run `32851965591` — success;
- M4 Network — run `32851965468` — success;
- M5 Login Security — run `32851965430` — success;
- M6 Persistence Refresh — run `32851965405` — success;
- M7 Flow Voice Functional — run `32851965525` — success;
- M8 Comprehensive Business — run `32851965465` — success.

M8-B dedicated acceptance includes:

- ordered-business client/session/cache/store static gate;
- direct allocation/query and Cookie-mutation tests;
- loginxx session recovery and renewed-credential tests;
- M5 credential save-and-strip tests;
- atomic cache codec/store tests;
- entry-policy, expiry, failure retention, write-warning and reconcile/batch-gap tests;
- all existing core/data/app unit tests;
- Debug assembly;
- Release assembly;
- `android-m8-ordered-business` status publication;
- failure gate skipped as expected after successful regression.

### M8-C implementation head

Initial implementation head `4c7621e6c404b0ccd748db8a466ac5a5835ed208` passed the M8-C static boundary but correctly failed the full regression because the new `data:phonebill` unit-test source set lacked a direct `:core:security` classpath dependency for `CredentialStore`. No M8-C closure was claimed from that run.

The minimal classpath correction produced accepted implementation head `8649aa57ecb970fe423956e6adaf31285abee2fa`.

M8-C Comprehensive Business run `32924827574` completed successfully and includes:

- M8-C client/session/cache/store static gate;
- phone-bill months/detail endpoint and compatibility-form tests;
- `iphone_c@9.0100` session recovery, Cookie mutation and renewed-credential tests;
- M5 credential save-and-strip tests;
- parser-v4 detail/money/member tests;
- atomic snapshot-codec/cache coverage;
- 13-month, parser compatibility, historical-sharing and fresh-current-cache tests;
- all existing `core:model`, `core:parser`, `core:network`, `core:security`, `core:login`, `core:storage`, `data:account`, `data:refresh`, `data:settings`, `data:balance`, `data:orderedbusiness`, `data:phonebill` and app unit tests;
- Debug assembly;
- Release assembly;
- `android-m8-phone-bill` success status publication;
- failure gate skipped as expected after the successful regression.

The same accepted head also published successful M2, M6 and M7 status contexts on the workflows triggered by the classpath-only correction.

## Next

`NEXT = Android-M8-D — Integral Client + Cache + Store`
