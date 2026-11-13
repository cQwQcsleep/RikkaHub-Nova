# DSH / Reasonix 已引入清单

本文件是 Nova 分支合并 Reasonix DSH 能力时的**唯一范围依据**。只有登记在这里的模块，才会
在后续轮次评估对应的上游更新；清单外内容一律视为未引入，只能作为“候选引入”记录。

- 维护方式：每轮同步前先核对；只有明确决定“新引入某一部分”时才扩充，并在当轮报告中单独声明。
- 参考源：`esengine/DeepSeek-Reasonix`（MIT）、`deepseek-ai/deepseek-harness`。
- 锚点含义：本分支首次引入时对应的上游提交/版本，不是当前应跟随的最新版本。

## 1. 前缀缓存诊断（Prefix Cache Diagnostics）

| 项 | 内容 |
|---|---|
| 状态 | 已引入，冻结跟随 |
| 本分支文件 | `ai/src/main/java/me/rerere/ai/cache/PromptCacheShape.kt`<br>`ai/src/main/java/me/rerere/ai/cache/PromptCacheLedger.kt`<br>`app/src/main/java/me/rerere/rikkahub/data/ai/PromptCacheObserver.kt` |
| 集成点 | `app/.../data/ai/ChatRequestFactory.kt`（工具声明按 name/description 排序）<br>`app/.../data/ai/UsageRecorder.kt`（读取厂商 usage 并回调观察器）<br>`ai/.../ui/StreamChunkHandler.kt`、`ai/.../provider/providers/*`（usage 透传） |
| 对外契约 | `PromptCacheShape(systemHash, toolsHash, prefixHash, bodyChain)`；`PromptCacheLedger.observe(key, shape, hitTokens, missTokens, ttlMillis): PromptCacheObservation`；`PromptCacheObserver.begin(...)` / `complete(...)`、`lastObservation` |
| 本地化改造 | ① 用 JVM `MessageDigest("SHA-256")` 取前 8 字节，替代 Reasonix 的短哈希；② 图片/视频/文档的 url 先哈希再拼接，避免 base64 放大哈希开销；③ TTL 用常量字面量（DeepSeek 24h、DashScope/Anthropic 5m），按 `baseUrl` 主机名判定厂商；④ 只观察、不改写请求 |
| 上游锚点 | Reasonix `internal/runtime/agent/cache_shape.go` @ `a534ead0ab`（2026-09-24）；观察方式参考 `run_loop.go` / `sessionstate.go` |
| 本分支提交 | `d6478072`（引入）、`48cbe7c7`（TTL 字面量修正） |

## 2. 上下文压缩（Context Compaction）

| 项 | 内容 |
|---|---|
| 状态 | 已引入（摘要检查点 + 保留尾部）；**上游已偏离，待迁移**（Reasonix 改为 archive fold，见 2026-11-13 记录）；默认触发比例保留 0.85 |
| 本分支文件 | `app/src/main/java/me/rerere/rikkahub/data/ai/ContextCompactionPolicy.kt`<br>`app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`（`autoCompactIfNeeded` / `compressSession`）<br>`app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`（`autoCompact`、`compactThresholdTokens`） |
| 集成点 | `AssistantBasicPage.kt`（压缩设置分组与模型窗口行）、`CompressContextDialog.kt`（手动压缩面板）、`FilesPicker.kt`（压缩入口） |
| 对外契约 | `ContextCompactionPolicy.TRIGGER_RATIO=0.85`、`RECENT_TAIL_BUDGET_RATIO=0.10`、`CHECKPOINT_CEILING_RATIO=0.50`、`TOKENS_PER_CHAR=0.25`、`MIN_THRESHOLD_TOKENS=1000`；`shouldCompact(...)`、`recentTailCount(...)`、`triggerTokens(...)` |
| 本地化改造 | ① 纯计算对象，不触碰会话状态，便于 JVM 单测；② 自定义阈值需落在 `[1000, 窗口85%)`，否则由容量保护接管；③ 未声明窗口（`contextLength<=0`）不自动压缩；④ 压缩摘要复用与上一轮相同的前缀以命中提示词缓存；⑤ 面板文案与用量行对齐 Reasonix 布局 |
| 上游锚点 | Reasonix `internal/runtime/agent/compact.go`、`compact_retention.go` @ `e1c38d3036`（2026-10-25）；常量可见 `context_budget.go` |
| 本分支提交 | `13b56336`（引入策略）、`343243df`（设置 UI 对齐）、`1eec7af8`、`948571fc`（手动面板对齐）、`3710cef9`（同步 RikkaHub 保留条数手动输入） |

## 上游跟进记录（清单内）

