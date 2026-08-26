# Android-M8 — Comprehensive Business

## Status

`M8_RESULT = IN_PROGRESS`

Substages:

- `M8-A_RESULT = PASS / CLOSED`
- `M8-B_RESULT = PASS / CLOSED`
- `M8-C_RESULT = PASS / CLOSED`
- `M8-D_RESULT = PASS / CLOSED`
- `M8-E_RESULT = NOT_STARTED`

Minimum supported Android version remains **Android 11 / API 30**.

Accepted implementation heads:

- M8-A: `896c923f8af877c16d73d94b2a620cb6f291f27c`
- M8-B: `f8d74c6de77b57e729f02597c26306d0097d04e1`
- M8-C: `8649aa57ecb970fe423956e6adaf31285abee2fa`
- M8-D: `c091cb87036fa620bc0289312ec08e122e106ed9`

## Source-derived M8 boundary

The iOS comprehensive root is an aggregation page. It does not actively refresh carrier data. It reads already cached quota/balance/voice data and cached integral points, while ordered business, phone bill and integral use independent clients/stores.

M8 is therefore split as:

1. **M8-A — comprehensive business model / refresh-policy / network-contract foundation — PASS / CLOSED**
2. **M8-B — ordered business client + cache + store — PASS / CLOSED**
3. **M8-C — phone bill client + cache + store — PASS / CLOSED**
4. **M8-D — integral client + cache + store — PASS / CLOSED**
5. **M8-E — comprehensive root aggregation / entries / final functional closure**

Visual refinement remains deferred until the later page-by-page visual pass.

## Frozen iOS source truth

| iOS source | SHA-256 | Role |
| --- | --- | --- |
| `Models/OrderedBusinessModels.swift` | `d003575500b18bcffe3defa112ebc9931c40ccecd20e158ffeda347b52fa9121` | ordered-business snapshot/section/item models |
| `Models/PhoneBillModels.swift` | `6669e13f8f5fd0444922f3b654b9f1c5f504e9c01b0089f7ab221264d155c72c` | bill month/snapshot/summary/user/item models |
| `Models/IntegralModels.swift` | `c42a2819035525eca03412e222130aac048e73948a538d5f568aca31fa194cb0` | integral overview/month/detail/query models |
| `Models/AppRefreshLogicPolicyModels.swift` | `ad1ed089d7c3079cabbbe38702d0d51758e5b3c979b4686711603fe408d23f22` | M8 refresh-policy defaults and raw enum values |
| `Services/OrderedBusinessClient.swift` | `176c30e4cc54f69ee3cf4db7dbcc6142a8b435aec31f9a6075bc89e54c42be37` | ordered-business request/session/parser truth |
| `Services/OrderedBusinessDiskCache.swift` | `b994d623b119ab7dc765026c9256d75eb1492fca7f9d658676eff0290797e0a0` | ordered-business disk-cache truth |
| `Stores/OrderedBusinessStore.swift` | `bb3955be81e6689cab1f1584e4ecff4fd8fc6a05644e99dffcbc05435d7f0107` | ordered-business state/refresh/cache policy truth |
| `Services/PhoneBillClient.swift` | `f86abeaa228aded4c28a52880d3fa848faee1e65253e04c7ddf30114eeaffb0e` | phone-bill endpoints and session-recovery truth |
| `Services/IntegralClient.swift` | `682c967406a4ca25105d0e7fc14aa1664a9b0cf655a70df275821291ed92a13f` | integral endpoints/source/session-recovery truth |
| `Services/IntegralDiskCache.swift` | `e7e38808e703b1fb7167e93442f84dd0ba586d82b667c3e5144d7ae3ef563546` | integral cache and refresh-cycle truth |
| `Stores/IntegralStore.swift` | `773f1bdee663bd41d310c08f59c89359293396d002004f09cd3a3ddb066ac86c` | integral state/detail/refresh truth |
| `Stores/ComprehensiveBusinessStore.swift` | `988db5155d634ea895a56f0f1b5e9cd8625385eb9bf9a4da8207b73c56b88fb7` | comprehensive cached-points aggregation |
| `Views/ComprehensiveBusinessView.swift` | `008dcb837f41873be60d098c0c66242122e2cd9ee310a1dfaf4f837f0c02cbdf` | root behavior: aggregate cached data, no proactive network refresh |

