# 上游差异审计与同步日志

本文件记录每一轮 RikkaHub 上游同步的基线、逐条决策，以及分支独有能力的现状。
目的是让后续轮次能直接看出“分支相对原版多出了什么、还缺什么”，避免无差别追新或静默回退。

- 上游：`rikkahub/rikkahub`
- 分支：`cQwQcsleep/RikkaHub-Nova`（`master` 为公开主线，`main` 为上游干净镜像）
- 最近更新：2026-11-13（第 2 轮）

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

---

# 第 2 轮（2026-11-13）

## 7. 本轮基线

| 项 | 值 |
|---|---|
| 本轮开始时 master | `15d0c355`（第 1 轮结束） |
| 上游 HEAD | `b10c1685`（2026-11-12） |
| 上游最近 release | `3.2.0`（2026-11-10，tag 指向 `e033a47d`） |
| 上次同步基线 | `f958af6a`（2026-11-05） |
| 上游新增提交 | 44 个（`f958af6a..b10c1685`，含 3.1.2 与 3.2.0） |
| 同步方式 | `port-3.2.0` 分支逐提交挑选移植，评审后并入 `master`；`main` 快进为上游镜像 |

上游 3.1.2 release 说明：重写消息渲染（BetterText）、标题栏对话菜单、导出保存为文件、消息概览左滑进入。
上游 3.2.0 release 说明：全局自动压缩（按上下文比例，默认 80%）、轮中压缩、Phone Use、
浮动胶囊顶栏、世界书拖拽排序、缓存历史图片压缩结果、Claude 思考签名与 Mistral 422 修复。

## 8. 44 个上游提交的逐项决策

### 8.1 本轮已合并（8 个，含 1 个局部移植）

| 上游提交 | 内容 | 本分支落点 | 说明 |
|---|---|---|---|
| `dba9fa57` | 收藏模型重复导致闪退 | `06c847bd` | 直接移植 |
| `e1667163` | Claude 思考块回传签名校验失败 | `c136caca` | 含上游新增回归测试 |
| `d3ef8174` | Mistral 推理模型多轮对话 422 | `0cb73d77` | 保留本分支 `requestBodyText` 序列化优化，手工解冲突 |
| `ae599e8d` | 缓存历史图片的压缩结果 | `6ad7f312` | 作为磁盘 L2 叠加本分支内存 L1，见第 9.3 节 |
| `5b7ca774` | 长对话对话地图滑动卡顿 | `669710f2` | 上游已重构为轮次卡片，本分支仍是逐消息卡片；只抽取“后台过滤 + 长文本截断”，并补淡入淡出 |
| `3733bac8` | 删除对话时同步删除收藏 | `8513ea1f` | **局部移植**：只取 DAO / Repository / ChatService 数据层；历史页多选 UI 改版不合并 |
| `c512338d` | 依赖更新 | `846f66f9` | material3 1.5.0-rc01、nav3 1.2.1、ktor 3.6.1、jmdns 3.6.5、sshd 2.20.1 |
| `0cd90ef1` | 按上下文比例自动压缩 | `cb9a0338` | **局部移植**：只取“压缩摘要请求组装移到 IO 协程”；全局触发/任务槽/轮中压缩不合并 |

### 8.2 暂缓 / 迁移项（不与功能合并）

