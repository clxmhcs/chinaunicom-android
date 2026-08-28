# Android M11 — 设置页面完整迁移

更新时间：2026-08-28

## 当前状态

- `M11_RESULT = IN_PROGRESS`
- `M11-A = PASS / CLOSED`
- `M11-B = PASS / CLOSED`
- `M11-C = NOT_STARTED`
- 最低 Android：Android 11 / API 30

## M11-A — 设置 authority 与根页面

闭合提交：`8255cf9bc3441eb26b28f4b85fe2b19aa38fca6d`

已完成：

- 新增持久化 `AppSettings` authority；
- 手机号中间四位隐藏、宽带号码隐藏、流量显示单位进入真实本地持久化；
- 设置根页面与凭据/App 刷新逻辑子入口完成；
- App 刷新设置继续复用既有统一 schema-3 authority，没有第二套刷新存储；
- Widget 主体仍属于 M12；抓包工具主体仍属于 M14；
- PR #33 在合并前通过 M1–M10 相关完整 PR 回归；
- 同步修正历史 M9-B4/M9-D/M9-E 工作流对累计迁移版本号的错误冻结，不改变业务合同。

## M11-B — 排序、合账、财务刷新号码、刷新策略编辑

闭合提交：`43762c18e8cb351b4a27303ec446bc0a8e055722`

已完成：

### 账户卡片排序

- 直接使用 M6 `AccountRepository` 普通账号元数据 authority；
- 上移/下移后重新生成连续 `sortOrder`；
- 写回持久化后通过唯一 production repository 重新水合；
- 因 `AccountRepository.loadAccounts()` 与刷新逻辑均按 `sortOrder` 排序，排序同时影响首页账号顺序和刷新全部号码顺序；
- 不接触 Cookie/appID/token_online。

### 合账号码选择

- 直接复用 M6 `BalanceRepository` / `BalanceAccountGroup`；
- 同一号码只能属于一个组；
- 至少两个成员才成为有效合账组；
- 新增组、删除组、成员加入/移除均调用现有 production authority；
- 不新增第二套合账持久化。

### 余额 / 账单刷新号码

- 首页余额显示/刷新号码直接调用既有 `setHomeBalanceAccountID`；
- 有效合账组默认财务号码直接调用既有 `setDefaultFinancialAccountID`；
- 首页余额号码位于有效合账组时，现有 M6 representative 规则继续优先使用首页号码；保存的组默认号码不会被清除，首页号码离组后可恢复生效；
- 综合业务话费/账单入口继续复用同一 `financialRepresentativeAccountID`。

### App 刷新逻辑

设置页已经为当前 Android 统一 schema-3 authority 中存在的全部业务域提供真实编辑控件：

- 流量 / 语音 quota；
- 余额；
- 已订业务；
- 我的套餐；
- 话费 / 账单；
- 积分；
- 我的订单；
- 返费 / 赠费；
- 视频彩铃。

所有修改仍写入同一个 `AppRefreshLogicPolicy` SharedPreferences 文档；没有新建并行刷新设置 authority。

PR #34 在合并前通过完整 M1–M10 / M9 历史回归，包括 Debug/Release assemble。

## M11-C — 尚未完成

下一批按 iOS 当前设置页继续迁移：

1. 号码归属纠正 / 运营商号段更新与已保存号段；
2. 每日用量基准管理；
3. Widget 单号码、双号码、刷新配置 authority（仅设置配置，Widget 主体仍在 M12）；
4. 电子受理单保存目录设置；
5. 快捷通知设置入口/authority；
6. 抓包工具设置入口（主体 M14）；
7. App 使用说明书入口；
8. 设置页剩余清除/维护动作与最终功能回归。

## 真机验收

M11-A/B 目前不要求单独真机截图。继续先完成 M11-C 功能代码和 CI；M11 形成完整候选 APK 后，再一次性列出设置页需要截图/操作验证的页面，避免中间阶段反复验收。

## NEXT

`NEXT = Android-M11-C — remaining settings authorities and entries`