## M8-A accepted foundation

Android reuses the repository's source-aligned ordered-business, phone-bill and integral models instead of introducing duplicate model hierarchies. The single tolerant `SettingsRepository` owns the three M8 refresh-policy domains while preserving unknown settings domains. M5 Keystore remains the only account credential authority.

Frozen endpoint roots:

- ordered business: `https://mxx.client.10010.com`;
- phone bill: `https://m.client.10010.com`;
- integral: `https://activity.10010.com`, source `ZXGS97000017640,003`.

## M8-B accepted ordered-business semantics

M8-B implements the real ordered-business allocation/query flow, `loginxx.10010.com/mobileService/onLine.htm` recovery, source `iphone_c@12.1300` semantics, stable section/item parsing, M5 credential renewal and app-private `AtomicFile` persistence. `DefaultOrderedBusinessStore` preserves cachePreferred / refreshWhenExpired / everyEntry / manualOnly policy behavior, duplicate suppression, serial refresh-all gap, orphan reconciliation, old-cache retention on request failure and in-memory success with warning on cache-write failure.

Accepted M8-B implementation head: `f8d74c6de77b57e729f02597c26306d0097d04e1`.

## M8-C accepted phone-bill semantics

M8-C implements the real months/detail requests, parser version `4`, standard `iphone_c@9.0100` activation recovery, M5 credential save-and-strip, app-private `AtomicFile` persistence and the source timing contract:

- visible current month plus preceding 12 months (`13` total);
- current-month cache `10` minutes;
- historical cache `15` days;
- historical refresh capped by the next day-2 08:00 Asia/Shanghai recheck boundary;
- current month stays account-local;
- historical cache may be reused only when bill membership uniquely proves the source/target relationship;
- same historical month network writes are serialized across store instances;
- prior successful state survives request failure; successful network data remains visible if local persistence fails.

Accepted M8-C implementation head: `8649aa57ecb970fe423956e6adaf31285abee2fa`; acceptance run: `32924827574`.

## M8-D accepted integral semantics

### Real client and account guard

`UnicomIntegralClient` implements the source-derived requests against `https://activity.10010.com`:

- balance: POST `/welfare-mall-front/mobile/show/bj2205/v2/1` with `position=123`, `isTermShow=1`;
- recent months: POST `/welfare-mall-front/new/integral/queryMonthlyList/v1` with `from=ZXGS97000017640,003`;
- details: POST `/welfare-mall-front/new/integral/querySummaryList/v1` with `scoreType`, `typeChar`, `from`, and optional `yearMonth`;
- 20-second integral transport timeout;
- source `Origin=https://img.client.10010.com`, `Accept-Language=zh-CN,zh-Hans;q=0.9` and `iphone_c@12.1400` user-agent contract.

Before any integral carrier request, the selected mobile is compared to integral Cookie identity using `c_mobile` first and `u_account` second. A mismatch fails immediately with the source account-mismatch message and is deliberately not treated as a session-expiry condition.

Overview parsing preserves `IntegralSnapshot.CURRENT_PARSER_VERSION = 1` and source type mapping: total available (`1`), communication (`2`), reward (`3`), expired/expiring reward (`4`), expiring-this-month (`5`), coupon count (`6`), expiring reward (`7`), expiring communication (`8`), directional (`9`) and expiration day (`10`). Missing total available points fails with the source error.

### Session and M5 credential boundary

Integral session expiry reuses the existing M4 `UnicomAPIClient.activateSession(...)` path instead of a second login implementation. The standard `/mobileService/onLine.htm` activation semantics therefore remain `iphone_c@9.0100`.

`IntegralAccountCredentialLifecycle`:

- reads `AccountCredentials` only from M5 `CredentialStore`;
- calls the integral validator/client;
- persists any renewed Cookie/appID/token_online back to the same M5 store;
- strips `updatedCredentials` before returning the result to ordinary M8 state/cache;
- fails before carrier access when credentials are missing.

### Atomic cache and refresh policy

`AndroidIntegralDiskCache` stores only integral business records at:

`filesDir/integral/integral-snapshots.json`

