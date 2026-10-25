package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.hugeicons.stroke.Repeat
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.toFixed
import java.text.NumberFormat
import java.time.Duration

/**
 * 显示消息的技术统计信息（如 token 使用量），点击查看详情
 *
 * 多轮工具调用时 usage 是各步累计值，行内只显示最后一次请求的上下文占用，累计值放在详情里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMessageNerdLine(
    message: UIMessage,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
) {
    val settings = LocalSettings.current
    val usage = message.usage
    if (!settings.displaySetting.showTokenUsage) return
    // 生成过程中 usage 还没回来，但小卡片要能实时刷新，先用流式内容估算
    val live = usage == null && message.finishedAt == null
    if (usage == null && !live) return

    val stats = remember(message, live) { message.nerdStats(usage, live) }
    // 消息里的模型快照不带上下文窗口，只能从设置里按 ID 找；模型被删了就不显示窗口大小
    val contextLength = remember(settings.providers, message.modelId) {
        settings.findModelById(message.modelId)?.contextLength?.takeIf { it > 0 }
    }
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            RichTooltip(
                title = { Text(stringResource(R.string.chat_message_usage_title)) },
            ) {
                UsageDetails(stats, contextLength)
            }
        },
        state = tooltipState,
        enableUserInput = false,
        modifier = modifier,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = color)) {
            CompositionLocalProvider(LocalContentColor provides color) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { scope.launch { tooltipState.show() } }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    if (stats.live) {
                        StatsItem(
                            icon = { StatsIcon(HugeIcons.Layers01, stringResource(R.string.chat_message_usage_output)) },
                            content = {
                                Text(
                                    text = stringResource(
                                        R.string.chat_message_usage_live_tokens,
                                        stats.outputTokens.formatNumber(),
                                    )
                                )
                            }
                        )
                        if (stats.tps != null) {
                            StatsItem(
                                icon = { StatsIcon(HugeIcons.Zap, stringResource(R.string.chat_message_usage_speed)) },
                                content = { Text(text = stats.tps.label(estimated = true)) }
                            )
                        }
                    } else {
                        if (stats.steps > 1) {
                            StatsItem(
                                icon = { StatsIcon(HugeIcons.Repeat, stringResource(R.string.chat_message_usage_step_count)) },
                                content = { Text(text = stringResource(R.string.chat_message_usage_steps, stats.steps)) }
                            )
                        }
                        if (stats.tps != null) {
                            StatsItem(
                                icon = { StatsIcon(HugeIcons.Zap, stringResource(R.string.chat_message_usage_speed)) },
                                content = { Text(text = stats.tps.label(stats.speedEstimated)) }
                            )
                        }
                        StatsItem(
                            icon = { StatsIcon(HugeIcons.Layers01, stringResource(R.string.chat_message_usage_context)) },
                            content = {
                                Text(text = stringResource(R.string.chat_message_usage_context_short, stats.contextTokens.formatNumber()))
                                val cached = stats.lastUsage.cachedTokens
                                if (cached > 0) {
                                    Text(text = stringResource(R.string.chat_message_usage_cached_short, cached.formatNumber()))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageDetails(stats: NerdStats, contextLength: Int?) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        if (stats.live) {
            UsageDetailRow(
                stringResource(R.string.chat_message_usage_output),
                "~${stats.outputTokens.exact()}",
            )
            stats.durationMillis?.let {
                UsageDetailRow(stringResource(R.string.chat_message_usage_duration), it.asDuration())
            }
            stats.tps?.let {
                UsageDetailRow(stringResource(R.string.chat_message_usage_speed), it.label(estimated = true))
            }
            return@Column
        }
        val total = stats.total
        ContextUsageBar(
            lastUsage = stats.lastUsage,
            contextLength = contextLength,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (stats.steps > 1) {
            UsageDetailRow(stringResource(R.string.chat_message_usage_step_count), stats.steps.toString())
            UsageDetailRow(stringResource(R.string.chat_message_usage_total_input), total.promptTokens.exact())
            if (total.cachedTokens > 0) UsageDetailRow(stringResource(R.string.chat_message_usage_total_cached), total.cachedTokens.exact())
            UsageDetailRow(stringResource(R.string.chat_message_usage_total_output), total.completionTokens.exact())
        }
        stats.cacheHitRate?.let { rate ->
            UsageDetailRow(stringResource(R.string.chat_message_usage_cache_rate), "${(rate * 100).toFixed(0)}%")
        }
        if (stats.tps != null) {
            UsageDetailRow(stringResource(R.string.chat_message_usage_speed), stats.tps.label(stats.speedEstimated))
        }
        if (stats.durationMillis != null) {
            UsageDetailRow(stringResource(R.string.chat_message_usage_duration), stats.durationMillis.asDuration())
        }
    }
}

private data class UsageSegment(val label: String, val tokens: Int, val color: Color)

/**
 * 最后一次请求的上下文占用：缓存命中、未命中的输入、输出依次排开，剩下的是窗口里的空余
 */
