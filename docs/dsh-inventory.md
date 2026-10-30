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
| 状态 | 已引入，跟随 Reasonix 常量 |
| 本分支文件 | `app/src/main/java/me/rerere/rikkahub/data/ai/ContextCompactionPolicy.kt`<br>`app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`（`autoCompactIfNeeded` / `compressSession`）<br>`app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`（`autoCompact`、`compactThresholdTokens`） |
| 集成点 | `AssistantBasicPage.kt`（压缩设置分组与模型窗口行）、`CompressContextDialog.kt`（手动压缩面板）、`FilesPicker.kt`（压缩入口） |
| 对外契约 | `ContextCompactionPolicy.TRIGGER_RATIO=0.85`、`RECENT_TAIL_BUDGET_RATIO=0.10`、`CHECKPOINT_CEILING_RATIO=0.50`、`TOKENS_PER_CHAR=0.25`、`MIN_THRESHOLD_TOKENS=1000`；`shouldCompact(...)`、`recentTailCount(...)`、`triggerTokens(...)` |
| 本地化改造 | ① 纯计算对象，不触碰会话状态，便于 JVM 单测；② 自定义阈值需落在 `[1000, 窗口85%)`，否则由容量保护接管；③ 未声明窗口（`contextLength<=0`）不自动压缩；④ 压缩摘要复用与上一轮相同的前缀以命中提示词缓存；⑤ 面板文案与用量行对齐 Reasonix 布局 |
| 上游锚点 | Reasonix `internal/runtime/agent/compact.go`、`compact_retention.go` @ `e1c38d3036`（2026-10-25）；常量可见 `context_budget.go` |
| 本分支提交 | `13b56336`（引入策略）、`343243df`（设置 UI 对齐）、`1eec7af8`、`948571fc`（手动面板对齐） |

## 未引入（候选，不合并）

以下内容在 Reasonix / DSH 中存在、但本分支**没有**引入，因此不随上游同步：

- Reasonix 的多轮 `compact_projection` / `context_receipt` / `context_report` 体系；
- DSH 的插件化工具总线与 `Everything is a Plugin` 运行时；
- Reasonix 桌面端压缩面板前端（`desktop/frontend-next/src/ui/Compaction.tsx`）；
- Reasonix 的 Go 侧 session store / 事件流。

这些条目若后续要引入，必须在某一轮报告中单独声明理由与收益，并在此处新增登记。

## 本分支自有、不属 DSH（仅备查）

- 流式热路径合并器 `ai/.../ui/StreamingMessageAccumulator.kt`、`AgentLoop`/`Agent` 的按帧快照；
- 图片/文档编码缓存 `ai/.../util/FileEncoder.kt`、`DocumentAsPromptTransformer`；
- 缓存稳定前缀（工具排序，见第 1 节集成点）；
- 动画/滚动/背景等 UI 优化（`ChatMessageReasoning.kt`、`ChatList.kt`、`MeshGradientBackground.kt`）。

这些是 Nova 的差异化价值，不属于 DSH 同步范围，也不以“同步上游”名义回退。
