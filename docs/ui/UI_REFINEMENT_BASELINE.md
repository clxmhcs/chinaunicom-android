# Android UI Refinement Baseline

## 1. Purpose

This document freezes the starting point for the page-by-page Android UI refinement pass after the functional migration and Android-FINAL closure.

Frozen Android base:

- repository: `clxmhcs/chinaunicom-android`
- base branch: `main`
- base commit: `43df0b2268cc2edf801fa9610777492be147b4fb`
- minimum Android: **Android 11 / API 30**

This pass changes the app-owned visual/presentation layer. It must not create a second network, credential, refresh, cache, Widget, notification, or Capture authority.

## 2. Permanent UI rules

1. **Current iOS is the visual truth.** App-owned layout, spacing, hierarchy, colors, radii, typography, icons, progress indicators, loading/empty/error presentation, and light/dark appearance are compared against the frozen iOS source/screenshots.
2. **Closed business behavior remains authoritative.** UI work consumes the already-closed repositories, ViewModels, StateFlows, parsers, storage, workers, Widget snapshots, notification profiles, and Capture state.
3. **No Material-default redesign.** Material/Compose primitives may be implementation tools, but their default appearance is not the design target when it differs from iOS.
4. **No protocol work during visual refinement.** UI differences are not fixed by changing Unicom endpoints, Cookie/token handling, parser semantics, refresh policy, or credential storage.
5. **Android 11 remains the floor.** Visual APIs and navigation behavior must continue to work on API 30.
6. **No Android “Shortcuts” feature.** Existing `ShortcutNotification*` model/function names are legacy migration names for notification configuration only. User-facing UI must eventually use Android-native notification wording and must not present an Android shortcut/快捷指令 feature.
7. **No visual-complete claim without screenshot evidence.** A functional screen is not “near iOS” merely because it renders real data.

Existing frozen visual/source references:

- `docs/migration/M0_VISUAL_BASELINE.md`
- `docs/migration/M0_SCREEN_MANIFEST.md`
- `docs/migration/M7_BASELINE.md`

## 3. Current Android shell

### Activity / root navigation

- `app/src/main/java/com/clxmhcs/chinaunicom/MainActivity.kt`
- `app/src/main/java/com/clxmhcs/chinaunicom/ui/ChinaUnicomApp.kt`
- `app/src/main/java/com/clxmhcs/chinaunicom/ui/navigation/RootTab.kt`

There is one Compose app shell with five root tabs:

1. `flow` — 流量
2. `voice` — 语音
3. `comprehensive` — 综合业务
4. `other-business` — 其它业务
5. `settings` — 设置

Current bottom navigation still renders temporary text glyphs (`▥`, `☎`, `▦`, `☷`, `⚙`) through Material3 `NavigationBar`. This is **not** final visual parity and is part of the root-shell refinement scope.

### Existing design-system foundation

`core/design` already contains:

- `ChinaUnicomColors.kt`
- `ChinaUnicomDimensions.kt`
- `ChinaUnicomShapes.kt`
- `ChinaUnicomTypography.kt`
- `ChinaUnicomTheme.kt`

These files are the preferred place for reusable visual constants. Page refinement should reduce new scattered hard-coded values rather than add another design system.

## 4. Screen inventory and iOS mapping

Status vocabulary:

- `FUNCTIONAL / VISUAL-DEFERRED`: real behavior/data is migrated; screenshot parity has not been accepted.
- `FUNCTIONAL-SHELL`: usable functional surface whose current layout explicitly exists to expose migrated behavior rather than final iOS visuals.
- `SYSTEM-SURFACE`: Android-owned UI where platform-native appearance is expected.

No current app page is marked `VISUAL-CLOSED` in this baseline.

### 4.1 Root / flow / voice

