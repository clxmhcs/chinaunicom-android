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
| `M0_VISUAL_BASELINE.md` | visual rules, source constants, screenshot contract | PASS (source-derived) |
| `M0_VISUAL_ASSET_HASHES.txt` | source image-asset SHA-256 baseline | PASS |
| `visual-baseline/` screenshot set | real light/dark iOS reference images | DEFERRED TO M7 |

## Security boundary

M0 documentation does not copy real Cookie values, `token_online`, passwords, SMS/captcha values or identity data from the source environment. Future fixtures must be sanitized before commit.

## Explicit progression decision — 2026-08-20

After being informed that the supplied ZIP contains no complete real runtime screenshot set, the project owner explicitly instructed the migration to **enter the next step**.

This is recorded as the explicit migration decision permitted by `M0_VISUAL_BASELINE.md` to remove the screenshot set as an M1 entry blocker. It does **not** claim the screenshots exist and does not waive visual parity itself.

The real iOS light/dark screenshot set remains mandatory before Android-M7 visual-parity acceptance. M7 cannot close without the required comparison baseline.

## Stage decision

`SOURCE_BASELINE = PASS`

`API_BASELINE = PASS`

`MODEL_BASELINE = PASS`

`STORAGE_BASELINE = PASS`

`SCREEN_BASELINE = PASS`

`VISUAL_SOURCE_SPEC = PASS`

`VISUAL_REAL_SCREENSHOTS = DEFERRED_TO_M7`

`M0_RESULT = CLOSED_BY_EXPLICIT_MIGRATION_DECISION`

`M1_ENTRY = AUTHORIZED`

## Deferred closure condition

Before M7 may be accepted for UI parity, all required screenshot files listed in `M0_VISUAL_BASELINE.md` must be committed from the frozen iOS build (or replaced by another explicit reviewed visual-baseline decision that preserves R2).
