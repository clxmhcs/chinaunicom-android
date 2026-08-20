# Android-M4 — HTTP / Cookie / Session Core

## Scope

M4 ports the observable China Unicom transport/session behavior required by the quota and balance paths. It does not introduce login UI, persistent credential storage, repositories, feature UI, Widget scheduling or automation.

The Android network layer is isolated in `core:network` and depends on `core:model`, `core:parser`, JSON runtime and OkHttp. App-owned UI does not own protocol behavior.

## Frozen iOS source truth

| iOS source | SHA-256 | Android target |
| --- | --- | --- |
| `ChinaUnicom/Services/UnicomNetworking.swift` | `3f1e31c6f1b367ac8119cd536f1cf7cbaa4109e033f3e8242627ac0aac910a2e` | `UnicomNetworking.kt` |
| `ChinaUnicom/Services/UnicomAPIClient.swift` | `8103d6ea94aec67d56a533f2631526ea6eac758f66a58c97a75ad3e73cf07704` | `UnicomAPIClient.kt`, remaining-response normalizer |
| `ChinaUnicom/Services/UnicomBalanceClient.swift` | `a306e5664e669cd5d09dfc1a97cd32f95b2adc6925b45d81c04b5f864b3c5c65` | `UnicomBalanceClient.kt` |

## M4-A — HTTP client

Preserved behavior:

- POST transport;
- 16 second default timeout;
- no automatic/shared Cookie jar;
- caller-supplied headers only;
- only HTTP 2xx is accepted;
- one retry after a transient I/O failure;
- one retry after HTTP 5xx;
- no retry for HTTP 4xx or parser/session errors;
- `Set-Cookie` is returned as explicit mutations rather than silently persisted by the HTTP stack.

Android uses OkHttp as a platform implementation detail. This is allowed by migration rule R3 because observable behavior remains source-owned.

## M4-B — Cookie / form / response status

Preserved behavior:

- case-insensitive Cookie key identity;
- duplicate keys keep original order but latest value;
- Cookie/Set-Cookie prefixes are accepted by normalizer;
- Cookie attributes are excluded from the normalized Cookie header;
- Set-Cookie additions, replacements and deletions are explicit mutations;
- `Max-Age <= 0` and epoch-1970 expiry delete a Cookie;
- combined Set-Cookie records split only on commas that begin a new cookie pair, preserving Expires commas;
- form encoding sorts map keys and uses RFC3986 unreserved characters (`A-Z a-z 0-9 - . _ ~`);
- success codes: `0`, `0000`, `200`, `success`;
- expired codes: `9998`, `999998`, `999999`, `0500`;
- plain response expiry markers include invalid Cookie / not logged in / relogin / login expired text.

A platform parsing difference found by CI is permanently accounted for: Kotlin serialization accepts a top-level numeric JSON primitive such as `999998`, while the frozen iOS `JSONSerialization` path falls through to plain-text handling for this response shape. Android therefore treats JSON objects/arrays as structured responses but allows JSON primitives to continue into the source-equivalent plain-text expiry-code fallback.

## M4-C — session activation

`onLine.htm` contract is frozen:

- URL: `https://m.client.10010.com/mobileService/onLine.htm`;
- body: `appId`, `token_online`, `version=iphone_c@9.0100`;
- Content-Type: `application/x-www-form-urlencoded`;
- old Cookie MUST NOT be attached to the activation request;
- successful Set-Cookie mutations are applied to the original normalized Cookie;
- returned `token_online` / `tokenOnline` replaces the old token when non-empty;
- quota/balance retries use the activated Cookie;
- changed credentials are returned to the caller for M5 secure persistence.

## M4-D — quota API

- URL: `https://m.client.10010.com/servicequerybusiness/operationservice/queryOcsPackageFlowLeftContentRevisedInJune`;
- original normalized Cookie is tried first;
- session-expired response triggers token-online activation only when appId/token_online exist;
- quota is retried with the activated Cookie;
- `QuotaParser` remains the M3 parser authority;
- full app query reuses the same response for `RemainingQueryParser`;
- Widget-light query skips Remaining detail parsing;
- package-name fallback uses `/servicequerybusiness/query/myInformation` only when allowed and needed;
- strong unlimited-flow response normalization from the iOS API client is preserved, including `summary.limitValue` pure-number-as-GB semantics.

## M4-E — balance API

- URL: `https://m.client.10010.com/servicequerybusiness/balancenew/accountBalancenew.htm`;
- exact iOS form fields are preserved;
- Cookie and form Content-Type headers are preserved;
- session expiry triggers the same token-online activation path;
- `curntbalancecust`, unavailable-limit details and frozen-balance details accept string/numeric wire values;
- balance strings remove commas before Double conversion.

