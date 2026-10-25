package me.rerere.ai.ui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.core.merge
import me.rerere.ai.provider.Model
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 流式响应的高频合并器。
 *
 * [StreamChunkHandler] 每收到一个分片就复制一次消息与 parts；在 300–1000 token/s 的回复里，
 * 正文拼接是 O(已生成长度) 的，一秒会产生几十 MB 的临时字符串，直接推高 GC 与渲染线程的停顿。
 *
 * 这里把“累积”和“生成快照”拆开：分片只往 StringBuilder 里追加，正文拼接变成 O(增量)；
 * 只有调用 [build] 时才生成不可变的 [UIMessage]。调用方按显示帧（约 16ms）调用 [build]，
 * 一秒最多 60 次快照，而不是每个 token 一次。
 *
 * 与 [StreamChunkHandler] 的语义保持一致：同一 id 的文本/推理/图片分片合并到同一 part，
 * 工具按 toolCallId 定位，Finish 关闭未结束的推理并记录完成时间。
 */
class StreamingMessageAccumulator(private val model: Model? = null) {

    private sealed interface Slot {
        fun build(): UIMessagePart
    }

    private class TextSlot(
        val id: String?,
        val builder: StringBuilder = StringBuilder(),
        var metadata: JsonObject? = null,
    ) : Slot {
        override fun build(): UIMessagePart = UIMessagePart.Text(builder.toString(), metadata)
    }

    private class ReasoningSlot(
        val id: String?,
        val type: ReasoningType,
        val createdAt: Instant,
        val builder: StringBuilder = StringBuilder(),
        var metadata: JsonObject? = null,
        var finishedAt: Instant? = null,
    ) : Slot {
        override fun build(): UIMessagePart = UIMessagePart.Reasoning(
            reasoning = builder.toString(),
            createdAt = createdAt,
            finishedAt = finishedAt,
            metadata = metadata,
            reasoningType = type,
        )
    }

    private class ToolSlot(
        var toolCallId: String,
        var toolName: String,
        var input: StringBuilder = StringBuilder(),
        var output: String? = null,
        var metadata: JsonObject? = null,
    ) : Slot {
        override fun build(): UIMessagePart = UIMessagePart.Tool(
            toolCallId = toolCallId,
            toolName = toolName,
            input = input.toString(),
            output = output?.let { listOf(UIMessagePart.Text(it)) } ?: emptyList(),
            metadata = metadata,
        )
    }

    private class ServerToolSlot(
        var toolCallId: String,
        var toolName: String,
        var input: JsonElement? = null,
        var output: JsonElement? = null,
        var status: ServerToolStatus = ServerToolStatus.IN_PROGRESS,
        var metadata: JsonObject? = null,
    ) : Slot {
        override fun build(): UIMessagePart = UIMessagePart.ServerTool(
            toolCallId = toolCallId,
            toolName = toolName,
            input = input,
            output = output,
            status = status,
            metadata = metadata,
        )
    }

    private class ImageSlot(
        val id: String?,
        val mimeType: String,
        var metadata: JsonObject? = null,
        var snapshot: String? = null,
        val builder: StringBuilder = StringBuilder(),
    ) : Slot {
        override fun build(): UIMessagePart {
            val data = snapshot ?: builder.toString()
            return UIMessagePart.Image(url = "data:$mimeType;base64,$data", metadata = metadata)
        }
    }

    private val slots = ArrayList<Slot>()
    private val textIndexes = HashMap<String, Int>()
    private val reasoningIndexes = HashMap<String, Int>()
    private val imageIndexes = HashMap<String, Int>()
    private val toolIndexes = HashMap<String, Int>()
    private val serverToolInputBuffers = HashMap<String, StringBuilder>()

    private val annotations = mutableListOf<UIMessageAnnotation>()
    private var usage: TokenUsage? = null
    private var finishedAt: kotlinx.datetime.LocalDateTime? = null

