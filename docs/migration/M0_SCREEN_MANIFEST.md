# Android-M0 — Screen / Navigation Manifest

## Root information architecture

Source: `Views/RootView.swift`.

The app has five root tabs and their order is part of the visual/behavior baseline:

1. **流量** -> `DashboardView` -> SF Symbol `chart.bar.fill`
2. **语音** -> `VoiceDashboardView` -> `phone.fill`
3. **综合业务** -> `ComprehensiveBusinessView` -> `square.grid.2x2.fill`
4. **其它业务** -> `OtherBusinessView` -> `list.bullet.rectangle.fill`
5. **设置** -> `SettingsView` -> `gearshape.fill`

Root tint: `Color(red: 0.18, green: 0.40, blue: 0.95)`.

Root also owns electronic-receipt migration sheet/alerts/progress overlay, so Android navigation must not treat receipt migration as an unrelated page-only feature.

## Core flow/voice/account screens — M7

Source families:

- `DashboardView.swift`
- `VoiceDashboardView.swift`
- `Components/AccountCardView.swift`
- `Components/FlowPackageViews.swift`
- `RemainingQueryView.swift`
- `PackageDisplaySettingsView.swift`
- `PackageEditView.swift`
- account add/edit/detail views

Behavioral UI includes multi-account cards, flow/voice summary, package rows/groups, remaining detail, refresh states, display settings and package editing.

## Login/credential screens — M5/M11

Source families:

- `AccountCredentialViews.swift`
- `AccountCredentialLoginSessions.swift`
- `AccountCredentialCaptchaViews.swift`
- `AccountCredentialManagementViews.swift`
- `AccountCredentialComponents.swift`
- `AccountCredentialBroadbandViews.swift`
- `SettingsSecurityViews.swift`

Includes SMS login, service-password login, security captcha, credential protection/access password, broadband credentials and account credential management.

## Comprehensive business — M8

Source: `ComprehensiveBusinessView.swift` plus stores/domain views.

Migration scope includes entries/results for:

- balance / remaining bill amount
- flow remaining
- voice remaining
- ordered business
- phone bill
- integral/points

The exact card/entry order and state presentation is a UI baseline to compare from real screenshots.

## Other business — M9/M10

Source: `OtherBusinessView.swift` and linked screens.

Current linked business areas include:

- ordered business
- video ring membership
- electronic receipts
- my orders
- my package
- integral/points
- phone bill
- rebate/gift
- tariff zone

## Electronic receipt — M10

Source families:

- `ElectronicReceiptViews.swift`
- `ElectronicReceiptComponents.swift`
- `ElectronicReceiptWebView.swift`
- `ElectronicReceiptStorage.swift`
- `ElectronicReceiptCore.swift`
- `ReceiptMigrationManager.swift`

Important states/screens include account selection, activation, loading, load/login failure, H5 query, local saved list, local PDF viewer, empty states, note display/edit and storage migration.

## Settings — M11

Source families:

- `SettingsView.swift`
- `SettingsSupportingViews.swift`
- `SettingsSecurityViews.swift`
- `AppRefreshLogicSettingsView.swift`
- `WidgetInformationSettingsView.swift`
- `WidgetDualInformationSettingsView.swift`

Current settings responsibilities include account credentials, clearing/protecting accounts, attribution/carrier correction, card ordering, package display, daily usage baseline/refresh policy, balance grouping/financial representative selection, single/dual Widget configuration, Widget refresh behavior, shortcut notification configuration and CaptureTool entry.

## Widget — M12

Source:

- `ChinaUnicomWidget/ChinaUnicomQuotaWidget.swift`
- `UnicomInfoWindMediumWidgetView.swift`
- `UnicomQuotaWidgetViews.swift`
- Widget configuration/snapshot exporters/stores in `Services/`

Single and dual-number configurations are both part of parity scope.

## Automation/notification — M13

Source:

- `ChinaUnicomIntentExtension.swift`
- `ShortcutNotificationConfigurationStore.swift`
- `ShortcutNotificationIntents.swift`
- `ShortcutQuotaNotificationService.swift`

Android may replace the Apple entry mechanism, but the user flow remains query -> cache/snapshot -> Widget update -> optional notification.

## Capture — M14

`ChinaUnicom/CaptureTool` contains its own UI and state surface for capture, certificate/MITM, filters, export and diagnostics. It remains excluded from daily-use-complete acceptance until M14.

## Exhaustive source view index

`M0_VIEW_DECLARATION_INDEX.txt` freezes every detected SwiftUI/Widget `View` declaration in the supplied source and is used to prevent pages/components from silently disappearing during migration.
