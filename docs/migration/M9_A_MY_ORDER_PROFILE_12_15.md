# Android M9-A — My Order Current H5 Client Profile Correction

## Status

`M9_A_MY_ORDER_PROFILE_12_15 = IMPLEMENTED / CI_PENDING`

Minimum supported Android remains **Android 11 / API 30**.

## Current iOS source of truth

Current `chinaunicom-ios` uses the shared `UnicomClientProfile.h5UserAgent(systemVersion:)` for both My Order list requests and the My Order detail `WKWebView` request path.

The current default shared profile resolves to:

- App version: `12.15`
- protocol version: `iphone_c@12.1500`
- H5 identity token: `unicom{version:iphone_c@12.1500}`
- OS version token derived from the current device system version.

Current iOS `MyOrderClient.swift` also delegates expired-session recovery to shared `UnicomAPIClient.activateSession(...)`; Android already matched that shared renewal path before this correction.

## Android drift corrected

Before this correction Android retained two historical H5 identities:

1. `UnicomMyOrderClient` built its own H5 User-Agent around `CLIENT_VERSION = iphone_c@12.1400`.
2. `MyOrderDetailWebBridgeContract` exposed one fully frozen H5 User-Agent containing `iphone_c@12.1400` and fixed OS-version text.

The list client's production default system-version provider also used `System.getProperty("os.version")`, which is a JVM/Linux kernel version rather than the Android release version expected by the source-equivalent H5 identity.

Android now:

- adds `UnicomClientProfile.h5UserAgent(systemVersion)` as the single shared H5 identity generator, matching current iOS formatting;
- removes My Order's private `CLIENT_VERSION` constant;
- routes My Order list User-Agent generation through the shared H5 profile;
- sources the production list system version from the existing Application-installed renewal device context (`Build.VERSION.RELEASE` authority);
- replaces the detail bridge's frozen `USER_AGENT` constant with `MyOrderDetailWebBridgeContract.userAgent(systemVersion)` backed by the same shared profile;
- supplies the real Android `Build.VERSION.RELEASE` from the WebView adapter;
- keeps My Order endpoints, request bodies, MD5 login number, Cookie mutation, shared session renewal, parsers, ready-URL gates, JavaScript bridge, content blocking, credential isolation and UI behavior unchanged.

## Regression boundary

`UnicomMyOrderClientTest` now freezes:

- exact shared H5 User-Agent generation for list requests;
- `iphone_c@12.1500` in the list H5 identity;
- runtime `OSVersion` propagation;
- the same H5 identity before and after shared session renewal;
- existing Cookie/appId/token_online propagation.

`MyOrderDetailCoreTest` now freezes:

- detail WebView identity through the same `UnicomClientProfile.h5UserAgent` authority;
- current protocol version and OS version tokens;
- existing ready URL and JavaScript endpoint behavior.

Android M9 CI permanently rejects `iphone_c@12.1400` from My Order list/detail code and requires the shared H5 profile plus runtime Android version wiring.

## Scope

This stage is intentionally limited to My Order. Other clients such as My Package, Integral and Tariff Zone are audited independently even though current iOS also uses the shared H5 profile for them. Video Ring remains a separate protocol because its current iOS 10155 transport has different request-identity semantics.

No real-device screenshot is required for this source-level identity correction; there is no UI layout change.

`NEXT = merge after M9 + M4 + Android FINAL and affected regressions are green, then migrate My Package H5 identity if its dedicated audit remains source-confirmed`