The document is keyed by account UUID and contains `snapshot`, query-keyed `details`, and `refreshCycleKey`. Android `AtomicFile`, synchronized read-modify-write and `fd.sync()` are used. Invalid/malformed persisted data fails closed rather than exposing partial state. No account credential material is written to this cache.

`IntegralCachePolicy` preserves the source policy:

- timezone: `Asia/Shanghai`;
- default automatic mode: monthly;
- monthly reset: day `2`, hour `08:00`, with day/hour clamping equivalent to iOS;
- fixed interval mode defaults to `24` hours and refreshes on clock rollback;
- manual-only disables automatic refresh;
- `checkOnEntry` gates automatic entry checks;
- no-cache automatic query occurs only when automatic refresh is enabled, mode is not manual-only and check-on-entry is enabled;
- parser-version mismatch always requires refresh when automatic refresh is allowed.

### Store / detail semantics

`DefaultIntegralStore` owns one `StateFlow<IntegralStoreState>` and preserves source loading, loaded, manual-required and failed behavior.

Accepted behavior:

- valid cached overview is exposed before an optional automatic refresh;
- switching account resets source-specific state before reading its cache;
- missing cache under manual-only / disabled entry auto-query becomes `ManualRequired`;
- manual refresh updates overview and, when requested, force-refreshes the active detail query;
- detail cache is keyed by `IntegralDetailQuery.cacheKey` (`scoreType-typeChar-yearMonth/all`);
- a non-forced detail load reuses the cached query result;
- overview refresh creates a new record with `details = emptyMap()`, intentionally invalidating stale detail data;
- new overview data is committed to in-memory state only after disk save succeeds, so a persistence failure preserves the previous successful overview;
- detail network success is kept in memory even if the subsequent detail cache write fails, while an error message is exposed;
- cancellation and duplicate refresh suppression do not create another refresh/session authority.

`AndroidIntegralStores.create(...)` composes the M5 request lifecycle, app-private cache and persisted Settings policy without introducing UI or a second account repository.

## Security / persistence boundary

- M5 Keystore remains the only account-credential authority;
- ordinary M8 caches contain business snapshots/details only;
- no Cookie/appID/token_online/password/SMS code/captcha token storage is introduced;
- `android:allowBackup="false"` remains required;
- Android minimum remains API 30.

## Visual / device boundary

M8-A, M8-B, M8-C and M8-D contain no final visual refinement and require **no real-device screenshots**.

M8-E is the comprehensive root functional integration stage. Before any M8-E substep that actually needs real-device evidence, the exact iOS/Android pages to capture must be stated explicitly. Final visual parity remains deferred until the later page-by-page visual pass.

## CI acceptance

### M8-A

Accepted implementation head `896c923f8af877c16d73d94b2a620cb6f291f27c`.

### M8-B

Accepted implementation head `f8d74c6de77b57e729f02597c26306d0097d04e1`; dedicated ordered-business gate, tests, full regression and Debug/Release assembly passed.

### M8-C

Accepted implementation head `8649aa57ecb970fe423956e6adaf31285abee2fa`; M8 run `32924827574` passed the M8-C static gate, phone-bill tests, all existing core/data/app tests, Debug assembly and Release assembly. `android-m8-phone-bill` was published as success.

### M8-D

Accepted implementation head `c091cb87036fa620bc0289312ec08e122e106ed9`.

M8 Comprehensive Business run `32926493346` completed successfully:

- M8-D integral client/session/cache/store static gate — success;
- integral endpoint/form/header/parser mapping tests — success;
- `c_mobile` / `u_account` mismatch guard test — success;
- standard M4 activation recovery test — success;
- M5 credential save-and-strip tests — success;
- monthly/fixed/manual refresh-policy tests, including clock rollback — success;
- cache/store tests for manual-required, old-overview retention, overview detail invalidation and detail-write warning — success;
- all existing core/data/app unit-test regression — success;
- Debug assembly — success;
- Release assembly — success;
- `android-m8-integral` status publication — success;
- failure gate — skipped as expected after successful verification.

The same accepted implementation head also published successful M2, M5, M6 and M7 status contexts on the workflows triggered by the M8-D change.

## Next

`NEXT = Android-M8-E — Comprehensive Root Aggregation / Entries / Final Functional Closure`
