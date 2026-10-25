package me.rerere.ai.cache

import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * 厂商前缀缓存的保留时长策略。
 *
 * 数值来自 Reasonix 的 cache policy：DeepSeek 的 Context Caching on Disk 会保留数小时到数天，
 * 因此默认 24 小时；只有文档明确给出更短 TTL 的厂商才覆盖。数值偏小会误判“缓存已经过期”
 * 并在命中时把原因归错，偏大只会错过一次冷启动判定，所以这里刻意保守。
 */
object PromptCachePolicy {
    const val DEFAULT_TTL_MILLIS: Long = TimeUnit.HOURS.toMillis(24)
    const val SHORT_TTL_MILLIS: Long = TimeUnit.MINUTES.toMillis(5)

    fun vendorOf(baseUrl: String?): String {
        val host = hostOf(baseUrl) ?: return ""
        return when {
            host == "dashscope.aliyuncs.com" ||
                host.endsWith(".dashscope.aliyuncs.com") ||
                host.endsWith(".maas.aliyuncs.com") -> "dashscope"

            host == "api.deepseek.com" || host.endsWith(".deepseek.com") -> "deepseek"
            host == "api.anthropic.com" || host.endsWith(".anthropic.com") -> "anthropic"
            else -> ""
        }
    }

    /** 该地址对应厂商的缓存保留时长 */
    fun ttlMillis(baseUrl: String?): Long = when (vendorOf(baseUrl)) {
        // DashScope 的会话缓存与 Anthropic 的 ephemeral 缓存都是 5 分钟
        "dashscope", "anthropic" -> SHORT_TTL_MILLIS
        else -> DEFAULT_TTL_MILLIS
    }

    /** 只按主机名匹配，避免把用户自填的、包含厂商字样的路径误认成官方地址 */
    private fun hostOf(baseUrl: String?): String? {
        val raw = baseUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return runCatching { URI(if ("://" in raw) raw else "https://$raw").host }
            .getOrNull()
            ?.lowercase()
            ?.removePrefix("www.")
    }
}

/** 一次请求观察到的缓存结果，以及前缀是否发生变化的归因 */
data class PromptCacheObservation(
    /** 相对于上一条基准请求，前缀是否发生了变化 */
    val prefixChanged: Boolean,
    /** 变化原因：system / tools / body / trim */
    val reasons: List<String>,
    /** 两次请求共同携带的消息条数 */
    val carriedMessages: Int,
    /** 共同区间内前缀内容是否真的被改写 */
    val bodyChanged: Boolean,
    /** 厂商返回的命中 token */
    val hitTokens: Int,
    /** 厂商返回的未命中 token */
    val missTokens: Int,
    /** 本条请求距离同一会话的上一条请求超过厂商缓存 TTL，前缀大概率已经不在缓存里 */
    val coldResume: Boolean,
    val shape: PromptCacheShape,
)

/**
 * 按会话记录前缀形状，用来回答“为什么这次没有命中缓存”。
 *
 * 与 Reasonix 的 PrefixShape 观察方式相同：保存上一次请求的形状，在下一次请求时比较
 * system/tools 的哈希与逐条累加的消息链。厂商返回的 usage 是唯一可信的命中量，日志里
 * 同时给出两者，命中数与归因不一致时可以直接看出是本地改写还是厂商侧淘汰。
 */
class PromptCacheLedger(
    private val maxEntries: Int = 64,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private data class Entry(val shape: PromptCacheShape, val observedAt: Long)

    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxEntries
    }

    fun observe(
        key: String,
        shape: PromptCacheShape,
        hitTokens: Int = 0,
        missTokens: Int = 0,
        ttlMillis: Long = PromptCachePolicy.DEFAULT_TTL_MILLIS,
    ): PromptCacheObservation {
        val now = clock()
        val previous = synchronized(entries) {
            val last = entries[key]
            entries[key] = Entry(shape, now)
            last
        }
        if (previous == null) {
            return PromptCacheObservation(
                prefixChanged = false,
                reasons = emptyList(),
                carriedMessages = 0,
                bodyChanged = false,
                hitTokens = hitTokens,
                missTokens = missTokens,
                // 没有基准可比，仍按时间判断是否属于长时间后的冷启动
                coldResume = false,
                shape = shape,
            )
        }

        val reasons = mutableListOf<String>()
        if (previous.shape.systemHash != shape.systemHash) reasons.add(REASON_SYSTEM)
        if (previous.shape.toolsHash != shape.toolsHash) reasons.add(REASON_TOOLS)

        val carried = minOf(previous.shape.bodyChain.size, shape.bodyChain.size)
        val bodyChanged = carried > 0 &&
            previous.shape.bodyChain[carried - 1] != shape.bodyChain[carried - 1]
        if (bodyChanged) reasons.add(REASON_BODY)
        // 共同区间比上一次短，说明中间的消息被裁剪或压缩替换过，前缀从截断点开始失效
        if (carried < previous.shape.bodyChain.size) reasons.add(REASON_TRIM)

        return PromptCacheObservation(
            prefixChanged = reasons.isNotEmpty(),
            reasons = reasons,
            carriedMessages = carried,
            bodyChanged = bodyChanged,
            hitTokens = hitTokens,
            missTokens = missTokens,
            coldResume = now - previous.observedAt > ttlMillis,
            shape = shape,
        )
    }

    fun clear() = synchronized(entries) { entries.clear() }

    companion object {
        const val REASON_SYSTEM = "system"
        const val REASON_TOOLS = "tools"
        const val REASON_BODY = "body"
        const val REASON_TRIM = "trim"
    }
}
