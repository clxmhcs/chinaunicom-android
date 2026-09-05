# Android M9-D — Integral Current H5 Client Profile Correction

## Status

`M9_D_INTEGRAL_PROFILE_12_15 = IMPLEMENTED / CI_PENDING`

Minimum supported Android remains **Android 11 / API 30**.

## Current iOS source of truth

Current `chinaunicom-ios` `IntegralClient.swift` resolves the User-Agent for integral balance, recent-month and detail requests through the shared `UnicomClientProfile.h5UserAgent(systemVersion:)` authority.

The current default shared profile resolves to:

- App version: `12.15`
- protocol version: `iphone_c@12.1500`
- H5 identity token: `unicom{version:iphone_c@12.1500}`
- OS-version token derived from the current device system version.

Current iOS Integral session-expiry recovery also delegates to shared `UnicomAPIClient.activateSession(...)`; Android already matched that renewal path before this correction.

## Android drift corrected

Before this correction `UnicomIntegralClient` built a private H5 User-Agent containing `iphone_c@12.1400` and defaulted `systemVersionProvider` from `System.getProperty("os.version")`, which reflects the JVM/Linux kernel rather than the Android release.

Android now:

- routes all Integral H5 requests through `UnicomClientProfile.h5UserAgent(systemVersion)`;
- removes the hand-built 12.1400 User-Agent;
- sources the production system version from the existing Application-installed renewal device context whose authority is `Build.VERSION.RELEASE`;
- keeps Integral endpoints, form fields, account-isolation validation, Cookie mutation, shared session renewal, parser mapping, cache keys, disk cache and UI behavior unchanged.

## Regression boundary

`UnicomIntegralClientTest` now freezes:

- exact shared H5 User-Agent generation using a deterministic system version;
- `iphone_c@12.1500` and `OSVersion` propagation;
- the same shared Integral H5 identity after session renewal;
- the detail-request identity;
- existing endpoint, form, Cookie and credential propagation behavior.

Android M9-D CI permanently requires the shared H5 profile and runtime Android release provider and rejects `iphone_c@12.1400` from `UnicomIntegralClient`.

## Scope

This correction is intentionally limited to Integral. Tariff Zone remains a separate source-confirmed follow-up stage. Video Ring remains outside this shared H5-profile migration because its current iOS 10155 transport uses different protocol semantics.

No real-device screenshot is required for this source-level identity correction; there is no UI layout change.

`NEXT = after My Package closes, align this branch to merged main, open the isolated Integral PR, require M9-D + M4 + Android FINAL and affected regressions green, then migrate Tariff Zone`