package me.rerere.ai.ui

import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMessageAccumulatorTest {

    private val model = Model(modelId = "test-model")
    private val base = UIMessage(modelId = model.id, role = me.rerere.ai.core.MessageRole.ASSISTANT, parts = emptyList())
    private val chunks = listOf(
        StreamChunk.TextStart("t1"),
        StreamChunk.TextDelta("t1", "Hello"),
        StreamChunk.TextDelta("t1", ", world"),
        StreamChunk.ReasoningStart("r1"),
        StreamChunk.ReasoningDelta("r1", "think"),
        StreamChunk.ReasoningDelta("r1", "ing"),
        StreamChunk.ToolCallStart("c1", "search"),
        StreamChunk.ToolCallDelta("c1", inputDelta = "{\"q\":"),
        StreamChunk.ToolCallDelta("c1", inputDelta = "\"x\"}"),
        StreamChunk.ToolCallOutput("c1", "result"),
        StreamChunk.ReasoningEnd("r1"),
        StreamChunk.TextEnd("t1"),
        StreamChunk.Usage(TokenUsage(promptTokens = 10, completionTokens = 5, cachedTokens = 3)),
        StreamChunk.Finish(),
    )

    @Test
    fun `matches StreamChunkHandler for a mixed stream`() {
        val handler = StreamChunkHandler(model)
        val expected = chunks.fold(base) { message, chunk -> handler.apply(message, chunk) }

        val accumulator = StreamingMessageAccumulator(model)
        chunks.forEach(accumulator::consume)
        val actual = accumulator.build(base)

        assertEquals(expected.parts.size, actual.parts.size)
        expected.parts.zip(actual.parts).forEach { (a, b) ->
            when (a) {
                is UIMessagePart.Text -> assertEquals(a.text, (b as UIMessagePart.Text).text)
                is UIMessagePart.Reasoning -> assertEquals(a.reasoning, (b as UIMessagePart.Reasoning).reasoning)
                is UIMessagePart.Tool -> {
                    b as UIMessagePart.Tool
                    assertEquals(a.toolName, b.toolName)
                    assertEquals(a.input, b.input)
                    assertEquals(a.output, b.output)
                }
                else -> assertEquals(a::class, b::class)
            }
        }
        assertEquals(10, actual.usage?.promptTokens)
        assertEquals(3, actual.usage?.cachedTokens)
    }

    @Test
    fun `deltas without a start still create the part`() {
        val accumulator = StreamingMessageAccumulator(model)
        accumulator.consume(StreamChunk.TextDelta("t1", "late"))

        val message = accumulator.build(base)
        assertEquals("late", (message.parts.single() as UIMessagePart.Text).text)
    }

    @Test
    fun `build is repeatable and does not mutate accumulated state`() {
        val accumulator = StreamingMessageAccumulator(model)
        accumulator.consume(StreamChunk.TextDelta("t1", "abc"))
        val first = accumulator.build(base)
        accumulator.consume(StreamChunk.TextDelta("t1", "def"))
        val second = accumulator.build(base)

        assertEquals("abc", (first.parts.single() as UIMessagePart.Text).text)
        assertEquals("abcdef", (second.parts.single() as UIMessagePart.Text).text)
    }

    @Test
    fun `finish closes open reasoning and records completion`() {
        val accumulator = StreamingMessageAccumulator(model)
        accumulator.consume(StreamChunk.ReasoningDelta("r1", "still thinking"))
        assertNull(accumulator.build(base).finishedAt)

        accumulator.consume(StreamChunk.Finish())
        val finished = accumulator.build(base)
        assertTrue(finished.finishedAt != null)
        assertTrue((finished.parts.single() as UIMessagePart.Reasoning).finishedAt != null)
    }

    @Test
    fun `annotations are deduplicated`() {
        val annotation = UIMessageAnnotation.UrlCitation("example", "https://example.com")
        val accumulator = StreamingMessageAccumulator(model)
        accumulator.consume(StreamChunk.Annotations(listOf(annotation)))
        accumulator.consume(StreamChunk.Annotations(listOf(annotation)))

        assertEquals(listOf(annotation), accumulator.build(base).annotations)
    }
}
