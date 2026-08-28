# Android M12 — 桌面 Widget

更新时间：2026-08-28

## 当前状态

- `M12_RESULT = IN_PROGRESS`
- `M12-A = IN_PROGRESS`
- 最低 Android：Android 11 / API 30
- UI 像素级精修继续后置；M12 先闭合 Widget 数据、刷新和系统桌面组件主体。

## 永久架构边界

Android Widget 不建立第二套中国联通业务网络栈。

唯一允许的数据路径：

`App / background unified repository -> committed account data -> Widget Snapshot -> Glance Widget`

因此 Widget 模块禁止直接调用联通接口、登录接口、Cookie、appID、token_online 等业务凭据。

## iOS 源合同

M12 对照当前 iOS：

- `WidgetConfigurationStore`
- `WidgetDualConfigurationStore`
- `WidgetSnapshotExporter`
- `WidgetDualSnapshotExporter`
- `WidgetDualSnapshotStore`
- `ChinaUnicomQuotaWidget`
- `UnicomInfoWindMediumWidgetView`
- `WidgetQuotaRefreshService`

### 单号码

- 账号选择：设置中指定账号 -> 首个启用账号 -> 首个排序账号；
- 手机号按 `3 + **** + 4` 展示；
- Snapshot 包含账号 UUID、显示名、套餐名、今日流量、余额、更新时间和最多 6 个资源位置；
- 默认位置为国内流量、省内流量、小区流量、校区流量、国内语音、一家亲语音；
- 资源位置继续复用 M11 的 Widget 配置 authority。

### 双号码

- 左右号码不能相同；
- 左右各固定 6 个位置；
- 类型支持流量、语音、可用积分；
- 积分只读取 App 已有积分缓存，Widget 不自行请求积分接口；
- 号码展示后四位；
- Snapshot 保存今日流量、余额、更新时间和资源数据。

### 今日用量

沿用 iOS 午夜基准边界：

- 基准按账号 UUID + 本地日期隔离；
- 白天缺失午夜基准时不伪造基准；
- 普通日累计计数回退时保留最近有效同日值；
- 同日缓存保持单调最大值；
- 月初允许运营商计数重置；
- 重复资源身份选择更可信的累计值，不能重复相加。

## M12-A — Snapshot 与 Glance 主体

当前分支：`work/m12-widget-a`

已落地：

- 新增 `:widget` Android library；
- 使用稳定 AndroidX Glance 1.2.0；
- 新增单/双号码 Snapshot model；
- 新增 `AtomicFile` Widget Snapshot 持久化；
- 新增 iOS 同源单/双槽位映射器；
- 新增午夜基准今日用量计算器，复用 M11 `AndroidDailyUsageBaselineStore`；
- 新增 Snapshot exporter，复用 M11 单/双号码配置；
- 新增 `SingleQuotaGlanceWidget` / `DualQuotaGlanceWidget`；
- 新增两个 AppWidget receiver/provider；
- provider `updatePeriodMillis=0`，不依赖 Android 最低 30 分钟轮询；
- 主 App 单号/全部刷新、前台自动刷新、手动余额刷新完成后，由唯一 production repository 导出当前账号 Snapshot；
- 设置中修改单/双号码 Widget 配置后立即重写 Snapshot 并请求 Glance 更新；
- Widget Snapshot/刷新副作用失败不会回滚已经成功持久化的运营商业务数据。

## M12-A 当前不包含

- Widget 自身的“刷新”点击入口；
- Widget 自动刷新后台调度器；
- 点击 Widget 深链定位到 App 业务页；
- 最终视觉精修。

这些进入 M12-B/M12-C；后台统一调度仍必须复用 production repository，不允许 Widget 直接访问联通接口。

## 验收策略

M12-A 先通过专用 `Android M12 Widget` CI：

- Widget JVM 单测；
- `data:refresh` 回归；
- App JVM 测试；
- Debug / Release assemble；
- 静态检查 Widget 模块没有敏感凭据和直接联通网络实现。

M12 所有功能代码完成后再一次性真机验证，不在 M12-A 中间阶段要求截图。

## NEXT

`NEXT = M12-A CI -> M12-B unified Widget refresh entry -> M12-C final functional acceptance`
