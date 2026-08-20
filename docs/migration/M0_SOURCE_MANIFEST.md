# Android-M0 — iOS Source Manifest

## Frozen source identity

| Item | Frozen value |
| --- | --- |
| Input archive | `chinaunicom-ios-main.zip` |
| ZIP SHA-256 | `72dfd9a9afea1caedefd7b00c1b1303722fd69a53fac89c7d09b8c2725f7bccf` |
| Archive embedded source commit | `e0ddba2b8da7ec8077e46c50b8dacbf4e13c2288` |
| Swift source files, excluding DerivedData | 359 |
| Swift source lines, excluding DerivedData | 67,585 |
| iOS deployment target | 17.0 |
| Swift language version | 5.0 |
| Main bundle ID | `com.clxmhcs.chinaunicom` |
| Shared App Group | `group.com.clxmhcs.chinaunicom` |

Build output (`.DerivedData/`, `DerivedData/`) is **not** part of the logical migration source baseline even though it exists in the supplied ZIP.

## Xcode targets

The frozen project contains five native targets:

1. `ChinaUnicom`
2. `ChinaUnicomWidgetExtension`
3. `ChinaUnicomIntentExtension`
4. `ChinaUnicomPacketTunnel`
5. `ChinaUnicomTests`

Important bundle identifiers present in the project:

- Main app: `com.clxmhcs.chinaunicom`
- Widget: `com.clxmhcs.chinaunicom.widget`
- Intent extension: `com.clxmhcs.chinaunicom.intent-extension`
- Packet tunnel: `com.clxmhcs.chinaunicom.packet-tunnel`
- Tests: `com.clxmhcs.chinaunicom.tests`

## Source-area size

| Source area | Swift files | Lines | Migration role |
| --- | ---: | ---: | --- |
| `ChinaUnicom/Models` | 11 | 3,319 | M2 model truth |
| `ChinaUnicom/Services` | 30 | 14,703 | network, parser, persistence, security, widget state |
| `ChinaUnicom/Stores` | 9 | 2,364 | business state/repository behavior |
| `ChinaUnicom/Views` | 52 | 30,098 | UI/interaction truth |
| `ChinaUnicomWidget` | 5 | 4,158 | M12 widget truth |
| `ChinaUnicomIntentExtension` | 4 | 2,079 | M13 automation/notification truth |
| `ChinaUnicomPacketTunnel` | 1 | 150 | M14 packet-tunnel entry |
| `ChinaUnicom/CaptureTool` | 236 | 9,004 | M14 capture/MITM implementation |
| top-level `CaptureTool` | 5 | 63 | M14 capture bridges |

The complete per-file line-count inventory is frozen in `M0_SOURCE_FILE_INDEX.txt`.

## Primary migration mapping

| iOS source | Android destination/role | Stage |
| --- | --- | --- |
| `Models/AppModels.swift` | `core/model` account/quota/settings models | M2 |
| `Models/RemainingQueryModels.swift` | remaining-detail and daily-baseline models | M2 |
| `Services/QuotaParser.swift` | quota parser | M3 |
| `Services/RemainingQueryParser.swift` | remaining-detail parser | M3 |
| `Services/Formatting.swift` | business formatting rules (UI-free portions) | M3/M7 |
| `Services/UnicomNetworking.swift` | HTTP/Cookie/status/shared-balance gate | M4/M6 |
| `Services/UnicomAPIClient.swift` | quota/session API client | M4 |
| `Services/UnicomBalanceClient.swift` | balance API client | M4/M6 |
| `Services/RSAEncryptor.swift` | password-login crypto/session | M5 |
| `Views/AccountCredentialLoginSessions.swift` | SMS login/session | M5 |
| `Services/KeychainStore.swift` | secure credential/backup semantics | M5 |
| `Services/PersistenceStore.swift` | account/settings persistence | M6 |
| `Services/AppStore.swift` + `AppStoreBalance.swift` | global/repository state behavior | M6 |
| `Views/DashboardView.swift` | flow dashboard | M7 |
| `Views/VoiceDashboardView.swift` | voice dashboard | M7 |
| `Views/ComprehensiveBusinessView.swift` | comprehensive business | M8 |
| `Views/OtherBusinessView.swift` | other-business navigation | M9 |
| electronic-receipt files | WebView/PDF/local storage | M10 |
| `Views/SettingsView.swift` + supporting settings views | settings | M11 |
| widget stores/exporters + `ChinaUnicomWidget` | Android widget | M12 |
| `ChinaUnicomIntentExtension` | automation/notifications | M13 |
| `CaptureTool` + packet tunnel | VpnService/capture/MITM | M14 |

## Platform capabilities that cannot be mechanically copied

- CloudKit/iCloud private database
- Keychain uninstall-survival behavior
- WidgetKit timelines
- App Intents/Shortcuts
- NetworkExtension packet tunnel
- Apple biometric/system UI

These are behavior-mapped, not source-copied.
