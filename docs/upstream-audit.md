# 上游差异审计与同步日志

本文件记录每一轮 RikkaHub 上游同步的基线、逐条决策，以及分支独有能力的现状。
目的是让后续轮次能直接看出“分支相对原版多出了什么、还缺什么”，避免无差别追新或静默回退。

- 上游：`rikkahub/rikkahub`
- 分支：`cQwQcsleep/RikkaHub-Nova`（`master` 为公开主线，`main` 为上游干净镜像）
- 最近更新：2026-11-06（第 1 轮）

## 1. 本轮基线

| 项 | 值 |
|---|---|
| 本轮开始时 master | `1715ea23` |
| 上游 HEAD | `f958af6a`（2026-11-05） |
| 上游最近 release | `3.1.1`（2026-10-24） |
| 共同祖先 | `ad2c4430`（即上游 `3.1.1` tag） |
| 上游领先提交 | 17 个（`ad2c4430..f958af6a`） |
| 分支领先提交 | 31 个（含本轮前） |

`master` 自 3.1.1 起采用**逐提交挑选移植**（cherry-pick + 本地适配），不做整支合并；
`main` 保持上游镜像，本轮由 `a5db9c9b` 快进到 `f958af6a`。

## 2. 上游 3.1.1 之后的 17 个提交

| 上游提交 | 日期 | 内容 | 决策 | 本分支落点 |
|---|---|---|---|---|
| `2c4d1979` | 10-26 | 修复非英文系统余额查询数字格式 | 已跟进 | `b25dea47` |
| `a7d1e6ef` | 10-26 | README 图标 512x512 | 已跟进 | `a862be0c` |
| `24a74227` | 10-27 | Markdown 迁移至 BetterText（176 文件，-19751 行） | **暂缓（迁移项）** | 见第 6 节 |
| `e0914b20` | 10-27 | 颜色代码前显示色块 | 暂缓（依赖 BetterText 的 inline decoration API） | — |
| `a1c61c3c` | 10-27 | 移除 Markdown 基准测试 | 跳过（随 `24a74227` 一并处理） | — |
| `79f4b6fb` | 10-28 | SAF 导出闪退 | 已跟进 | `b1b55d45` |
| `1575d179` | 10-28 | 兼容中转接口空 role | 已跟进 | `b2526adc` |
| `50bb5785` | 10-28 | 折叠思考链保持待审批工具可见 | 已跟进 | `e801690b` + `ba3f5bae` |
| `a046505a` | 10-28 | 加号面板滚动 | 已跟进 | `fd72e673` |
| `a5db9c9b` | 10-29 | Tinyfish purpose 参数 | 已跟进 | `d4f1e29f` |
| `5ad19405` | 11-01 | 模型选择多关键词搜索 | 已跟进 | `32905b1b` |
| `20e9ddcc` | 11-02 | 移除 material-color-utilities 子模块 | 已跟进 | `2226897c` |
| `903e058b` | 11-02 | 设置页本地网络权限提示 | 已跟进 | `3bcf39a4` |
| `e85c1d90` | 11-05 | 压缩保留消息数支持手动输入 | 已跟进 | `3710cef9` |
| `8b53b537` | 11-05 | 推荐提供商 STA1N + 图标 | 已跟进 | `c0835e97` |
| `afc335e9` | 11-05 | README 赞助商换为 STA1N | 跳过（fork README 已重写，文档类改动不适用） | — |
| `f958af6a` | 11-05 | README 恢复 Aihubmix logo | 跳过（同上） | — |

## 3. 差异清单（上游已有 / 分支状态）

### 3.1 已跟进

见上表“已跟进”行。除 BetterText 外，3.1.1 之后的 3.1.1→HEAD 功能与修复均已落地。

### 3.2 上游已有、分支未跟进

| 项 | 未跟进原因 | 后续动作 |
|---|---|---|
| BetterText Markdown 迁移 | 176 文件、删除本地 `ui` markdown 模块与 `highlight` 模块；必须做视觉与交互对照，当前环境无设备/模拟器 | 独立 `port-markdown-bettertext` 分支，见第 6 节 |
| 颜色色块 | 依赖 BetterText `InlineDecoration` | 随迁移一起 |
| Markdown BaselineProfile 基准 | 依赖迁移后的实现 | 随迁移一起，并作为滚动/流式指标采集手段 |
| README 赞助商变更 | fork README 已完全重写，不应覆盖 | 永久跳过 |

### 3.3 分支独有、上游没有（价值区，持续完善）

