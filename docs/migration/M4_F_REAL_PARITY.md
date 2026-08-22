# Android-M4-F — Real iOS / Android Same-Account Query Parity

## Status

`M4-F_RESULT = PASS / ACCEPTED`

The final M4-F gate is closed from the sanitized same-account parity evidence accepted on 2026-08-21.

Accepted evidence included the sanitized Android report:

`ChinaUnicom-M4-F-Android-Parity.txt`

Recorded report facts:

- report timestamp: `2026-08-21 10:01:50Z`;
- `overall=PASS`;
- quota/balance normalized output was suitable for comparison without exposing credential material;
- `session.credentialMutationObserved=false` for that run.

The accepted report is external validation evidence. It is **not** treated as proof that a new repository commit was produced by the parity run, and no raw authenticated payload or credential archive is committed to Git.

## Purpose

M4-F is the final real-account parity gate for Android-M4. It validates Android network/parser output against the frozen iOS business truth while keeping all credential material local to the user's devices.

## Safe credential source

The frozen iOS app provides a protected export path:

`设置 → 账户凭据 → 导出全部凭据`

The original export can contain:

- account identifiers;
- Cookie;
- appID;
- tokenOnline.

That source archive is secret-bearing and MUST NOT be uploaded to GitHub, committed to the repository, attached to CI, copied into logs, or retained as parity evidence.

## Android debug-only harness

`M4ParityActivity` remains under `app/src/debug/` only.

Its security boundary is:

- absent from release sources;
- reads the local credential archive through the Android file picker;
- keeps credential material process-local;
- never renders raw Cookie/appId/token_online;
- never logs authenticated headers or response bodies;
- never persists imported credentials;
- queries quota and balance through the M4 production network clients;
- emits only sanitized normalized result fields.

M5 remains responsible for production Android Keystore-backed credential persistence.

## Sanitized report contract

Permitted report fields include:

- masked mobile identity only;
- quota PASS/FAIL and non-sensitive error category;
- quota resource status;
- package name;
- normalized flow total/used/remaining values;
- quota type/category/share/carry-forward classification;
- normalized voice values;
- Remaining-query non-identity summaries;
- balance and unavailable/frozen normalized totals;
- unavailable/frozen item counts;
- whether a credential mutation occurred, never the mutated value.

Forbidden evidence includes:

- raw Cookie;
- appId/token_online values;
- passwords;
- SMS/captcha codes;
- full credential export JSON;
- authenticated response bodies;
- unredacted identity data.

## Acceptance record

The project owner supplied the sanitized Android parity report and corresponding sanitized iOS business-value evidence during the M4-F validation flow. The comparison was accepted as PASS.

This acceptance closes the earlier pending requirements that Android real quota/balance output match the iOS source-defined business values and formatting/classification semantics for the validated accounts.

No naturally encountered session-expiry credential mutation was observed in the accepted run (`session.credentialMutationObserved=false`). This does not weaken the automated session-reactivation contract: activation, Cookie mutation, token propagation and retry behavior remain covered by M4 automated tests derived from the frozen iOS networking source.

## Relationship to visual parity

M4-F validates business/data parity, not pixel parity. The screenshots supplied for this gate are evidence of iOS business values only.

M7 still owns formal page-by-page visual parity. Any deferred real-device light/dark screenshot requirements remain a later UI acceptance concern and do not reopen M4.

## Closure

- [x] sanitized real Android quota query evidence accepted
- [x] sanitized real Android balance query evidence accepted
- [x] normalized flow/voice classification/value comparison accepted
- [x] iOS business-value evidence accepted
- [x] no secrets committed to Git
- [x] session mutation observation recorded without exposing values

`M4-F_RESULT = PASS / ACCEPTED`

`M4_RESULT = PASS / CLOSED`

`NEXT = Android-M5 — Login + Security Storage`
