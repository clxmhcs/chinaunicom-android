# Android-M8 — Comprehensive Business

## Status

`M8_RESULT = IN_PROGRESS`

Current substage:

- `M8-A_RESULT = PASS / CLOSED`

Minimum supported Android version remains **Android 11 / API 30**.

Accepted M8-A implementation head:

- `896c923f8af877c16d73d94b2a620cb6f291f27c`

## Source-derived M8 boundary

The iOS comprehensive root is an aggregation page. It does not actively refresh carrier data. It reads already cached quota/balance/voice data and cached integral points, while ordered business, phone bill and integral use independent clients/stores.

M8 is therefore split as:

1. **M8-A — comprehensive business model / refresh-policy / network-contract foundation — PASS / CLOSED**
2. **M8-B — ordered business client + cache + store**
3. **M8-C — phone bill client + cache + store**
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
| `Services/OrderedBusinessClient.swift` | `176c30e4cc54f69ee3cf4db7dbcc6142a8b435aec31f9a6075bc89e54c42be37` | ordered-business endpoints and session-recovery contract |
| `Services/PhoneBillClient.swift` | `f86abeaa228aded4c28a52880d3fa848faee1e65253e04c7ddf30114eeaffb0e` | phone-bill endpoints and session-recovery contract |
| `Services/IntegralClient.swift` | `682c967406a4ca25105d0e7fc14aa1664a9b0cf655a70df275821291ed92a13f` | integral endpoints/source and session-recovery contract |
| `Stores/ComprehensiveBusinessStore.swift` | `988db5155d634ea895a56f0f1b5e9cd8625385eb9bf9a4da8207b73c56b88fb7` | comprehensive root cached-points aggregation |
| `Views/ComprehensiveBusinessView.swift` | `008dcb837f41873be60d098c0c66242122e2cd9ee310a1dfaf4f837f0c02cbdf` | root behavior: aggregate cached data, no proactive network refresh |

## M8-A accepted source semantics

### Ordered business models

Android reuses the pre-existing source-aligned `OrderedBusinessModels.kt`; M8-A does not introduce a second business model hierarchy.

- `OrderedBusinessSnapshot`: optional title/queryTime, fetchedAt, sections;
- `totalCount` is the sum of all section item counts;
- section fields: id/title/icon/items;
- item fields: id/name/subtitle/fee/startDate/endDate.

### Phone bill models

Android reuses the pre-existing source-aligned `PhoneBillModels.kt`.

- `BillMonth.key` defaults to `year + two-digit month`;
- month title removes a leading zero and appends `月` when numeric;
- `PhoneBillSnapshot.currentParserVersion = 4`;
- user bill exposes the flattened list of all section items;
- monetary fields remain carrier-provided strings at this model boundary.

### Integral models

Android reuses the pre-existing source-aligned `IntegralModels.kt`.

- `IntegralSnapshot.currentParserVersion = 1`;
- month `yearMonth` is derived from the first two numeric groups of cycleID and validates month 1...12;
- detail item identity includes all source fields in stable order;
- section queries match iOS raw values:
  - communication: `scoreType=0`, `typeChar=3`;
  - reward: `scoreType=1`, `typeChar=3`;
  - expiring: `scoreType=2`, `typeChar=2`;
- detail cache key is `scoreType-typeChar-yearMonth|all`;
- monthly detail query uses `scoreType=2`.

### Refresh policies

Exact source defaults now exist in Android `SettingsRepository`:

- ordered business: `cachePreferred`, 12 hours, no-cache auto query disabled, refresh-all gap 1 second;
- phone bill: current month 10 minutes, historical 15 days, monthly recheck day 2 at 08:00;
- integral: automatic enabled, monthly cycle, day 2 at 08:00, fixed interval 24 hours, check on entry enabled.

The existing schema-version-3 refresh-policy document remains a single tolerant document. M8 saves preserve unknown top-level domains and existing quota/balance domains. `JsonPrimitiveCompat.kt` keeps nullable string extraction compatible with the repository's kotlinx.serialization version without treating JSON null as the literal string `"null"`.

### Frozen endpoint contract

Ordered business:

- root `https://mxx.client.10010.com`
- online `https://loginxx.10010.com/mobileService/onLine.htm`
- `/servicebusiness/newOrdered/provincialAlloc`
- `/servicebusiness/newOrdered/queryOrderRelationship`

Phone bill:

- root `https://m.client.10010.com`
- online `https://m.client.10010.com/mobileService/onLine.htm`
- `/serviceimportantbusiness/phoneBillNew/queryMonths`
- `/serviceimportantbusiness/phoneBillNew/queryDetail`

Integral:

- root `https://activity.10010.com`
- `/welfare-mall-front/mobile/show/bj2205/v2/1`
- `/welfare-mall-front/new/integral/queryMonthlyList/v1`
- `/welfare-mall-front/new/integral/querySummaryList/v1`
- source `ZXGS97000017640,003`

M8-A defines typed network contracts/results but deliberately does **not** implement the three real clients yet. Those are independent substages so each parser/session/cache path can be tested and reviewed separately.

## Security / persistence boundary

- no new credential persistence path is introduced;
- M5 Keystore remains the only account-credential authority;
- M8 network result contracts may return renewed `AccountCredentials` for M5-authority persistence by the future stores;
- ordinary M8 caches must not contain Cookie/appID/token_online/password/SMS code;
- `android:allowBackup="false"` remains required.

## Regression compatibility fix

Extending `SettingsRepository` with the three M8 refresh-policy domains requires existing test doubles to implement the same interface. `BalanceRepositoryTest`'s `FakeSettingsRepository` was updated only as a test contract adapter; the change does not create another production settings authority or alter balance behavior.

## Visual / device boundary

M8-A contains no new app-owned feature UI and requires **no real-device screenshots**.

When M8-E later performs functional page acceptance, the exact required screenshots will be requested before that step. Final visual parity remains deferred by the project decision to finish business functionality first.

## CI acceptance

Accepted implementation head `896c923f8af877c16d73d94b2a620cb6f291f27c` passed every triggered migration workflow:

- M2 Models — run `32847755833` — success;
- M3 Parsers — run `32847755804` — success;
- M4 Network — run `32847755892` — success;
- M5 Login Security — run `32847755798` — success;
- M6 Persistence Refresh — run `32847755788` — success;
- M7 Flow Voice Functional — run `32847755812` — success;
- M8 Comprehensive Business — run `32847755901` — success.

M8's dedicated job passed:

- source-derived M8 model / endpoint / policy static gate;
- all existing core/data/app unit tests;
- `data:settings` compatibility tests;
- `data:balance` regression tests;
- Debug assembly;
- Release assembly;
- `android-m8-foundation` status publication.

## Next

`NEXT = Android-M8-B — Ordered Business Client + Cache + Store`