| 领域 | 内容 | 主要提交 / 文件 |
|---|---|---|
| DSH 前缀缓存诊断 | 请求前缀形状、TTL、命中观察 | `d6478072`、`48cbe7c7`、`ai/.../cache/*` |
| DSH 上下文压缩 | 自动压缩策略、摘要检查点、设置与手动面板 | `13b56336`、`343243df`、`1eec7af8`、`948571fc`、`3710cef9` |
| 流式热路径 | ChannelFlow 预览、按帧合并快照、按文件编码缓存 | `53e604c5`、`e5720751`、`13b56336`、`45e2b03f` |
| 思维链滚动 | 增量思考文本末尾裁剪、步骤折叠、按帧快照 | `abd5b20b`、`c50ae00a` |
| 列表滚动 | 稳定 key、contentType、预取窗口、增删淡入淡出 | `c50ae00a`、`6cfe6ac0` |
| 品牌与构建 | 移除 Firebase、固定签名、版本后缀 | `a8ff100f`、`ebbfda5d`、`7c72bf33`、`d17261db` |
| 上游修复的提前落地 | Tinyfish purpose、配额、SAF、空 role 等 | `d4f1e29f`、`b25dea47`、`b1b55d45`、`b2526adc` |

## 4. 外部借鉴登记

| 来源 | 星标 / 活跃 | 许可证 | 借用内容 | 落地位置 |
|---|---|---|---|---|
| `androidx/androidx`（Compose Foundation 1.12.1） | 6131 / 2026-11-05 | Apache-2.0 | `LazyLayoutCacheWindow` 预取窗口参数（ahead 0.5 / behind 0.25），替代默认约 10% 的前向窗口 | `ChatPage.kt` |
| `androidx/androidx`（Compose Foundation `LazyItemScope.animateItem`） | 6131 / 2026-11-05 | Apache-2.0 | 消息条目增删淡入淡出，显式禁用 placement 规格以避免流式高度变化时的位置抖动 | `ChatList.kt` |
| `androidx/androidx`（Material3 `MotionScheme`） | 6131 / 2026-11-05 | Apache-2.0 | `fastEffectsSpec()` 作为淡入淡出曲线，与分支现有动效语言一致 | `ChatList.kt` |

未采用候选：

| 候选 | 星标 / 活跃 | 许可证 | 拒绝理由 |
|---|---|---|---|
| `skydoves/chatgpt-android` | 3874 / 2026-01-03 | Apache-2.0 | 流式更新交给 GetStream SDK，抽取会把整套 IM SDK 带进来 |
| `mikepenz/multiplatform-markdown-renderer` | 1130 / 2026-11-05 | Apache-2.0 | 与上游 BetterText 方向冲突；换渲染器无法在无设备环境做视觉回归 |
| `GetStream/stream-chat-android` | 1665 / 2026-11-05 | NOASSERTION | 许可证不明确，且为完整 SDK |
| `skydoves/compose-performance` | 839 / 2026-06-16 | Apache-2.0 | 以文章/技能为主，无可直接抽取的实现；仅作检阅 |
| `jisungbin/ComposeInvestigator` | 306 / 2025-10-25 | Apache-2.0 | 重组追踪工具，需设备与 debug 插桩；列为下一轮验证手段候选 |

## 5. 已知问题状态（每轮更新）

| 问题 | 本轮结论 | 依据 |
|---|---|---|
| 思维链滚动 | 无变化（热路径已在上轮完成；本轮未触碰） | 本轮仅改消息列表条目与预取窗口 |
| CPU 占用 / AI 功耗 | 无变化（流式按帧合并沿用上轮实现） | 本轮未改流式链路 |
| 动画连贯性 | 改善（消息项增删有淡入淡出，预取减少空白帧） | `6cfe6ac0`；设备帧率待下一轮采集 |

> 本环境无 Android 设备/模拟器，帧率与 CPU 只能通过代码路径与 JVM 测试验证；
> 设备级数字必须由 CI 或人工在真机复测后填入，不得估算。

## 6. 迁移项：BetterText Markdown（暂缓）

| 项 | 内容 |
|---|---|
| 上游提交 | `24a74227`（2026-10-27），`e0914b20` 依赖其 API |
| 规模 | 176 文件，+653 / -19751；删除 `:ui` 的 markdown 解析器与 `:highlight` 模块，改为 `com.github.re-ovo.bettertext`（MIT，3 星，2026-10-28 仍活跃） |
| 与本地重叠 | 仅 3 个文件：`app/build.gradle.kts`、`ChatMessage.kt`、`ChatMessageReasoning.kt`；本地改动共 69 行，主要是“流式期间禁用 SelectionContainer”的 CME 规避 |
| 影响面 | 所有 Markdown/代码高亮/LaTeX 渲染；ChatMessage 与 ChatMessageReasoning 的 `streaming` 参数；BaselineProfile |
| 回退风险 | 迁移后 BetterText 自带选择容器，可移除本分支的流式选择规避；若 BetterText 在流式下不稳定，将同时影响正文与思维链，回退需整支回滚 |
| 暂缓原因 | 迁移要求“重构前后视觉效果可对照验证”（本任务 3.3 / 4.1），当前沙箱无设备与模拟器；同时上游只有 3 星的新库，需先做稳定性观察 |
| 下一轮动作 | 建 `port-markdown-bettertext` 分支压测；在真机走查表格、代码块、LaTeX、链接、选择与折叠；通过后再合并 |

本轮未合并该迁移，`ChatMessage.kt` / `ChatMessageReasoning.kt` 保持现有实现，本地流式选择规避完整保留。
