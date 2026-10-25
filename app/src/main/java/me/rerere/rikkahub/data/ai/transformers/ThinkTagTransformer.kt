package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock
import kotlin.time.Instant

// 部分供应商不会返回reasoning parts, 所以需要这个transformer
object ThinkTagTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformThinkTags(
            now = Clock.System.now(),
            generationFinished = false,
        )
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformThinkTags(
            now = Clock.System.now(),
            generationFinished = true,
        )
    }
}

internal fun List<UIMessage>.transformThinkTags(
    now: Instant,
    generationFinished: Boolean,
): List<UIMessage> = map { message ->
    if (message.role != MessageRole.ASSISTANT) {
        return@map message
    }
    if (message.hasPart<UIMessagePart.Reasoning>()) {
        return@map if (generationFinished) {
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Reasoning && part.finishedAt == null) {
                        part.copy(finishedAt = now)
                    } else {
                        part
                    }
                }
            )
        } else {
            message
        }
    }

    val textPartIndex = message.parts.indexOfFirst { part ->
        part is UIMessagePart.Text && part.text.isNotBlank()
    }
    val textPart = message.parts.getOrNull(textPartIndex) as? UIMessagePart.Text
        ?: return@map message
    // 流式输出时每个 chunk 都会走到这里；用字符串扫描替代正则，避免惰性匹配每帧把全文回扫一遍
    val match = findLeadingThinkTag(textPart.text) ?: return@map message
    val reasoning = UIMessagePart.Reasoning(
        reasoning = match.content.trim(),
        createdAt = message.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
        finishedAt = if (generationFinished || match.hasClosingTag) now else null,
    )
    val strippedText = textPart.copy(text = textPart.text.substring(match.end))

    message.copy(
        parts = buildList {
            addAll(message.parts.subList(0, textPartIndex))
            add(reasoning)
            add(strippedText)
            addAll(message.parts.subList(textPartIndex + 1, message.parts.size))
        }
    )
}

internal class LeadingThinkTag(
    /** 开标签与闭标签之间的内容 */
    val content: String,
    val hasClosingTag: Boolean,
    /** 从文本起点到标签结束的下标，内容之外的这部分会被移除 */
    val end: Int,
)

/**
 * 匹配开头的 `<think>` 块，等价于原来的 `\A\s*<think>([\s\S]*?)(</think>|$)`。
 *
 * 正则写法在流式输出里是 O(n²)：每个 chunk 都要用惰性匹配把不断变长的正文重新扫一遍。
 * 这里改成一次线性扫描，且没有开标签时只检查前导空白。
 */
internal fun findLeadingThinkTag(text: String): LeadingThinkTag? {
    var start = 0
    while (start < text.length && text[start].isRegexWhitespace()) start++
    if (!text.startsWith("<think>", start)) return null

    val contentStart = start + "<think>".length
    val closing = text.indexOf("</think>", contentStart)
    return if (closing >= 0) {
        LeadingThinkTag(
            content = text.substring(contentStart, closing),
            hasClosingTag = true,
            end = closing + "</think>".length,
        )
    } else {
        LeadingThinkTag(
            content = text.substring(contentStart),
            hasClosingTag = false,
            end = text.length,
        )
    }
}

// 与 Java 正则默认的 \s 一致，避免把 Unicode 空白也算进去而改变匹配结果
private fun Char.isRegexWhitespace(): Boolean =
    this == ' ' || this == '\t' || this == '\n' || this == '\u000B' || this == '\u000C' || this == '\r'

