# Android M11 — 设置页面完整迁移

更新时间：2026-08-28

## 当前状态

- `M11_RESULT = WAITING_REAL_DEVICE`
- `M11_FUNCTIONAL_CODE = PASS / CLOSED`
- `M11-A = PASS / CLOSED`
- `M11-B = PASS / CLOSED`
- `M11-C = PASS / CLOSED`
- `M11-D = PASS / CLOSED`
- 最低 Android：Android 11 / API 30
- UI 截图级视觉精修继续按项目约定后置，不属于本轮功能闭合阻塞项。

M11 的设置功能代码与 CI 已全部完成；当前只等待最终 `main` 候选 APK 的一次性真机功能验收。真机通过后再把 `M11_RESULT` 改为 `PASS / CLOSED`。

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

## M11-C — 剩余设置 authority 与入口

闭合合并提交：`7c1f55927c036b3433f66da5e2c5a6c1156f0a65`

PR：#35

专用 CI：Android M11 Settings run `33140161793` — SUCCESS。

已完成：

### 号码归属纠正与运营商号段

- 新增联通、移动、电信、广电基础号段模型；
- 支持本地号段回退与远端更新；
- 支持号码归属人工纠正；
- 人工纠正只改变显示运营商，不修改真实账号号码、CredentialStore 绑定或网络刷新目标。

### 每日用量基准

- 使用 Android 本地持久化；
- 以 `账号 UUID + 日期` 隔离；
- 不在白天缺失时伪造午夜基准；
- 同日派生的今日流量用量采用单调最大值，防止运营商累计计数回退造成显示倒退；
- 设置页提供只读查看入口。

### Widget 配置 authority

- 单号码 Widget 配置；
- 双号码 Widget 配置；
- 每侧固定六个资源位置；
- 双号码左右账号不能相同；
- 支持流量、语音、积分等配置模型；
- Widget 刷新策略写回现有 schema-3 刷新文档；
- 默认时间为 08:00、11:00、14:00、17:00，补偿窗口 6 分钟，失败重试 30 秒；
- 本阶段只完成设置/配置 authority，真正 Android 桌面 Widget 主体仍属于 M12。

### 电子受理单保存目录

- 复用 M10 已有 `ElectronicReceiptViewModel` / SAF directory authority；
- 使用 Android 系统目录选择器持久授权；
- 不建立第二套电子受理单目录设置。

### 快捷通知

- 按账号 UUID 保存普通通知配置；
- 支持 A/B/C/D 槽位；
- 同一槽位不能同时分配给两个账号；
- 支持流量、语音、余额、失败通知与模板；
- 普通设置中不保存 Cookie/appID/token_online 等敏感凭据。

### 抓包工具入口

- 设置入口已迁移；
- VPN / HTTPS 解密 / 证书 / 抓包会话主体明确属于 M14；
- M11 不用假抓包页面冒充完成能力。

### App 使用说明书

- Android 原生离线说明书资源；
- 支持搜索、目录、章节跳转、继续阅读和联通水印；
- 内容按当前 Android 已迁移能力重新编写，不直接照搬 iOS 中 Face ID、iOS 17、WidgetKit 等平台专属说明。

M11-C PR 在合并前通过专用 M11 gate 以及完整历史回归，包含 Debug/Release assemble。

## M11-D — 清空账户与凭据

闭合合并提交：`f77908de75e205aab8ff4237f343170cb35e3867`

PR：#36

专用 CI：Android M11 Settings run `33141205027` — SUCCESS。

该 PR 对应提交上的 19 个 M1–M11 / M9 历史工作流全部 SUCCESS。

已完成：

- 设置根页增加“清空账户与凭据”维护入口；
- 存在手机账号时，进入破坏性维护前必须完成身份验证；
- 当前 Android 尚无独立“凭据管理密码” authority，因此按 iOS 兼容回退边界使用排序后的首个手机号码作为验证值；
- 验证输入只存在于 Compose `remember` 内存并使用密码遮罩，不写入普通设置；
- 支持手机账号、独立宽带分别选择；
- 支持全选、取消全选、删除选中、全部清空；
- 真实执行前有二次确认；
- 删除手机账号同步删除同 UUID CredentialStore 凭据、账号元数据、合账关系、首页余额代表引用、每日基准，并裁剪账单缓存；
- 单账号删除保留普通元数据与同 UUID 凭据的回滚副本，中途失败时尽量恢复，避免半删除状态；
- 删除独立宽带同步删除宽带元数据与同 UUID CredentialStore 凭据；
- 全部清空会删除全部手机/宽带账户与账户凭据，清理账户级运行状态和缓存，并恢复 App 显示、刷新、号段、Widget、快捷通知等设置默认值；
- 已保存的电子受理单 PDF 不随账户清空删除，保持与源清理边界一致；
- 全清后重新水合长期存活的 Settings / repository 状态，避免 UI 继续持有已删除账号或旧设置。

## 安全边界

M11 设置迁移继续遵守：

- Cookie、appID、token_online 不进入普通设置模型或普通元数据文件；
- 独立宽带敏感凭据仍由既有安全 CredentialStore 管理；
- Widget、号码归属、快捷通知、说明书等普通设置不复制保存敏感登录凭据；
- 号码归属纠正不改变真实网络查询目标；
- 合账、余额代表、刷新设置均复用既有 production authority；
- Android 最低版本保持 API 30。

## 真机验收

M11 功能代码已经形成完整候选版本。最终真机只做一次性功能验收，不要求截图级 UI 像素对照。

建议验收页面/操作：

1. **设置根页**：确认显示、桌面组件、数据刷新、合账、运营商号段、数据与安全、工具、维护、关于等入口均可见。
2. **号码归属纠正**：进入页面并确认手机号码列表可加载，修改一个号码后返回再进入仍保持。
3. **App 刷新逻辑**：进入后确认流量/语音、余额、已订业务、套餐、账单、积分、订单、返赠、视频彩铃设置均可打开；无需逐项修改。
4. **每日用量基准**：确认页面可以打开并显示已有账号对应状态。
5. **单号码组件信息编辑**：确认账号可选且六个位置可见；M12 前不要求桌面出现 Widget。
6. **双号码组件信息编辑**：确认左右号码不能设置成同一个号码。
7. **组件刷新编辑**：确认默认/当前刷新时间列表可以显示。
8. **电子受理单保存目录**：确认能打开 Android 系统目录选择器；无需改动当前已验证的真实目录。
9. **快捷指令余量通知**：确认账号与 A/B/C/D 槽位设置页可以打开。
10. **App 使用说明书**：确认说明书能打开；搜索、目录和继续阅读至少各操作一次。
11. **清空账户与凭据**：只验证身份页、选择页和最终确认框即可，**不要实际执行全部清空**，避免破坏现有真实测试账号。可以在最终确认框选择取消。

无需再次验证 M9-B4、M9-C/D/E/F/G/H/I 或 M10 已经通过的业务页面。

## NEXT

`NEXT = M11 final real-device acceptance -> M12 Android desktop Widget`