The shared balance cache/lease gate is not reimplemented here; it belongs to M6 repository/persistence state, per the frozen stage order.

## Security boundaries

- no real phone numbers or account credentials in source/tests;
- no Cookie, appId, token_online or response body logging;
- no automatic Cookie jar;
- no persistence in `core:network`;
- Android Keystore-backed persistence remains M5;
- third-party phone-attribution services are not part of this authenticated transport.

## Automated tests

M4 unit tests cover:

- Cookie normalize/add/update/delete semantics;
- combined Set-Cookie parsing with Expires commas;
- response success/expiry markers;
- RFC3986 form encoding and deterministic key ordering;
- retry once for I/O and 5xx, no retry for 4xx;
- raw `999998` response -> session activation -> retry path;
- activation request excludes the old Cookie;
- Set-Cookie mutation is applied before quota retry;
- updated token_online propagation;
- Widget quota path omits Remaining snapshot;
- balance endpoint/body/detail parsing;
- strong unlimited-flow summary limit normalization.

## Automated verification evidence

Initial real CI reached all Kotlin compilation stages but exposed one behavioral mismatch in `UnicomAPIClientTest.expiredQuotaActivatesWithoutOldCookieThenRetriesWithMutatedCookie`: raw `999998` was accepted as a Kotlin JSON primitive and did not enter the plain-text expiry fallback, producing `NoPackages`. No Golden expectation or parser behavior was weakened to hide the failure.

The source-equivalent fallback was fixed in commit:

`107a3806cdc0ce7d745fb7ea4f9a3dff5db5d649`

Final GitHub Actions verification:

- run: `32331797633`
- job: `96313727291` (`network-test-and-build`)
- Android SDK platform: `platforms;android-37.0`
- command: `gradle :core:network:testDebugUnitTest :core:parser:testDebugUnitTest :app:assembleDebug --stacktrace`
- `:core:network:compileDebugKotlin` = success
- `:core:network:compileDebugUnitTestKotlin` = success
- `:core:network:testDebugUnitTest` = success
- `:core:parser:testDebugUnitTest` = success
- `:app:compileDebugKotlin` = success
- `:app:assembleDebug` = success
- Gradle result: `BUILD SUCCESSFUL in 2m 40s`
- `121 actionable tasks: 121 executed`
- workflow job conclusion = success
- commit status `android-m4-network` = success
- failure guard = skipped as expected

## Acceptance gates

- [x] `core:network` module exists
- [x] iOS networking source hashes frozen
- [x] HTTP transport implemented without automatic Cookie persistence
- [x] Cookie mutation codec implemented
- [x] response/session status rules implemented
- [x] session activation contract implemented
- [x] quota endpoint wired to M3 parsers
- [x] balance endpoint and detail parser implemented
- [x] no real credentials in tests
- [x] Android 11 / API 30 minimum preserved
- [x] `:core:network:testDebugUnitTest` succeeds in GitHub Actions
- [x] existing `:core:parser:testDebugUnitTest` still succeeds
- [x] `:app:assembleDebug` succeeds with `core:network` integrated
- [x] commit status `android-m4-network=success` observed
- [ ] M4-F real iOS/Android account-query parity performed

`M4_RESULT = AUTOMATED_PASS / REAL_PARITY_PENDING`

## Screenshot requirement

No real-device UI screenshots are required for M4-A through M4-E. M4-F requires sanitized same-account query evidence, not visual-parity screenshots. UI screenshots remain mandatory at M7.

## M4-F real parity gate

Automated CI is green. M4 remains open until a real account verifies the same quota/balance/session behavior on iOS and Android without committing secrets.

The M4-F harness/evidence must obey these constraints:

- use the same China Unicom account on iOS and Android;
- compare parsed flow packages, voice packages, quota status and balance values;
- compare session-expiry/reactivation behavior when it occurs naturally or can be tested without exposing credentials;
- redact subscriber/account identifiers in any saved evidence;
- never save or upload raw Cookie, appId, token_online, password, SMS code or captcha;
- no authenticated response body may be committed to Git;
- Android diagnostic output must report normalized result fields only, not credential/header material.

M4 is not `CLOSED` and M5 remains blocked until this gate passes.

`NEXT = Android-M4-F — Real iOS / Android Same-Account Query Parity`
`NEXT_AFTER_M4_PASS = Android-M5 — Login + Security Storage`
