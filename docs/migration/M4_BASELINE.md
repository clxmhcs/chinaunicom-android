# Android-M4 — HTTP / Cookie / Session Core

## Status

`M4_RESULT = PASS / CLOSED`

Android-M4 is closed after the original M4-A through M4-F implementation plus repair stages M4-R1 through M4-R5.

M4 owns transport/session behavior and the business-semantics repairs required before Login/Security Storage begins. It does not implement production credential persistence, refresh orchestration, shared-balance cache state, feature UI parity, Widget scheduling, automation, or packet capture.

Minimum supported Android version remains **Android 11 / API 30**.

## Frozen iOS source truth

| iOS source | SHA-256 | Android target |
| --- | --- | --- |
| `ChinaUnicom/Services/UnicomNetworking.swift` | `3f1e31c6f1b367ac8119cd536f1cf7cbaa4109e033f3e8242627ac0aac910a2e` | `UnicomNetworking.kt` |
| `ChinaUnicom/Services/UnicomAPIClient.swift` | `8103d6ea94aec67d56a533f2631526ea6eac758f66a58c97a75ad3e73cf07704` | `UnicomAPIClient.kt`, remaining-response normalizer |
| `ChinaUnicom/Services/UnicomBalanceClient.swift` | `a306e5664e669cd5d09dfc1a97cd32f95b2adc6925b45d81c04b5f864b3c5c65` | `UnicomBalanceClient.kt` |

The migration rule remains: iOS is business truth. Android platform implementation may differ, but URL/request/session/parser/data behavior may not be re-guessed.

## M4-A — HTTP client

Preserved behavior:

- POST transport;
- 16 second default timeout;
- no automatic/shared Cookie jar;
- caller-supplied headers only;
- only HTTP 2xx is accepted;
- exactly one retry for source-equivalent transient network failures;
- exactly one retry for HTTP 5xx;
- no retry for HTTP 4xx, generic non-transient I/O, TLS handshake failures, parser errors, or session errors.

After M4-R1, Android retry classification is intentionally narrower than generic `IOException`. Retryable Android exception classes are the platform equivalents used for timeout, DNS/host lookup, connect/no-route, connection-lost/socket, and EOF-style transport loss.

## M4-B — Cookie / form / response status

Preserved behavior:

- case-insensitive Cookie key identity;
- duplicate keys keep original order but latest value;
- Cookie/Set-Cookie prefixes accepted by normalizer;
- Cookie attributes excluded from normalized Cookie header;
- Set-Cookie additions/replacements/deletions returned as explicit mutations;
- `Max-Age <= 0` and epoch-1970 expiry delete a Cookie;
- combined Set-Cookie records split only on commas that begin a new cookie pair, preserving Expires commas;
- form encoding sorts map keys and uses RFC3986 unreserved characters (`A-Z a-z 0-9 - . _ ~`);
- success codes: `0`, `0000`, `200`, `success`;
- expired codes: `9998`, `999998`, `999999`, `0500`;
- plain response expiry markers include invalid Cookie / not logged in / relogin / login-expired text.

The Kotlin top-level JSON primitive difference remains explicitly normalized so raw `999998` follows the source-equivalent expiry path instead of being treated as a successful structured quota container.

## M4-C — session activation

`onLine.htm` contract remains frozen:

- URL: `https://m.client.10010.com/mobileService/onLine.htm`;
- body: `appId`, `token_online`, `version=iphone_c@9.0100`;
- Content-Type: `application/x-www-form-urlencoded`;
- old Cookie is not attached to activation;
- successful Set-Cookie mutations are applied to the normalized original Cookie;
- returned `token_online` / `tokenOnline` replaces the old token when non-empty;
- quota/balance retry uses activated credentials;
- changed credentials are returned to the caller for later secure persistence.

Production secure persistence is M5, not M4.

## M4-D — quota API

- URL: `https://m.client.10010.com/servicequerybusiness/operationservice/queryOcsPackageFlowLeftContentRevisedInJune`;
- original normalized Cookie is tried first;
- session-expired response triggers token-online activation only when appId/token_online exist;
- quota is retried with the activated Cookie;
- `QuotaParser` remains the M3 parser authority;
- full app query reuses the same response for `RemainingQueryParser`;
- Widget-light query skips Remaining detail parsing;
- package-name fallback uses `/servicequerybusiness/query/myInformation` only when allowed and needed;
- strong unlimited-flow response normalization from the iOS API client remains preserved, including `summary.limitValue` pure-number-as-GB semantics.

## M4-E — balance API

- URL: `https://m.client.10010.com/servicequerybusiness/balancenew/accountBalancenew.htm`;
- exact iOS form fields are preserved;
- Cookie and form Content-Type headers are preserved;
- session expiry triggers the same token-online activation path;
- `curntbalancecust`, unavailable-limit details and frozen-balance details accept string/numeric wire values;
- balance strings remove commas before Double conversion.

The shared-balance cache/lease gate remains intentionally deferred to M6. It must later port the iOS representative-account/lease/refresh-window/failure-release behavior rather than being reduced to a simple non-null cache check.

## Historical automated evidence

