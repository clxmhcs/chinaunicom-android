# M5-B1 登录 RSA 加密基线

## 状态

- 实现状态：`M5_B1_IMPLEMENTED`
- 激活状态：`NOT_WIRED`
- 未执行签名或 Gradle 编译验证；本次仅做静态检查。

## 冻结来源与映射

| iOS 来源 | SHA-256 | Android 映射 |
| --- | --- | --- |
| `ChinaUnicom/Services/RSAEncryptor.swift` | `8c5b12e7bd2cbc99f008047d9c9008c2822b1438449aa81371e67ed74ed6bd0f` | `UnicomLoginCrypto`：同一 SubjectPublicKeyInfo 公钥、`RSA/PKCS#1 v1.5`、Base64 输出。 |
| `ChinaUnicom/Views/AccountCredentialLoginSessions.swift` | `2f637bf416c1d6f62a414509276c521825c4726fafc17410fc57b0c4bd2e0ebd` | 后续 M5-B2 用该加密器构造短信发送和短信登录字段。 |

## 安全约束

- 不记录任何明文或密文，不缓存输入，也不承担网络请求。
- 移动号、密码和短信码只在调用期间转换为 UTF-8 字节；调用结束立即清零该字节数组。
- RSA 公钥为服务端公开材料；账户 Cookie、`token_online`、App ID 仍只由 M5-A Keystore vault 保存。
- M5-B2 才实现会话预热、Cookie mutation、验证码挑战和登录响应解析；本阶段不向联通接口发送请求。
