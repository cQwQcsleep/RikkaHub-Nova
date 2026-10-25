package me.rerere.rikkahub.data.ai

import android.util.Log
import me.rerere.ai.cache.PromptCacheLedger
import me.rerere.ai.cache.PromptCacheObservation
import me.rerere.ai.cache.PromptCachePolicy
import me.rerere.ai.cache.PromptCacheShape
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage

private const val TAG = "PromptCache"

/**
 * 提示词缓存诊断。
 *
 * 在请求发出前记录前缀形状，收到厂商 usage 后比较并给出命中量与失效原因。厂商返回的
 * `prompt_cache_hit_tokens` 是唯一可信的命中数据，前缀形状只负责回答“为什么”：
 * 系统提示、工具声明或历史正文被改写，还是只是隔了太久导致缓存过期。
 *
 * 只做观察，不改写请求：任何会改变发送内容的判断都必须留在请求组装层，否则诊断本身就会
 * 破坏它要测量的前缀。
 */
class PromptCacheObserver {
    private val ledger = PromptCacheLedger()

    /** 一次请求发出前捕获的前缀形状；请求结束后交给 [complete] */
    class Pending internal constructor(
        internal val key: String,
        internal val ttlMillis: Long,
        internal val shape: PromptCacheShape,
    )

    @Volatile
    var lastObservation: PromptCacheObservation? = null
        private set

    fun begin(
        providerSetting: ProviderSetting,
        params: TextGenerationParams,
        messages: List<UIMessage>,
        tools: List<Tool>,
        tag: UsageTag,
    ): Pending {
        val endpoint = providerSetting as? ProviderSetting.ApiKey
        return Pending(
            key = cacheKey(tag, params),
            ttlMillis = PromptCachePolicy.ttlMillis(endpoint?.baseUrl),
            shape = PromptCacheShape.capture(messages, tools),
        )
    }

    /**
     * 记录一次请求的缓存结果。
     *
     * DeepSeek 的 prompt_tokens 已经包含命中的部分，命中量由 cachedTokens 单独给出，
     * 因此未命中量要减掉它；其它厂商没有缓存字段时两个数都不会为正，日志里省略。
     */
    fun complete(pending: Pending, usage: TokenUsage) {
        val hit = usage.cachedTokens.coerceAtLeast(0)
        val miss = (usage.promptTokens - hit).coerceAtLeast(0)
        val observation = ledger.observe(
            key = pending.key,
            shape = pending.shape,
            hitTokens = hit,
            missTokens = miss,
            ttlMillis = pending.ttlMillis,
        )
        lastObservation = observation

        if (!Log.isLoggable(TAG, Log.INFO)) return
        val reasons = observation.reasons.ifEmpty { listOf("-") }.joinToString(",")
        Log.i(
            TAG,
            buildString {
                append("prefix=").append(observation.shape.prefixHash)
                append(" changed=").append(observation.prefixChanged)
                append(" reasons=").append(reasons)
                append(" carried=").append(observation.carriedMessages)
                append(" hit=").append(observation.hitTokens)
                append(" miss=").append(observation.missTokens)
                if (observation.coldResume) append(" cold_resume=true")
            },
        )
    }

    /** 缓存诊断只在同一请求序列内比较；压缩请求会复用对话前缀，因此与对话共用一条序列 */
    private fun cacheKey(tag: UsageTag, params: TextGenerationParams): String {
        val sequence = when (tag.source) {
            UsageSource.CHAT, UsageSource.COMPRESSION -> "chat"
            else -> tag.source.name
        }
        return "$sequence:${tag.conversationId}:${params.model.id}"
    }
}
