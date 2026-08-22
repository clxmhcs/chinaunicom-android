# Android-M5 — Login + Security Storage

## Status

`M5_RESULT = IN_PROGRESS`

Completed substage:

`M5-A_RESULT = PASS / CLOSED`

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

Credential unit tests freeze:

- exact round-trip of Cookie/appID/token_online;
- nullable appID/token_online preservation;
- multi-account isolation;
- overwrite affects only the selected account;
- stored blob does not contain known plaintext Cookie/token sentinels;
- blob copied to another account ID fails AES-GCM authentication;
- corrupt envelope fails closed;
- delete and delete-all remove stored credentials.

`M5-A_RESULT = PASS / CLOSED`

## Remaining M5 work

### M5-B — SMS login

Port the source-defined `sendRadomNum.htm` + `radomLogin.htm` flow, including request preparation, RSA mobile/code encryption, Cookie accumulation, appID/token_online extraction and SMS risk-captcha continuation.

### M5-C — password login + risk captcha

Port `login.htm`, RSA password encryption, success/error classification and official risk-captcha continuation contract.

### M5-D — login integration / persistence acceptance

- successful login validates credentials through the M4 quota path before account creation;
- only Cookie/appID/token_online are persisted;
- M4 renewed credentials are securely overwritten;
- process/app restart can read credentials without re-login;
- logout/account deletion removes the corresponding encrypted credentials;
- final M5 security/static/regression gates pass.

## Screenshot requirement

M5-A requires no real-device screenshots. M5-B/M5-C implementation can also proceed from the frozen iOS source without visual screenshots. If a later runtime acceptance step requires real-device login/captcha evidence, the exact screens will be requested before that step begins.

## Next

`NEXT = Android-M5-B — SMS Login Core`
