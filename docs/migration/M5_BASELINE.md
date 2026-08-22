# Android-M5 — Login + Security Storage

## Status

`M5_RESULT = IN_PROGRESS`

Completed substage:

`M5-A_RESULT = PASS / CLOSED`

Current substage:

`M5-B_RESULT = SMS_LOGIN_IMPLEMENTED / CI_PENDING`

Minimum supported Android version remains **Android 11 / API 30**.

## Scope

M5 ports the iOS login/credential lifecycle before M6 repository/persistence orchestration. UI polish is not part of this stage.

Frozen migration scope from the iOS app:

- SMS verification-code login;
- password login;
- risk/security captcha continuation;
- RSA PKCS#1 encryption used by login requests;
- Cookie acquisition;
- appID / token_online acquisition;
- multi-account credential storage;
- secure persistence across app restarts.

M5 must not persist service passwords, SMS verification codes or captcha result tokens as account credentials.

## Frozen iOS source truth

| iOS source | SHA-256 | Role |
| --- | --- | --- |
| `ChinaUnicom/Services/KeychainStore.swift` | `99120d8043bba28fc0e7ef5f1feb45f246d79a4efbb3a06c99b7403caab96005` | credential persistence/security boundary |
| `ChinaUnicom/Views/AccountCredentialLoginSessions.swift` | `2f637bf416c1d6f62a414509276c521825c4726fafc17410fc57b0c4bd2e0ebd` | SMS login/session/captcha behavior |
| `ChinaUnicom/Services/RSAEncryptor.swift` | `8c5b12e7bd2cbc99f008047d9c9008c2822b1438449aa81371e67ed74ed6bd0f` | password login, device identity, RSA encryption |
| `ChinaUnicom/Views/AccountCredentialViews.swift` | `c4f1836b75cc34c79a533b81f3076cc3a9d2f28f7734cda00768df987d95a012` | login workflow/business state |
| `ChinaUnicom/Models/AppModels.swift` | `84bdd7c16e37a39b914827a02f11ef1d4e1c58a76335c5cd423247bb053133fa` | `AccountCredentials` model |

Authoritative credential model:

- `cookie: String`
- `appID: String?`
- `tokenOnline: String?`

The iOS account-creation flow validates the credentials by performing a quota request before saving the new account credentials. If M4 session activation returns renewed credentials, those renewed credentials replace the original values before persistence.

## M5-A — Android secure credential storage

Android cannot copy Apple Keychain APIs. The platform-equivalent boundary is:

`AccountCredentials -> binary payload -> AES-256-GCM -> Android Keystore protected key -> encrypted app-private blob`

Implemented module:

`core:security`

### Security properties

- AES key is generated/stored by the `AndroidKeyStore` provider;
- key alias is versioned: `chinaunicom.account.credentials.aes.v1`;
- encryption is `AES/GCM/NoPadding` with a fresh randomized IV;
- the account UUID is authenticated as AES-GCM associated data, so encrypted blobs cannot be swapped between accounts;
- only ciphertext/IV envelopes are written to app-private SharedPreferences;
- Cookie, appID and token_online are never written directly to SharedPreferences/files;
- plaintext credential byte buffers are overwritten after encrypt/decrypt processing where the JVM representation allows it;
- corrupt envelopes/authentication failures fail closed instead of returning partial credentials;
- save/read/delete/delete-all and independent multi-account storage are supported;
- the application manifest keeps `android:allowBackup="false"`, preventing encrypted credential blobs from entering Android Auto Backup without their device-bound Keystore key.

The Android app exposes `CredentialStoreProvider` as the app-layer entry point. Future M5 login code and M6 repository code must use this boundary rather than creating another credential persistence mechanism.

### M5-A regression evidence

GitHub Actions run `32541541070` (`Android M5 Login Security`) completed successfully on the implementation head.

Verified gates:

- secure-storage source guard = PASS;
- `AndroidKeyStore` + `AES/GCM/NoPadding` guard = PASS;
- `android:allowBackup="false"` guard = PASS;
- direct credential-field SharedPreferences write guard = PASS;
- `:core:security:testDebugUnitTest` = PASS;
- `:core:model:testDebugUnitTest` = PASS;
- `:core:network:testDebugUnitTest` = PASS;
- `:app:assembleDebug` = PASS;
- `:app:assembleRelease` = PASS;
- commit status `android-m5-security` = success;
- failure gate skipped as expected.

Credential unit tests freeze exact credential round-trip, nullable appID/token_online preservation, multi-account isolation, overwrite, plaintext sentinel absence, account-bound AAD, corruption failure and delete/delete-all behavior.

`M5-A_RESULT = PASS / CLOSED`

## M5-B — SMS login core

The Android implementation is source-derived from `UnicomSMSLoginSession` and `RSAEncryptor` rather than re-discovered from live traffic.

