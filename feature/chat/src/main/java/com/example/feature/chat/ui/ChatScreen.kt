package com.example.feature.chat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.components.MarkdownText
import com.example.core.common.theme.*
import com.example.core.domain.models.Message
import com.example.feature.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAgent: () -> Unit = {},
    onNavigateToProviders: () -> Unit = {}
) {
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val selectedProviderId by viewModel.selectedProviderId.collectAsState()
    val mcpServers by viewModel.mcpServers.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val streamingContent by viewModel.streamingMessageContent.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showProviderDropdown by remember { mutableStateOf(false) }
    var chatInputText by remember { mutableStateOf("") }
    
    val attachedImageUri by viewModel.attachedImageUri.collectAsState()
    val isProcessingImage by viewModel.isProcessingImage.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.processAndValidateImage(context, uri)
    }

    val activeProvider = providers.firstOrNull { it.id == selectedProviderId }
    val activeMcpToolsCount by viewModel.activeMcpToolsCount.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Slate900,
                drawerContentColor = Color.White,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Drawer Header
                    Text(
                        text = "🔑 BYOK OS",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealGlow,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.createNewChat()
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                            Text("New Conversation", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Text(
                        text = "CONVERSATIONS HISTORY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // History list
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions) { s ->
                            val isSelected = s.id == currentSessionId
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0x3314B8A6) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectSession(s.id)
                                        coroutineScope.launch { drawerState.close() }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MailOutline,
                                        contentDescription = "Chat",
                                        tint = if (isSelected) TealGlow else Color(0xFF475569),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = s.title,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        IconButton(
                                            onClick = { viewModel.deleteSession(s.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 12.dp))

                    // Bottom Navigation triggers (Settings Page & Autonomous Agent Screen)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onNavigateToSettings()
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                                Text("Settings", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                onNavigateToAgent()
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal600),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Agent", modifier = Modifier.size(16.dp))
                                Text("Agent", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            // Model dropdown toggle selectors
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(enabled = !isStreaming) { showProviderDropdown = !showProviderDropdown }
                                    .background(if (isStreaming) Slate800.copy(alpha = 0.5f) else Slate800)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .heightIn(min = 48.dp)
                                    .testTag("provider_switcher_button"),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isStreaming) Icons.Default.Lock else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Dropdown Indicator",
                                    tint = if (isStreaming) Color.Gray else TealGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Model: ${activeProvider?.displayName ?: "None"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStreaming) Color.Gray else Color.White
                                )

                                DropdownMenu(
                                    expanded = showProviderDropdown,
                                    onDismissRequest = { showProviderDropdown = false },
                                    modifier = Modifier.background(Slate800)
                                ) {
                                    providers.forEach { provider ->
                                        val isSelected = provider.id == selectedProviderId
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Selected",
                                                            tint = TealGlow,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = provider.displayName,
                                                        color = if (isSelected) TealGlow else Color.White
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.selectProvider(provider.id)
                                                showProviderDropdown = false
                                            }
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFF334155))
                                    DropdownMenuItem(
                                        text = { Text("＋ Manage Keys", color = TealGlow) },
                                        onClick = {
                                            onNavigateToProviders()
                                            showProviderDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "MenuToggle", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onNavigateToSettings() }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Slate900
                    )
                )
            },
            containerColor = Slate900
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding() // Adapts with the phone keyboard
            ) {
                if (currentSessionId == null || messages.isEmpty() && !isStreaming) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            color = Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                textTagHelper("🔥", 36.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "BYOK OS AI Engine",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Connect custom API keys, enable local MCP tools, and deploy self-contained prompts local-first.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
                        )

                        // Preset Prompts
                        listOf("Explain Redux vs Riverpod", "Simulate read_file using MCP filesystem", "Check local database memory sync log").forEach { prompt ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Slate800),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        if (currentSessionId == null) {
                                            viewModel.createNewChat()
                                        }
                                        chatInputText = prompt
                                    }
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 13.sp,
                                    color = TealGlow,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                } else {
                    val scrollState = rememberLazyListState()
                    LaunchedEffect(messages.size, streamingContent) {
                        if (messages.isNotEmpty()) {
                            scrollState.animateScrollToItem(messages.size - 1)
                        }
                    }

                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(messages) { message ->
                            MessageItem(
                                message = message,
                                onCopyClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Copied Local Message", message.content)
                                    clipboard.setPrimaryClip(clip)
                                    viewModel.showToast("Message copied to clipboard.")
                                },
                                onRetryClick = {
                                    viewModel.retryGeneration()
                                }
                            )
                        }

                        if (isStreaming && streamingContent.isNotEmpty()) {
                            item {
                                MessageStreamBubble(content = streamingContent)
                            }
                        }
                    }
                }

                // Chat Input controllers
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate900)
                ) {
                    HorizontalDivider(color = Color(0xFF1E293B))

                    if (isProcessingImage) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate800)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = TealGlow,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Evaluating optical assets...",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        HorizontalDivider(color = Color(0xFF1E293B))
                    }

                    if (attachedImageUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate800)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                coil.compose.AsyncImage(
                                    model = attachedImageUri,
                                    contentDescription = "Attached Image Preview",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, TealGlow, RoundedCornerShape(8.dp))
                                        .background(Slate900),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Ready to upload: Image captured",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearAttachedImage() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Attachment",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFF1E293B))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("attachment_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Attach Image",
                                tint = TealGlow,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        OutlinedTextField(
                            value = chatInputText,
                            onValueChange = { chatInputText = it },
                            placeholder = { Text("Ask something...", color = Color(0xFF475569)) },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Slate800,
                                unfocusedContainerColor = Slate800,
                                focusedBorderColor = TealGlow,
                                unfocusedBorderColor = Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        )

                        IconButton(
                            onClick = {
                                if (chatInputText.isBlank() && attachedImageUri == null) return@IconButton
                                if (currentSessionId == null) {
                                    viewModel.createNewChat()
                                }
                                viewModel.sendMessage(chatInputText)
                                chatInputText = ""
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(TealGlow, CircleShape)
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeMcpToolsCountVal = activeMcpToolsCount
                        val toolsText = when (activeMcpToolsCountVal) {
                            null -> "Loading…"
                            -1 -> "—"
                            else -> "$activeMcpToolsCountVal tools active"
                        }
                        Text(
                            text = "🔧 MCP connection: $toolsText",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if ((activeMcpToolsCountVal ?: 0) > 0) EmeraldGlow else Color(0xFF475569)
                        )

                        IconButton(
                            onClick = { viewModel.showToast("🎤 Voice model synthesis starting...") },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Voice", tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: Message,
    onCopyClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"
    val isToolOutput = message.role == "tool"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isSystem) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = message.content,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp)
                )
            }
        } else if (isToolOutput) {
            val toolNameRaw = message.toolName ?: "MCP Server"
            val parsedId = com.example.core.data.service.ToolIdentifier.parse(toolNameRaw)
            val displayName = if (parsedId != null) {
                "🔧 Tool [${parsedId.toolName}] (Server: ${parsedId.serverId})"
            } else {
                "🔧 Tool output [$toolNameRaw]"
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1A14B8A6)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, TealGlow, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealGlow,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = message.content,
                        fontSize = 12.sp,
                        color = Color(0xFFE5E7EB),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                if (!isUser) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFF1E293B), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🤖", fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) Teal600 else Slate800
                    ),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (message.isToolCall && !message.toolName.isNullOrBlank()) {
                            val toolNameRaw = message.toolName
                            val parsedId = com.example.core.data.service.ToolIdentifier.parse(toolNameRaw)
                            val displayCall = if (parsedId != null) {
                                "⚡ THICKENING REASONING: calling tool '${parsedId.toolName}' on server '${parsedId.serverId}'..."
                            } else {
                                "⚡ THICKENING REASONING: calling tool '$toolNameRaw'..."
                            }
                            Text(
                                text = displayCall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealGlow,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        val messageVal = message.content
                        val hasAttachedImage = messageVal.startsWith("[IMAGE: ") && messageVal.contains("] ")
                        val imageUriStr = if (hasAttachedImage) {
                            val endIdx = messageVal.indexOf("] ")
                            messageVal.substring(8, endIdx)
                        } else null
                        val cleanContentText = if (hasAttachedImage) {
                            val endIdx = messageVal.indexOf("] ")
                            messageVal.substring(endIdx + 2)
                        } else messageVal

                        if (imageUriStr != null) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Slate900),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                coil.compose.AsyncImage(
                                    model = imageUriStr,
                                    contentDescription = "Attached Media Content",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }

                        if (cleanContentText.isNotEmpty()) {
                            MarkdownText(
                                text = cleanContentText,
                                colors = getByokColors("DARK"),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!isUser) {
                            Row(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📋 Copy",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onCopyClick() }
                                )

                                Text(
                                    text = "🔄 Retry",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onRetryClick() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStreamBubble(content: String) {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color(0xFF1E293B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🤖", fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                MarkdownText(
                    text = content,
                    colors = getByokColors("DARK"),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF14B8A6), CircleShape))
                    Box(modifier = Modifier.size(6.dp).background(Color(0x9914B8A6), CircleShape))
                    Box(modifier = Modifier.size(6.dp).background(Color(0x3314B8A6), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun textTagHelper(emoji: String, size: androidx.compose.ui.unit.TextUnit) {
    Text(text = emoji, fontSize = size)
}
