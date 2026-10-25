package me.rerere.ai.cache

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCacheShapeTest {

    private fun tool(name: String, description: String = "tool $name"): Tool = Tool(
        name = name,
        description = description,
        execute = { emptyList() },
    )

    private fun messages(vararg texts: String, system: String = "system prompt") = listOf(
        UIMessage.system(system),
    ) + texts.mapIndexed { index, text ->
        if (index % 2 == 0) UIMessage.user(text) else UIMessage.assistant(text)
    }

    @Test
    fun `same request keeps the same prefix and full body chain`() {
        val first = PromptCacheShape.capture(messages("hi", "hello"), listOf(tool("a")))
        val second = PromptCacheShape.capture(messages("hi", "hello"), listOf(tool("a")))

        assertEquals(first, second)
        assertEquals(2, second.bodyChain.size)
    }

    @Test
    fun `appending a message preserves the cached prefix`() {
        val first = PromptCacheShape.capture(messages("hi"), listOf(tool("a")))
        val second = PromptCacheShape.capture(messages("hi", "hello"), listOf(tool("a")))

        assertEquals(first.prefixHash, second.prefixHash)
        assertEquals(first.bodyChain, second.bodyChain.take(first.bodyChain.size))
    }

    @Test
    fun `tool declaration order does not change the prefix`() {
        val tools = listOf(tool("b"), tool("a"), tool("c"))
        val reordered = listOf(tool("c"), tool("b"), tool("a"))

        val first = PromptCacheShape.capture(messages("hi"), tools)
        val second = PromptCacheShape.capture(messages("hi"), reordered)

        assertEquals(first.toolsHash, second.toolsHash)
        assertEquals(first.prefixHash, second.prefixHash)
    }

    @Test
    fun `tool schema change breaks the prefix`() {
        val first = PromptCacheShape.capture(messages("hi"), listOf(tool("a", "one")))
        val second = PromptCacheShape.capture(messages("hi"), listOf(tool("a", "two")))

        assertNotEquals(first.toolsHash, second.toolsHash)
        assertNotEquals(first.prefixHash, second.prefixHash)
    }

    @Test
    fun `system prompt change breaks the prefix`() {
        val first = PromptCacheShape.capture(messages("hi", system = "prompt v1"), emptyList())
        val second = PromptCacheShape.capture(messages("hi", system = "prompt v2"), emptyList())

        assertNotEquals(first.systemHash, second.systemHash)
        assertNotEquals(first.prefixHash, second.prefixHash)
    }

    @Test
    fun `local fields such as usage do not affect the shape`() {
        val base = messages("hi")
        val withUsage = base.map { message ->
            message.copy(
                id = message.id,
                usage = null,
                finishedAt = null,
            )
        }

        assertEquals(
            PromptCacheShape.capture(base, emptyList()),
            PromptCacheShape.capture(withUsage, emptyList()),
        )
    }

    @Test
    fun `tool output is part of the projection`() {
        val withoutOutput = UIMessage.assistant("").copy(
            parts = listOf(UIMessagePart.Tool(toolCallId = "1", toolName = "a", input = "{}")),
        )
        val withOutput = withoutOutput.copy(
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = "1",
                    toolName = "a",
                    input = "{}",
                    output = listOf(UIMessagePart.Text("done")),
                )
            ),
        )

        assertNotEquals(
            PromptCacheShape.capture(listOf(withoutOutput), emptyList()),
            PromptCacheShape.capture(listOf(withOutput), emptyList()),
        )
    }
}

class PromptCacheLedgerTest {

    private fun shape(texts: List<String> = listOf("a", "b"), system: String = "system") =
        PromptCacheShape.capture(
            listOf(UIMessage.system(system)) + texts.map { UIMessage.user(it) },
            emptyList(),
        )

    @Test
    fun `first observation has no baseline`() {
        val ledger = PromptCacheLedger()
        val result = ledger.observe("k", shape())
        assertFalse(result.prefixChanged)
        assertTrue(result.reasons.isEmpty())
        assertEquals(0, result.carriedMessages)
    }

