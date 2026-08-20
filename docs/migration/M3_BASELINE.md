# Android-M3 — Quota / Remaining Parser Migration + Golden Tests

## Scope

M3 ports the parser/formatting behavior required to transform China Unicom quota JSON into the M2 domain models. It does **not** introduce HTTP, Cookie/session transport, login, persistence, repositories, Compose feature UI, Widget or automation.

The Android parser layer is isolated in `core:parser` and depends only on `core:model` plus a JSON runtime.

## iOS source truth

M3 was derived directly from the frozen `chinaunicom-ios-main.zip` source:

| iOS source | SHA-256 | Android target |
| --- | --- | --- |
| `ChinaUnicom/Services/QuotaParser.swift` | `b632521e00378a7134fe0bf8029e0a7b51e4b3cacb1a847e24a2ab0d920a3581` | `QuotaParser.kt` |
| `ChinaUnicom/Services/RemainingQueryParser.swift` | `bc2668d1417bfedbe97918192cb539fa4b3eff0771d5113ae36c21c974e3215a` | `RemainingQueryParser.kt` |
| `ChinaUnicom/Services/Formatting.swift` | `4e53f95f229a92c2037f1e814f012d9b7a1ca6030621fd2865be086eba89db83` | pure `FlowFormatter`/text helpers only |

`Formatting.swift` also contains SwiftUI/video-ring UI declarations. Those UI declarations are explicitly outside M3 and were not migrated into `core:parser`.

## QuotaParser semantics ported

The Android parser preserves the source-owned behavior for:

- top-level success and session-expired response codes;
- recursive package-name lookup;
- recursive candidate discovery with inherited flow/voice/SMS container context;
- resource-kind detection from type codes, item codes, names, paths and units;
- flow and voice unit conversion;
- `TB/GB/G/MB/M/KB/K/B` capacity interpretation;
- hour/minute/second voice conversion, including ambiguous `M` minute text;
- derived used value when total + remaining are present;
- unlimited detection from flags, names and negative totals;
- directed/general flow classification;
- shared/unshared detection including `typemark` semantics;
- carry-forward and merged-carry-forward metadata;
- stable fallback IDs using the same 64-bit FNV-1a algorithm;
- flow/voice deduplication and preferred-candidate scoring;
- `notSubscribed` behavior when quota resources are absent or only voice resources are returned.

M3 exposes parser-owned error states (`sessionExpired`, server message, `noPackages`). M4 will map these into the final network/API error surface when transport/session code is introduced.

## RemainingQueryParser semantics ported

The Android parser preserves:

- `data` payload-root fallback behavior;
- member and member-usage parsing/merging;
- flow summary categories;
- shared and unshared flow/voice/SMS parsing;
- derived used quota values;
- deterministic package IDs;
- package/member deduplication;
- unlimited-flow metadata indexing by fee-policy ID and normalized package name;
- explicit finite-quota protection against false unlimited classification;
- direct, inferred, indexed and global speed-limit resolution;
- `TB/GB/G/MB/M` speed-limit capacity parsing;
- near-whole-GB threshold normalization;
- role and current-login flag semantics;
- negative quota clamping where the iOS parser clamps values.

## Formatting semantics ported

The pure formatting portion of `Formatting.swift` is represented by `FlowFormatter.kt`:

- automatic mode chooses MB/GB from the original MB value before rounding;
- `1024 MB = 1 GB`;
- MB keeps up to two decimals and removes meaningless trailing zeros;
- GB rounds to two decimals; integer GB removes `.00`, non-integer GB keeps exactly two decimals;
- nil/non-finite values render as `--`;
- negative MB values display as zero;
- trimmed-text and mobile masking helpers are included.

## Golden fixtures

All M3 fixtures are synthetic/sanitized and contain no real Cookie, `token_online`, password, SMS code, identity data or unmasked real subscriber data.

Quota fixtures:

1. `fixture_01_normal` — finite domestic flow + duplicate candidate deduplication;
2. `fixture_02_unlimited` — unlimited flow;
3. `fixture_03_shared` — shared flow + included carry-forward split;
4. `fixture_04_directional` — directed/free-flow classification;
5. `fixture_05_voice` — voice minute parsing and voice-only status behavior.

Remaining fixture:

6. `fixture_01_full` — members, summaries, shared finite flow, shared unlimited flow with 10GB speed limit, voice, SMS and unshared resources.

Each fixture has a frozen `.expected.json` projection. `ParserGoldenTest` compares parser output field-by-field through a deterministic JSON projection.

## Additional parser tests

M3 also tests:

- `9998` session-expired preservation;
- successful empty quota container -> `notSubscribed`;
- iOS flow-formatting rounding/unit behavior;
- mobile masking and cleaned-text behavior.

## Module boundaries

`core:parser`:

- depends on `core:model`;
- has no Compose dependency;
- has no HTTP client;
- has no Cookie/session transport;
- has no credential storage;
- has no persistence/repository dependency.

The app depends on `core:parser` only to ensure the parser module participates in the integrated Android build. No M4 networking is introduced.

## Real CI verification — 2026-08-20

The first real M3 build reached Kotlin compilation and exposed one implementation-only type-boundary error in `RemainingQueryParser`: the recursive speed-limit leaf scan could hold `JsonElement?`, while the capacity helper accepted only a non-null element. The expected Golden outputs were not altered to hide this failure. The helper boundary was made nullable-safe without changing parser business semantics.

Final verification commit:

`af2171e03cbc75e94041efc7961af110ef70fc7d`

GitHub Actions run:

`32330224609`

Job:

`96309303999` (`parser-test-and-build`)

The runner discovered and used:

`ANDROID_API_37_PLATFORM_PACKAGE=platforms;android-37.0`

The authoritative verification command was:

`gradle :core:parser:testDebugUnitTest :app:assembleDebug --stacktrace`

Observed results:

- `:core:parser:compileDebugKotlin` = success
- `:core:parser:compileDebugUnitTestKotlin` = success
- `:core:parser:testDebugUnitTest` = success
- all frozen Quota/Remaining Golden projections = success
- `:app:compileDebugKotlin` = success
- `:app:assembleDebug` = success
- Gradle result = `BUILD SUCCESSFUL in 2m 50s`
- `97 actionable tasks: 97 executed`
- workflow job conclusion = `success`
- commit status `android-m3-parsers` = `success`
- failure guard step = skipped, as expected

This closes the parser compilation, Golden parity and integrated Android application build gates for M3.

## Acceptance gates

- [x] `core:parser` module exists
- [x] source parser SHA-256 fingerprints frozen
- [x] `QuotaParser` ported
- [x] `RemainingQueryParser` ported
- [x] pure `FlowFormatter` behavior ported
- [x] five required quota Golden fixtures added
- [x] full Remaining golden fixture added
- [x] expected-output projections added
- [x] no real account secrets in fixtures
- [x] parser layer has no Compose dependency
- [x] parser layer has no HTTP/Cookie/login dependency
- [x] `:core:parser:testDebugUnitTest` succeeds in GitHub Actions
- [x] `:app:assembleDebug` succeeds with `core:parser` integrated
- [x] commit status `android-m3-parsers=success` observed

`M3_RESULT = PASS / CLOSED`

## Screenshot requirement

No iOS or Android real-device screenshots were required for M3. Golden parser parity is data/behavior based. Real visual screenshots remain mandatory at the M7 visual-parity gate.

## Next stage gate

M3 acceptance is complete. M4 is authorized.

`NEXT = Android-M4 — HTTP / Cookie / Session Core`
