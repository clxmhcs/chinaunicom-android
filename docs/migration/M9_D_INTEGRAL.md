# Android-M9-D — 积分

## Status

`M9-D_RESULT = PASS / CLOSED`

- `M9-D1_RESULT = PASS / CLOSED` — M8 Integral reuse + Other Business functional wiring
- `M9-D2_RESULT = PASS / CLOSED` — real-device functional validation on 2026-08-27

Minimum supported Android remains **Android 11 / API 30**.

Final visual parity remains deferred until the later page-by-page visual pass.

## Frozen functional boundary

Current iOS Other Business routes `积分` to an account-selection view which uses only persisted, enabled mobile accounts whose mobile number contains exactly 11 digits. Independent broadband accounts are not Integral targets.

The selected mobile account opens the existing Integral experience. Therefore Android M9-D reuses the M8 Integral authority instead of creating a second client, store, cache, refresh policy, or credential path.

## Accepted code boundary

Android provides:

- `其它业务 -> 积分` as an active destination;
- `other/integral` as the mobile-account selector route;
- `other/integral/{accountId}` as the selected-account Integral destination;
- account selection from the existing M6 persisted mobile-account authority only;
- enabled + 11-digit filtering and `sortOrder` ordering;
- no independent-broadband adapter in the Integral selector;
- masked mobile display in the rough functional UI;
- direct reuse of the existing M8 `IntegralEntryScreen` / `ComprehensiveBusinessViewModel` / single `IntegralStore`;
- direct reuse of the M8 real carrier Integral client, M5 credential lifecycle, account anti-cross-leak validation, app-private `AtomicFile` cache, overview/month/detail queries and refresh policy;
- no Cookie, token, appID, identity suffix, password or SMS code in the new selector/navigation state.

Implementation head: `1f6e9f517115e94d2bb120e99ab53c4f41280aa9`.

Initial dedicated Android M9-D run `33055619586` — **SUCCESS**.
Android M9 permanent regression run `33055619556` — **SUCCESS**.
Main APK run `33055619579` — **SUCCESS**.

Real-device artifact used for combined M9-C2 + M9-D2 validation:

- name: `chinaunicom-debug-apk`
- artifact id: `9639557916`
- SHA-256: `14c241cd4a24e40441de509a8192a0098a5cd1fd2dcd762d2c71226fd63061a0`

## Real-device closure evidence

Accepted on 2026-08-27:

- Other Business showed `积分` as `已接入`;
- the Integral selector showed persisted mobile numbers only and did not publish the independent broadband account;
- a production mobile account opened the real Integral experience successfully;
- the real overview returned available points `887`, communication points `327`, reward points `560`, and expiring-this-month points `0`;
- month/category query entry points were rendered from the same M8 Integral store.

The same evidence round also closed M9-C: mobile Ordered Business returned 57 real items and independent broadband returned 9 real items. The broadband account was present lower in the Ordered Business target list; the footer count currently reflects only mobile-number count. That count is a non-blocking display/text issue deferred to the final page-by-page visual pass.

M9-D's CI version gate was made forward-compatible before later M9 app versions. On M9-E1 head `7c14ce0f34ba8931245ff024a1a10385b09c388e`, dedicated M9-D run `33063974631` passed again.

`NEXT = Android-M9-E2 — Phone Bill real-device functional validation`