| 上游提交 | 内容 | 决策与理由 |
|---|---|---|
| `0cd90ef1` + `bcc649ab` | 全局自动压缩（比例触发、任务槽）+ 轮中压缩 | 与本分支助手级 `autoCompact` / `compactThresholdTokens` 及 DSH 摘要检查点形成双触发；需要统一策略与迁移旧设置，单列评估 |
| `24a74227` + `50bbc99b` + `dfbe9a08` | BetterText 消息渲染重写、行内公式与删除线修复 | 仍暂缓，理由同第 6 节：176 文件迁移、无设备做视觉回归；两个 markdown 修复依赖新版 BetterText，不能单独移植 |
| `e11f3043` | 重新生成 baseline profile | 生成物基于上游代码与设备轨迹；本分支类集合与启动路径不同，需本地真机重新生成，整包搬运不可验证 |
| `4fa60373` | 消息概览改为左滑进入 | UI 入口重构，依赖上游轮次卡片实现；本分支现有入口可用，先不引入新交互面 |
| `b40d7a27` | 顶部栏按钮改为浮动胶囊 | 与本地聊天页动画/布局改动重叠，无设备无法做视觉对照；列为候选 UX |
| `fcc46712` | 顶栏对话菜单、导出保存为文件 | 新功能，不属于性能/流式/DSH 范围；列为候选功能 |

### 8.3 跳过（不属于本轮优先级或超出范围）

| 类别 | 上游提交 | 理由 |
|---|---|---|
| Phone Use | `25b70392`、`ffb1c79e`、`8a837b27`、`b10c1685` | 全新无障碍操作手机的大功能，引入面大，非本轮目标 |
| Workspace / Shell | `4ebfde22`、`27ef05ed`、`9135903d`、`e011970c`、`7691e385` | 工作区能力，非动画/滚动/启动/流式优先项；`27ef05ed` 留作后续 CPU 排查参考 |
| Web / 桌面 | `921427f9`、`64afac74` | Web UI 专属，按约束不无差别搬运 |
| 提供商 | `da498cb2`、`9bc0fd65`、`88a11a3b`、`6687f636`、`bbae6d3a`、`6328f4a8`、`133f4752` | 内置提供商/默认开启/余额 POST/目录刷新属于产品或新引入面；本分支已有 STA1N 推荐 |
| 设置 / 交互 | `62d6acd3`、`055faeb3`、`90fe936e` | 通知权限提示、快捷消息追加、用户 CA 信任；非本轮性能目标，列为候选 |
| 世界书 / 调试 / 文档 / 版本 | `88dee920`、`1c93ca72`、`1ad51511`、`90269e39`、`e033a47d`、`fe5a28f9`、`d10fedaf` | 功能、调试工具、文档与版本号；fork 自行管理版本 |
| 搜索框固定 | `5863c4e4` | 本分支概览搜索框本来就在 LazyColumn 外固定，无需改动 |

## 9. 差异清单（第 2 轮结束）

### 9.1 上游已有 / 分支已跟进

本轮 8 项见 8.1；上一轮 3.1.1→`f958af6a` 的功能与修复保持已跟进状态。

### 9.2 上游已有 / 分支未跟进

| 项 | 原因 | 后续动作 |
|---|---|---|
| BetterText 渲染迁移 | 无设备视觉回归，且上游库仍新 | `port-markdown-bettertext` 独立分支 |
| 全局/轮中自动压缩 | 与 DSH 摘要检查点策略冲突 | 单列“压缩策略统一”迁移轮 |
| Phone Use | 新增大功能，超出范围 | 人工决定是否引入 |
| 浮动胶囊顶栏 / 顶栏菜单 / 概览左滑 | UI 重构，视觉无法验证 | 有设备后做对照 |
| Baseline Profile 重生成 | 生成物不可跨代码基线搬运 | 真机 Macrobenchmark 轮次 |
| 工作区后台 Shell / SSH 跳板机 | 非优先项 | 候选 |

### 9.3 分支独有（本轮继续完善）

