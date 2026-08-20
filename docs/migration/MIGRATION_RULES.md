# ChinaUnicom Android Migration — Permanent Rules

These rules are frozen at Android-M0 and apply to all later migration stages unless a later reviewed migration decision explicitly supersedes one of them.

## R1 — iOS is the business truth

For the same input/response, if Android and iOS produce different business results, Android is treated as incorrect by default. An exception requires evidence that the iOS behavior itself is defective and a documented cross-platform correction.

## R2 — iOS is the visual truth

For UI owned by the app, Android must preserve the current iOS information architecture and visual hierarchy as closely as practical: layout, order, spacing, radii, typography hierarchy, colors, cards, progress indicators, icons, empty/loading/error states and light/dark behavior.

Platform-owned surfaces may remain native to the platform: keyboard, permission prompts, biometric prompts, system share/file pickers, status/navigation bars and similar operating-system UI.

## R3 — Platform implementation may differ; user-visible behavior may not

| iOS | Android target |
| --- | --- |
| SwiftUI | Jetpack Compose |
| ObservableObject / @Published | ViewModel / StateFlow |
| URLSession | Android HTTP client layer |
| Keychain | Android Keystore-backed secure storage |
| WidgetKit | AppWidget / Glance |
| App Intents / Shortcuts | Android automation/task entry points |
| UserNotifications | NotificationManager |
| WKWebView | WebView |
| NetworkExtension | VpnService |
| CloudKit | Android/cross-platform replacement decided at M10-CLOUD |

## R4 — Do not rediscover or reinvent China Unicom protocol behavior

Android must derive URL, HTTP method, request fields, headers, Cookie mutation, `appId`, `token_online`, RSA/AES/MD5/SHA256 behavior, response-code interpretation, retry/session activation, balance grouping, quota classification and parser rules from the frozen iOS source.

## R5 — No real secrets in Git

Never commit real Cookie values, `token_online`, `appId` tied to a real account, passwords, SMS/captcha codes, identity-card data or other account secrets. Golden fixtures must be sanitized.

## R6 — Business/data layers must not depend on Compose

Parsers, repositories, network/session code, storage and refresh policy must be independently testable without UI.

## R7 — Widget/automation must reuse the authoritative data path

The intended flow is:

`App / Worker -> shared Repository -> authoritative Snapshot -> Widget`

Widget or automation code must not create an independent China Unicom query/business implementation.

## R8 — Packet capture is last

`CaptureTool` and `ChinaUnicomPacketTunnel` are migrated only after the normal app, widget and automation path is complete. They may not block the daily-use Android app.

## Stage gate

The migration order is fixed unless explicitly changed by a reviewed decision:

`M0 -> M1 -> M2 -> M3 -> M4 -> M5 -> M6 -> M7 -> M8 -> M9 -> M10 -> M11 -> M12 -> M13 -> M14 -> FINAL`

A stage does not advance merely because code exists. Its acceptance criteria must be met and recorded.
