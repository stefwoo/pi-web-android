package dev.piweb.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.piweb.android.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val isConnected by viewModel.isConnected.collectAsState()
    val selectedSession by viewModel.selectedSession.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val status by viewModel.sessionStatus.collectAsState()
    val permissionReq by viewModel.activePermission.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showNewSessionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isConnected) (selectedSession?.name ?: "Pi Session") else "连接 Pi Web",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (isConnected && status != null) {
                            Text(
                                text = "${status?.tokens ?: 0} tok · $${String.format("%.4f", status?.cost ?: 0.0)} · Ctx ${(status?.contextUsage?.times(100))?.toInt() ?: 0}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (isConnected) {
                        if (isStreaming) {
                            IconButton(onClick = { viewModel.abortExecution() }) {
                                Icon(Icons.Default.Stop, contentDescription = "停止", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("新建会话") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = {
                                    showMenu = false
                                    showNewSessionDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("压缩上下文 (Compact)") },
                                leadingIcon = { Icon(Icons.Default.Compress, null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.compactSession()
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("断开连接") },
                                leadingIcon = { Icon(Icons.Default.Logout, null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.disconnect()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!isConnected) {
                ConnectView(viewModel)
            } else {
                ChatWorkspaceView(viewModel)
            }

            // 权限确认对话框
            permissionReq?.let { req ->
                PermissionDialog(
                    req = req,
                    onApprove = { viewModel.respondPermission(true) },
                    onDeny = { viewModel.respondPermission(false, "用户已在手机端拒绝执行") }
                )
            }

            // 新建会话对话框
            if (showNewSessionDialog) {
                NewSessionDialog(
                    onDismiss = { showNewSessionDialog = false },
                    onConfirm = { name ->
                        showNewSessionDialog = false
                        viewModel.createSession(name)
                    }
                )
            }
        }
    }
}

@Composable
fun ConnectView(viewModel: MainViewModel) {
    var url by remember { mutableStateOf(viewModel.serverUrl.value) }
    var token by remember { mutableStateOf(viewModel.authToken.value) }
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Terminal,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("连接到 Pi Web 节点", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("支持 Tailscale 域名、CF Tunnel 或局域网 IP", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("服务器地址") },
            placeholder = { Text("https://pi-web.tailnet.ts.net") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("认证 Token / Header (可选)") },
            placeholder = { Text("Bearer token 或 CF-Access 凭据") },
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.connectServer(url, token) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("连接")
            }
        }
    }
}

@Composable
fun ChatWorkspaceView(viewModel: MainViewModel) {
    val sessions by viewModel.sessions.collectAsState()
    val selectedSession by viewModel.selectedSession.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val liveStreamingText by viewModel.liveStreamingText.collectAsState()
    val activeToolCalls by viewModel.activeToolCalls.collectAsState()

    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 自动滚动到底部
    LaunchedEffect(messages.size, liveStreamingText, activeToolCalls.size) {
        if (messages.isNotEmpty() || liveStreamingText.isNotEmpty()) {
            listState.animateScrollToItem((messages.size + if (isStreaming) 1 else 0).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 会话切换 Tabs
        if (sessions.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = sessions.indexOf(selectedSession).coerceAtLeast(0),
                edgePadding = 8.dp
            ) {
                sessions.forEach { s ->
                    Tab(
                        selected = s.id == selectedSession?.id,
                        onClick = { viewModel.selectSession(s) },
                        text = { Text(s.name ?: s.id.take(8)) }
                    )
                }
            }
        }

        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                MessageCard(msg)
            }

            // 正在流式生成的实时气泡
            if (isStreaming && (liveStreamingText.isNotBlank() || activeToolCalls.isNotEmpty())) {
                item {
                    MessageCard(
                        Message(
                            role = "assistant",
                            content = liveStreamingText,
                            toolCalls = activeToolCalls
                        ),
                        isLive = true
                    )
                }
            }
        }

        // 底部发送栏
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("输入指令给 Agent...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))

                if (isStreaming) {
                    FilledIconButton(
                        onClick = { viewModel.abortExecution() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "停止")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            viewModel.sendMessage(input)
                            input = ""
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageCard(msg: Message, isLive: Boolean = false) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"

    if (isSystem) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(msg.content, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 如果有工具调用（Tool Calls），先渲染工具卡片
                if (msg.toolCalls.isNotEmpty()) {
                    msg.toolCalls.forEach { tool ->
                        ToolCallItem(tool)
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // 消息文本
                if (msg.content.isNotBlank()) {
                    Text(
                        text = msg.content,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = if (!isUser) FontFamily.Monospace else FontFamily.Default,
                        fontSize = 14.sp
                    )
                }

                if (isLive) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                }
            }
        }
    }
}

@Composable
fun ToolCallItem(tool: ToolCall) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Tool: ${tool.name}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // 状态指示
                val statusColor = when (tool.status) {
                    "completed" -> Color(0xFF2E7D32)
                    "failed" -> MaterialTheme.colorScheme.error
                    else -> Color(0xFFF57C00)
                }
                Text(tool.status, color = statusColor, style = MaterialTheme.typography.labelSmall)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded && tool.args.isNotBlank()) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        text = tool.args,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionDialog(
    req: PermissionRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("高危权限请求") },
        text = {
            Column {
                Text("Agent 请求执行以下操作：", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                ) {
                    Text(
                        text = req.command ?: req.description ?: req.tool,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("允许执行")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDeny) {
                Text("拒绝")
            }
        }
    )
}

@Composable
fun NewSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("会话名称") },
                placeholder = { Text("例如：重构代码 / 修复 bug") }
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.ifBlank { "新会话" }) }) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
