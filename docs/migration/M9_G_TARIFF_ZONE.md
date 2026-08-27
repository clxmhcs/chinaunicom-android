# Android-M9-G — 资费专区

## Status

`M9-G_RESULT = IN_PROGRESS`

- `M9-G1_RESULT = PASS / CLOSED` — source-derived native core + mobile-account credential lifecycle + browse/search store + rough functional wiring + CI
- `M9-G2_RESULT = NOT_STARTED` — real-device functional validation

Minimum supported Android remains **Android 11 / API 30**.
Final visual parity is intentionally deferred to the later page-by-page visual pass.

## Source-derived functional boundary

Current iOS source establishes `资费专区` as a native carrier-backed experience rather than a WebView placeholder.

Android preserves the following source behavior:

- selectable business targets are persisted enabled **mobile accounts** only; independent broadband metadata is not inserted into the account selector;
- scope values are `全国资费 = 1` and `本地资费 = 2`;
- local mode supports province/city region selection;
- first-level and second-level tariff categories are queried from the carrier;
- tariff/product-name references are queried beneath the selected category;
- tariff details are fetched in batches of **5** and support incremental `加载更多` pagination;
- global search builds results across the carrier category/product catalog and supports opening a matching tariff detail;
- search accepts tariff name, keyword and plan/report number semantics exposed by the source experience;
- carrier code `0001` is treated as a legitimate empty result rather than a session/network failure;
- no TariffZone-specific disk cache or refresh-policy Settings authority is invented.

Carrier root and endpoints:

- `https://mxx.client.10010.com`
- `/servicequerybusiness/queryTariffNew/indexData`
- `/servicequerybusiness/queryTariffNew/threeLevelName`
- `/servicequerybusiness/queryTariffNew/operateData`

Request/session behavior continues to use source `iphone_c@12.1400` semantics, existing M4 session activation and M5 `CredentialStore` as the only credential authority.

## Android implementation boundary

M9-G1 introduces:

- `core:model/TariffZoneModels.kt` — scope, region, category, product, detail and search-result models;
- `core:network/UnicomTariffZoneClient.kt` — the three real carrier endpoints, tolerant parsing, Set-Cookie mutation application, `0001` empty-result handling and existing session recovery;
- `core:login/TariffZoneAccountCredentialLifecycle.kt` — reads credentials only from M5, persists renewed credentials immediately and strips them before ordinary business state receives results;
- `data:tariffzone/TariffZoneStore.kt` — browse state, local/national switching, region/category/product selection, 5-item detail pagination, whole-catalog search and stale-request suppression;
- `data:tariffzone/TariffZoneRegionCatalog.kt` — source-derived/fallback region presentation used by local-mode selection;
- `TariffZoneViewModel` and `TariffZoneScreen` — rough functional Compose wiring with masked mobile account targets;
- `其它业务 -> 资费专区` as an active native route at `other/tariff-zone`;
- protocol and store unit tests plus the dedicated `Android M9 G Tariff Zone` workflow.

No Cookie, appID, token_online, password, SMS code or identity material is placed in Compose/navigation/business disk state. TariffZone does not add an independent credential authority.

## Implementation / CI history

Primary G1 implementation:

- `c27a9f87d883c46678b1c7cbcc91e920836450a0` — source-derived M9-G1 model/network/lifecycle/store/UI/tests/workflow

The first CI pass exposed a Compose-only compile problem in `TariffZoneScreen.kt`: an explicit import of `androidx.compose.foundation.layout.weight` resolved to an internal layout symbol under the repository's current Compose dependency set. Business/network tests were not the cause.

Accepted fix:

- `2b91e0eab1d4b065c740d22e1997fe31bfdc9ffc` — remove the explicit `weight` import and use the normal RowScope/ColumnScope `Modifier.weight()` resolution

Accepted CI for head `2b91e0eab1d4b065c740d22e1997fe31bfdc9ffc`:

- Android M9 G Tariff Zone run `33075369336` — **SUCCESS**
- Android Main APK Build run `33075369429` — **SUCCESS**
- Android M2 Models run `33075369406` — **SUCCESS**
- Android M9 Other Business permanent regression run `33075369466` — **SUCCESS**
- Android M9 D Integral run `33075369368` — **SUCCESS**, confirming the prior compile failure is fixed
- Android M5 Login Security run `33075369324` — **SUCCESS**
- Android M6 Persistence Refresh run `33075369435` — **SUCCESS**
- Android M7 Flow Voice Functional run `33075369334` — **SUCCESS**
- Android M8 Comprehensive Business run `33075369335` — **SUCCESS**
- Android M9 B4 Broadband Account Core run `33075369354` — **SUCCESS**
- Android M9 C Ordered Business run `33075369439` — **SUCCESS**
- Android M9 E Phone Bill run `33075369380` — **SUCCESS**
- Android M9 F Rebate Gift run `33075369346` — **SUCCESS**
- Action Test run `33075369339` — **SUCCESS**

## Real-device candidate

Main APK run: `33075369429`

Artifact:

- name: `chinaunicom-debug-apk`
- artifact id: `9647744051`
- head SHA: `2b91e0eab1d4b065c740d22e1997fe31bfdc9ffc`
- size: `12336778` bytes
- GitHub artifact ZIP digest: `sha256:6ee335ad55f39f31f53d34958cdd589985483d6eaeb41d4c4682e168c2bde05b`
- extracted `app-debug.apk` SHA-256: `f48327aefa75b92fde4bd2003975c4251c827e85852ac5e8b6b8eaf4e87e2fd2`
- expires: `2026-11-25T13:09:26Z`

## M9-G2 real-device acceptance checklist

No credential material is required in screenshots.

Required evidence:

1. `其它业务` page: `资费专区` is enabled and opens normally.
2. `资费专区` main page with a persisted mobile account selected: account is masked, local tariff mode loads normally, and region / first-level / second-level / tariff-product controls are functional. An independent broadband account must not appear as a mobile target.
3. Switch to `全国资费`: a real carrier result or legitimate empty result is displayed without network/session/parser failure.
4. Browse a real category/product and open or display a real tariff detail. If `加载更多` is available, load the next batch and verify items append; if the carrier returns five or fewer total items, the valid no-more state is acceptable.
5. Search using a tariff name, keyword or plan/report number available from current carrier data, then open one search result and verify its real tariff detail.

A legitimate carrier empty result, including source code `0001`, is acceptable. Network/session/parser failures are not.

After these checks are accepted, set `M9-G2_RESULT = PASS / CLOSED`, then `M9-G_RESULT = PASS / CLOSED` and proceed in order to `M9-H — 视频彩铃`.

`NEXT = Android-M9-G2 — Tariff Zone Real-device Functional Validation`
