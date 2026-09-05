# Android M9-G — Tariff Zone Current H5 Client Profile Correction

## Status

`M9_G_TARIFF_ZONE_PROFILE_12_15 = IMPLEMENTED / CI_PENDING`

Minimum supported Android remains **Android 11 / API 30**.

## Current iOS source of truth

Current `chinaunicom-ios` `TariffZoneClient.swift` resolves the User-Agent for tariff index, product-reference and detail requests from the real device system version and `UnicomClientProfile.h5UserAgent(systemVersion:)`.

The current shared profile resolves to:

- App version: `12.15`
- protocol version: `iphone_c@12.1500`
- H5 identity token: `unicom{version:iphone_c@12.1500}`
- OS-version token derived from the current device system version.

Current iOS session-expiry recovery delegates to shared `UnicomAPIClient.activateSession(...)`; Android already matched that recovery path before this correction.

## Android drift corrected

Before this correction `UnicomTariffZoneClient`:

- built its own H5 User-Agent containing `iphone_c@12.1400`;
- defaulted `systemVersionProvider` from `System.getProperty("os.version")`, which reflects the JVM/Linux kernel rather than the Android release expected by the iOS-derived H5 identity.

Android now:

- routes Tariff Zone index/reference/detail requests through `UnicomClientProfile.h5UserAgent(systemVersion)`;
- sources production system version from the existing Application-installed renewal environment whose authority is `Build.VERSION.RELEASE`;
- removes the private 12.1400 H5 identity and kernel-version fallback;
- preserves the `mxx.client.10010.com` endpoints, page origin/referer, behavior ID, form fields, `0001` empty-result handling, Cookie mutation, shared session renewal, region/category mapping, pagination, detail parsing and UI/store behavior.

## Regression boundary

`UnicomTariffZoneClientTest` now freezes:

- exact shared H5 User-Agent generation for index requests;
- `iphone_c@12.1500` and `OSVersion` propagation;
- the same shared H5 identity before and after shared session activation;
- detail-request identity;
- existing endpoint, form, region, date parsing and Cookie/credential propagation behavior.

Android M9-G CI permanently requires the shared H5 profile and runtime Android release provider and rejects both `iphone_c@12.1400` and `System.getProperty("os.version")` from `UnicomTariffZoneClient`.

## Scope

This correction is intentionally limited to Tariff Zone. It does not rewrite unrelated clients by search-and-replace. Video Ring remains outside this generic H5-profile migration because the current iOS 10155 transport uses separate protocol semantics.

No real-device screenshot is required for this source-level identity correction; there is no UI layout change.

`NEXT = after Integral closes, align this branch to merged main, open the isolated Tariff Zone PR, require M9-G + M4 + Android FINAL and affected regressions green, then audit remaining 12.14 candidates individually`
