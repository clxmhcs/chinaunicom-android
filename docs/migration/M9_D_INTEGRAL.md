# Android-M9-D — 积分

## Status

`M9-D_RESULT = IN_PROGRESS`

- `M9-D1_RESULT = PASS` — M8 Integral reuse + Other Business functional wiring
- `M9-D2_RESULT = NOT_STARTED` — real-device functional validation

Minimum supported Android remains **Android 11 / API 30**.

Final visual parity remains deferred until the later page-by-page visual pass.

## Frozen functional boundary

Current iOS Other Business routes `积分` to an account-selection view which uses only persisted, enabled mobile accounts whose mobile number contains exactly 11 digits. Independent broadband accounts are not Integral targets.

The selected mobile account opens the existing Integral experience. Therefore Android M9-D must reuse the M8 Integral authority instead of creating a second client, store, cache, refresh policy, or credential path.

## M9-D1 accepted code boundary

Android now provides:

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

Dedicated Android M9-D run `33055619586` — **SUCCESS**.
Android M9 permanent regression run `33055619556` — **SUCCESS**.
Main APK run `33055619579` — **SUCCESS**.

Real-device artifact:

- name: `chinaunicom-debug-apk`
- artifact id: `9639557916`
- SHA-256: `14c241cd4a24e40441de509a8192a0098a5cd1fd2dcd762d2c71226fd63061a0`

The M9-C dedicated workflow initially rejected the later `m9d1` version string even though the C implementation remained intact. Its version gate was made forward-compatible at commit `b1458d95e5e1ee6d34cf18dc81bd987f29142360`; corrected M9-C run `33055811783` passed completely.

## Pending combined real-device validation

Per migration sequencing, M9-C2 (已订业务) and M9-D2 (积分) may be accepted together on the M9-D1 APK because that APK contains both functional paths.

`NEXT = Android-M9-C2 + Android-M9-D2 combined real-device functional validation`
