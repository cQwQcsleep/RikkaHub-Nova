package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextCompactionPolicyTest {

    private fun message(chars: Int): UIMessage = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text("x".repeat(chars))),
    )

    @Test
    fun `compacts only near the window limit`() {
        assertFalse(ContextCompactionPolicy.shouldCompact(promptTokens = 50_000, contextLength = 100_000))
        assertTrue(ContextCompactionPolicy.shouldCompact(promptTokens = 85_000, contextLength = 100_000))
        assertTrue(ContextCompactionPolicy.shouldCompact(promptTokens = 90_000, contextLength = 100_000))
    }

    @Test
    fun `unknown window never triggers compaction`() {
        assertFalse(ContextCompactionPolicy.shouldCompact(promptTokens = 10_000, contextLength = 0))
        assertFalse(ContextCompactionPolicy.shouldCompact(promptTokens = 0, contextLength = 100_000))
    }

    @Test
    fun `recent tail stops at ten percent of the window`() {
        // 每条约 1008 token，窗口 100k → 预算 10k，保留 9 条后下一条会超预算
        val messages = List(30) { message(4_000) }
        assertEquals(9, ContextCompactionPolicy.recentTailCount(messages, contextLength = 100_000))
    }

    @Test
    fun `never keeps every message so something remains to compact`() {
        val messages = List(3) { message(40_000) }
        assertEquals(1, ContextCompactionPolicy.recentTailCount(messages, contextLength = 100_000))
    }

    @Test
    fun `single message has nothing to compact`() {
        assertEquals(0, ContextCompactionPolicy.recentTailCount(listOf(message(1_000)), 100_000))
    }

    @Test
    fun `tool output counts toward message size`() {
        val withTool = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = "1",
                    toolName = "shell",
                    input = "{\"cmd\":\"ls\"}",
                    output = listOf(UIMessagePart.Text("y".repeat(4_000))),
                )
            ),
        )
        assertEquals(
            (4_000 * ContextCompactionPolicy.TOKENS_PER_CHAR).toInt() + 8 + ("{\"cmd\":\"ls\"}".length * 0.25f).toInt(),
            ContextCompactionPolicy.estimateTokens(withTool),
        )
    }
}
