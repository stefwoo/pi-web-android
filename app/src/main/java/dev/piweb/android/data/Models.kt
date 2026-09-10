package dev.piweb.android.data

import kotlinx.serialization.Serializable

@Serializable
data class SessionInfo(
    val id: String,
    val cwd: String? = null,
    val name: String? = null,
    val model: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

@Serializable
data class TokenUsage(
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
    val reasoning: Long = 0,
    val totalTokens: Long = 0,
    val cost: Double = 0.0
)

@Serializable
data class SessionStatus(
    val tokens: Long = 0,
    val cost: Double = 0.0,
    val contextUsage: Double = 0.0, // 0.0 ~ 1.0 (0% ~ 100%)
    val contextTokens: Long = 0,
    val contextLimit: Long = 0,
    val isRunning: Boolean = false,
    val currentModel: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val args: String = "",
    val status: String = "running" // "running", "completed", "failed", "waiting_approval"
)

@Serializable
data class ToolResult(
    val callId: String,
    val output: String = "",
    val isError: Boolean = false,
    val exitCode: Int? = null
)

@Serializable
data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system"
    val content: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val usage: TokenUsage? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class PermissionRequest(
    val id: String,
    val tool: String,
    val command: String? = null,
    val description: String? = null
)

@Serializable
data class StreamEvent(
    val type: String? = null, // "token", "tool_start", "tool_end", "turn_start", "turn_end", "permission_request", "status", "error", "done"
    val chunk: String? = null,
    val messageId: String? = null,
    val toolCall: ToolCall? = null,
    val toolResult: ToolResult? = null,
    val status: SessionStatus? = null,
    val permission: PermissionRequest? = null,
    val error: String? = null
)

@Serializable
data class PromptRequest(
    val prompt: String
)

@Serializable
data class PermissionResponse(
    val requestId: String,
    val approved: Boolean,
    val reason: String? = null
)

@Serializable
data class SessionActionRequest(
    val name: String? = null,
    val fromMessageId: String? = null
)
