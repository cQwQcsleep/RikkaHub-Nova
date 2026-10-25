package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.ContextCompactionPolicy
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ModelField
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.toFixed
import me.rerere.ui.components.Select
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.model.Tag as DataTag

@Composable
fun AssistantBasicPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_basic))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantBasicContent(
            innerPadding = innerPadding,
            assistant = assistant,
            providers = providers,
            tags = tags,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
            vm = vm
        )
    }
}

@Composable
internal fun AssistantBasicContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    tags: List<DataTag>,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM
) {
    val modelListState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = providers,
        type = ModelType.CHAT,
    )
    ModelListSheet(
        state = modelListState,
        onSelect = { onUpdate(assistant.copy(chatModelId = it.id)) },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .padding(bottom = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            UIAvatar(
                value = assistant.avatar,
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                onUpdate = { avatar ->
                    onUpdate(
                        assistant.copy(
                            avatar = avatar
                        )
                    )
                },
                modifier = Modifier
                    .size(104.dp)
                    .heroAnimation("assistant_${assistant.id}")
            )
        }

        CardGroup {
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_name)) },
            ) {
                OutlinedTextField(
                    value = assistant.name,
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                name = it
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_tags)) },
            ) {
                TagsInput(
                    value = assistant.tags,
                    tags = tags,
                    onValueChange = { tagIds, tagList ->
                        vm.updateTags(tagIds, tagList)
                    },
                )
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_workspace)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_workspace_desc)) },
            ) {
                val selectedWorkspace = workspaces.find { it.id == assistant.workspaceId?.toString() }
                Select(
                    options = listOf<WorkspaceEntity?>(null) + workspaces,
                    selectedOption = selectedWorkspace,
                    onOptionSelected = { workspace ->
                        onUpdate(
                            assistant.copy(
                                workspaceId = workspace?.id?.let { Uuid.parse(it) }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { workspace ->
                        workspace?.name ?: stringResource(R.string.workspace_no_binding)
                    },
                )
            }
            switchItem(
                checked = assistant.useAssistantAvatar,
                onCheckedChange = {
                    onUpdate(
                        assistant.copy(
                            useAssistantAvatar = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_use_assistant_avatar_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_use_assistant_avatar)) },
            )
        }

        CardGroup {
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_chat_model)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_chat_model_desc)) },
            ) {
                ModelField(
                    model = modelListState.currentModel,
                    onClick = { modelListState.open() },
                )
            }
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_thinking_budget)) },
                trailingContent = {
                    ReasoningButton(
                        reasoningLevel = assistant.reasoningLevel,
                        onUpdateReasoningLevel = { level ->
                            onUpdate(assistant.copy(reasoningLevel = level))
                        }
                    )
                },
            )
            switchItem(
                checked = assistant.streamOutput,
                onCheckedChange = {
                    onUpdate(
                        assistant.copy(
                            streamOutput = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_stream_output_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_stream_output)) },
            )
        }

        CardGroup {
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_temperature)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_temperature_warning)) },
                trailingContent = {
                    Switch(
                        checked = assistant.temperature != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    temperature = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                AnimatedVisibility(visible = assistant.temperature != null) {
                    var temperatureInput by remember(assistant.id) {
                        mutableStateOf(assistant.temperature.toString())
                    }
                    val temperatureValue = temperatureInput.toFloatOrNull()
                    OutlinedTextField(
                        value = temperatureInput,
                        onValueChange = { value ->
                            temperatureInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..2f }?.let { temperature ->
                                onUpdate(
                                    assistant.copy(
                                        temperature = temperature
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = temperatureValue == null || temperatureValue !in 0f..2f,
                        supportingText = {
                            Text("0 - 2")
                        }
                    )
                }
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_top_p)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_top_p_warning)) },
                trailingContent = {
                    Switch(
                        checked = assistant.topP != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    topP = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                AnimatedVisibility(visible = assistant.topP != null) {
                    var topPInput by remember(assistant.id) {
                        mutableStateOf(assistant.topP.toString())
                    }
                    val topPValue = topPInput.toFloatOrNull()
                    OutlinedTextField(
                        value = topPInput,
                        onValueChange = { value ->
                            topPInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { nextTopP ->
                                onUpdate(
                                    assistant.copy(
                                        topP = nextTopP
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = topPValue == null || topPValue !in 0f..1f,
                        supportingText = {
                            Text("0 - 1")
                        }
                    )
                }
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_context_message_limit)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_context_message_limit_desc)) },
            ) {
                var contextMessageLimitInput by remember(
                    assistant.id,
                    assistant.contextMessageLimit
                ) {
                    mutableStateOf(assistant.contextMessageLimit.toString())
                }
                var contextMessageLimitFocused by remember(assistant.id) {
                    mutableStateOf(false)
                }
                val focusManager = LocalFocusManager.current

                fun commitContextMessageLimit() {
                    val value = contextMessageLimitInput.toIntOrNull()
                    if (value == null) {
                        contextMessageLimitInput = assistant.contextMessageLimit.toString()
                        return
                    }

                    contextMessageLimitInput = value.toString()
                    if (value != assistant.contextMessageLimit) {
                        onUpdate(assistant.copy(contextMessageLimit = value))
                    }
                }

                OutlinedTextField(
                    value = contextMessageLimitInput,
                    onValueChange = { input ->
                        if (input.all(Char::isDigit) &&
                            (input.isEmpty() || input.toIntOrNull() != null)
                        ) {
                            contextMessageLimitInput = input
                            input.toIntOrNull()
                                ?.takeIf { it != assistant.contextMessageLimit }
                                ?.let { onUpdate(assistant.copy(contextMessageLimit = it)) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (contextMessageLimitFocused && !focusState.isFocused) {
                                commitContextMessageLimit()
                            }
                            contextMessageLimitFocused = focusState.isFocused
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true,
                    supportingText = {
                        Text(
                            stringResource(R.string.assistant_page_context_message_limit_hint)
                        )
                    }
                )

                if (assistant.contextMessageLimit > 0) {
                    Text(
                        text = stringResource(R.string.assistant_page_context_message_limit_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            formItem(
                headlineContent = { Text(stringResource(R.string.assistant_page_max_tokens)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_max_tokens_desc)) },
            ) {
                OutlinedTextField(
                    value = assistant.maxTokens?.toString() ?: "",
                    onValueChange = { text ->
                        val tokens = if (text.isBlank()) {
                            null
                        } else {
                            text.toIntOrNull()?.takeIf { it > 0 }
                        }
                        onUpdate(
                            assistant.copy(
                                maxTokens = tokens
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.assistant_page_max_tokens_no_limit))
                    },
                    supportingText = {
                        if (assistant.maxTokens != null) {
                            Text(stringResource(R.string.assistant_page_max_tokens_limit, assistant.maxTokens))
                        } else {
                            Text(stringResource(R.string.assistant_page_max_tokens_no_token_limit))
                        }
                    }
                )
            }
        }

        // 上下文压缩：阈值与容量保护与 Reasonix 一致
        val compactModel = remember(assistant.chatModelId, providers) {
            assistant.chatModelId?.let { id ->
                providers.flatMap { it.models }.find { it.id == id }
            }
        }
        val compactWindow = compactModel?.contextLength?.takeIf { it > 0 } ?: 0
        val compactCapacity = ContextCompactionPolicy.capacityTokens(compactWindow)

        CardGroup(title = { Text(stringResource(R.string.assistant_page_compact_title)) }) {
            switchItem(
                checked = assistant.autoCompact,
                onCheckedChange = { onUpdate(assistant.copy(autoCompact = it)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_compact_auto_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_compact_auto)) },
            )

            if (assistant.autoCompact) {
                formItem(
                    headlineContent = { Text(stringResource(R.string.assistant_page_compact_policy)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_compact_policy_desc)) },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = assistant.compactThresholdTokens <= 0,
                            onClick = { onUpdate(assistant.copy(compactThresholdTokens = 0)) },
                            label = { Text(stringResource(R.string.assistant_page_compact_policy_capacity)) },
                        )
                        FilterChip(
                            selected = assistant.compactThresholdTokens > 0,
                            onClick = {
                                if (assistant.compactThresholdTokens <= 0) {
                                    // 切到自定义时先给一个有效起点，用户再改成具体值
                                    onUpdate(
                                        assistant.copy(
                                            compactThresholdTokens = compactCapacity
                                                .takeIf { it > ContextCompactionPolicy.MIN_THRESHOLD_TOKENS }
                                                ?: 50_000
                                        )
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.assistant_page_compact_policy_custom)) },
                        )
                    }
                }

                if (assistant.compactThresholdTokens > 0) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.assistant_page_compact_threshold)) },
                        supportingContent = {
                            Text(
                                if (compactCapacity > 0) {
                                    stringResource(
                                        R.string.assistant_page_compact_threshold_desc_known,
                                        ContextCompactionPolicy.capacityTokens(compactWindow),
                                    )
                                } else {
                                    stringResource(R.string.assistant_page_compact_threshold_desc)
                                }
                            )
                        },
                    ) {
                        val focusManager = LocalFocusManager.current
                        var thresholdInput by remember(assistant.id, assistant.compactThresholdTokens) {
                            mutableStateOf(assistant.compactThresholdTokens.toString())
                        }
                        fun commitThreshold() {
                            val value = thresholdInput.toIntOrNull()
                                ?.takeIf { it >= ContextCompactionPolicy.MIN_THRESHOLD_TOKENS }
                                ?: assistant.compactThresholdTokens
                            thresholdInput = value.toString()
                            if (value != assistant.compactThresholdTokens) {
                                onUpdate(assistant.copy(compactThresholdTokens = value))
                            }
                        }
                        OutlinedTextField(
                            value = thresholdInput,
                            onValueChange = { input -> thresholdInput = input.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
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
                        if (compactCapacity > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0.5f, 0.7f, 0.85f).forEach { ratio ->
                                    val value = (compactWindow * ratio / 1000).toInt() * 1000
                                    FilterChip(
                                        selected = assistant.compactThresholdTokens == value,
                                        onClick = {
                                            onUpdate(assistant.copy(compactThresholdTokens = value))
                                            thresholdInput = value.toString()
                                        },
                                        label = { Text("${(ratio * 100).roundToInt()}%") },
                                    )
                                }
                            }
                        }
                    }
                }

                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_compact_capacity)) },
                    supportingContent = {
                        Text(stringResource(R.string.assistant_page_compact_capacity_desc))
                    },
                    trailingContent = {
                        if (compactCapacity > 0) {
                            Text(
                                text = "${compactCapacity / 1000}k",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    },
                )

                if (compactWindow == 0) {
                    item(
                        headlineContent = { Text(stringResource(R.string.assistant_page_compact_no_window)) },
                        supportingContent = { Text(stringResource(R.string.assistant_page_compact_no_window_desc)) },
                    )
                }
            }
        }

        CardGroup {
            switchItem(
                checked = assistant.useGradientBackground,
                onCheckedChange = {
                    onUpdate(
                        assistant.copy(
                            useGradientBackground = it
                        )
                    )
                },
                supportingContent = { Text(stringResource(R.string.assistant_page_gradient_background_desc)) },
                headlineContent = { Text(stringResource(R.string.assistant_page_gradient_background)) },
            )

            if (!assistant.useGradientBackground) {
                formItem(
                    headlineContent = { Text(stringResource(R.string.assistant_page_chat_background)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_chat_background_desc)) },
                ) {
                    BackgroundPicker(
                        background = assistant.background,
                        backgroundOpacity = assistant.backgroundOpacity,
                        onUpdate = { background ->
                            onUpdate(
                                assistant.copy(
                                    background = background
                                )
                            )
                        }
                    )
                }
            }

            if (!assistant.useGradientBackground && assistant.background != null) {
                val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
                formItem(
                    headlineContent = { Text(stringResource(R.string.assistant_page_background_opacity)) },
                    supportingContent = { Text(stringResource(R.string.assistant_page_background_opacity_desc)) },
                    trailingContent = {
                        Text(
                            text = stringResource(
                                R.string.assistant_page_background_opacity_value,
                                (backgroundOpacity * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                ) {
                    Slider(
                        value = backgroundOpacity,
                        onValueChange = {
                            onUpdate(
                                assistant.copy(
                                    backgroundOpacity = it.toFixed(2).toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f
                                )
                            )
                        },
                        valueRange = 0f..1f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