| 领域 | 本轮变化 | 文件 |
|---|---|---|
| 流式热路径 | 由“按帧（16ms）”改为 50ms 固定窗口 + 空窗口跳过；快照上限 60→20 次/秒 | `AgentLoop.kt`、`StreamingMessageAccumulator.kt` |
| 图片编码缓存 | 保留内存 L1，新增 RikkaHub 磁盘 L2（256MB、按内容键） | `FileEncoder.kt`、`ImageEncodeCache.kt` |
| 对话概览滚动 | 过滤移出主线程、conflate、正文截断 300 字符、卡片淡入淡出 | `ChatList.kt` |
| 压缩卡顿 | 压缩请求组装移到独立 IO 协程，取消不再等组装 | `ChatRequestFactory.kt` |
| 思维链滚动 | 沿用上轮按帧指数跟随；本轮流式内容更新降到 20Hz，滚动跟随仍 60Hz | `ChatMessageReasoning.kt` |
| 数据一致性 | 删除会话/节点时同步清理收藏 | `FavoriteDAO.kt`、`ConversationRepository.kt`、`ChatService.kt` |

## 10. 外部借鉴与搜索记录（第 2 轮）

搜索关键词：`compose performance android`、`compose streaming chat android`、`markdown compose android`、
`lazycolumn optimization compose`。筛选标准：近 3 年活跃、Apache-2.0/MIT 优先、可抽取为独立模块。

| 候选 | 星标 | 最近活跃 | 许可证 | 结论 |
|---|---|---|---|---|
| `AhmadHassan-BTed/Parchment` | 3609 | 2026-11-10 | Apache-2.0 | 未采用：完整 PDF 阅读器架构，无可独立抽取的滚动/批处理模块 |
| `skydoves/compose-performance` | 839 | 2026-06-16 | Apache-2.0 | 未采用代码：文章合集，仅作为重组检查清单参考 |
| `skydoves/compose-performance-skills` | 530 | 2026-06-16 | Apache-2.0 | 未采用：Agent 技能库，非运行时代码 |
| `skydoves/chatgpt-android` | 3876 | 2026-01-03 | Apache-2.0 | 未采用：流式依赖 Stream SDK，抽取会带入整套 IM SDK |
| `GetStream/stream-chat-android` | 1666 | 2026-11-12 | NOASSERTION | 未采用：许可证不明确且为完整 SDK |
| `mikepenz/multiplatform-markdown-renderer` | 1136 | 2026-11-10 | Apache-2.0 | 未采用：与上游 BetterText 方向冲突，且无法做视觉回归 |
| `MohamedRejeb/compose-rich-editor` | 1866 | 2026-11-11 | Apache-2.0 | 未采用：富文本编辑器，非只读渲染需求 |

本轮实际外部借用：**无新增代码或参数**。50ms 合并窗口与图片磁盘缓存均来自上游 RikkaHub 提交
（`0cd90ef1` 的压缩组装、`ae599e8d` 的图片缓存）与本分支既有实现；上游提交已在 8.1 逐条标注。

## 11. 已知问题状态（第 2 轮）

| 问题 | 状态 | 依据 / 局限 |
|---|---|---|
| 思维链滚动 | **改善（代码路径）** | 流式内容刷新从 60Hz 降到 20Hz，思维链 Markdown 重排次数同比例下降；滚动跟随仍按帧 60Hz。无设备，未测帧率 |
| CPU 占用 / AI 功耗 | **改善（代码路径）** | 快照构建上限 −66.7%，空窗口跳过；图片二次编码改为命中磁盘缓存。无设备，未测 CPU |
| 动画连贯性 | **改善** | 概览卡片增删加入淡入淡出；现有 `animateItem` 覆盖保持不变，未删减任何动画/特效 |

设备级帧率、卡顿率、CPU 与功耗数值仍无法在本沙箱采集；本轮不填估算值，留待 CI 真机或人工复测。

## 12. 验证与发布

- JVM：新增 `AgentLoopTest` 用例锁定 50ms 窗口；Claude/Mistral 上游测试随提交带入。
- 编译：由 GitHub Actions `nova-ci.yml` 在 `port-3.2.0` PR 与并入后的 `master` 上执行。
- 设备：本环境无 Android SDK/设备，Layout Inspector、Macrobenchmark、动画走查未执行；
  发布说明中不声称设备指标。
