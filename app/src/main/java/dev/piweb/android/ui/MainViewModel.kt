package dev.piweb.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.piweb.android.data.*
import dev.piweb.android.network.PiWebClient
import dev.piweb.android.service.NotificationHelper
import dev.piweb.android.service.PiKeepAliveService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val client = PiWebClient("")
    private var eventCollectJob: Job? = null

    val serverUrl = MutableStateFlow("https://pi-web.ts.net")
    val authToken = MutableStateFlow("")

    val isConnected = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val errorMessage = MutableStateFlow<String?>(null)

    val sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val selectedSession = MutableStateFlow<SessionInfo?>(null)
    val sessionStatus = MutableStateFlow<SessionStatus?>(null)

    val messages = MutableStateFlow<List<Message>>(emptyList())
    val isStreaming = MutableStateFlow(false)
    val liveStreamingText = MutableStateFlow("")
    val activeToolCalls = MutableStateFlow<List<ToolCall>>(emptyList())

    // 权限审批队列
    val activePermission = MutableStateFlow<PermissionRequest?>(null)

    fun connectServer(url: String, token: String) {
        isLoading.value = true
        errorMessage.value = null
        serverUrl.value = url
        authToken.value = token
        client.updateConfig(url, token)

        client.listSessions { list, error ->
            isLoading.value = false
            if (list != null) {
                sessions.value = list
                isConnected.value = true
                if (list.isNotEmpty()) {
                    selectSession(list.first())
                }
            } else {
                errorMessage.value = error?.localizedMessage ?: "连接失败，请检查服务地址与网络"
            }
        }
    }

    fun disconnect() {
        eventCollectJob?.cancel()
        eventCollectJob = null
        PiKeepAliveService.stop(getApplication())
        isConnected.value = false
        isStreaming.value = false
        isLoading.value = false
        sessions.value = emptyList()
        selectedSession.value = null
        sessionStatus.value = null
        messages.value = emptyList()
        liveStreamingText.value = ""
        activeToolCalls.value = emptyList()
        activePermission.value = null
    }

    fun selectSession(session: SessionInfo) {
        selectedSession.value = session
        messages.value = emptyList()
        liveStreamingText.value = ""
        activeToolCalls.value = emptyList()
        activePermission.value = null

        // 启动 Android 前台保活服务
        PiKeepAliveService.start(getApplication(), session.name ?: session.id.take(8))

        // 拉取历史消息与会话状态
        client.getMessages(session.id, session.cwd) { list, _ ->
            list?.let { messages.value = it }
        }
        refreshStatus()

        // 订阅实时事件流
        eventCollectJob?.cancel()
        eventCollectJob = viewModelScope.launch {
            client.observeEvents(session.id, session.cwd).collect { event ->
                handleStreamEvent(event)
            }
        }
    }

    fun refreshStatus() {
        val s = selectedSession.value ?: return
        client.getSessionStatus(s.id, s.cwd) { status, _ ->
            status?.let { sessionStatus.value = it }
        }
    }

    private fun handleStreamEvent(event: StreamEvent) {
        when (event.type) {
            "token" -> {
                isStreaming.value = true
                event.chunk?.let { liveStreamingText.value += it }
            }
            "tool_start" -> {
                event.toolCall?.let { tc ->
                    activeToolCalls.value = activeToolCalls.value + tc
                }
            }
            "tool_end" -> {
                event.toolResult?.let { tr ->
                    activeToolCalls.value = activeToolCalls.value.map { tc ->
                        if (tc.id == tr.callId) tc.copy(status = if (tr.isError) "failed" else "completed") else tc
                    }
                }
            }
            "permission_request" -> {
                event.permission?.let { req ->
                    activePermission.value = req
                    NotificationHelper.showTaskCompleteNotification(
                        getApplication(),
                        "Pi Agent 请求执行权限",
                        "命令: ${req.command ?: req.tool}"
                    )
                }
            }
            "status" -> {
                event.status?.let { sessionStatus.value = it }
            }
            "turn_end", "done", "stop" -> {
                if (liveStreamingText.value.isNotBlank() || activeToolCalls.value.isNotEmpty()) {
                    val finalMsg = Message(
                        role = "assistant",
                        content = liveStreamingText.value,
                        toolCalls = activeToolCalls.value
                    )
                    messages.value = messages.value + finalMsg
                    liveStreamingText.value = ""
                    activeToolCalls.value = emptyList()
                }
                isStreaming.value = false
                refreshStatus()
                NotificationHelper.showTaskCompleteNotification(
                    getApplication(),
                    "Pi Agent 任务完成",
                    "会话 ${selectedSession.value?.name ?: "当前任务"} 已完成当前轮次生成"
                )
            }
            "error" -> {
                isStreaming.value = false
                errorMessage.value = event.error
            }
        }
    }

    fun sendMessage(content: String) {
        val session = selectedSession.value ?: return
        if (content.isBlank()) return

        messages.value = messages.value + Message("user", content)
        liveStreamingText.value = ""
        activeToolCalls.value = emptyList()
        isStreaming.value = true

        client.sendPrompt(session.id, session.cwd, content) { success, error ->
            if (!success) {
                isStreaming.value = false
                errorMessage.value = error?.localizedMessage ?: "发送失败"
            }
        }
    }

    fun abortExecution() {
        val session = selectedSession.value ?: return
        client.abortSession(session.id, session.cwd) { success, _ ->
            if (success) {
                isStreaming.value = false
                if (liveStreamingText.value.isNotBlank()) {
                    messages.value = messages.value + Message("assistant", liveStreamingText.value + " [已中断]")
                    liveStreamingText.value = ""
                }
            }
        }
    }

    fun respondPermission(approved: Boolean, reason: String? = null) {
        val req = activePermission.value ?: return
        val session = selectedSession.value ?: return
        client.respondPermission(session.id, req.id, approved, reason, session.cwd) { success, _ ->
            if (success) {
                activePermission.value = null
            }
        }
    }

    fun createSession(name: String) {
        isLoading.value = true
        client.createSession(name = name) { newSession, _ ->
            isLoading.value = false
            newSession?.let {
                sessions.value = sessions.value + it
                selectSession(it)
            }
        }
    }

    fun compactSession() {
        val session = selectedSession.value ?: return
        client.compactSession(session.id, session.cwd) { success, _ ->
            if (success) {
                messages.value = messages.value + Message("system", "✨ 会话已压缩（Context Compacted）")
                refreshStatus()
            }
        }
    }

    fun deleteSession(session: SessionInfo) {
        client.deleteSession(session.id, session.cwd) { success, _ ->
            if (success) {
                val updated = sessions.value.filter { it.id != session.id }
                sessions.value = updated
                if (selectedSession.value?.id == session.id) {
                    if (updated.isNotEmpty()) selectSession(updated.first()) else selectedSession.value = null
                }
            }
        }
    }
}