| ID | Android surface | Android source | iOS visual/source truth | Data authority | Current UI status |
|---|---|---|---|---|---|
| UI-ROOT | Five-tab app shell | `ChinaUnicomApp.kt`, `RootTab.kt` | `RootView.swift` | root navigation + existing ViewModels | FUNCTIONAL / VISUAL-DEFERRED |
| UI-01 | Flow dashboard | `FlowHomeScreen.kt` | `DashboardView.swift`, `AccountCardView.swift`, `FlowPackageViews.swift` | `FlowViewModel` -> production `UnicomRepository` | FUNCTIONAL / VISUAL-DEFERRED |
| UI-02 | Flow remaining detail | `FlowHomeScreen.kt` (`FlowRemainingDetail`) | `RemainingQueryView.swift`, `RemainingQueryModels.swift`, `RemainingQueryParser.swift` | same root `FlowViewModel` / persisted account state | FUNCTIONAL / VISUAL-DEFERRED |
| UI-03 | Voice dashboard | `VoiceDashboardScreen.kt` | `VoiceDashboardView.swift`, `AccountCardView.swift` | shared `FlowViewModel`; no independent voice refresh | FUNCTIONAL / VISUAL-DEFERRED |

Important: `FlowHomeScreen.kt` and `M7_BASELINE.md` explicitly state that visual parity was deferred. UI-01/UI-02/UI-03 therefore begin from functional layouts, not accepted iOS reproductions.

### 4.2 Comprehensive business

| ID | Android surface | Android source | iOS visual/source truth | Data authority | Current UI status |
|---|---|---|---|---|---|
| UI-04 | Comprehensive root | `ComprehensiveBusinessScreen.kt` | `ComprehensiveBusinessView.swift` | `FlowViewModel` + `ComprehensiveBusinessViewModel` | FUNCTIONAL / VISUAL-DEFERRED |
| UI-05 | Ordered business detail | `ComprehensiveBusinessScreen.kt` (`OrderedBusinessEntryScreen`) | `OrderedBusinessView.swift` | `ComprehensiveBusinessViewModel` / ordered-business repository | FUNCTIONAL / VISUAL-DEFERRED |
| UI-06 | Phone bill detail | `ComprehensiveBusinessScreen.kt` (`PhoneBillEntryScreen`) | Phone bill views/components | `ComprehensiveBusinessViewModel` / phone-bill repository | FUNCTIONAL / VISUAL-DEFERRED |
| UI-07 | Comprehensive flow/voice remaining | `ComprehensiveBusinessScreen.kt` (`ComprehensiveRemainingEntryScreen`) | `RemainingQueryView.swift` | existing account/quota state | FUNCTIONAL / VISUAL-DEFERRED |
| UI-08 | Integral detail | `ComprehensiveBusinessScreen.kt` (`IntegralEntryScreen`) | `IntegralView.swift` | `ComprehensiveBusinessViewModel` / integral repository | FUNCTIONAL / VISUAL-DEFERRED |

`ComprehensiveBusinessScreen.kt` explicitly labels M8 wiring as functional-only with final visual parity deferred.

### 4.3 Other business hub and linked pages

