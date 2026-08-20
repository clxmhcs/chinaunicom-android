# Android-M0 — Visual Baseline

## Baseline policy

The current iOS app is the visual truth for app-owned UI. Android must not default to generic Material styling where that changes the existing visual identity.

This M0 freeze contains two visual layers:

1. **source-derived visual specification** — frozen in M0;
2. **real iOS screenshot set** — not present in the supplied ZIP and explicitly deferred to the M7 visual-parity gate by project-owner instruction on 2026-08-20.

The deferral authorizes M1 engineering only. It does not reduce the R2 requirement that iOS remains the visual truth.

## Source-derived constants already frozen

### Root

Source: `RootView.swift`.

- global tab tint: RGB `(0.18, 0.40, 0.95)`
- tab background: visible `ultraThinMaterial`
- receipt migration overlay dim: black opacity `0.22`
- migration card max width: `300`
- migration card padding: `22`
- migration card radius: `18`
- migration progress text: 15 semibold

### Flow dashboard

Source: `DashboardView.swift`.

- page horizontal padding: `16`
- top padding: `18`
- bottom padding: `26`
- top gradient begins with RGB `(0.95, 0.97, 1.0)` and transitions into grouped system background
- major numeric/title display: 36 bold rounded
- compact action capsule height: `42`
- empty-state icon frame: `84 x 84`
- empty-state icon container radius: `26`
- empty card radius: `24`
- empty card vertical padding: `40`, horizontal `24`

### Account card

Source: `Components/AccountCardView.swift`.

- card radius: `24`
- horizontal content padding: `20`
- top padding: `19`
- bottom padding: `18`
- multiple source-defined accent palettes (green/yellow/purple/orange families)
- secondary/tertiary text uses primary color opacity rather than fixed Android gray

### Voice dashboard/card

Source: `VoiceDashboardView.swift`.

- page horizontal padding: `16`
- top/bottom padding: `18` / `26`
- top 36 bold rounded hierarchy
- empty-state icon frame: `84 x 84`, radius `26`
- primary voice card padding: `20`
- voice card radius: `26`
- card outline uses very low primary opacity
- shadow: black opacity `0.05`, radius `15`, y `7`

### Widget

Source: `ChinaUnicomWidget/UnicomInfoWindMediumWidgetView.swift`.

- adaptive corner radius clamped between `18` and `22`
- shadow: black opacity `0.10`, radius `10`, y `4`
- header divider: black opacity `0.08`
- source-defined blue/green quota palettes, custom background image/assets and custom metric typography

## Frozen visual assets

The supplied iOS asset catalog contains 11 primary image assets relevant to app/widget identity, including app icon, launch background/logo, China Unicom knot watermark, InfoWind background/maps/voice/campus assets and receipt SIM card icon.

Their SHA-256 hashes are frozen in `M0_VISUAL_ASSET_HASHES.txt`. Android may convert formats where required, but a conversion must be traceable to these source assets.

## Required real screenshot set

The following screenshots must be captured from the frozen iOS build **before M7 visual-parity acceptance**. Use filenames exactly as listed under `docs/migration/visual-baseline/`.

### Light mode

- `light-01-flow-dashboard.png`
- `light-02-voice-dashboard.png`
- `light-03-comprehensive-business.png`
- `light-04-other-business.png`
- `light-05-settings.png`
- `light-06-remaining-query.png`
- `light-07-account-detail.png`
- `light-08-balance-card.png`
- `light-09-phone-bill.png`
- `light-10-my-package.png`
- `light-11-login.png`
- `light-12-widget-single.png`
- `light-13-widget-dual.png`

### Dark mode

- `dark-01-flow-dashboard.png`
- `dark-02-voice-dashboard.png`
- `dark-03-comprehensive-business.png`
- `dark-04-other-business.png`
- `dark-05-settings.png`
- `dark-06-remaining-query.png`
- `dark-07-account-detail.png`
- `dark-08-balance-card.png`
- `dark-09-phone-bill.png`
- `dark-10-my-package.png`
- `dark-11-login.png`

Widget dark-mode screenshots may be added if the current widget rendering differs by system appearance.

## Screenshot capture rules

- screenshots must come from the frozen iOS baseline identified in `M0_SOURCE_MANIFEST.md`;
- do not expose real unmasked secrets/credentials in screenshots committed to Git;
- use the same account/data state for paired iOS/Android comparison whenever practical;
- do not crop away app-owned spacing that is needed for measurement;
- preserve original image dimensions;
- platform-owned status bar differences are not parity failures by themselves.

## Visual-stage status

`SOURCE_VISUAL_SPEC = PASS`

`SOURCE_ASSET_HASH_BASELINE = PASS`

`REAL_IOS_SCREENSHOT_SET = DEFERRED_TO_M7`

`M1_VISUAL_FOUNDATION = AUTHORIZED_FROM_SOURCE_SPEC`

`M7_VISUAL_PARITY_GATE = BLOCKED_UNTIL_REAL_IOS_SCREENSHOTS_EXIST`
