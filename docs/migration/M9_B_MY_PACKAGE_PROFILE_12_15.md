# Android M9-B — My Package Current H5 Client Profile Correction

## Status

`M9_B_MY_PACKAGE_PROFILE_12_15 = IMPLEMENTED / CI_PENDING`

Minimum supported Android remains **Android 11 / API 30**.

## Current iOS source of truth

Current `chinaunicom-ios` `MyPackageClient.swift` sends package, resource-detail, member and pretty-number requests with `UnicomClientProfile.h5UserAgent(systemVersion:)`. The system-version input comes from the current device system version. Session expiry recovery delegates to shared `UnicomAPIClient.activateSession(...)`.

The current default shared profile resolves to App 12.15 / protocol `iphone_c@12.1500`.

## Android drift corrected

Before this correction Android `UnicomMyPackageClient`:

- manually built its H5 User-Agent around private `CLIENT_VERSION = iphone_c@12.1400`;
- used `System.getProperty("os.version")` as its production system-version source, which represents the JVM/Linux kernel rather than the Android release expected by the source-equivalent H5 identity;
- had an M9 permanent guard that explicitly required the historical 12.1400 value.

Android now:

- routes all My Package H5 requests through shared `UnicomClientProfile.h5UserAgent(systemVersion)`;
- removes the My Package private `CLIENT_VERSION` constant;
- sources production H5 system version from `UnicomSessionRenewalEnvironment.current().userAgentSystemVersion`, whose Application-installed Android authority is `Build.VERSION.RELEASE`;
- reverses the M9 permanent guard so `iphone_c@12.1400` is forbidden from the active My Package client;
- keeps package/resource/member/pretty-number endpoints and forms unchanged;
- keeps AES/CBC/NoPadding member payload decryption unchanged;
- keeps optional enhancement failure tolerance unchanged;
- keeps Cookie mutation and shared session renewal behavior unchanged;
- keeps cache, refresh policy, credential lifecycle and UI behavior unchanged.

## Regression boundary

`MyPackageNetworkTest` now freezes:

- exact shared H5 User-Agent generation for a deterministic system version;
- `iphone_c@12.1500` in the H5 identity;
- runtime `OSVersion` propagation;
- existing successful-primary/optional-enhancement behavior;
- existing AES/CBC member decryption contract.

Android M9 CI permanently requires the shared H5 profile and real Android-release provider while preserving all existing package security, storage and functional guards.

## Scope

This stage is intentionally limited to My Package. Integral and Tariff Zone remain separate source-confirmed H5 profile corrections. Video Ring remains a separate protocol and is not globally rewritten.

No real-device screenshot is required for this source-level identity correction; no UI layout changes are included.

`NEXT = merge after MyOrder dependency closes and M9 + M4 + Android FINAL regressions are green, then continue with Integral H5 profile audit/correction`
