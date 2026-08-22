# Android-M5 — Login + Security Storage

## Status

`M5_RESULT = IN_PROGRESS`

Completed substages:

- `M5-A_RESULT = PASS / CLOSED`
- `M5-B_RESULT = PASS / CLOSED`

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

The iOS account-creation flow validates credentials with a quota request before persistence. If M4 session activation returns renewed credentials, those values replace the original values before persistence.

## M5-A — Android secure credential storage

Android platform equivalent:

`AccountCredentials -> binary payload -> AES-256-GCM -> Android Keystore protected key -> encrypted app-private blob`

Implemented module: `core:security`.

Security properties:

- AES key is generated/stored by `AndroidKeyStore`;
- key alias `chinaunicom.account.credentials.aes.v1`;
- `AES/GCM/NoPadding` with randomized IV;
- account UUID authenticated as GCM associated data;
- only ciphertext/IV envelopes written to app-private SharedPreferences;
- Cookie/appID/token_online never directly persisted in plaintext;
- corrupt/authentication-failed blobs fail closed;
- multi-account save/read/overwrite/delete/delete-all supported;
- application remains `android:allowBackup="false"`.

App entry point: `CredentialStoreProvider`.

M5-A accepted regression run: `32541541070` (`Android M5 Login Security`) = success.

`M5-A_RESULT = PASS / CLOSED`

## M5-B — SMS login core

Android implementation is source-derived from the frozen iOS `UnicomSMSLoginSession` and `RSAEncryptor`, not rediscovered from live traffic.

### Frozen endpoints/constants

- preflight: `https://loginxx.10010.com/login-web/v1/switch/getSwitch`;
- send SMS: `https://loginxx.10010.com/mobileService/sendRadomNum.htm`;
- SMS login: `https://loginxx.10010.com/mobileService/radomLogin.htm`;
- client version: `iphone_c@12.1400`;
- key version: `2`;
- channel: `GGPD`;
- switch version: `237`;
- default city seed: `017|170`.

### Request/session behavior

- mobile is normalized to digits; only a 13-digit value beginning `86` loses that prefix; resulting length must be 11;
- SMS code is digits-only and must be exactly 6 digits;
- mobile/code use the frozen 1024-bit RSA public key with PKCS#1 v1.5 and Base64 output;
- first send/login performs source-defined `getSwitch` preflight;
- preflight failure is non-fatal;
- base cookies are `PvSessionId`, `c_version`, `channel`, `devicedId`, `city`;
- Set-Cookie mutations are explicitly accumulated through the authoritative M4 Cookie codec;
- `sendRadomNum.htm` preserves source form fields and optional `resultToken`;
- `radomLogin.htm` preserves `loginStyle=0`, `voiceoff_flag=1`, `keyVersion=2` and encrypted mobile/code;
- success accepts response code `0` / `0000`;
- `ECS99998 + type=10` maps to captcha-required and adds `channel=smssms` to the bridge payload;
- successful login requires normalized Cookie and nonempty `token_online`;
- returned `appId/appID` supersedes requested appID when present;
- `invalidat/invalidAt` is retained;
- list-derived `proCode|cityCode` updates the city seed only after credential Cookie snapshot, preserving iOS ordering.

### Device identity security

The iOS source stores `deviceCode`, `uniqueIdentifier`, `deviceID` and generated default `appID` in Keychain. Android mirrors this with a separate Android Keystore AES-GCM identity key and isolated encrypted blob namespace.

Generation rules:

- `deviceCode`: UUID, generated uppercase;
- `uniqueIdentifier`: `iosa` + 32 lowercase hex characters;
- `deviceID`: lowercase SHA-256 of `deviceCode`;
- default `appID`: 192 lowercase hex characters;
- city seed remains non-secret app-private state;
- Android device model/system version are platform-native while source-owned protocol constants remain frozen.

No mobile number, SMS code, password, Cookie or token_online is persisted by the device-identity store.

### M5-B regression evidence

Implementation head `7fe422e13e5627c19f6b1c29bb37c11c473dd3bd` passed all PR workflows:

- Android M1 Build run `32542898754` = success;
- Android M2 Models run `32542898782` = success;
- Android M3 Parsers run `32542898682` = success;
- Android M4 Network run `32542898741` = success;
- Android M5 Login Security run `32542898737` = success.

The M5 job additionally verified:

- Keystore + SMS protocol static boundary = PASS;
- exact three SMS login endpoints present;
- frozen RSA public key and `RSA/ECB/PKCS1Padding` present;
- direct plaintext secret-field persistence guard = PASS;
- `:core:security:testDebugUnitTest` = PASS;
- `:core:model:testDebugUnitTest` = PASS;
- `:core:parser:testDebugUnitTest` = PASS;
- `:core:network:testDebugUnitTest` = PASS;
- `:app:assembleDebug` = PASS;
- `:app:assembleRelease` = PASS;
- commit status `android-m5-security` = success;
- failure gate skipped as expected.

Regression tests freeze preflight ordering/failure tolerance, request fields, RSA block size/randomization, Cookie propagation, captcha continuation, success extraction, city update ordering, missing-token failure and oversized RSA input rejection.

`M5-B_RESULT = PASS / CLOSED`

## Remaining M5 work

### M5-C — password login + risk captcha

Port `login.htm`, RSA password encryption, success/error classification and official risk-captcha continuation contract. Reuse the M5-B RSA, device identity, explicit Cookie accumulation and captcha contracts instead of creating a parallel login stack.

### M5-D — login integration / persistence acceptance

- successful login validates credentials through the M4 quota path before account creation;
- only Cookie/appID/token_online persist as account credentials;
- M4 renewed credentials securely overwrite prior credentials;
- process/app restart reads credentials without re-login;
- logout/account deletion removes corresponding encrypted credentials;
- final M5 security/static/regression gates pass.

## Screenshot requirement

M5-A and M5-B require no real-device screenshots. M5-C implementation can also proceed from the frozen iOS source without visual screenshots. If a later runtime acceptance step requires real-device login/captcha evidence, the exact screens will be requested before that step begins.

## Next

`NEXT = Android-M5-C — Password Login + Risk Captcha`