| 日期 | 上游提交 | 触及清单项 | 结论 |
|---|---|---|---|
| 2026-10-30 | `3baa8532b` fix(agent): charge auxiliary usage to the active root token budget | 上下文压缩（`compact.go` 摘要请求用量） | 无需移植：本分支摘要请求已由 `generateCompressionSummary` 包在 `UsageRecorder` 里，以 `UsageSource.COMPRESSION` 记账；本分支没有 Reasonix 的 root token budget 概念，等价能力已具备 |
| 2026-10-30 | — | 前缀缓存诊断（`cache_shape.go`） | 自锚点 `a534ead0a`（2026-09-23）后无改动，跳过 |
| 2026-11-02 | `7e1cdccb8` refactor(context): drop the soft compaction paths and default to 0.90 | 上下文压缩（`compact.go`） | 部分评估，**不跟进**：被移除的 soft-path / 失败诊断 / 保留消息裁切本分支从未引入；默认触发比例 0.85→0.90 属产品策略调整（会推迟压缩、抬高单次请求成本），不属修复或性能改进，保留 0.85 待人工确认 |
| 2026-11-06 | RikkaHub `e85c1d90` feat(chat): 压缩对话时保留消息数支持手动输入 | 上下文压缩（集成点 `CompressContextDialog.kt`） | 已合并（`3710cef9`）：仅把固定 4 条改为可输入，不改变策略契约与阈值语义 |
| 2026-11-06 | Reasonix `76ed2566ed` feat(context): a fold archives the whole span and keeps nothing | 上下文压缩（规则、保留策略、摘要） | **不跟进，标记“上游已偏离，待迁移”**：移除首轮钉住、最近尾部、保留用户轮次、摘要与 work index，改为把整段折叠进 `<session>.outputs/fold-<hash>.md`，只留一条指针让模型 `read_file` 读回。与本分支“摘要检查点 + 最近尾部 + 助手级阈值”契约不兼容，不能与功能/性能改动混在同一提交 |
| 2026-11-06 | Reasonix `1b8b56aa65` feat(studio): compaction archives earlier turns to a file instead of summarizing | 上下文压缩（摘要请求、失败重试、边界、UI 面板） | **不跟进**：删除 summarizer 路径及其重试/边界/测试，属于同一 archive fold 迁移；桌面端压缩面板不在本分支引入范围 |
| 2026-11-12 | RikkaHub `0cd90ef1` / `bcc649ab` 自动压缩改为全局比例触发 + 轮中压缩 | 上下文压缩（RikkaHub 侧集成） | **暂缓，单独评估**：本分支自动压缩是助手级 `autoCompact` + 声明阈值；上游改为通用偏好全局比例与任务槽。仅合并了其中无关性质的“压缩请求组装移到 IO 协程”（`cb9a0338`），触发/任务槽/轮中压缩不合并，避免与 DSH 策略形成双触发 |

本轮（2026-11-13）无新增引入项，清单未扩充。

`cache_shape.go` 自锚点 `a534ead0a`（2026-09-24）后无新提交，前缀缓存诊断无需同步。
`compact_retention.go` 在 `e1c38d303` 之后只有 `76ed2566ed` 一次删除性重构，已在上表记录；`context_budget.go` 最新为 `362c8381b`（2026-10-06），早于锚点，不涉及。

### 待迁移说明（archive fold）

- **迁移成本**：需要在 Android 侧建立可被模型读回的会话产物目录与 `read_file` 工具契约；重写压缩提示、检查点格式与恢复路径；旧摘要检查点需要兼容读取或一次性迁移。
- **影响面**：`ChatService.compressSession`、`Assistant` 压缩设置、`CompressContextDialog`、提示词缓存前缀计算、会话导出/备份。
- **回退风险**：现有摘要检查点、最近尾部保留、手动压缩面板都会失效；在端侧文件权限与清理策略未验证前强行跟进，可能造成上下文丢失。
- **结论**：本轮保留旧实现，不强行跟进；待有独立迁移轮次并具备设备验证条件后再做。

## 未引入（候选，不合并）

以下内容在 Reasonix / DSH 中存在、但本分支**没有**引入，因此不随上游同步：

- Reasonix 的多轮 `compact_projection` / `context_receipt` / `context_report` 体系；
- DSH 的插件化工具总线与 `Everything is a Plugin` 运行时；
- Reasonix 桌面端压缩面板前端（`desktop/frontend-next/src/ui/Compaction.tsx`）；
- Reasonix 的 Go 侧 session store / 事件流。

这些条目若后续要引入，必须在某一轮报告中单独声明理由与收益，并在此处新增登记。

## 本分支自有、不属 DSH（仅备查）

- 流式热路径合并器 `ai/.../ui/StreamingMessageAccumulator.kt`、`AgentLoop` 的 50ms 合并窗口与空窗口跳过（2026-11-13 起，替代按帧快照）；
- 图片/文档编码缓存 `ai/.../util/FileEncoder.kt`、`DocumentAsPromptTransformer`；内存 L1（2026-10 引入）叠加 RikkaHub 磁盘 L2（2026-11-13 移植）；
- 缓存稳定前缀（工具排序，见第 1 节集成点）；
- 对话概览后台预过滤与长文本截断（`ChatList.kt`，2026-11-13）；
- 动画/滚动/背景等 UI 优化（`ChatMessageReasoning.kt`、`ChatList.kt`、`MeshGradientBackground.kt`）。

这些是 Nova 的差异化价值，不属于 DSH 同步范围，也不以“同步上游”名义回退。
