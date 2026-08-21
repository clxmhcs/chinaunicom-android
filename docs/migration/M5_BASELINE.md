# M5-A 凭据安全存储基线

## 状态

- 实现状态：`M5_A_IMPLEMENTED`
- 激活状态：`BLOCKED_BY_M4_F_DEVICE_EVIDENCE`
- 本阶段不包含登录页面、密码/RSA 加密、短信验证码、账号增删或 UI。
- 未执行签名或 Gradle 编译验证；本次仅做静态检查。

## 冻结的 iOS 对照输入

| 文件 | SHA-256 | 本阶段使用点 |
| --- | --- | --- |
| `ChinaUnicom/Services/KeychainStore.swift` | `99120d8043bba28fc0e7ef5f1feb45f246d79a4efbb3a06c99b7403caab96005` | 每个账号独立保存、读取、删除凭据；可清空全部。 |
| `ChinaUnicom/Models/AppModels.swift` | `84bdd7c16e37a39b914827a02f11ef1d4e1c58a76335c5cd423247bb053133fa` | 凭据字段仅为 `cookie`、`appID`、`tokenOnline`。 |
| `ChinaUnicom/Views/AccountCredentialLoginSessions.swift` | `2f637bf416c1d6f62a414509276c521825c4726fafc17410fc57b0c4bd2e0ebd` | 留待 M5-B 接入登录会话流程。 |
| `ChinaUnicom/Services/RSAEncryptor.swift` | `8c5b12e7bd2cbc99f008047d9c9008c2822b1438449aa81371e67ed74ed6bd0f` | 留待 M5-B 实现密码登录所需 RSA 处理。 |

## Android 映射

- 新模块：`core:security`。
- `AndroidKeystoreCredentialVault` 使用不可导出的 `AndroidKeyStore` AES-256-GCM 密钥；私有 SharedPreferences 只保存密文、IV 和格式版本。
- 存储以账号 UUID 隔离；`save`、`read`、`delete`、`deleteAll` 都是同步完成，写入失败会明确失败而不会伪造成功。
- 使用默认凭据加密存储而非 device-protected storage，因此设备首次解锁前不可访问；这与 iOS `AfterFirstUnlock` 的可访问边界保持一致。
- 任何凭据、账号 UUID、密文均不记录日志；异常消息为固定安全文案。
- `deleteAll` 同时删除此 vault 的 SharedPreferences 记录和唯一 Keystore 密钥，确保无法恢复旧密文。

## 明确不做

- 不将密码、短信验证码、账号资料、套餐/余额数据写入此 vault。
- 不把 vault 接到现有登录、刷新或 UI；M4-F 的设备侧时间窗口证据完成前，生产链路保持未激活。
- 不替代后续 M6 的账号资料持久化与刷新协调器。
