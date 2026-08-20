# Android-M0 — iOS Migration Baseline

## Purpose

Freeze the supplied `chinaunicom-ios-main.zip` as the authoritative business/source/visual reference before Android implementation starts.

No Android business implementation is introduced in M0.

## Frozen baseline identity

- ZIP SHA-256: `72dfd9a9afea1caedefd7b00c1b1303722fd69a53fac89c7d09b8c2725f7bccf`
- archive embedded source commit: `e0ddba2b8da7ec8077e46c50b8dacbf4e13c2288`
- logical Swift source inventory: 359 files / 67,585 lines (DerivedData excluded)
- five Xcode targets are inventoried in `M0_SOURCE_MANIFEST.md`

## M0 artifacts

| Artifact | Purpose | Status |
| --- | --- | --- |
| `MIGRATION_RULES.md` | permanent R1-R8 migration contract | PASS |
| `M0_SOURCE_MANIFEST.md` | source identity, targets, source-area mapping | PASS |
| `M0_API_MANIFEST.md` | known API/session protocol surface | PASS |
| `M0_MODEL_MANIFEST.md` | core/business/cross-cutting model scope | PASS |
| `M0_STORAGE_MANIFEST.md` | persistence, Keychain, cache, Widget/shared state | PASS |
| `M0_SCREEN_MANIFEST.md` | root navigation and feature screen map | PASS |
| `M0_VISUAL_BASELINE.md` | visual rules, source constants, screenshot contract | PARTIAL |
| `M0_VISUAL_ASSET_HASHES.txt` | source image-asset SHA-256 baseline | PASS |
| `visual-baseline/` screenshot set | real light/dark iOS reference images | PENDING |

## Security boundary

M0 documentation does not copy real Cookie values, `token_online`, passwords, SMS/captcha values or identity data from the source environment. Future fixtures must be sanitized before commit.

## Stage decision

`SOURCE_BASELINE = PASS`

`API_BASELINE = PASS`

`MODEL_BASELINE = PASS`

`STORAGE_BASELINE = PASS`

`SCREEN_BASELINE = PASS`

`VISUAL_SOURCE_SPEC = PASS`

`VISUAL_REAL_SCREENSHOTS = PENDING`

`M0_RESULT = NOT_CLOSED`

Reason: the migration plan requires a real iOS light/dark screenshot baseline before M0 can authorize progression to M1. The ZIP contains source/assets/build outputs but no complete real page screenshot set, and such screenshots must not be fabricated from source assumptions.

## M0 closure condition

M0 becomes `PASS / CLOSED` when all required screenshot files listed in `M0_VISUAL_BASELINE.md` are committed from the frozen iOS build (or when an explicit reviewed migration decision changes that gate).
