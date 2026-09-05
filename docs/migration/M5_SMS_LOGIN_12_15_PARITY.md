# Android M5 SMS Login — iOS 12.15 parity correction

Date: 2026-09-05

## Source of truth

Current `clxmhcs/chinaunicom-ios` SMS login uses the shared iOS client profile introduced after the original Android M5 migration:

- protocol version: `iphone_c@12.1500`
- native client version: `12.15`
- native build: `4`
- login endpoints remain `loginxx.10010.com`
- RSA PKCS#1 login encryption, request fields, Cookie accumulation and SMS captcha continuation remain unchanged.

Android M5 had been migrated on 2026-08-22 while iOS still used `iphone_c@12.1400` / native `12.14` build `13`. The iOS source then advanced to 12.15, leaving Android's SMS login identity stale.

## This correction

`UnicomSMSLoginSession` now sends the current iOS 12.15 identity consistently through:

- `version=iphone_c@12.1500` in SMS-code and `radomLogin.htm` forms;
- `appVersion=iphone_c@12.1500` in the switch bootstrap JSON;
- `c_version=iphone_c@12.1500` in the login Cookie seed;
- `ChinaUnicom4.x/12.15` / `build:4` / `unicom{version:iphone_c@12.1500}` in the native User-Agent.

The existing tests now freeze these values explicitly on switch bootstrap, code sending and SMS login. No verification code, Cookie value, appId or token_online is added to logs or ordinary persistence.

## Deliberately not changed in this step

This correction does not yet migrate the separate Android `UnicomAPIClient` session-renewal implementation from its older `iphone_c@9.0100` activation contract to the current iOS 12.15 modern renewal contract. That is a separate cross-cutting migration because the current iOS renewal request also carries the stable device identity and affects quota, balance and several extended-business clients.

It also does not invent an iPhone hardware model for Android. Android currently reuses the existing M5 device-identity abstraction; changing the device-model strategy requires real-device evidence rather than guessing an iPhone model string.

## Acceptance

- core SMS-login unit tests must pass;
- existing M1–M14 regression/build workflows must remain green;
- real-device acceptance: request a fresh SMS code and immediately submit that exact six-digit code using the same phone number/session.
