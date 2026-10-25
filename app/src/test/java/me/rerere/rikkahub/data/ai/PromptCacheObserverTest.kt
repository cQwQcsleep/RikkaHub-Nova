package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.TokenUsage
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class PromptCacheObserverTest {

    private val params = TextGenerationParams(model = Model(modelId = "deepseek-chat"))
    private val setting = ProviderSetting.OpenAI(baseUrl = "https://api.deepseek.com")
    private val tag = UsageTag(UsageSource.CHAT, conversationId = Uuid.random())
    private val messages = listOf(UIMessage.system("system"), UIMessage.user("hello"))

    private fun observer() = PromptCacheObserver()

    private fun text(value: String): List<UIMessage> = messages + UIMessage.user(value)

    @Test
    fun `records hit and miss tokens from vendor usage`() {
        val observer = observer()
        val pending = observer.begin(setting, params, messages, emptyList(), tag)
        observer.complete(pending, TokenUsage(promptTokens = 100, cachedTokens = 80))

        val observation = requireNotNull(observer.lastObservation)
        assertEquals(80, observation.hitTokens)
        assertEquals(20, observation.missTokens)
    }

    @Test
    fun `appending a message keeps the prefix carried`() {
        val observer = observer()
        observer.complete(
            observer.begin(setting, params, messages, emptyList(), tag),
            TokenUsage(promptTokens = 50, cachedTokens = 0),
        )
        observer.complete(
            observer.begin(setting, params, text("next"), emptyList(), tag),
            TokenUsage(promptTokens = 70, cachedTokens = 50),
        )

        val observation = requireNotNull(observer.lastObservation)
        assertFalse(observation.prefixChanged)
        assertEquals(2, observation.carriedMessages)
    }

    @Test
    fun `changed system prompt is reported`() {
        val observer = observer()
        observer.complete(
            observer.begin(setting, params, messages, emptyList(), tag),
            TokenUsage(promptTokens = 50),
        )
        observer.complete(
            observer.begin(
                setting,
                params,
                listOf(UIMessage.system("other system"), UIMessage.user("hello")),
                emptyList(),
                tag,
            ),
            TokenUsage(promptTokens = 50),
        )

        val observation = requireNotNull(observer.lastObservation)
        assertTrue(observation.prefixChanged)
        assertTrue("system" in observation.reasons)
    }

    @Test
    fun `different conversations do not share a baseline`() {
        val observer = observer()
        observer.complete(
            observer.begin(setting, params, messages, emptyList(), tag),
            TokenUsage(promptTokens = 50),
        )
        observer.complete(
            observer.begin(
                setting,
                params,
                text("next"),
                emptyList(),
                UsageTag(UsageSource.CHAT, conversationId = Uuid.random()),
            ),
            TokenUsage(promptTokens = 50),
        )

        assertEquals(0, requireNotNull(observer.lastObservation).carriedMessages)
    }
}
