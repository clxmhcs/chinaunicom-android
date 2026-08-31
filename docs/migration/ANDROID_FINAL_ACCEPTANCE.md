# Android FINAL Acceptance Baseline

## 1. Final baseline

- Repository: `clxmhcs/chinaunicom-android`
- FINAL branch: `work/android-final`
- FINAL base main: `307e9ee32ef8e8de4e876c3557c570113d367a21`
- Minimum supported system: Android 11 / API 30
- `compileSdk`: 37
- `targetSdk`: 36
- Java/Kotlin target: 17
- FINAL version suffix: `-final1`

This document closes the migration sequence after M14 and records the permanent contracts that must remain true before Android-FINAL can be declared closed.

## 2. Migration stage closure

The migration sequence M0 through M14 is treated as implemented and closed subject to the permanent CI gates already stored in `.github/workflows/`.

Closed areas include the project/module baseline, models, parsers, China Unicom networking, login/security, account persistence and refresh, flow/voice/balance, migrated business functions, electronic receipt, settings, Widget, WorkManager automation/notifications, and CaptureTool.

Two product decisions are intentionally excluded from FINAL blocking criteria:

1. Android 快捷指令迁移已由用户明确跳过；Android 端保留 WorkManager、通知和 Widget 的原生自动化链路，不新增 ShortcutManager/App Shortcuts 实现。
2. CaptureTool 的高级 TLS 真机验收已由用户明确跳过。已迁移的 TLS/证书生命周期架构可以保留，但 FINAL 运行时继续保持 host certificate generation 与 active TLS decryption 为 disabled；M14-F 正式主链继续采用本地代理与 opaque HTTPS CONNECT 透传。

## 3. Final architecture contracts

### 3.1 Carrier networking authority

Widget、通知和 CaptureTool 不得复制或成为中国联通业务请求的独立 authority。账号凭据、刷新、查询与提交数据继续由既有 core/data repository 链路负责。

### 3.2 Credentials

- 账号敏感凭据继续通过 `AndroidCredentialStore` 保护。
- AndroidKeyStore + `AES/GCM/NoPadding` 合同必须保留。
- 主清单继续 `android:allowBackup="false"`。
- cookie、`token_online`、密码、验证码和 result token 不得直接写入普通 SharedPreferences。
- 运行时日志不得输出上述敏感值。

### 3.3 WebView bridges

FINAL 已知且允许的 JavaScript bridge 仅有：

- `MyOrderDetailScreen.kt`
- `ElectronicReceiptScreen.kt`

订单详情 bridge 必须保持联通域名限制、禁止文件访问并在销毁时移除 bridge。电子受理单 bridge 必须保持受控 document-start origins、URL allowlist、禁止文件访问并在销毁时移除 bridge。新增第三处 `addJavascriptInterface` 必须先经过新的安全审计，不得静默进入 FINAL。

### 3.4 Debug fixture isolation

`FakeUnicomRepository` 与测试号码/测试余量只允许存在于 debug source set，不得进入 `app/src/main` 或 `app/src/release`。

### 3.5 CaptureTool isolation

- `:capture` 保持独立模块。
- VPN 正式运行时继续只使用 TEST-NET 安全路由，不开放默认全量路由。
- 本地代理继续绑定 `127.0.0.1:9090`。
- 上游 Socket 必须在连接前由 `VpnService.protect(socket)` 排除 VPN 回环。
- HTTPS CONNECT 建立后只透明转发 TLS 字节，不保存正文。
- HTTP/HAR 历史继续只保存受限、脱敏后的结构化元数据，不保存请求/响应正文或原始包。
- TLS host certificate generation 与 active TLS decryption 在 FINAL 中继续为 disabled。

## 4. Permanent FINAL CI contract

`.github/workflows/android-final-build.yml` is the aggregate FINAL gate. It verifies Android 11 / API 30 minimum support, module graph integrity, FINAL version marker, credential security, logging boundaries, skipped shortcuts, audited WebView bridge surface, debug fixture isolation, CaptureTool runtime boundaries, all available debug unit tests, Widget/Capture debug assembly, and App debug/release assembly.

All earlier M1-M14 permanent workflows remain authoritative for their stage-specific invariants. FINAL does not replace those gates; it adds a cross-stage closure gate.

## 5. FINAL closure criteria

Android-FINAL may be declared closed only when:

1. FINAL PR is mergeable and its Android FINAL workflow succeeds.
2. Existing permanent M1-M14 workflows triggered by the FINAL change have no failures.
3. FINAL PR is merged into `main` with a locked expected head SHA.
4. Post-merge Android FINAL succeeds on the resulting `main` SHA.
5. Post-merge Android Main APK Build succeeds and uploads the APK artifact.
6. Any remaining user-visible real-device acceptance explicitly required at FINAL is recorded separately.

The skipped Android 快捷指令 implementation and skipped CaptureTool advanced TLS device acceptance are intentional product decisions and must not be reintroduced as hidden closure blockers.

## 6. Real-device acceptance boundary

No new screenshot is required merely to create or run the FINAL CI gate. If source/CI closure succeeds and a final UI/device acceptance is needed, the exact required pages must be named before requesting screenshots.
