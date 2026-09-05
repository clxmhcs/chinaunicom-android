# Android M5 — Password Login Current Client Profile Correction

## Status

`M5_PASSWORD_PROFILE_12_15 = IMPLEMENTED / CI_PENDING`

Minimum supported Android remains **Android 11 / API 30**.

## Current iOS source of truth

Current `chinaunicom-ios` `UnicomPasswordLoginSession` does not freeze a private client version. It resolves the password-login protocol version from `UnicomClientProfile.protocolVersion` and its native User-Agent from `UnicomClientProfile.nativeUserAgent(systemVersion:)`.

The current default shared iOS profile is:

- App version: `12.15`
- protocol version: `iphone_c@12.1500`
- bundle identifier: `com.chinaunicom.mobilebusiness`
- native build: `4`
- Alamofire version: `4.7.3`

Password-login-specific behavior remains unchanged: `/mobileService/login.htm`, switch bootstrap `/login-web/v1/switch/getSwitch`, RSA PKCS#1 encryption, `netWay=wifi`, `isRemberPwd=false`, ECS99999/type 10 captcha handling, Cookie accumulation, city update ordering, appId/token_online extraction and failure classification.

## Android drift corrected

Before this correction Android `UnicomPasswordLoginSession` independently froze:

- `iphone_c@12.1400`
- `ChinaUnicom4.x/12.14`
- native build `13`

That created a second client-identity authority after M4-R6 introduced the shared current Android `UnicomClientProfile`.

Android now:

- reads password login `version` from `UnicomClientProfile.PROTOCOL_VERSION`;
- uses the same shared protocol version for `c_version`, switch `appVersion`, `captchaSystemInfo.appVersion`, and `captchaSystemInfo.clientVersion`;
- generates native password-login User-Agent through `UnicomClientProfile.nativeUserAgent(identity.deviceOS)`;
- removes the password session's private 12.14/build-13 identity;
- keeps RSA, request fields, cookies, captcha flow, returned credential handling and device identity unchanged.

## Regression boundary

`UnicomPasswordLoginSessionTest` freezes one shared identity across switch bootstrap and login request, including:

- shared 12.15 native User-Agent;
- `c_version=iphone_c@12.1500`;
- switch `appVersion=iphone_c@12.1500`;
- login `version=iphone_c@12.1500`;
- captcha system info using the same shared profile;
- existing RSA/Cookie/captcha/success/failure behavior.

M5 CI permanently rejects a return of `iphone_c@12.1400`, `ChinaUnicom4.x/12.14`, or `build:13` inside `UnicomPasswordLoginSession` and requires the shared profile references.

## Scope

This is an isolated password-login identity correction. Other endpoint-specific clients that still contain 12.14 strings are **not** globally replaced; they must be audited against their current iOS sources individually.

No real-device screenshot is required for this source-level correction. Password-login UI/exposure is not changed by this stage.

`NEXT = merge after M5 + M4 + Android FINAL and affected regressions are green, then continue endpoint-by-endpoint protocol drift audit`