### Frozen endpoints and constants

- preflight: `https://loginxx.10010.com/login-web/v1/switch/getSwitch`;
- send SMS: `https://loginxx.10010.com/mobileService/sendRadomNum.htm`;
- SMS login: `https://loginxx.10010.com/mobileService/radomLogin.htm`;
- client version: `iphone_c@12.1400`;
- key version: `2`;
- channel: `GGPD`;
- switch version: `237`;
- base city seed: `017|170`;
- source-defined login User-Agent/header contract is preserved.

### Request/session behavior

- mobile input is reduced to digits; a leading `86` is removed only from a 13-digit value;
- mobile must then be exactly 11 digits;
- SMS login code is reduced to digits and must be exactly 6 digits;
- mobile and verification code are encrypted with the frozen 1024-bit RSA public key using PKCS#1 v1.5 and then Base64 encoded;
- first send/login prepares the source-defined `getSwitch` session with JSON body fields `mobile/seq/sign/provinceCode/timestamp/appVersion/version/deviceCode`;
- `getSwitch` failure is non-fatal, matching the iOS preflight behavior;
- base cookies are `PvSessionId`, `c_version`, `channel`, `devicedId`, `city`;
- all `Set-Cookie` mutations from preflight/send/login are accumulated explicitly through the M4 Cookie codec;
- `sendRadomNum.htm` uses the source-defined form body, including `loginCodeLen=6`, device identity, province/city, `appId`, request time and optional `resultToken`;
- `radomLogin.htm` uses `loginStyle=0`, `voiceoff_flag=1`, `keyVersion=2`, encrypted mobile and encrypted six-digit code;
- success requires response code `0` or `0000`;
- SMS send response `ECS99998` with `type=10` becomes a captcha-required outcome;
- SMS captcha bridge payload carries the source-defined keys and adds `channel=smssms`;
- successful login requires both a non-empty normalized Cookie and `token_online`;
- response `appId/appID` replaces the requested appID when present;
- `invalidat/invalidAt` is retained as the login result validity text;
- list-derived `proCode|cityCode` updates the city seed only after the returned credential Cookie has been snapshotted, preserving iOS ordering.

### Device identity persistence

The iOS source stores `deviceCode`, `uniqueIdentifier`, `deviceID` and generated default `appID` in Keychain. Android now mirrors that security boundary with a dedicated Android Keystore AES-GCM identity key and isolated encrypted blob namespace.

Generation/validation rules are preserved:

- `deviceCode`: UUID, generated uppercase;
- `uniqueIdentifier`: `iosa` + 32 lowercase hex characters;
- `deviceID`: lowercase SHA-256 hex of `deviceCode`;
- default `appID`: 192 lowercase hex characters;
- city seed remains app-private non-secret state and defaults to `017|170`;
- Android hardware model/system version are supplied as platform-native device values while the source-owned China Unicom protocol profile/constants remain unchanged.

No mobile number, SMS code, password, Cookie or token_online is persisted by this device-identity store.

### M5-B regression requirements

- exact preflight URL/body/header/base-Cookie shape;
- RSA mobile ciphertext decodes to a 128-byte RSA block;
- preflight Set-Cookie reaches the following send/login request;
- send-code field set and preferred appID behavior;
- `ECS99998 + type=10` maps to captcha-required with `channel=smssms`;
- `resultToken` continuation skips a new preflight, matching source behavior;
- preflight network failure does not block SMS send;
- login form encrypts both mobile and six-digit code;
- success extracts Cookie/appID/token_online/invalidat;
- city seed update ordering matches iOS;
- missing token_online fails closed;
- RSA public key/padding and oversize plaintext rejection are frozen by test;
- M1/M2/M3/M4/M5 CI and Debug/Release assembly remain green.

`M5-B_RESULT = SMS_LOGIN_IMPLEMENTED / CI_PENDING`

## Remaining M5 work

### M5-C — password login + risk captcha

Port `login.htm`, RSA password encryption, success/error classification and official risk-captcha continuation contract. Reuse the M5-B RSA, device identity, explicit Cookie accumulation and captcha data contracts rather than creating a parallel login stack.

### M5-D — login integration / persistence acceptance

- successful login validates credentials through the M4 quota path before account creation;
- only Cookie/appID/token_online are persisted as account credentials;
- M4 renewed credentials are securely overwritten;
- process/app restart can read credentials without re-login;
- logout/account deletion removes the corresponding encrypted credentials;
- final M5 security/static/regression gates pass.

## Screenshot requirement

M5-A and M5-B require no real-device screenshots. M5-C implementation can also proceed from the frozen iOS source without visual screenshots. If a later runtime acceptance step requires real-device login/captcha evidence, the exact screens will be requested before that step begins.

## Next

`NEXT_AFTER_M5_B_PASS = Android-M5-C — Password Login + Risk Captcha`
