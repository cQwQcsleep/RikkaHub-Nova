<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>RikkaHub Nova</h1>

原生安卓 LLM 聊天客户端，基于 [RikkaHub](https://github.com/rikkahub/rikkahub) 的第三方分支。

原版作者：**Reovo / rikkahub** · 分支维护：**cQwQcsleep**

[English](README.md) | 简体中文
</div>

<div align="center">
  <img src="docs/img/poster.png" alt="RikkaHub" width="800" />
</div>

## 🧬 这个分支改了什么

Nova 保留上游的界面与行为，重点优化提示词缓存与流式输出的开销。

| 方向 | 改动 |
|---|---|
| 提示词缓存 | 每次请求记录前缀形状（系统提示、工具声明、逐条消息链），并与同一会话的上一条请求对比。厂商返回的 usage 仍是命中量的唯一依据，形状只负责解释「为什么没命中」（`system` / `tools` / `body` / `trim`），以及长时间未请求后的冷启动（DeepSeek 24 小时，DashScope / Anthropic 5 分钟）。 |
| 前缀稳定 | 工具声明按名称排序后再发送，MCP 服务重连后枚举顺序变化不会再让厂商缓存整体失效。 |
| 流式热路径 | 请求体只序列化一次；日志字符串只在对应级别打开时拼接；合并流式分片时不再逐 token 复制整个消息列表。 |
| 思维链解析 | 开头的 `<think>` 块改为线性扫描，替代每个 token 都重扫全文的惰性正则。 |
| 重复编码 | 图片编码结果与文档解析结果按文件标识做有界 LRU 缓存，历史图片/PDF 不再每次请求重新解码。 |
| 界面开销 | 动画全部保留：流式快照按显示帧合并（≤60/s），尺寸动画从「每个 token 重启」变成「每帧最多重启一次」；思维链预览用连续缓动跟随；动态渐变背景以 20fps 采样。 |
| 数据显示 | token 速度改用口径一致的字段计算（多步累计），新增缓存命中率；生成过程中用量卡片即可实时显示估算 token 与速度，结束后切换为真实用量。 |
| 上下文压缩 | 自动折叠沿用 Reasonix 的阈值：窗口用到 85% 触发，最近约 10% 消息原样保留，检查点占用上限 50%；摘要请求复用同一前缀，折叠本身也命中缓存。 |
| 移除 Firebase | 不再包含统计与崩溃上报，自行构建的分支不再需要 `google-services.json`。 |
| 关于页 | 增加分支标识、两位作者、本仓库与原版的链接，以及本分支与原版的区别列表。 |

本分支的问题请反馈到本仓库，不要提交给上游。

## 🚀 下载

- Releases：<https://github.com/cQwQcsleep/RikkaHub-Nova/releases>
- `app-github-universal-release.apk` 适配所有受支持的 ABI，`arm64-v8a` / `x86_64` 体积更小。
- APK 使用分支固定的 CI 密钥签名，后续 Nova 版本可以直接覆盖安装。
- `app-github-universal-debug.apk` 未混淆、包名带 `.debug`，可与正式包共存，用于排查只有 R8 包才会出现的问题。

## ✨ 功能（继承自上游）

- Material 3 Expressive 设计、深色模式、动态取色
- 多提供商：任意 OpenAI / Anthropic / Google 兼容接口，可自定义请求头与请求体
- 多模态输入（图片、文本文档、PDF、DOCX）、媒体生成
- MCP、技能、本地工作区（proot Linux shell）、网页访问
- Markdown 渲染（代码高亮、LaTeX、表格、Mermaid）
- 搜索服务（Exa、Tavily、智谱、LinkUp、Brave、Perplexity、SearXNG 等）
- 助手定制、文件式记忆、AI 翻译、SillyTavern 角色卡

## 🛠 从源码构建

### 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 21 | 与 CI 一致 |
| Android SDK | compileSdk 37（`minorApiLevel = 2`），build-tools 与 AGP 9.4 匹配 | 通过 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir` 指定 |
| Android NDK + CMake | `:terminal`、`:workspace` 需要 | 在 SDK manager 中安装 |
| Node.js | 22 | 构建 `web-ui` |
| pnpm | 11 | 使用 `web-ui/pnpm-lock.yaml` |
| Git | 任意较新版本 | 需要拉取 `material3` 子模块 |

```bash
git clone --recurse-submodules https://github.com/cQwQcsleep/RikkaHub-Nova.git
cd RikkaHub-Nova

# 内嵌网页服务的前端（:web:preBuild 会再次执行）
(cd web-ui && pnpm install --frozen-lockfile)

# 构建
./gradlew :app:assembleGithubDebug        # debug APK
./gradlew :app:assembleGithubRelease      # R8 正式包（需要签名，见下）
./gradlew :app:testGithubDebugUnitTest    # JVM 单元测试
./gradlew :ai:testDebugUnitTest           # 缓存核心单元测试
./gradlew spotlessApply                   # 代码格式化
```

正式包签名从 `local.properties` 读取（路径相对于 `app` 模块）：

```properties
storeFile=nova-release.jks
storePassword=...
keyAlias=nova
keyPassword=...
```

未提供时构建出的正式包不签名。因为已移除 Firebase，构建不再需要 `google-services.json`。

## 📁 工程结构

| 模块 | 职责 |
|---|---|
| `app` | Compose 界面、ViewModel、对话服务、设置、Room 数据库、依赖注入，以及本分支的缓存诊断 |
| `ai` | 提供商抽象（OpenAI Chat Completions / Responses、Claude、Gemini）、消息变换、流式解码、缓存形状引擎 |
| `common` | 通用 HTTP / JSON / Android 工具 |
| `ui` | 可复用的 Compose 组件与 Markdown 渲染 |
| `material3` | Material 取色工具（git 子模块 `material-color-utilities`） |
| `document` | PDF / DOCX / PPTX / EPUB 文本提取 |
| `highlight` | 代码高亮 |
| `search` | 搜索服务 SDK |
| `speech` | TTS / ASR 实现 |
| `web` | 内嵌 Ktor 服务，托管构建好的 `web-ui` |
| `web-ui` | React 前端，构建产物复制到 `web/src/main/resources/static` |
| `workspace` | 沙箱化的 proot 工作区与 shell 工具 |
| `terminal` | 终端模拟器核心与 PTY 传输 |
| `mediagen` / `oauth` | 媒体生成与 OAuth 辅助 |
| `app:baselineprofile` | 启动/滚动基线配置生成器 |
| `tools/` | `i18n` 命令行与 trace 录制工具（不进入 APK） |

关键入口：`RikkaHubApp`（启动、Koin）、`ChatService`（一轮对话的编排）、`ChatRequestFactory`（请求组装）、`GenerationHandler` / `AgentLoop`（步骤循环与流式合并）、`PromptCacheObserver`（缓存诊断）。

## 🧾 资源引用说明

下列资源要么已提交在仓库里，要么在构建时生成。多数资源在运行时按固定路径读取，替换时请保持文件名不变。

| 资源 | 位置 | 引用方式 / 替换方法 |
|---|---|---|
| 启动图标 | `app/src/main/res/mipmap-*` 各密度 PNG + `mipmap-anydpi-v26/ic_launcher.xml`（自适应图标的背景/前景/单色三层） | 由 `AndroidManifest.xml` 的 `android:icon` 引用。替换时保持同名，四套密度 PNG 与三个自适应图层都要齐全。 |
| 提供商 / 模型图标 | `app/src/main/assets/icons/*.svg`，少量 `.png` | `AIIcon` 通过 `file:///android_asset/icons/$path` 加载，文件名来自 `computeAIIconByName` 与模型注册表。新增提供商时补一个同名文件即可。 |
| models.dev 模型目录 | `app/src/main/assets/models_dev.json` | 只是离线快照。`ModelsDevUpdater` 会定期从 `https://models.dev/api.json` 拉取、精简后存到 `filesDir/models_dev` 并应用；assets 里的文件只用于首次启动兜底。 |
| 表情选择器 | `app/src/main/assets/emoji/categories.with.modifiers.min.json` | `EmojiUtils.loadEmoji` 从 assets 读取。 |
| Markdown / Mermaid WebView | `app/src/main/assets/html/mark.html`、`html/mermaid.min.js` | `MarkdownWeb` 用该模板在 WebView 中渲染 HTML 内容。升级 Mermaid 时保持文件名即可。 |
| 内置技能 | `app/src/main/assets/builtin_skills` | 首次启动时由 `SkillManager.ensureBuiltinSkillsExtracted` 解压到应用文件目录。 |
| 字体 | `app/src/main/res/font/google_sans_flex.ttf`、`jetbrains_mono.ttf` | 主题与代码高亮引用。 |
| 文档图片 | `docs/img/*`、`docs/icon.png` | 仅用于 README，不打包进 APK。 |
| SQLite 扩展 | `app/src/main/jniLibs/{arm64-v8a,x86_64}/libsimple.so` | `SQLiteConfiguration` 通过 `SQLiteCustomExtension` 在运行时加载。属于预编译 ABI 产物，替换时所有 ABI 要一起更新。 |
| proot 二进制 | `workspace/src/main/jniLibs/{arm64-v8a,x86_64}/libproot_*.so` | 预编译产物，供工作区启动器使用。 |
| 原生源码 | `terminal/src/main/cpp/termux_pty.cpp`、`workspace/src/main/cpp/workspace.cpp` | Gradle 构建时由 CMake 编译（需要 NDK）。 |
| Material 取色 | `material3/material-color-utilities` | git 子模块，锁定到固定提交；构建前执行 `git submodule update --init --recursive`。 |
| 网页前端产物 | `web-ui/` → `web/src/main/resources/static` | `:web:preBuild` 执行 `pnpm run build` 并复制产物，目标目录已被 git 忽略。 |
| 多语言文案 | `app/src/main/res/values*/strings.xml`，各模块自带的 `values*/strings.xml` | 用 `tools/i18n`（Bun 命令行）新增与校验。 |

## 📈 性能画像

对一次长思维链回复录制的 388 个线程采样显示，CPU 主要花在：

| 线程 | 均值 | 峰值 | 解读 |
|---|---|---|---|
| `RenderThread` | 41.6% | 60.9% | Compose 绘制与不断增长的文本光栅化 |
| `mali-cmar-backend` | 24.6% | 36.8% | GPU 命令提交与光栅化 |
| `HeapTaskDaemon` | 11.8% | 75.0% | 逐 token 字符串/列表拷贝造成的 GC |
| `ReferenceQueueDaemon` | 6.0% | 19.9% | 同一批垃圾的引用处理 |
| `DefaultDispatcher-*` | 各自约 4%，峰值约 50% | | 后台 Markdown 解析、缓存哈希、JSON |

绘制与光栅化约占三分之二，GC 约占六分之一，后台计算只占少数。因此本分支**没有**用另一种语言重写流式链路：瓶颈不在计算，而在分配与绘制。对应改动是：按帧合并快照并用 StringBuilder 累积、编码/解析结果有界缓存、背景 20fps 采样、去掉重复的动画重启。如果后续采样出现单一计算热点占主导，再引入原生模块才有意义——由测量结果决定，而不是语言偏好。

## 🔍 缓存实现说明

- `ai/.../cache/PromptCacheShape.kt`：生成形状快照。系统文本、归一化后的工具签名各取 8 字节哈希，正文按逐条累加得到哈希链；id、时间戳、usage 这类本地字段不参与。
- `ai/.../cache/PromptCacheLedger.kt`：按会话序列保存上一次形状并对比，给出 `system` / `tools` / `body` / `trim` 原因，并按 `PromptCachePolicy` 的厂商 TTL 判断冷启动。
- `app/.../data/ai/PromptCacheObserver.kt`：由 `UsageRecorder` 调用，在请求发出前捕获形状，拿到 usage 后合并结论。每次请求在 `PromptCache` tag 下打一行：`prefix`、`changed`、`reasons`、`carried`、`hit`、`miss`、`cold_resume`。
- 观察器只测量、不改写请求。任何会影响发送内容的逻辑都属于请求组装层。

## 🔁 CI 与发布

| 工作流 | 触发方式 | 产物 |
|---|---|---|
| `nova-ci.yml` | push / PR / 手动 | 运行 `:ai` 与 `:app` 单元测试，再构建 debug APK |
| `nova-release.yml` | 手动（可指定 tag）或推送 `nova-*` tag | 签名正式包，同时构建 debug 包，一起发布为 Pre-release |

签名使用仓库 Secret `NOVA_KEYSTORE_B64`（base64 的 keystore）与 `NOVA_KEY_PASSWORD`。未配置时会临时生成一次性密钥，可安装但无法覆盖升级。

## 💖 支持原作者

如果你喜欢这个分支，请支持原作者的工作~

- Ko-fi：<https://ko-fi.com/reovodev>
- 爱发电：<https://afdian.com/a/reovo>

应用内的赞助名单仍指向原版的赞助者，原因同上。

## 📄 许可证

沿用 RikkaHub 的 GNU Affero General Public License v3.0。原版版权归 RikkaHub 作者所有，本分支的改动同样以 AGPL-3.0 发布。「RikkaHub」名称与图标仍属于原项目。
