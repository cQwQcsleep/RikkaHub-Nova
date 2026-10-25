package me.rerere.ai.cache

import kotlinx.serialization.encodeToString
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.json
import java.security.MessageDigest

/**
 * 一次请求的前缀形状快照。
 *
 * 厂商的提示词缓存按请求前缀的字节复用，因此“两次请求的前缀是否仍然相同”可以直接由这份快照
 * 回答，而不必依赖厂商返回的命中数反推原因。字段含义与 Reasonix 的 PrefixShape 一致：
 *
 * - [systemHash]：系统消息文本的哈希，对应前缀里的 system 段
 * - [toolsHash]：工具定义的哈希，工具按名称排序后参与哈希，因此声明顺序变化不算改动
 * - [prefixHash]：system + tools 的整体哈希，是消息链条的起点
 * - [bodyChain]：逐条累加的哈希链，第 i 项覆盖从前缀到第 i 条消息的全部内容
 *
 * 失效定位依赖 [bodyChain]：两次请求在较短长度处比较，只有累加值相同的位置才真正复用。
 */
data class PromptCacheShape(
    val systemHash: String,
    val toolsHash: String,
    val prefixHash: String,
    val bodyChain: List<String>,
) {
    companion object {
        val EMPTY = PromptCacheShape(
            systemHash = "",
            toolsHash = "",
            prefixHash = "",
            bodyChain = emptyList(),
        )

        /** 捕获请求形状；与具体 Provider 无关，只覆盖真正发给模型的内容 */
        fun capture(messages: List<UIMessage>, tools: List<Tool>): PromptCacheShape {
            val systemText = messages
                .filter { it.role == MessageRole.SYSTEM }
                .joinToString(separator = "\n") { it.providerText() }
            val toolsSignature = toolsSignature(tools)

            val systemHash = shortHash(systemText)
            val toolsHash = shortHash(toolsSignature)
            val prefixHash = shortHash("$systemText\u0000$toolsSignature")

            var accumulator = prefixHash
            val chain = ArrayList<String>(messages.size)
            messages.forEach { message ->
                if (message.role == MessageRole.SYSTEM) return@forEach
                accumulator = shortHash("$accumulator\u0000${message.role.name}\u0000${message.providerText()}")
                chain.add(accumulator)
            }

            return PromptCacheShape(
                systemHash = systemHash,
                toolsHash = toolsHash,
                prefixHash = prefixHash,
                bodyChain = chain,
            )
        }

        /**
         * 工具定义的稳定签名。
         *
         * 按名称、描述排序，使 MCP 这类由 Map/Cache 枚举得到的工具列表在重新连接后仍然得到相同的前缀；
         * 顺序变化本身不会让厂商的缓存失效，前缀内容变化才会。
         */
        fun toolsSignature(tools: List<Tool>): String = tools
            .sortedWith(compareBy({ it.name }, { it.description }))
            .joinToString(separator = "\n") { tool ->
                buildString {
                    append(tool.name)
                    append('\u0000')
                    append(tool.description)
                    append('\u0000')
                    append(tool.parameters()?.let { encodeSchema(it) }.orEmpty())
                }
            }

        private fun encodeSchema(schema: me.rerere.ai.core.InputSchema): String =
            json.encodeToString(me.rerere.ai.core.InputSchema.serializer(), schema)
    }
}

/** 消息中真正会进入请求正文的部分；createdAt、usage 这类本地字段不参与 */
internal fun UIMessage.providerText(): String = parts.joinToString(separator = "\u0001") { part ->
    when (part) {
        is UIMessagePart.Text -> "t:${part.text}"
        is UIMessagePart.Reasoning -> "r:${part.reasoning}"
        is UIMessagePart.Tool -> buildString {
            append("c:${part.toolCallId}:${part.toolName}:${part.input}:")
            append(part.output.filterIsInstance<UIMessagePart.Text>().joinToString("\u0002") { it.text })
        }
        // 图片/视频/文档的 url 可能是整段 base64，直接拼进来会放大哈希开销
        is UIMessagePart.Image -> "i:${shortHash(part.url)}"
        is UIMessagePart.Video -> "v:${shortHash(part.url)}"
        is UIMessagePart.Audio -> "a:${shortHash(part.url)}"
        is UIMessagePart.Document -> "d:${part.fileName}:${shortHash(part.url)}"
        is UIMessagePart.ServerTool -> "s:${part.toolCallId}:${part.toolName}"
        else -> part::class.simpleName.orEmpty()
    }
}

/** 8 字节摘要，足够区分前缀，又不会让诊断日志难以阅读 */
internal fun shortHash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    val chars = CharArray(16)
    for (i in 0 until 8) {
        val b = digest[i].toInt() and 0xFF
        chars[i * 2] = HEX[b ushr 4]
        chars[i * 2 + 1] = HEX[b and 0x0F]
    }
    return String(chars)
}

private val HEX = "0123456789abcdef".toCharArray()
