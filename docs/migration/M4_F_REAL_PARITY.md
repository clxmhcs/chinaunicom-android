# Android-M4-F — Real iOS / Android Same-Account Query Parity

## Purpose

M4-F is the final gate for Android-M4. Automated transport/parser/build verification is already green. This gate verifies the Android network implementation against a real account while keeping all credential material local to the user's devices.

## Safe credential source

The frozen iOS app already provides a protected export path:

`设置 → 账户凭据 → 导出全部凭据`

The iOS v1 archive is JSON with:

- `version`
- `exportedAt`
- `accounts[]`
  - `account`
  - `credentials`
    - `cookie`
    - `appID`
    - `tokenOnline`

This original JSON is a secret-bearing file. It MUST NOT be uploaded to ChatGPT, GitHub, an issue, a PR, CI artifact, log, screenshot or parity report.

## Android debug-only harness

M4-F adds `M4ParityActivity` under `app/src/debug/` only.

Properties:

- not present in release source set / release APK;
- appears as a separate `M4 联网验收` launcher entry only in debug builds;
- opens the iOS credential JSON with Android Storage Access Framework;
- does not request broad file-system access;
- reads archive bytes locally;
- uses credential values only in process memory;
- zeroes the source ByteArray after parsing/query setup where possible;
- never renders raw Cookie/appId/token_online;
- never logs authenticated headers or response bodies;
- never persists imported credentials;
- queries quota and balance through the production M4 `UnicomAPIClient`;
- outputs only masked account identity and normalized parsed result fields;
- can save/copy only the sanitized TXT report.

Kotlin/Java immutable strings cannot be reliably zeroed in managed memory. The harness therefore minimizes credential lifetime and holds no persistent reference after the run. M5 is still responsible for production Keystore-backed credential persistence.

## Android sanitized report fields

Per account:

- masked mobile only (`138****1234` form);
- quota result PASS/FAIL and non-sensitive error category;
- quota resource status;
- package name;
- flow package names and normalized total/used/remaining MB;
- quota type, category, share scope, carry-forward scope;
- voice package names and normalized minutes;
- Remaining-query package/count/limit summary without member identities;
- balance value and unavailable/frozen totals;
- unavailable/frozen item counts, not serial numbers;
- whether a credential mutation was observed, never the mutated value.

## Required M4-F evidence

1. Run the current frozen iOS app with the same account and refresh it normally.
2. Export the iOS credential archive locally. Do not upload it.
3. Install a debug Android build containing the M4-F harness.
4. Open `M4 联网验收` and choose the iOS credential JSON locally.
5. Let the harness query all exported mobile accounts.
6. Save `ChinaUnicom-M4-F-Android-Parity.txt`.
7. Compare Android normalized values with the iOS values from the same refresh window.
8. Upload/share only the sanitized Android TXT report plus the required sanitized iOS evidence below.

## iOS screenshots required for M4-F

M4-F is a data parity gate, not a visual-parity gate, but the iOS app currently has no equivalent sanitized text exporter for parsed results. Therefore the following **sanitized real-device iOS screenshots** are required as business-truth evidence for each account being validated:

1. **流量 → 余量/套餐详情页面**: package names plus total/used/remaining flow values visible.
2. **语音页面**: voice package names plus total/used/remaining minutes visible.
3. **余额显示页面**: the balance value used by the app visible. Either the home balance display or 综合业务 remaining-balance card is acceptable when it represents the same account/query state.

If Remaining Query shows additional shared/unshared detail not visible on the first flow screen, also capture the relevant **余量查询详情** page.

Screenshot rules:

- mask/cover full mobile numbers or account identifiers if visible;
- never show the credential-management page;
- never show Cookie, appId, token_online, password, SMS code or captcha;
- capture Android and iOS values in the same refresh window where practical;
- screenshots are evidence of business values only and are not used for M7 pixel/visual parity.

## Acceptance

M4-F passes only when:

- Android real quota request succeeds for the test account(s);
- Android real balance request succeeds for the test account(s);
- flow/voice package classification and normalized values match iOS within source-defined rounding/unit semantics;
- balance matches the corresponding iOS value;
- any naturally encountered session expiry is recovered with the source-defined activation path;
- no secret material appears in the Android report or submitted evidence.

Until then:

`M4_RESULT = AUTOMATED_PASS / REAL_PARITY_PENDING`

After evidence passes:

`M4_RESULT = PASS / CLOSED`

`NEXT_AFTER_PASS = Android-M5 — Login + Security Storage`