    /** 累积一个分片；不生成不可变对象，除工具参数外不产生与已生成正文等长的拷贝 */
    fun consume(chunk: StreamChunk) {
        when (chunk) {
            is StreamChunk.TextStart -> {
                if (chunk.id !in textIndexes) {
                    textIndexes[chunk.id] = slots.size
                    slots.add(TextSlot(chunk.id, metadata = chunk.metadata))
                }
            }

            is StreamChunk.TextDelta -> {
                val index = textIndexes[chunk.id]
                val slot = index?.let { slots.getOrNull(it) } as? TextSlot
                if (slot == null) {
                    textIndexes[chunk.id] = slots.size
                    slots.add(TextSlot(chunk.id, StringBuilder(chunk.text), chunk.metadata))
                } else {
                    slot.builder.append(chunk.text)
                    if (chunk.metadata != null) slot.metadata = chunk.metadata
                }
            }

            is StreamChunk.TextEnd -> textIndexes.remove(chunk.id)

            is StreamChunk.ReasoningStart -> {
                if (chunk.id !in reasoningIndexes) {
                    reasoningIndexes[chunk.id] = slots.size
                    slots.add(
                        ReasoningSlot(
                            id = chunk.id,
                            type = chunk.reasoningType,
                            createdAt = Clock.System.now(),
                            metadata = chunk.metadata,
                        )
                    )
                }
            }

            is StreamChunk.ReasoningDelta -> {
                val index = reasoningIndexes[chunk.id]
                val slot = index?.let { slots.getOrNull(it) } as? ReasoningSlot
                if (slot == null) {
                    reasoningIndexes[chunk.id] = slots.size
                    slots.add(
                        ReasoningSlot(
                            id = chunk.id,
                            type = chunk.reasoningType,
                            createdAt = Clock.System.now(),
                            builder = StringBuilder(chunk.text),
                            metadata = chunk.metadata,
                        )
                    )
                } else {
                    slot.builder.append(chunk.text)
                    if (chunk.metadata != null) slot.metadata = chunk.metadata
                }
            }

            is StreamChunk.ReasoningEnd -> {
                val index = reasoningIndexes.remove(chunk.id) ?: return
                val slot = slots.getOrNull(index) as? ReasoningSlot ?: return
                slot.finishedAt = Clock.System.now()
                if (chunk.metadata != null) slot.metadata = chunk.metadata
            }

            is StreamChunk.ToolCallStart -> {
                if (chunk.id !in toolIndexes) {
                    toolIndexes[chunk.id] = slots.size
                    slots.add(ToolSlot(chunk.id, chunk.toolName, metadata = chunk.metadata))
                }
            }

            is StreamChunk.ToolCallDelta -> {
                val index = toolIndexes[chunk.id]
                val slot = index?.let { slots.getOrNull(it) } as? ToolSlot
                if (slot == null) {
                    toolIndexes[chunk.id] = slots.size
                    slots.add(
                        ToolSlot(
                            toolCallId = chunk.id,
                            toolName = chunk.toolNameDelta,
                            input = StringBuilder(chunk.inputDelta),
                            metadata = chunk.metadata,
                        )
                    )
                } else {
                    slot.toolName += chunk.toolNameDelta
                    slot.input.append(chunk.inputDelta)
                    if (chunk.metadata != null) slot.metadata = chunk.metadata
                }
            }

            is StreamChunk.ToolCallEnd -> Unit

            is StreamChunk.ToolCallOutput -> {
                val index = toolIndexes[chunk.id] ?: return
                val slot = slots.getOrNull(index) as? ToolSlot ?: return
                slot.output = chunk.output
            }

            is StreamChunk.ServerToolStart -> {
                val index = slots.indexOfFirst { it is ServerToolSlot && it.toolCallId == chunk.id }
                if (index < 0) {
                    slots.add(
                        ServerToolSlot(
                            toolCallId = chunk.id,
                            toolName = chunk.toolName,
                            input = chunk.input,
                            metadata = chunk.metadata,
                        )
                    )
                } else {
                    val slot = slots[index] as ServerToolSlot
                    if (chunk.toolName.isNotBlank()) slot.toolName = chunk.toolName
                    if (chunk.input != null) slot.input = chunk.input
                    slot.metadata = mergeMetadata(slot.metadata, chunk.metadata)
                }
            }

            is StreamChunk.ServerToolInputDelta -> {
                serverToolInputBuffers.getOrPut(chunk.id) { StringBuilder() }.append(chunk.inputDelta)
                val index = slots.indexOfFirst { it is ServerToolSlot && it.toolCallId == chunk.id }
                if (index >= 0) {
                    val slot = slots[index] as ServerToolSlot
                    slot.metadata = mergeMetadata(slot.metadata, chunk.metadata)
                }
            }

            is StreamChunk.ServerToolInputEnd -> {
                val parsed = parseServerToolJson(serverToolInputBuffers.remove(chunk.id)?.toString())
                    ?: return
                val index = slots.indexOfFirst { it is ServerToolSlot && it.toolCallId == chunk.id }
                if (index >= 0) (slots[index] as ServerToolSlot).input = parsed
            }

            is StreamChunk.ServerToolEnd -> {
                val buffered = parseServerToolJson(serverToolInputBuffers.remove(chunk.id)?.toString())
                val index = slots.indexOfFirst { it is ServerToolSlot && it.toolCallId == chunk.id }
                if (index < 0) {
                    slots.add(
                        ServerToolSlot(
                            toolCallId = chunk.id,
                            toolName = "",
                            input = chunk.input ?: buffered,
                            output = chunk.output,
                            status = chunk.status,
                            metadata = chunk.metadata,
                        )
                    )
                } else {
                    val slot = slots[index] as ServerToolSlot
                    slot.input = chunk.input ?: buffered ?: slot.input
                    if (chunk.output != null) slot.output = chunk.output
                    slot.status = chunk.status
                    slot.metadata = mergeMetadata(slot.metadata, chunk.metadata)
                }
            }

            is StreamChunk.ImageStart -> {
                if (chunk.id !in imageIndexes) {
                    imageIndexes[chunk.id] = slots.size
                    slots.add(ImageSlot(chunk.id, chunk.mimeType, chunk.metadata))
                }
            }

            is StreamChunk.ImageDelta -> {
                val index = imageIndexes[chunk.id]
                val slot = index?.let { slots.getOrNull(it) } as? ImageSlot
                if (slot == null) {
                    imageIndexes[chunk.id] = slots.size
                    slots.add(
                        ImageSlot(
                            id = chunk.id,
                            mimeType = "image/png",
                            metadata = chunk.metadata,
                            builder = StringBuilder(chunk.data),
                        )
                    )
                } else {
                    slot.builder.append(chunk.data)
                    if (chunk.metadata != null) slot.metadata = chunk.metadata
                }
            }

            is StreamChunk.ImageSnapshot -> {
                val index = imageIndexes[chunk.id]
                val slot = index?.let { slots.getOrNull(it) } as? ImageSlot
                if (slot == null) {
                    imageIndexes[chunk.id] = slots.size
                    slots.add(
                        ImageSlot(
                            id = chunk.id,
                            mimeType = "image/png",
                            metadata = chunk.metadata,
                            snapshot = chunk.data,
                        )
                    )
                } else {
                    slot.snapshot = chunk.data
                    if (chunk.metadata != null) slot.metadata = chunk.metadata
                }
            }

            is StreamChunk.ImageEnd -> imageIndexes.remove(chunk.id)

            is StreamChunk.Annotations -> {
                chunk.annotations.forEach { annotation ->
                    if (annotation !in annotations) annotations.add(annotation)
                }
            }

            is StreamChunk.Usage -> usage = usage.merge(chunk.usage)

            is StreamChunk.Finish -> {
                finishedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                // Finish 同时结束尚未显式结束的推理
                val now = Clock.System.now()
                slots.forEach { slot ->
                    if (slot is ReasoningSlot && slot.finishedAt == null) slot.finishedAt = now
                }
                textIndexes.clear()
                reasoningIndexes.clear()
                imageIndexes.clear()
                toolIndexes.clear()
                serverToolInputBuffers.clear()
            }
        }
    }

    /**
     * 生成当前状态的不可变消息。[base] 提供此前步骤已有的 parts 与消息级字段。
     * 只应在显示帧上调用，避免每个 token 都复制一次整条消息。
     */
    fun build(base: UIMessage): UIMessage {
        val parts = ArrayList<UIMessagePart>(base.parts.size + slots.size)
        parts.addAll(base.parts)
        slots.forEach { parts.add(it.build()) }
        return base.copy(
            parts = parts,
            annotations = annotations.toList(),
            usage = usage ?: base.usage,
            finishedAt = finishedAt ?: base.finishedAt,
            modelId = base.modelId ?: model?.id,
        )
    }
}

private fun parseServerToolJson(value: String?): JsonElement? {
    val text = value?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { me.rerere.ai.util.json.parseToJsonElement(text) }.getOrNull()
}

private fun mergeMetadata(old: JsonObject?, new: JsonObject?): JsonObject? = when {
    old == null -> new
    new == null -> old
    else -> JsonObject(old + new)
}
