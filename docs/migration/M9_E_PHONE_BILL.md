# Android-M9-E — 话费 / 账单

## Status

`M9-E_RESULT = PASS / CLOSED`

- `M9-E1_RESULT = PASS / CLOSED` — M8 PhoneBill reuse + Other Business functional wiring
- `M9-E2_RESULT = PASS / CLOSED` — real-device functional validation accepted 2026-08-27
- `M9-E3_SHARED_RENEWAL = IMPLEMENTED / CI_PENDING` — post-closure protocol-drift correction to current iOS shared 12.15 session renewal

Minimum supported Android remains **Android 11 / API 30**.

Final visual parity remains deferred until the later page-by-page visual pass.

## Frozen functional boundary

Current iOS Other Business routes `话费/账单 查询` through a mobile-account selector. Enabled mobile accounts are resolved through the financial-group representative mapping and duplicate group members collapse to the same representative once. Independent broadband accounts are not Phone Bill targets.

The selected representative opens the existing Phone Bill experience. Android M9-E therefore reuses the already-closed M8 Phone Bill authority instead of creating a second client, store, cache, refresh policy, credential path, or broadband billing path.

## M9-E1 accepted code boundary

Android provides:

- `其它业务 -> 话费 / 账单` as an active destination;
- `other/phone-bill` as the billing-account selector route;
- `other/phone-bill/{accountId}` as the selected-account Phone Bill destination;
- selector input from the existing M6 persisted mobile-account authority only;
- enabled + 11-digit mobile filtering and `sortOrder` ordering;
- `FlowViewModel.financialRepresentativeAccountID()` reuse for source-equivalent financial-group representative selection;
- one visible target per representative ID, so valid combined-billing group members do not create duplicate billing targets;
- no independent-broadband adapter in the Phone Bill selector;
- masked mobile display in the rough functional UI;
- direct reuse of the existing M8 `PhoneBillEntryScreen` / `ComprehensiveBusinessViewModel` / single `PhoneBillStore`;
- direct reuse of the M8 real carrier Phone Bill client, M5 credential lifecycle, 13-month range, current/history cache policy, historical member sharing, same-month serialization and app-private `AtomicFile` persistence;
- no Cookie, token, appID, identity suffix, password or SMS code in the new selector/navigation state.

Implementation head: `7c14ce0f34ba8931245ff024a1a10385b09c388e`.

Validation for this head:

- dedicated Android M9-E run `33063974555` — **SUCCESS**;
- Android M9-D regression run `33063974631` — **SUCCESS**;
- Android M9-C regression run `33063974531` — **SUCCESS**;
- Android M9 permanent regression run `33063974558` — **SUCCESS**;
- Main APK run `33063974632` — **SUCCESS**.

Real-device artifact:

- name: `chinaunicom-debug-apk`
- artifact id: `9643016306`
- SHA-256: `fdd90d3e633a35af6c771def80e469b47722a4a47fd40b66e0947fec28c5d0ee`
- head SHA: `7c14ce0f34ba8931245ff024a1a10385b09c388e`

## M9-E2 accepted real-device evidence

Accepted 2026-08-27 from the user's direct real-device confirmation:

- Other Business showed `话费 / 账单` as integrated;
- the billing-account selector contained the intended mobile billing targets and preserved representative-group semantics without introducing the independent broadband account;
- a selected production account returned a real current-month Phone Bill result rather than a query/session failure;
- the same account returned a real historical-month Phone Bill result, confirming the historical query/cache path on device.

No raw credential, token, appID, identity suffix or unmasked production identifier is recorded in Git.

## M9-E3 — current iOS shared-renewal correction

The original Android M8/M9 Phone Bill implementation retained a historical private session-recovery request using its own `PHONE_BILL_ONLINE` endpoint and `iphone_c@9.0100` three-field body. That was valid for the earlier frozen source but is no longer the current iOS behavior.

Current `chinaunicom-ios` `PhoneBillClient.swift` delegates recovery to the shared `UnicomAPIClient.activateSession(...)`. The shared client now owns the current 12.15 runtime-profile renewal request and returns authoritative Cookie, `appId`, and `token_online` values.

Android M9-E3 therefore:

- removes Phone Bill's private HTTP renewal implementation;
- removes the obsolete `PHONE_BILL_ONLINE` endpoint constant;
- injects/delegates to shared `UnicomAPIClient.activateSession`;
- keeps the pre-activation guard requiring saved `appId` and `token_online`;
- uses the renewed Cookie for the retry and propagates renewed Cookie + `appId` + `token_online` through the existing `updatedCredentials` result boundary;
- leaves Phone Bill months/detail endpoints, parser version, amount parsing, 13-month behavior, cache/store/lifecycle and UI wiring unchanged;
- adds a permanent M9-E CI source gate prohibiting the historical private `iphone_c@9.0100` / `PHONE_BILL_ONLINE` authority from returning.

This correction is intentionally stacked on Android M4-R6 shared renewal and must not merge before that shared authority is accepted into `main`.

No screenshot is required for this protocol-only correction. Existing M9-E2 real-device evidence remains valid for the Phone Bill query/display path; a later natural session-expiry event may be used as additional renewal evidence but is not required to prove the source-level delegation boundary.

`NEXT = close M9-E3 after CI, then audit the remaining password-login 12.14 runtime-profile drift`
