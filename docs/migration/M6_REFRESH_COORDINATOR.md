# M6-A 刷新协调器基线

## 状态

- 实现状态：`M6_A_IMPLEMENTED`
- 持久化状态：`PENDING_M6_B`
- 未执行签名或 Gradle 编译验证；本次仅做静态检查。

## 已实现语义

- 自动刷新优先复用同一自然日、间隔内的成功缓存；手动刷新绕过缓存有效期。
- 同一刷新单元只能有一个有效 lease；并发调用获得 `InFlight`，不会重复联网。
- 只有 lease 所有者可提交结果；失败仅释放 lease，绝不覆盖上一次成功缓存。
- 共享账号组变更会同时失效其缓存和 in-flight lease；未配置账号自动使用独立单账号 scope。
- 下一个自动刷新时间取“间隔截止”与“次日零点”中较早者，与 iOS 余额 Shared Gate 的自然日语义一致。

## 来源

`ChinaUnicom/Services/UnicomNetworking.swift` 中 `SharedBalanceCacheStore` 与 `ChinaUnicom/Services/AppStoreBalance.swift` 的 refresh lease / cache / forced refresh 语义。

## 未实现

- 跨进程文件锁与持久化状态（M6-B）。
- 账号仓库、设置仓库、余额分组与网络请求执行（后续 M6）。