| ID | Android surface | Android source | iOS visual/source truth | Data authority | Current UI status |
|---|---|---|---|---|---|
| UI-09 | Other business hub | `OtherBusinessScreen.kt` | `OtherBusinessView.swift` | navigation only | FUNCTIONAL-SHELL |
| UI-10 | Ordered business account hub | `OtherOrderedBusinessScreen.kt` | `OrderedBusinessView.swift` | comprehensive ordered-business authority | FUNCTIONAL / VISUAL-DEFERRED |
| UI-11 | Video ring account selection / member center | `VideoRingScreens.kt` | current formal behavior: `VideoRingMemberView.swift` | `VideoRingViewModel` / migrated video-ring data layer | FUNCTIONAL / VISUAL-DEFERRED |
| UI-12 | Electronic receipt | `ElectronicReceiptScreen.kt` | `ElectronicReceiptViews.swift`, `ElectronicReceiptWebView.swift`, `ElectronicReceiptStorage.swift` | `ElectronicReceiptViewModel` + local receipt storage/WebView bridge | FUNCTIONAL / VISUAL-DEFERRED |
| UI-13 | My orders | `MyOrderScreen.kt` | `MyOrderView.swift` | `MyOrderViewModel` / order repository | FUNCTIONAL / VISUAL-DEFERRED |
| UI-14 | My order detail | `MyOrderDetailScreen.kt` | `MyOrderDetailView.swift`, `MyOrderDetailWebBridge.swift` | `MyOrderViewModel` + controlled WebView bridge | FUNCTIONAL / VISUAL-DEFERRED |
| UI-15 | My package | `MyPackageScreen.kt` | `MyPackageView.swift` | `MyPackageViewModel` / package repository | FUNCTIONAL / VISUAL-DEFERRED |
| UI-16 | Integral account selection | `OtherIntegralAccountSelectionScreen.kt` | `IntegralView.swift` entry flow | existing mobile accounts | FUNCTIONAL / VISUAL-DEFERRED |
| UI-17 | Phone bill account selection | `OtherPhoneBillAccountSelectionScreen.kt` | phone bill account/entry flow | existing accounts + financial representative rule | FUNCTIONAL / VISUAL-DEFERRED |
| UI-18 | Rebate/gift account selection | `OtherRebateAndGiftAccountSelectionScreen.kt` | rebate/gift source family | existing accounts | FUNCTIONAL / VISUAL-DEFERRED |
| UI-19 | Rebate/gift detail | `RebateAndGiftScreen.kt` | rebate/gift source family | `RebateAndGiftViewModel` | FUNCTIONAL / VISUAL-DEFERRED |
| UI-20 | Tariff zone | `TariffZoneScreen.kt` | `TariffZoneView.swift` | `TariffZoneViewModel` | FUNCTIONAL / VISUAL-DEFERRED |
| UI-21 | Nearby service hall list | `NearbyServiceHallScreen.kt` | `ServiceHallView.swift` | `ServiceHallViewModel` | FUNCTIONAL / VISUAL-DEFERRED |
| UI-22 | Service hall detail | `NearbyServiceHallScreen.kt` (`ServiceHallDetailContent`) | service hall detail flow | selected hall state | FUNCTIONAL / VISUAL-DEFERRED |
| UI-23 | Service hall appointment | `NearbyServiceHallScreen.kt` (`ServiceHallAppointmentContent`) | `ServiceHallAppointmentView.swift` | appointment state / ticket client | FUNCTIONAL / VISUAL-DEFERRED |
| UI-24 | CaptureTool main | `CaptureToolScreen.kt` | current formal Capture chain beginning at `CaptureFeatureView.swift` | `capture` module runtime/state | FUNCTIONAL / VISUAL-DEFERRED |

The current `OtherBusinessScreen.kt` itself says business functionality was migrated first and visual refinement was deferred. Its current two-column Material cards are therefore not a visual target.

### 4.4 Settings / account / maintenance

