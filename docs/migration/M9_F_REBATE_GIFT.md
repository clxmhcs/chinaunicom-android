# Android-M9-F — 返费 / 赠费

## Status

`M9-F_RESULT = IN_PROGRESS`

- `M9-F1_RESULT = PASS / CLOSED` — source-derived core + cache + unified refresh settings + rough functional app wiring + CI
- `M9-F2_RESULT = NOT_STARTED` — real-device functional validation

Minimum supported Android remains **Android 11 / API 30**.
Final visual parity is intentionally deferred to the later page-by-page visual pass.

## Source-derived functional boundary

M9-F is a native carrier-backed feature, not a WebView placeholder.

Android preserves the current iOS business split:

- **合约返赠**: `https://hlbasic.10010.com/servicequerybusiness/grantsAndContractRebates/contractRebate`
  - account scope: `qrytype=0`
  - user scope: `qrytype=1`
- **赠款记录**: `https://hlbasic.10010.com/servicequerybusiness/grantsAndContractRebates/canOpenAnInterfaceCall`

The client also preserves the source request fields including `duanlianjieabc` and `ticketChannel`, tolerant JSON/string-array response handling, source time parsing, Set-Cookie mutation handling, and one existing M4 session-activation recovery path using `iphone_c@9.0100` semantics.

Renewed credentials are returned through the existing M5 credential lifecycle and persisted only by `CredentialStore`. Cookie/appID/token material is not placed in ordinary Compose state, navigation arguments, cache files, or migration evidence.

## Android implementation boundary

M9-F1 introduces/reuses the following authorities:

- `core:model/RebateAndGiftModels.kt` — rebate/gift business models and `RebateQueryScope`;
- `core:network/UnicomRebateAndGiftClient.kt` — the two real carrier queries and session recovery;
- `core:login/RebateAndGiftAccountCredentialLifecycle.kt` — M5-only credential read/renew/write boundary;
- `data:rebategift` — account/scope cache, refresh state machine and Android `AtomicFile` persistence;
- the existing single refresh-settings document — `rebateGift` is stored in schema 3 beside the other app refresh domains rather than in a second settings store;
- `RebateAndGiftViewModel` — maps the unified persisted settings policy into the rebate/gift store at refresh-decision time;
- `其它业务 -> 返费 / 赠费` — enabled native route with persisted enabled **mobile** account selection only.

Independent broadband metadata is not inserted into the M9-F selector and remains isolated from the ordinary mobile-account authority.

## Cache / refresh contract

App-private rebate/gift cache uses `AtomicFile` plus `fd.sync()` and contains no credential fields.

The source-derived refresh policy is persisted through the same refresh-policy JSON authority:

- `automaticRefreshEnabled = true`
- `monthlyRefreshDay = 2`
- `monthlyRefreshHour = 8`
- `queryImmediatelyWhenNoCache = true`
- monthly boundary is evaluated in `Asia/Shanghai`
- manual refresh always remains available

Saved range normalization is day `1..28` and hour `0..23`; malformed/missing fields fall back independently to source defaults while unrelated/future JSON domains are preserved.

## Accepted implementation / CI

Primary implementation:

- `2a883357765061e907956a3727205b2b685ab7a6` — real M9-F client/model/cache/store/route/UI foundation
- `769ccadca338bd54fb5c64d0157ac03c19b11561` — Java 17 regression-test queue ABI correction
- `f93d8b5369f477cc86ea6e2d363f78e96acb4c9c` — unified `rebateGift` refresh settings extension
- `002f79e2e399e070dd7331f0a4c79fc992a9015c` — Android unified settings repository factory
- `d0b712ad89a0e3552457205c5ec14af285f215aa` — ViewModel -> unified settings authority wiring
- `b803925afa5995067ba6591398e3ba795afa02d9` — settings persistence/tolerance regression tests
- `d32cc1c965d5ad7a5d6f98fa7b5bfed6d942ff1f` — dedicated M9-F CI contract including unified settings

Accepted CI for head `d32cc1c965d5ad7a5d6f98fa7b5bfed6d942ff1f`:

- Android M9 F Rebate Gift run `33068256957` — **SUCCESS**
- Android M2 Models run `33068256805` — **SUCCESS**
- Android Main APK Build run `33068256856` — **SUCCESS**
- Action Test run `33068256814` — **SUCCESS**

The preceding app-wiring head `d0b712ad89a0e3552457205c5ec14af285f215aa` also passed the broad permanent/historical regression set, including M5, M6, M7, M8, M9-B4, M9-C, M9-D, M9-E, permanent M9, M9-F, M2, Main APK and Action Test workflows.

## Real-device candidate

Main APK run: `33068256856`

Artifact:

- name: `chinaunicom-debug-apk`
- artifact id: `9644724113`
- head SHA: `d32cc1c965d5ad7a5d6f98fa7b5bfed6d942ff1f`
- size: `12234938` bytes
- digest: `sha256:87296388e6b80bb584584503a95698ac31f7fdb2d4b3951231b2fad8d44e0514`
- expires: `2026-11-25T11:38:19Z`

## M9-F2 real-device acceptance checklist

Use the candidate above. No Cookie, appID, token_online, password, SMS code, identity suffix, or other credential material should appear in screenshots.

Required evidence:

1. `其它业务` page: `返费 / 赠费` is enabled and opens normally.
2. `返费/赠费 查询` account selector: persisted mobile accounts are offered; an independent broadband account must not appear as a mobile target.
3. Detail page, `合约返赠` + `账户`: carrier query completes successfully. A legitimate carrier empty result (`暂无数据`) is acceptable; network/session failure is not.
4. Detail page, `合约返赠` + `用户`: carrier query completes successfully. A legitimate carrier empty result is acceptable.
5. `赠款记录`: carrier query completes successfully. A legitimate carrier empty result is acceptable.

After these checks are accepted, set `M9-F2_RESULT = PASS / CLOSED`, then `M9-F_RESULT = PASS / CLOSED` and proceed in order to `M9-G — 资费专区`.

`NEXT = Android-M9-F2 — Rebate / Gift Real-device Functional Validation`
