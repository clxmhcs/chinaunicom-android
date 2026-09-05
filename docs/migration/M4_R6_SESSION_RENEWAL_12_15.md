# Android M4-R6 — iOS 12.15 Modern Session Renewal

Date: 2026-09-05

## Status

`M4-R6_SHARED_RENEWAL = IMPLEMENTED / CI_PENDING`

This is a protocol-drift correction after the original Android M4 closure. It does not reopen the already accepted parser, Cookie codec, quota, balance, persistence, or credential-authority architecture.

## Current iOS source of truth

Current `clxmhcs/chinaunicom-ios` no longer renews the main carrier session with the historical three-field `iphone_c@9.0100` request. `UnicomAPIClient.swift` now uses one runtime-profile renewal request:

- endpoint: `https://loginhl.10010.com/mobileService/onLine.htm`;
- protocol version: `iphone_c@12.1500`;
- native client: `ChinaUnicom4.x/12.15`;
- native build: `4`;
- original normalized Cookie is attached to the renewal request;
- native headers include the current User-Agent, `Accept`, `Accept-Language`, and `Accept-Encoding`;
- the form carries `reqtime`, `version`, `simOperator`, `token_online`, `appId`, `deviceId`, `pip`, `deviceModel`, `deviceOS`, `deviceBrand`, `uniqueIdentifier`, `step`, `isFirstInstall`, `flushkey`, `deviceCode`, and `voipToken`;
- successful renewal applies returned `Set-Cookie` mutations and also accepts renewed `appId` and `token_online` values.

## Android correction

Android now has one shared modern renewal request factory in `UnicomSessionRenewal.kt` and routes `UnicomAPIClient.activateSession`, quota recovery, balance recovery, and clients delegating to that API through it.

The existing M5 installation-level identity is reused:

- `deviceCode`;
- `deviceID`;
- `uniqueIdentifier`;
- Android device model mapped into the source `deviceModel` field;
- Android release version mapped into the source `deviceOS` / native User-Agent system-version fields.

The stable identifiers remain protected by the existing Android Keystore AES-GCM identity store. This correction does not create a second device identity and does not move any credential or device secret into ordinary SharedPreferences.

`ChinaUnicomApplication` installs the Keystore-backed identity provider at process startup so App, Worker, Widget, and migrated business callers that instantiate `UnicomAPIClient` all use the same installation identity.

Renewal remains fail-closed if the production identity composition root is unavailable.

## Credential propagation

The shared renewal result now treats all three values as authoritative:

1. Cookie after `Set-Cookie` mutation application;
2. returned `appId` when present;
3. returned `token_online` when present.

All existing M5 credential lifecycle/store boundaries remain responsible for durable saving. Core networking only returns updated credentials to its caller.

## Regression coverage

The network tests freeze:

- the `loginhl.10010.com` renewal endpoint;
- original Cookie attachment;
- 12.15 / build 4 native User-Agent;
- all modern renewal form fields;
- deterministic request time/device context;
- Cookie deletion/addition semantics;
- renewed `appId` and `token_online` propagation;
- downstream business retry after successful renewal.

## Intentionally separate next corrections

This step does **not** silently rewrite every endpoint-specific client-version string in the project.

Two known differences remain isolated:

1. `UnicomPhoneBillClient` still contains its older independent `iphone_c@9.0100` activation implementation. Current iOS `PhoneBillClient.swift` now delegates renewal to `UnicomAPIClient.activateSession`; Android must migrate that client in the next isolated step instead of combining a new endpoint with the old three-field body.
2. Android `UnicomPasswordLoginSession` still freezes the original 12.14 identity while current iOS password-login code reads the shared 12.15 runtime profile. Password UI remains disabled, so this is a separate lower-priority parity correction after active session renewal is closed.

Other business clients may intentionally carry their own endpoint-specific User-Agent/version semantics. They must be compared with current iOS individually rather than changed by global search-and-replace.

## Platform floor

Minimum supported Android remains **Android 11 / API 30**.

## Acceptance

M4-R6 shared renewal may close only after:

- `:core:network:testDebugUnitTest` succeeds;
- existing M4 closure build succeeds for Debug and Release;
- Android FINAL and affected historical regression workflows remain green;
- the PR is merged with a locked expected head SHA.

No new screenshot is required for this protocol-only step.
