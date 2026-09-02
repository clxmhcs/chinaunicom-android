# UI-08 首页余额呼吸灯

## iOS 源码基线

`DashboardView.swift` 的 `BalancePillBackground` 在 `BalanceRefreshState.loading` 时：

- 从 7 个固定色中随机抽取 5 个作为当前渐变色；
- 每 0.85 秒重新随机一组；
- 使用 ease-in-out 过渡；
- 呼吸明暗在 0.26 与 0.52 opacity 之间交替；
- `idle` 为红色 0.09 底色并叠加淡紫到淡绿静态渐变；
- `failed` 为红到绿底色并叠加同一静态渐变；
- 余额 loading 指示器至少保持 5 秒。

## Android 对齐

- `FlowBalancePill` 的原固定五色 loading 渐变替换为 0.85 秒随机五色 + 0.26/0.52 明暗呼吸动画；
- 7 个候选颜色与 iOS 数值一致；
- idle / failed 背景层级按 iOS 恢复；
- 左半“手动刷新余额”和右半“查看话费账单”点击区保持各占 50%，不改变导航语义；
- `DefaultBalanceRepository.MINIMUM_HOME_LOADING_MILLIS` 已为 5000ms，因此未修改余额 Repository、网络、共享缓存、合账代表号码或刷新权威。

## 真机验收

录制 5~8 秒：流量首页点击余额胶囊左半区后，确认彩色渐变持续随机变化并呈现明暗呼吸，至少约 5 秒后随余额刷新结果回到 idle 或 failed 背景。
