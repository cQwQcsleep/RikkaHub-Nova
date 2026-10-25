<div align="center">
  <img src="docs/icon.png" alt="App Icon" width="100" />
  <h1>RikkaHub Nova</h1>

A native Android LLM chat client, forked from [RikkaHub](https://github.com/rikkahub/rikkahub).

Original author: **Reovo / rikkahub** · Fork maintainer: **cQwQcsleep**
</div>

<div align="center">
  <img src="docs/img/poster.png" alt="RikkaHub" width="800" />
</div>

## 🧬 What this fork changes

Nova keeps upstream behavior and UI, and concentrates on prompt-cache efficiency plus streaming cost.

| Area | Change |
|---|---|
| Prompt cache | Every request records the shape of its prefix (system prompt, tool declarations, cumulative message chain) and compares it with the previous request of the same conversation. Vendor usage stays the source of truth for hit/miss tokens; the shape explains *why* it missed (`system` / `tools` / `body` / `trim`) and reports long idle gaps as a cold resume using a per-vendor TTL (24h DeepSeek, 5m DashScope/Anthropic). |
| Cache-stable prefix | Tool declarations are sorted by name before sending, so MCP servers that enumerate tools in a different order after a reconnect no longer invalidate the provider cache. |
| Streaming hot path | Request bodies are serialized once instead of twice, log strings are only built when the level is enabled, and chunks are merged into one assistant message without copying the whole message list per token. |
| Reasoning tags | The leading `<think>` block is found with a linear scan instead of a lazy regex that re-scanned the growing answer every token. |
| Repeated encoding | Encoded images and parsed documents are cached by file identity with bounded LRUs, so history images/PDFs are no longer decoded and re-encoded on every request. |
| UI cost | Animations are kept, but streaming snapshots are frame-coalesced (≤60/s) so size animations restart once per frame instead of once per token; the reasoning preview follows with a continuous eased scroll; the animated mesh background is sampled at 20 fps. |
| Data display | Token speed is computed from consistently measured fields (multi-step totals), cache hit rate is shown, and the usage card updates live during generation with estimated tokens/speed. |
| Compaction | Automatic context folding follows Reasonix's thresholds: fold at 85% of the window, keep the most recent ~10% verbatim, cap the checkpoint at 50%, and reuse the exact request prefix so the summary call itself hits the cache. |
| No Firebase | Analytics and Crashlytics are removed; self-built forks no longer need `google-services.json`. |
| About page | Adds fork identity, both authors, links to this repository and upstream, and a list of differences. |

Bugs in this fork belong to the fork repository; please do not report them upstream.

## 🚀 Download

- Releases: <https://github.com/cQwQcsleep/RikkaHub-Nova/releases>
- `app-github-universal-release.apk` works on every supported ABI; `arm64-v8a` / `x86_64` are smaller.
- APKs are signed with the fork's CI key, so later Nova releases install over earlier ones.
- `app-github-universal-debug.apk` is unminified and uses the `.debug` package id; it installs alongside release and is there to diagnose R8-only issues.

## ✨ Features (inherited from upstream)

- Material 3 Expressive design, dark mode, dynamic color
- Multiple providers: any OpenAI / Anthropic / Google-compatible API, custom headers and bodies
- Multimodal input (image, text document, PDF, DOCX), media generation
- MCP, skills, local workspace (proot Linux shell), Web access
- Markdown rendering with code highlighting, LaTeX, tables, Mermaid
- Search providers (Exa, Tavily, Zhipu, LinkUp, Brave, Perplexity, SearXNG, ...)
- Agent customization, file-based memory, AI translation, SillyTavern character cards

## 🛠 Build from source

### Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Matches the CI toolchain |
| Android SDK | compileSdk 37 (`minorApiLevel = 2`), build-tools matching AGP 9.4 | `ANDROID_HOME` or `local.properties` `sdk.dir` |
| Android NDK + CMake | required by `:terminal` and `:workspace` | Installed through the SDK manager |
| Node.js | 22 | Builds `web-ui` |
| pnpm | 11 | `web-ui/pnpm-lock.yaml` |
| Git | any recent | Must fetch the `material3` submodule |

```bash
git clone --recurse-submodules https://github.com/cQwQcsleep/RikkaHub-Nova.git
cd RikkaHub-Nova

# frontend for the embedded web server (:web:preBuild runs this again)
(cd web-ui && pnpm install --frozen-lockfile)

# build
./gradlew :app:assembleGithubDebug        # debug APK
./gradlew :app:assembleGithubRelease      # R8 release APK (needs signing, see below)
./gradlew :app:testGithubDebugUnitTest    # JVM unit tests
./gradlew :ai:testDebugUnitTest           # cache core unit tests
./gradlew spotlessApply                   # formatting (ratcheted against upstream)
```

Release signing is read from `local.properties` (path relative to the `app` module):

```properties
storeFile=nova-release.jks
storePassword=...
keyAlias=nova
keyPassword=...
```

Without those values the release variant is built unsigned. There is no `google-services.json` requirement because Firebase was removed.

## 📁 Project structure

| Module | Responsibility |
|---|---|
| `app` | UI (Compose), ViewModels, chat service, settings, Room database, DI, this fork's cache diagnostics |
| `ai` | Provider abstraction (OpenAI Chat Completions / Responses, Claude, Gemini), transformers, stream decoders, prompt-cache shape engine |
| `common` | Shared HTTP/JSON/Android helpers |
| `ui` | Reusable Compose components and the Markdown renderer |
| `material3` | Material color utilities (git submodule `material-color-utilities`) |
| `document` | PDF / DOCX / PPTX / EPUB text extraction |
| `highlight` | Code syntax highlighting |
| `search` | Search provider SDK |
| `speech` | TTS / ASR implementations |
| `web` | Embedded Ktor server that serves the built `web-ui` |
| `web-ui` | React admin/client frontend, built into `web/src/main/resources/static` |
| `workspace` | Sandboxed proot workspace and shell tools |
| `terminal` | Terminal emulator core and PTY transport |
| `mediagen` / `oauth` | Media generation and OAuth helpers |
| `app:baselineprofile` | Baseline profile generator used for startup/scroll profiles |
| `tools/` | `i18n` CLI and trace recorder (not part of the APK) |

Key entry points: `RikkaHubApp` (startup, Koin), `ChatService` (turn orchestration), `ChatRequestFactory` (request assembly), `GenerationHandler`/`AgentLoop` (step loop and streaming merge), `PromptCacheObserver` (cache diagnostics).

## 🧾 Resource references

Everything below is either committed in the repository or generated at build time. Keep the names stable: most are resolved at runtime by exact path.

| Resource | Location | Referenced by / how to replace |
|---|---|---|
| Launcher icon | `app/src/main/res/mipmap-*` PNGs + `mipmap-anydpi-v26/ic_launcher.xml` (adaptive background/foreground/monochrome layers) | `AndroidManifest.xml` `android:icon`. Keep the same resource names when replacing; all four density PNGs and the three adaptive layers are needed. |
| Provider / model icons | `app/src/main/assets/icons/*.svg` and a few `.png` | `AIIcon` loads `file:///android_asset/icons/$path`; the file name comes from `computeAIIconByName` and the model registry. Add a file with the expected name to support a new provider. |
| models.dev catalog | `app/src/main/assets/models_dev.json` | Offline snapshot only. `ModelsDevUpdater` fetches `https://models.dev/api.json` periodically, trims it, stores it under `filesDir/models_dev` and applies it; the asset is the first-launch fallback. |
| Emoji picker | `app/src/main/assets/emoji/categories.with.modifiers.min.json` | `EmojiUtils.loadEmoji` reads it from assets. |
| Markdown / Mermaid WebView | `app/src/main/assets/html/mark.html`, `html/mermaid.min.js` | `MarkdownWeb` renders legacy HTML content in a WebView with this template. Replacing `mermaid.min.js` only requires keeping the file name. |
| Built-in skills | `app/src/main/assets/builtin_skills` | Extracted to the app files directory on first launch by `SkillManager.ensureBuiltinSkillsExtracted`. |
| Fonts | `app/src/main/res/font/google_sans_flex.ttf`, `jetbrains_mono.ttf` | Referenced by the theme and code highlighting. |
| README images | `docs/img/*`, `docs/icon.png` | Documentation only; not packaged. |
| SQLite extension | `app/src/main/jniLibs/{arm64-v8a,x86_64}/libsimple.so` | Loaded at runtime by `SQLiteConfiguration` via `SQLiteCustomExtension`. Prebuilt ABI binaries; replace all shipped ABIs together. |
| proot binaries | `workspace/src/main/jniLibs/{arm64-v8a,x86_64}/libproot_*.so` | Prebuilt; used by the workspace launcher. |
| Native sources | `terminal/src/main/cpp/termux_pty.cpp`, `workspace/src/main/cpp/workspace.cpp` | Built by CMake during the Gradle build (needs NDK). |
| Material color utilities | `material3/material-color-utilities` | Git submodule pinned to a commit; `git submodule update --init --recursive` before building. |
| Web UI output | `web-ui/` → `web/src/main/resources/static` | `:web:preBuild` runs `pnpm run build` and copies the output; the destination is git-ignored. |
| Translations | `app/src/main/res/values*/strings.xml`, module-level `values*/strings.xml` | Added/validated with `tools/i18n` (Bun CLI). |

## 📈 Performance profile

A 388-sample thread profile recorded while streaming a long reasoning answer showed where the CPU actually goes:

| Thread | Mean | Max | Reading |
|---|---|---|---|
| `RenderThread` | 41.6% | 60.9% | compose draw + text rasterization of the growing message |
| `mali-cmar-backend` | 24.6% | 36.8% | GPU command submission / rasterization |
| `HeapTaskDaemon` | 11.8% | 75.0% | GC caused by per-token string and list churn |
| `ReferenceQueueDaemon` | 6.0% | 19.9% | reference processing for the same churn |
| `DefaultDispatcher-*` | ~4% each, bursts to ~50% | | background markdown parsing, cache hashing, JSON |

Rendering plus rasterization accounts for roughly two thirds of app CPU, GC for about a sixth, and background compute is a minority. That is why this fork does **not** rewrite the streaming stack in another language: the cost is not compute-bound, it is allocation and draw-bound. The changes target those instead — frame-coalesced snapshots with `StringBuilder` accumulation, bounded encode/parse caches, 20 fps background sampling, and removing redundant animation restarts. If a future profile shows a single compute hotspot dominating, a native module becomes worthwhile; the measurement, not the language, decides.

## 🔍 Prompt-cache internals

- `ai/.../cache/PromptCacheShape.kt` builds the shape: 8-byte hashes for the system text, the normalized tool signature, and a cumulative chain over provider-visible message content. Local-only fields (ids, timestamps, usage) never enter the hash.
- `ai/.../cache/PromptCacheLedger.kt` keeps the previous shape per conversation sequence, compares them, and reports `system` / `tools` / `body` / `trim` plus cold resumes using `PromptCachePolicy` TTLs.
- `app/.../data/ai/PromptCacheObserver.kt` is called from `UsageRecorder`: it captures the shape before a request and combines it with the vendor usage afterwards. Each request logs one line (`prefix`, `changed`, `reasons`, `carried`, `hit`, `miss`, `cold_resume`) under the `PromptCache` tag.
- The observer never rewrites a request. Anything that changes what is sent belongs to request assembly; the observer only measures.

## 🔁 CI and releases

| Workflow | Trigger | Result |
|---|---|---|
| `nova-ci.yml` | push / PR / manual | Runs `:ai` and `:app` unit tests, then builds the debug APK |
| `nova-release.yml` | manual (optional tag) or `nova-*` tags | Signs release APKs, also builds the debug APK, publishes them as a pre-release |

Release signing uses repository secrets `NOVA_KEYSTORE_B64` (base64 keystore) and `NOVA_KEY_PASSWORD`. Without them the workflow generates a throwaway key, which is fine for testing but breaks upgrade installs.

## 💖 Support the original author

If you like this fork, please support the original author's work:

- Ko-fi: <https://ko-fi.com/reovodev>
- 爱发电: <https://afdian.com/a/reovo>

The in-app sponsor list still points to upstream's sponsors and is kept for the same reason.

## 📄 License

GNU Affero General Public License v3.0, inherited from RikkaHub. Upstream copyright belongs to the RikkaHub authors; changes in this fork are released under the same license. The "RikkaHub" name and logo remain the original project's identity.
