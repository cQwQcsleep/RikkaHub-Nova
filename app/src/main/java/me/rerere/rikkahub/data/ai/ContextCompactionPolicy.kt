package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 上下文压缩策略，阈值与保留区比例沿用 Reasonix 的 compaction 常量：
 *
 * - 上一次请求的输入达到窗口的 85% 时触发折叠
 * - 折叠后把最近约 10% 窗口的消息原样保留（Reasonix 的 recent tail budget）
 * - 摘要请求复用与上一轮完全相同的前缀，因此这些 token 仍然命中提示词缓存
 *
 * 这里只做纯计算，便于单元测试；不触碰会话状态。
 */
object ContextCompactionPolicy {
    /** 触发折叠的窗口占用比例 */
    const val TRIGGER_RATIO = 0.85f

    /** 折叠后允许保留的窗口占用上限，仅用于判断是否值得折叠 */
    const val CHECKPOINT_CEILING_RATIO = 0.50f

    /** 原样保留的最近消息预算，占窗口的比例 */
    const val RECENT_TAIL_BUDGET_RATIO = 0.10f

    /** 估算 token 用的字符换算；Reasonix 在拿到真实 usage 前也用 0.25（约 4 字符 1 token） */
    const val TOKENS_PER_CHAR = 0.25f

    /** 每条消息的固定开销，覆盖角色、分隔符等协议字段 */
    private const val MESSAGE_OVERHEAD_TOKENS = 8

    fun shouldCompact(promptTokens: Int, contextLength: Int): Boolean {
        if (contextLength <= 0 || promptTokens <= 0) return false
        return promptTokens >= (contextLength * TRIGGER_RATIO).toInt()
    }

    /** 按预算估算一条消息占用的 token */
    fun estimateTokens(message: UIMessage): Int {
        val chars = message.parts.sumOf { part ->
            when (part) {
                is UIMessagePart.Text -> part.text.length
                is UIMessagePart.Reasoning -> part.reasoning.length
                is UIMessagePart.Tool -> part.input.length +
                    part.output.filterIsInstance<UIMessagePart.Text>().sumOf { it.text.length }

                else -> 0
            }
        }
        return (chars * TOKENS_PER_CHAR).toInt() + MESSAGE_OVERHEAD_TOKENS
    }

    /**
     * 最近应该原样保留的消息条数：从末尾往前累计到 10% 窗口预算为止。
     * 至少保留一条可压缩的历史，且不会把全部消息都留在检查点之后。
     */
    fun recentTailCount(
        messages: List<UIMessage>,
        contextLength: Int,
    ): Int {
        if (contextLength <= 0 || messages.size <= 1) return 0
        val budget = (contextLength * RECENT_TAIL_BUDGET_RATIO).toInt().coerceAtLeast(1)
        var used = 0
        var count = 0
        for (message in messages.asReversed()) {
            val tokens = estimateTokens(message)
            if (count > 0 && used + tokens > budget) break
            used += tokens
            count++
            if (count >= messages.size - 1) break
        }
        return count.coerceAtMost(messages.size - 1)
    }
}
