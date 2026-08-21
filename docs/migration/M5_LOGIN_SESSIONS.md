# M5-B2 登录会话网络层基线

## 状态

- 实现状态：`M5_B2_IMPLEMENTED`
- 激活状态：`NOT_WIRED`
- 未执行签名或 Gradle 编译验证；本次仅做静态检查。

## 覆盖范围

- 短信发送、短信验证码登录、密码登录，以及两类服务端安全验证码分支。
- 登录会话预热、响应 `Set-Cookie` 变更累积、城市 Cookie 更新。
- 成功响应提取 `cookie`、`appId`、`token_online` 和 `invalidAt`；调用方只能在成功后将 `AccountCredentials` 交给 M5-A vault。
- 输入校验与异常均使用固定错误码；不打印手机号、密码、验证码、Cookie 或 token。

## 与 iOS 的边界对齐

| iOS 文件 | SHA-256 | Android 对应 |
| --- | --- | --- |
| `ChinaUnicom/Services/RSAEncryptor.swift` | `8c5b12e7bd2cbc99f008047d9c9008c2822b1438449aa81371e67ed74ed6bd0f` | 密码登录会话、预热和密码安全验证码状态。 |
| `ChinaUnicom/Views/AccountCredentialLoginSessions.swift` | `2f637bf416c1d6f62a414509276c521825c4726fafc17410fc57b0c4bd2e0ebd` | 短信发送/登录、短信安全验证码状态。 |
| `ChinaUnicom/Services/KeychainStore.swift` | `99120d8043bba28fc0e7ef5f1feb45f246d79a4efbb3a06c99b7403caab96005` | 成功凭据由调用方交给 M5-A vault；本会话不落盘。 |

## 明确未接入

- 不创建或持久化 Android 设备身份；M5-B3 将通过 Keystore 存储稳定设备身份。
- 不实现验证码 WebView、页面、账号列表或自动登录。
- 不自动调用此类；仅未来用户主动登录操作才能实例化并调用。