The original M4 CI exposed a source-relevant mismatch: Kotlin serialization accepted raw `999998` as a JSON primitive instead of allowing it to fall through to the source-equivalent plain-text session-expiry path. The parser contract was not weakened to hide the failure.

That compatibility fix was closed in commit:

`107a3806cdc0ce7d745fb7ea4f9a3dff5db5d649`

Original M4 automated verification then passed in GitHub Actions run `32331797633`, including `:core:network:testDebugUnitTest`, `:core:parser:testDebugUnitTest`, integrated `:app:assembleDebug`, and `android-m4-network=success`.

R1-R5 later strengthened the same baseline; the historical run is retained as implementation evidence, not as the final closure gate.

## M4-F — real same-account parity

The sanitized real-account parity gate was accepted on 2026-08-21.

Accepted Android report:

`ChinaUnicom-M4-F-Android-Parity.txt`

Recorded accepted facts:

- report timestamp: `2026-08-21 10:01:50Z`;
- `overall=PASS`;
- `session.credentialMutationObserved=false` for that run;
- only sanitized normalized evidence was used;
- raw credentials/authenticated payloads were not committed.

The report is validation evidence, not commit provenance. See `M4_F_REAL_PARITY.md` for the evidence/security contract.

## M4 repair ledger

### M4-R1 — retry parity

Closed in main commit:

`d0db954c38168718055394c3f5264587373e8e42`

Changes:

- removed retry-for-every-IOException behavior;
- restricted retries to iOS-equivalent transient transport failures plus HTTP 5xx;
- added non-retry regression coverage for generic IOException, protocol/TLS failures and HTTP 4xx.

### M4-R2 — authoritative business-model boundary

Closed in main commit:

`f4eb6bbe18be8cebdc1a9e9927c380585af3dd05`

Changes:

- removed lossy parallel `AccountSummary`, `QuotaItem`, and `VoiceSummary` business truth;
- `BusinessOverview` now carries authoritative M2 `UnicomAccount` objects;
- aggregation preserves full `FlowPackage`/`VoicePackage` values and classification fields;
- M2 model CI was restored for PRs targeting main.

### M4-R3 — remaining / unlimited / formatting semantics

Closed in main commit:

`c1133de4743ce1ab7d3630ce4e1288ee403a9d0f`

Changes:

- limited quota uses independent `remainingMB`, never `total-used`;
- `null` stays unknown (`--`) instead of becoming zero;
- unlimited quota is handled separately and never gets a fabricated remaining/total value;
- flow formatting is routed through the M3 `FlowFormatter`;
- duplicate formatting/presentation logic was removed from `core:model`;
- tests freeze MB/GB threshold, rounding, negative, NaN/infinity and override semantics.

### M4-R4 — fake/production repository isolation

Closed in main commit:

`3a950d9105693fd4d04339dbb5565af53e556807`

Changes:

- `app/src/main` contains only the repository contract;
- all hard-coded account/quota fixtures moved to the debug source set;
- release wiring contains no fake account data and remains explicitly pending M6 production repository wiring;
- `FlowViewModel` receives the active variant repository provider;
- CI rejects fake markers leaking into main/release;
- Debug and Release variants both assemble successfully.

### M4-R5 — closure gate

R5 is the documentation/regression closure step. The M4 GitHub Actions workflow is upgraded to run the complete M4 closure regression:

- `:core:model:testDebugUnitTest`;
- `:core:parser:testDebugUnitTest`;
- `:core:network:testDebugUnitTest`;
- fake-repository leakage guard;
- `:app:assembleDebug`;
- `:app:assembleRelease`;
- debug APK artifact generation.

The R5 branch may merge only when this workflow is green. Therefore the merged `PASS / CLOSED` state is CI-gated rather than documentation-only.

## Security boundaries

- no real phone numbers or account credentials in main/release source;
- no Cookie, appId, token_online or authenticated response body logging;
- no automatic Cookie jar;
- no credential persistence in `core:network`;
- imported M4-F credentials remain debug-harness/local-only;
- Android Keystore-backed production persistence begins in M5.

## Acceptance gates

- [x] `core:network` module exists
- [x] frozen iOS networking source hashes recorded
- [x] explicit Cookie mutation codec implemented
- [x] response/session status rules implemented
- [x] session activation contract implemented
- [x] quota endpoint wired to authoritative parsers
- [x] balance endpoint/detail parsing implemented
- [x] retry policy corrected to iOS-equivalent transient cases
- [x] authoritative M2 domain models established as business truth
- [x] independent remaining/unlimited/formatting semantics corrected
- [x] fake repository isolated to debug
- [x] release source contains no fake fixture data
- [x] sanitized real iOS/Android same-account parity evidence accepted
- [x] Android 11 / API 30 minimum preserved
- [x] final M4 closure workflow defined as model + parser + network + Debug/Release build gate

`M4_RESULT = PASS / CLOSED`

## Screenshot requirement

R5 requires no new real-device screenshots. M4-F business-value screenshots/evidence were already supplied and accepted. Formal visual parity remains deferred to M7, where required pages/modes must be requested before that stage begins.

## Next stage

`NEXT = Android-M5 — Login + Security Storage`
