# UI-07 Flow Account Detail / Display Settings

## iOS source baseline

- `ChinaUnicom/Views/DashboardView.swift`
  - flow dashboard account-card `onOpenCard` routes to `AccountDetailView(accountID:)`.
- `ChinaUnicom/Views/AccountDetailView.swift`
  - account detail header, refresh time, package detail list, `管理显示`, and top-right menu actions.
- `ChinaUnicom/Views/PackageDisplaySettingsView.swift`
  - full-screen `显示内容` sheet, home preview, ambiguous-resource confirmation, flow summary groups, visible package management, and hidden package restore.
- `ChinaUnicom/Services/Formatting.swift`
  - masked mobile format is `123 **** 4567`.

## Android parity boundary

UI-07 corrects only the dashboard card navigation and presentation/configuration UI. Existing Android `UnicomAccount` presentation metadata (`displayPreferences`, `summaryGroups`, resource-kind override, package placement/order) remains authoritative and is persisted through the existing account metadata store. Carrier quota parsing, network requests, credentials, cookie/session lifecycle, and refresh authorities are unchanged.

Minimum supported Android version remains Android 11 (`minSdk=30`).