@Composable
private fun ContextUsageBar(
    lastUsage: TokenUsage,
    contextLength: Int?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val segments = listOf(
        UsageSegment(stringResource(R.string.chat_message_usage_cached), lastUsage.cachedTokens, colorScheme.primary),
        UsageSegment(
            stringResource(R.string.chat_message_usage_input),
            (lastUsage.promptTokens - lastUsage.cachedTokens).coerceAtLeast(0),
            colorScheme.primary.copy(alpha = 0.45f),
        ),
        UsageSegment(stringResource(R.string.chat_message_usage_output), lastUsage.completionTokens, colorScheme.tertiary),
    ).filter { it.tokens > 0 }
    val contextTokens = lastUsage.promptTokens + lastUsage.completionTokens
    // 窗口未知或已经超出时，占用本身铺满整条
    val capacity = maxOf(contextLength ?: 0, contextTokens, 1)
    val hasFreeSpace = capacity > contextTokens
    val trackColor = colorScheme.surfaceContainerHighest

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.chat_message_usage_context),
                color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            if (contextLength != null) {
                val fraction = contextTokens.toFloat() / contextLength
                Text(
                    text = "${contextTokens.formatNumber()} / ${contextLength.formatNumber()} · ${(fraction * 100).toFixed(0)}%",
                    color = if (fraction >= 0.9f) colorScheme.error else Color.Unspecified,
                )
            } else {
                Text(text = contextTokens.exact())
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val gap = 2.dp.toPx()
            val radius = CornerRadius(size.height / 2)
            // 每段之间留出间隔；还有空余时最后一段和空余之间也要留
            val gaps = if (hasFreeSpace) segments.size else segments.size - 1
            val available = size.width - gap * gaps
            var x = 0f
            segments.forEach { segment ->
                if (x >= size.width) return@forEach
                // 太小的段至少画成一个圆点，不然看不出来
                val width = (segment.tokens.toFloat() / capacity * available).coerceAtLeast(size.height)
                drawRoundRect(
                    color = segment.color,
                    topLeft = Offset(x, 0f),
                    size = Size(width.coerceAtMost(size.width - x), size.height),
                    cornerRadius = radius,
                )
                x += width + gap
            }
            if (hasFreeSpace && x < size.width) {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(size.width - x, size.height),
                    cornerRadius = radius,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            segments.forEach { segment ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(segment.color, CircleShape)
                    )
                    Text(
                        text = "${segment.label} ${segment.tokens.exact()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageDetailRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
        Text(text = value)
    }
}

@Composable
private fun StatsIcon(imageVector: ImageVector, contentDescription: String) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(12.dp)
    )
}

@Composable
fun StatsItem(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        icon()
        content()
    }
}

private data class NerdStats(
    val total: TokenUsage,
    val lastUsage: TokenUsage,
    val steps: Int,
    val contextTokens: Int,
    val durationMillis: Long?,
    val tps: Float?,
    val speedEstimated: Boolean = false,
    val cacheHitRate: Float? = null,
    val live: Boolean = false,
    val outputTokens: Int = 0,
)

/** [usage] 为空表示还在流式生成，用已生成字符估算，生成结束后会被厂商返回的真实用量替换 */
private fun UIMessage.nerdStats(usage: TokenUsage?, live: Boolean): NerdStats {
    val last = lastRequestUsage() ?: usage ?: TokenUsage()
    val steps = stepCount ?: 1
    if (live) {
        val chars = parts.sumOf { part ->
            when (part) {
                is UIMessagePart.Text -> part.text.length
                is UIMessagePart.Reasoning -> part.reasoning.length
                else -> 0
            }
        }
        // 约 4 字符 1 token；中英混排也接近，只用于生成过程中的实时反馈
        val estimatedTokens = (chars / 4f).toInt().coerceAtLeast(if (chars > 0) 1 else 0)
        val duration = generationTimeMs?.takeIf { it > 0 }
        return NerdStats(
            total = usage ?: TokenUsage(),
            lastUsage = last,
            steps = steps,
            contextTokens = last.promptTokens + last.completionTokens,
            durationMillis = duration,
            tps = duration?.takeIf { estimatedTokens > 0 }
                ?.let { estimatedTokens.toFloat() / it * 1000f }
                ?.takeIf { it.isFinite() && it > 0f },
            speedEstimated = true,
            live = true,
            outputTokens = estimatedTokens,
        )
    }
    val completed = requireNotNull(usage)
    // 优先使用逐步骤累计的生成耗时；旧消息没有这个字段时，只有单步消息能用创建到完成
    // 的间隔粗略估算——多步消息的间隔里还包含工具执行与审批等待，拿来算速度会明显偏低
    val measuredDuration = generationTimeMs?.takeIf { it > 0 }
    val estimatedDuration = if (measuredDuration == null && steps == 1) {
        finishedAt?.let { finishedAt ->
            Duration.between(
                createdAt.toJavaLocalDateTime(),
                finishedAt.toJavaLocalDateTime()
            ).toMillis()
        }?.takeIf { it > 0 }
    } else {
        null
    }
    val durationMillis = measuredDuration ?: estimatedDuration
    return NerdStats(
        total = completed,
        lastUsage = last,
        steps = steps,
        contextTokens = last.promptTokens + last.completionTokens,
        durationMillis = durationMillis,
        // 输出 token 与生成耗时都按步骤累计，二者相除才是一整段生成的平均速度
        tps = durationMillis?.takeIf { completed.completionTokens > 0 }
            ?.let { completed.completionTokens.toFloat() / it * 1000f }
            ?.takeIf { it.isFinite() && it > 0f },
        speedEstimated = measuredDuration == null && estimatedDuration != null,
        cacheHitRate = last.promptTokens.takeIf { it > 0 && last.cachedTokens > 0 }
            ?.let { last.cachedTokens.toFloat() / it },
    )
}

/** 小于 10 时保留两位，避免 0.4 这类值被四舍五入成 0.0 */
private fun Float.label(estimated: Boolean = false): String {
    val value = if (this < 10f) toFixed(2) else toFixed(1)
    return (if (estimated) "~" else "") + "$value tok/s"
}

private fun Long.asDuration(): String = when {
    this < 1_000 -> "${this}ms"
    this < 60_000 -> "${(this / 1000f).toFixed(1)}s"
    else -> "${(this / 60_000f).toFixed(1)}min"
}

private fun Int.exact(): String = NumberFormat.getIntegerInstance().format(this)