| ID | Android surface | Android source | iOS visual/source truth | Data authority | Current UI status |
|---|---|---|---|---|---|
| UI-25 | Settings root | `SettingsRootScreen.kt` | `SettingsView.swift`, `SettingsSupportingViews.swift`, `SettingsSecurityViews.swift` | `SettingsRootViewModel` + existing settings repositories | FUNCTIONAL / VISUAL-DEFERRED |
| UI-26 | Account credentials / SMS login / iOS credential import / broadband credentials | `SettingsAccountScreen.kt` | `AccountCredentialViews.swift`, `AccountCredentialCaptchaViews.swift`, `AccountCredentialBroadbandViews.swift` | `FlowViewModel`, credential store, `BroadbandAccountViewModel` | FUNCTIONAL-SHELL |
| UI-27 | App refresh logic | `SettingsRootScreen.kt` (`AppRefreshLogicSettingsScreen`) | `AppRefreshLogicSettingsView.swift` | existing schema-3 refresh repository | FUNCTIONAL / VISUAL-DEFERRED |
| UI-28 | Carrier/location correction | `SettingsM11CGeneralScreens.kt` | settings supporting views | `SettingsM11CViewModel` | FUNCTIONAL / VISUAL-DEFERRED |
| UI-29 | Single Widget configuration | `SettingsM11CWidgetScreens.kt` | `WidgetInformationSettingsView.swift` | existing Widget configuration store | FUNCTIONAL / VISUAL-DEFERRED |
| UI-30 | Dual Widget configuration | `SettingsM11CWidgetScreens.kt` | `WidgetDualInformationSettingsView.swift` | existing dual Widget configuration store | FUNCTIONAL / VISUAL-DEFERRED |
| UI-31 | Widget refresh configuration | `SettingsM11CWidgetScreens.kt` | Widget refresh settings/source family | existing refresh policy repository | FUNCTIONAL / VISUAL-DEFERRED |
| UI-32 | Daily usage baseline | `SettingsM11CGeneralScreens.kt` | settings daily-baseline source family | `AndroidDailyUsageBaselineStore` | FUNCTIONAL / VISUAL-DEFERRED |
| UI-33 | Phone segments | `SettingsM11CGeneralScreens.kt` | settings/operator attribution source family | `SettingsM11CViewModel` | FUNCTIONAL / VISUAL-DEFERRED |
| UI-34 | Receipt directory | `SettingsM11CGeneralScreens.kt` | receipt storage settings | `ElectronicReceiptViewModel` / SAF directory authority | FUNCTIONAL / VISUAL-DEFERRED |
| UI-35 | Notification profile configuration | `SettingsM11CWidgetScreens.kt` (`ShortcutNotificationSettingsScreen`) | iOS shortcut-notification configuration is behavior reference only | existing Android notification profile/settings authority | FUNCTIONAL / TERMINOLOGY-DEBT |
| UI-36 | Capture settings entry | `SettingsM11CGeneralScreens.kt` (`CaptureToolSettingsEntryScreen`) | Capture settings entry | navigation/status only | FUNCTIONAL-SHELL |
| UI-37 | App manual | `SettingsM11CGeneralScreens.kt` (`AppManualScreen`) | `RootView.swift` manual/markdown responsibility | local `AppManual.txt` | FUNCTIONAL / VISUAL-DEFERRED |
| UI-38 | Clear accounts / credentials | `SettingsClearAccountsScreen.kt` | settings security/credential-management views | existing maintenance/security coordinator | FUNCTIONAL / VISUAL-DEFERRED |

`SettingsAccountScreen.kt` explicitly states `Functional-only account/settings entry. Visual parity is intentionally deferred.`

UI-35 note: the current visible title `快捷指令余量通知` is legacy iOS-migration wording. Android does **not** gain a shortcut feature. During its UI pass it must be renamed to Android-native notification wording while keeping the already-closed notification profile data contract.

### 4.5 Non-page visual surfaces

| ID | Android surface | Android source | iOS reference | Current status |
|---|---|---|---|---|
| UI-W01 | Single-number Widget | `widget/.../QuotaWidgets.kt` | `UnicomInfoWindMediumWidgetView.swift`, Widget views | FUNCTIONAL; visual pass separate |
| UI-W02 | Dual-number Widget | `widget/.../QuotaWidgets.kt` | dual Widget source family | FUNCTIONAL; visual pass separate |
| UI-N01 | Android notifications | `automation/AndroidNotificationService.kt` | iOS notification output semantics | SYSTEM-SURFACE + app-owned content |
| UI-SYS01 | VPN permission | Android system | `NetworkExtension` behavior reference only | SYSTEM-SURFACE |
| UI-SYS02 | Notification permission | Android system | no pixel-parity requirement | SYSTEM-SURFACE |
| UI-SYS03 | SAF directory/file picker | Android system | no pixel-parity requirement | SYSTEM-SURFACE |
| UI-SYS04 | Location permission | Android system | no pixel-parity requirement | SYSTEM-SURFACE |

Platform-owned permission dialogs, keyboard, status/navigation bars, file pickers and similar system surfaces are not expected to pixel-match iOS.

