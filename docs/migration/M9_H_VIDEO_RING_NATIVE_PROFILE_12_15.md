# Android M9-H — Video Ring Current Native Client Profile Correction

## Status

`M9_H_VIDEO_RING_NATIVE_PROFILE_12_15 = IMPLEMENTED / CI_PENDING`

Minimum supported Android remains **Android 11 / API 30**.

## Current iOS source of truth

The active member-center path in current `chinaunicom-ios` is the inline `VideoRingInlineMemberService` embedded in `VideoRingMemberView.swift`.

That active path keeps the 10155-specific protocol (`clientAppID = 3000013947`, `signSalt = VNEU8G4V`, `osWoVersion = 1018`, uid/timestamp/nonce/sign/accessToken) but resolves its User-Agent through:

`UnicomClientProfile.nativeUserAgent(systemVersion: UIDevice.current.systemVersion)`

Current iOS network-identity guards also require the native-ticket request to `https://m.client.10010.com/edop_ng/getTicketByNative` to send the same centralized native User-Agent.

The separate `VideoRingAPIClient.swift` is not the Android source authority for this screen: it is a distinct 10155 transport helper with different header semantics. Android `UnicomVideoRingClient` explicitly mirrors the active inline service, so this correction follows that active source path rather than applying generic H5 rules.

## Android drift corrected

Before this correction Android:

- froze `USER_AGENT` to `ChinaUnicom4.x/12.14 ... iphone_c@12.1400` for signed 10155 requests;
- did not send the centralized native User-Agent on `getTicketByNative`;
- therefore could diverge from the current iOS 12.15 identity even though its 10155 signing/session flow otherwise matched the active inline service.

Android now:

- resolves the active Video Ring native identity through `UnicomClientProfile.nativeUserAgent(systemVersion)`;
- sources production system version from `UnicomSessionRenewalEnvironment.current().userAgentSystemVersion`, whose Android authority is `Build.VERSION.RELEASE`;
- sends that same current native User-Agent on the native-ticket request and all signed 10155 requests;
- sends the source-matching `zh-Hans-CN;q=1.0` language header on the native-ticket request;
- removes the frozen 12.14 / `iphone_c@12.1400` constant.

## Preserved protocol boundaries

This stage intentionally does **not** convert Video Ring into the generic H5 profile. It preserves:

- native app id `edop_unicom_c43eac06`;
- 10155 app id `3000013947`;
- `VNEU8G4V` signature salt;
- `oswoversion = 1018`;
- timestamp / nonce / uppercase MD5 signature construction;
- uid and accessToken semantics;
- no synthetic `Authorization` header on the active-inline Android path;
- ephemeral per-refresh 10155 Cookie isolation;
- shared Unicom session activation only for renewing selected account credentials before retrying the native ticket;
- caller-versus-selected-number hard isolation;
- active member endpoints, member merge behavior, cache/store and UI behavior.

## Regression boundary

`UnicomVideoRingClientTest` now freezes a deterministic system version and verifies:

- the native-ticket request carries the exact shared current native User-Agent;
- the same User-Agent is used by 10155 login/member requests;
- the resolved profile contains native app version 12.15 and protocol `iphone_c@12.1500`;
- session renewal retries the native ticket with the same current identity;
- existing signing headers, uid/accessToken behavior and account-isolation semantics remain unchanged.

Android M9-H CI permanently requires the shared native profile/runtime Android release provider, requires native-ticket and 10155 User-Agent wiring, and rejects regression to `ChinaUnicom4.x/12.14` or `iphone_c@12.1400` in `UnicomVideoRingClient`.

## Scope

This is a source-level network identity correction only. No UI layout is changed and no real-device screenshot is required.

`NEXT = after Tariff Zone closes, realign this four-file Video Ring branch onto merged main, open an isolated M9-H PR, require M9-H + M4 + Android FINAL and affected regressions green, then continue auditing any remaining endpoint-specific identity drift`
