package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Remove01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ContextCompactionPolicy
import me.rerere.rikkahub.data.model.Assistant

/**
 * 手动压缩面板，布局与信息层级对齐 Reasonix 的「上下文」压缩设置：
 * 当前用量 / 下次整理 / 模型窗口 + 进度条，然后是整理策略、阈值、容量保护，
 * 最后是本分支原有的立即压缩参数（附加说明、保留最近消息数）。
 */
@Composable
fun CompressContextDialog(
    usedTokens: Int?,
    contextWindow: Int,
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, keepRecentMessages: Int) -> Job,
) {
    var additionalPrompt by remember { mutableStateOf("") }
    // 默认全部压缩，请求前缀与对话一致，缓存命中最好
    var keepRecentMessages by remember { mutableIntStateOf(0) }
    var currentJob by remember { mutableStateOf<Job?>(null) }
    val isLoading = currentJob?.isActive == true

    // Monitor job completion
    LaunchedEffect(currentJob) {
        currentJob?.join()
        if (currentJob?.isCompleted == true && currentJob?.isCancelled == false) {
            onDismiss()
        }
        currentJob = null
    }

    val window = contextWindow.takeIf { it > 0 } ?: 0
    val capacity = ContextCompactionPolicy.capacityTokens(window)
    val trigger = ContextCompactionPolicy.triggerTokens(window, assistant.compactThresholdTokens)

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) {
                onDismiss()
            }
        },
        title = {
            Text(stringResource(R.string.chat_page_compress_context_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (isLoading) {
                    // Loading state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContainedLoadingIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_page_compressing))
                    }
                } else {
                    if (window <= 0) {
                        Text(
                            text = stringResource(R.string.assistant_page_compact_no_window_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        CompactGauge(
                            usedTokens = usedTokens,
                            triggerTokens = trigger,
                            windowTokens = window,
                        )
                        CompactPolicySelector(
                            assistant = assistant,
                            capacityTokens = capacity,
                            windowTokens = window,
                            onUpdateAssistant = onUpdateAssistant,
                        )
                        CapacityGuardRow(capacityTokens = capacity)
                    }

                    Text(
                        text = stringResource(R.string.chat_page_compress_now_title),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    // Additional context input
                    OutlinedTextField(
                        value = additionalPrompt,
                        onValueChange = { additionalPrompt = it },
                        label = {
                            Text(stringResource(R.string.chat_page_compress_additional_prompt))
                        },
                        placeholder = {
                            Text(stringResource(R.string.chat_page_compress_additional_prompt_hint))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.chat_page_compress_keep_recent),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalIconButton(
                                onClick = { keepRecentMessages-- },
                                enabled = keepRecentMessages > 0,
                            ) {
                                Icon(HugeIcons.Remove01, contentDescription = "Decrease")
                            }
                            Text(
                                text = keepRecentMessages.toString(),
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 40.dp),
                            )
                            FilledTonalIconButton(onClick = { keepRecentMessages++ }) {
                                Icon(HugeIcons.Add01, contentDescription = "Increase")
                            }
                        }
                        Text(
                            text = stringResource(R.string.chat_page_compress_keep_recent_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                TextButton(onClick = {
                    currentJob?.cancel()
                    currentJob = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            } else {
                TextButton(onClick = {
                    currentJob = onConfirm(additionalPrompt, keepRecentMessages)
                }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

/** 当前用量 / 下次整理 / 模型窗口，与 Reasonix 的压缩面板一致 */
@Composable
private fun CompactGauge(
    usedTokens: Int?,
    triggerTokens: Int,
    windowTokens: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GaugeRow(
            label = stringResource(R.string.chat_page_compress_current),
            value = usedTokens?.let(::tokenLabel) ?: "—",
        )
        GaugeRow(
            label = stringResource(R.string.chat_page_compress_next_fold),
            value = tokenLabel(triggerTokens),
        )
        GaugeRow(
            label = stringResource(R.string.assistant_page_compact_window),
            value = tokenLabel(windowTokens),
        )
        val fraction = if (triggerTokens <= 0) {
            0f
        } else {
            ((usedTokens ?: 0).toFloat() / triggerTokens).coerceIn(0f, 1f)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.chat_page_compress_trigger_note,
                tokenLabel(triggerTokens),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GaugeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
    }
}

/** 整理策略：按模型容量（默认）或自定义阈值，写入助手配置 */
@Composable
private fun CompactPolicySelector(
    assistant: Assistant,
    capacityTokens: Int,
    windowTokens: Int,
    onUpdateAssistant: (Assistant) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val custom = assistant.compactThresholdTokens > 0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.assistant_page_compact_policy),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.assistant_page_compact_policy_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !custom,
                onClick = { onUpdateAssistant(assistant.copy(compactThresholdTokens = 0)) },
                label = { Text(stringResource(R.string.assistant_page_compact_policy_capacity)) },
            )
            FilterChip(
                selected = custom,
                onClick = {
                    if (!custom) {
                        onUpdateAssistant(
                            assistant.copy(
                                compactThresholdTokens = capacityTokens
                                    .takeIf { it > ContextCompactionPolicy.MIN_THRESHOLD_TOKENS }
                                    ?: 50_000
                            )
                        )
                    }
                },
                label = { Text(stringResource(R.string.assistant_page_compact_policy_custom)) },
            )
        }

        if (custom) {
            var thresholdInput by remember(assistant.id, assistant.compactThresholdTokens) {
                mutableStateOf(assistant.compactThresholdTokens.toString())
            }
            fun commitThreshold() {
                val value = thresholdInput.toIntOrNull()
                    ?.takeIf { it >= ContextCompactionPolicy.MIN_THRESHOLD_TOKENS }
                    ?: assistant.compactThresholdTokens
                thresholdInput = value.toString()
                if (value != assistant.compactThresholdTokens) {
                    onUpdateAssistant(assistant.copy(compactThresholdTokens = value))
                }
            }
            OutlinedTextField(
                value = thresholdInput,
                onValueChange = { input -> thresholdInput = input.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    commitThreshold()
                    focusManager.clearFocus()
                }),
                singleLine = true,
                suffix = { Text("tokens") },
                supportingText = {
                    if (thresholdInput.toIntOrNull()?.let {
                            it < ContextCompactionPolicy.MIN_THRESHOLD_TOKENS
                        } == true
                    ) {
                        Text(
                            text = stringResource(
                                R.string.assistant_page_compact_threshold_invalid,
                                ContextCompactionPolicy.MIN_THRESHOLD_TOKENS,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.7f, 0.85f).forEach { ratio ->
                    val value = (windowTokens * ratio / 1000).toInt() * 1000
                    FilterChip(
                        selected = assistant.compactThresholdTokens == value,
                        onClick = {
                            onUpdateAssistant(assistant.copy(compactThresholdTokens = value))
                            thresholdInput = value.toString()
                        },
                        label = { Text("${(ratio * 100).roundToInt()}%") },
                    )
                }
            }
        }
    }
}

@Composable
private fun CapacityGuardRow(capacityTokens: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.assistant_page_compact_capacity),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.assistant_page_compact_capacity_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (capacityTokens > 0) tokenLabel(capacityTokens) else "—",
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
    }
}

private fun tokenLabel(tokens: Int): String =
    if (tokens >= 1000) "${tokens / 1000}k" else tokens.toString()