    @Test
    fun `unchanged prefix reports carried messages`() {
        val ledger = PromptCacheLedger()
        ledger.observe("k", shape())
        val result = ledger.observe("k", shape(), hitTokens = 120, missTokens = 8)

        assertFalse(result.prefixChanged)
        assertEquals(2, result.carriedMessages)
        assertEquals(120, result.hitTokens)
        assertEquals(8, result.missTokens)
    }

    @Test
    fun `history rewrite is attributed to the body`() {
        val ledger = PromptCacheLedger()
        ledger.observe("k", shape())
        val result = ledger.observe("k", shape(listOf("a", "rewritten")))

        assertTrue(result.prefixChanged)
        assertEquals(listOf(PromptCacheLedger.REASON_BODY), result.reasons)
        // carried 是两次请求共同发送的消息条数，不是内容仍然相同的条数
        assertEquals(2, result.carriedMessages)
    }

    @Test
    fun `system change is attributed to the system prompt`() {
        val ledger = PromptCacheLedger()
        ledger.observe("k", shape())
        val result = ledger.observe("k", shape(system = "other"))

        assertTrue(result.prefixChanged)
        assertTrue(PromptCacheLedger.REASON_SYSTEM in result.reasons)
    }

    @Test
    fun `truncating the history is reported separately`() {
        val ledger = PromptCacheLedger()
        ledger.observe("k", shape(listOf("a", "b", "c")))
        val result = ledger.observe("k", shape(listOf("c")))

        assertTrue(PromptCacheLedger.REASON_TRIM in result.reasons)
    }

    @Test
    fun `entries are isolated per conversation`() {
        val ledger = PromptCacheLedger()
        ledger.observe("first", shape())
        val other = ledger.observe("second", shape())

        assertEquals(0, other.carriedMessages)
    }

    @Test
    fun `idle longer than the vendor ttl is a cold resume`() {
        var now = 0L
        val ledger = PromptCacheLedger(clock = { now })
        ledger.observe("k", shape(), ttlMillis = 1_000)

        now = 500
        assertFalse(ledger.observe("k", shape(), ttlMillis = 1_000).coldResume)

        now = 2_000
        assertTrue(ledger.observe("k", shape(), ttlMillis = 1_000).coldResume)
    }

    @Test
    fun `ledger keeps the entry count bounded`() {
        val ledger = PromptCacheLedger(maxEntries = 2)
        repeat(5) { ledger.observe("k$it", shape()) }

        assertEquals(0, ledger.observe("k0", shape()).carriedMessages)
    }
}

class PromptCachePolicyTest {

    @Test
    fun `deepseek keeps the long ttl`() {
        assertEquals("deepseek", PromptCachePolicy.vendorOf("https://api.deepseek.com/v1"))
        assertEquals(PromptCachePolicy.DEFAULT_TTL_MILLIS, PromptCachePolicy.ttlMillis("https://api.deepseek.com"))
    }

    @Test
    fun `short lived vendors use five minutes`() {
        assertEquals("anthropic", PromptCachePolicy.vendorOf("https://api.anthropic.com/v1"))
        assertEquals(
            PromptCachePolicy.SHORT_TTL_MILLIS,
            PromptCachePolicy.ttlMillis("https://api.anthropic.com"),
        )
        assertEquals(
            PromptCachePolicy.SHORT_TTL_MILLIS,
            PromptCachePolicy.ttlMillis("https://dashscope.aliyuncs.com/compatible-mode/v1"),
        )
    }

    @Test
    fun `lookalike hosts are not treated as official`() {
        assertEquals("", PromptCachePolicy.vendorOf("https://api.deepseek.com.evil.example/v1"))
        assertEquals(PromptCachePolicy.DEFAULT_TTL_MILLIS, PromptCachePolicy.ttlMillis(null))
    }
}
