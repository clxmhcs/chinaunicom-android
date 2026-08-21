# M5-B3 登录设备身份安全存储基线

## 状态

- 实现状态：`M5_B3_IMPLEMENTED`
- 激活状态：`NOT_WIRED`
- 未执行签名或 Gradle 编译验证；本次仅做静态检查。

## 实现

- `AndroidKeystoreLoginDeviceIdentityStore` 为一次安装生成并保存稳定的 `deviceCode`、`uniqueIdentifier`、`deviceId`、`appId`、城市 Cookie。
- 每个字段同样以独立 Android Keystore AES-256-GCM 密钥加密后保存；SharedPreferences 不出现明文身份值。
- 设备身份与账号凭据分开保存：删除一个账号的 Cookie 不会改变设备身份；显式 `delete()` 才能移除该身份和密钥。
- `updateCityCookie()` 仅接受数字省市编码，用于登录成功后更新后续请求的地区上下文。

## iOS 对照

`ChinaUnicom/Services/RSAEncryptor.swift`（SHA-256：`8c5b12e7bd2cbc99f008047d9c9008c2822b1438449aa81371e67ed74ed6bd0f`）中的 `PasswordLoginDeviceIdentity` 是字段形状、生成规则与城市 Cookie 行为的来源。

## 未接入项

- 未将此身份转换为 `UnicomLoginIdentity`，也未接到 M5-B2 网络会话。
- 未创建 UI、验证码 WebView 或自动登录。