## 5. Current visual maturity conclusion

The migration has **functional closure but not visual closure**.

Evidence in current source/baselines includes:

- M7: visual parity explicitly deferred;
- Flow dashboard: source comment explicitly defers visual parity;
- Comprehensive business: source comment explicitly defers visual parity;
- Other business: source comment identifies the page as a functional shell pending later visual refinement;
- Settings account/login: source comment explicitly identifies it as functional-only;
- root bottom bar: temporary text glyphs remain;
- several screens still use generic Material `Surface`, `Button`, `OutlinedButton`, `FilterChip`, `NavigationBar`, default progress indicators and generic spacing without screenshot-based acceptance.

Therefore **no page may be marked “already close enough” solely from source inspection**. Screenshot comparison is required.

## 6. Do-not-touch authority during UI work

Unless a visual task exposes a separately reproducible business defect, UI PRs must not rewrite:

- `core/network` protocol/session/Cookie authority;
- `core/security` Keystore/credential authority;
- parser semantics and golden fixtures;
- production account/quota/balance repositories;
- shared balance gate semantics;
- refresh schema/WorkManager scheduling authority;
- Widget snapshot/export/update authority;
- Android notification delivery authority;
- Capture packet/session/runtime authority;
- controlled WebView origin/bridge security boundaries.

UI may consume these states, rearrange presentation, introduce visual-only adapters, or add presentation-formatting helpers where semantics do not change.

## 7. Fixed refinement order

To prevent UI work from becoming unordered, the default sequence is frozen as follows:

1. **UI-ROOT** — shared app shell, bottom bar, common page background/header behavior
2. **UI-01** — flow dashboard
3. **UI-02** — flow remaining detail
4. **UI-03** — voice dashboard
5. **UI-04** — comprehensive root
6. **UI-05..UI-08** — comprehensive details
7. **UI-09** — other-business hub
8. **UI-13..UI-23** — daily-use other-business pages (orders, package, integral, bill, rebate/gift, tariff, service hall)
9. **UI-12** — electronic receipt states/WebView/local list/viewer
10. **UI-25..UI-38** — settings/account/maintenance pages
11. **UI-W01/UI-W02** — Widget visual parity
12. **UI-24/UI-36** — CaptureTool UI
13. final cross-page light/dark consistency sweep

Exception: a shared component discovered during a page pass may be refined with that page if its changes are visual-only and all affected screens remain regression-safe.

## 8. Per-page acceptance contract

A page is `UI PASS / CLOSED` only when all applicable items are satisfied:

### Source / screenshots

- correct iOS source family identified;
- same logical data/account state used where practical;
- iOS Light vs Android Light reviewed;
- iOS Dark vs Android Dark reviewed;
- screenshot is uncropped for app-owned spacing measurements.

### Visual comparison

- page structure/order;
- horizontal/vertical margins;
- card width/radius/padding;
- background/gradient/material treatment;
- typography size/weight/hierarchy;
- number hierarchy;
- icons;
- progress bars;
- dividers/shadows;
- empty/loading/error states;
- expanded/collapsed states where applicable.

### Functional regression

- existing ViewModel/repository ownership unchanged unless separately justified;
- no fake production data;
- Android 11/API 30 retained;
- Android FINAL gate remains green;
- Main APK build remains green.

## 9. Screenshot naming for the new UI pass

Store accepted comparison evidence under a later dedicated evidence directory using stable page IDs, for example:

- `ui-01-ios-light.png`
- `ui-01-android-light.png`
- `ui-01-ios-dark.png`
- `ui-01-android-dark.png`

Do not commit screenshots containing unmasked credentials, Cookie, token, password, ID-card data, or other secrets.

## 10. Next stage

`NEXT = UI-ROOT/UI-01 — root shell + flow dashboard visual refinement`

The first implementation pass should start by comparing the root/flow screen rather than changing all pages at once. Root-shell changes are allowed only where they are needed to establish the shared visual baseline for UI-01.
