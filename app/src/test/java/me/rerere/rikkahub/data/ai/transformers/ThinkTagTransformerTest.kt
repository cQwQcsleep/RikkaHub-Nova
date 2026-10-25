package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class ThinkTagTransformerTest {
    private val now = Instant.parse("2026-08-20T00:00:00Z")

    @Test
    fun `think tag at start should be converted to reasoning`() {
        val result = transform("<think>reason</think>answer")

        assertEquals("reason", result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().reasoning)
        assertEquals("answer", result.parts.filterIsInstance<UIMessagePart.Text>().single().text)
        assertEquals(now, result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().finishedAt)
    }

    @Test
    fun `think tag in visible answer should be preserved`() {
        val text = "The literal `<think>` tag should stay visible."

        assertEquals(listOf(UIMessagePart.Text(text)), transform(text).parts)
    }

    @Test
    fun `native reasoning should prevent fallback tag parsing`() {
        val nativeReasoning = UIMessagePart.Reasoning("native reasoning")
        val text = UIMessagePart.Text("answer mentions <think> without a closing tag")
        val message = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(nativeReasoning, text),
        )

        val result = listOf(message).transformThinkTags(now, generationFinished = true).single()

        assertEquals(listOf(nativeReasoning, text), result.parts)
    }

    @Test
    fun `later think tags should remain visible`() {
        val result = transform("<think>reason</think>answer <think>literal")

        assertEquals("reason", result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().reasoning)
        assertEquals("answer <think>literal", result.parts.filterIsInstance<UIMessagePart.Text>().single().text)
    }

    @Test
    fun `think tag in a later text part should be preserved`() {
        val parts = listOf(
            UIMessagePart.Text("visible answer"),
            UIMessagePart.Text("<think>literal"),
        )
        val message = UIMessage(role = MessageRole.ASSISTANT, parts = parts)

        val result = listOf(message).transformThinkTags(now, generationFinished = true).single()

        assertEquals(parts, result.parts)
    }

    @Test
    fun `unclosed prefix tag should remain unfinished while streaming`() {
        val message = UIMessage.assistant("<think>reason in progress")

        val result = listOf(message).transformThinkTags(now, generationFinished = false).single()

        assertEquals("reason in progress", result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().reasoning)
        assertNull(result.parts.filterIsInstance<UIMessagePart.Reasoning>().single().finishedAt)
    }

    @Test
    fun `generation finish should close reasoning created by visual transform`() {
        val message = UIMessage.assistant("<think>reason in progress")
        val visualResult = listOf(message).transformThinkTags(now, generationFinished = false)

        val finishedResult = visualResult.transformThinkTags(now, generationFinished = true).single()

        assertEquals(now, finishedResult.parts.filterIsInstance<UIMessagePart.Reasoning>().single().finishedAt)
    }

    // 流式输出改为字符串扫描后，匹配结果必须与原来的正则完全一致
    @Test
    fun `string scan agrees with the legacy regex`() {
        val legacy = Regex("\\A\\s*<think>([\\s\\S]*?)(</think>|$)")
        val cases = listOf(
            "",
            "plain answer",
            "<think>reason</think>answer",
            "   \n\t<think>reason</think>\n\nanswer",
            "<think>reason only",
            "<think>",
            "<think></think>",
            "<think></think><think>second",
            "  <thinker>not a tag",
            "<think>a</think>b</think>c",
            "answer <think>literal",
            "　<think>ideographic space is not regex whitespace</think>",
        )

        cases.forEach { text ->
            val expected = legacy.find(text)
            val actual = findLeadingThinkTag(text)
            if (expected == null) {
                assertNull("expected no match for <$text>", actual)
            } else {
                requireNotNull(actual) { "expected a match for <$text>" }
                assertEquals("content of <$text>", expected.groupValues[1], actual.content)
                assertEquals("closing tag of <$text>", expected.groupValues[2] == "</think>", actual.hasClosingTag)
                assertEquals("end of <$text>", expected.range.last + 1, actual.end)
            }
        }
    }

    private fun transform(text: String): UIMessage {
        val message = UIMessage.assistant(text)
        return listOf(message).transformThinkTags(now, generationFinished = true).single()
    }
}
