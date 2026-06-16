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
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Bundle
import java.util.Locale
import android.content.ActivityNotFoundException
import android.app.Activity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    themeModeStr: String = "DARK",
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAgent: () -> Unit = {},
    onNavigateToProviders: () -> Unit = {}
) {
    val colors = getByokColors(themeModeStr)
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

    var isListening by remember { mutableStateOf(false) }
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    
    val speechRecognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toString())
        }
    }

    val systemSpeechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now / কথা বলুন...")
        }
    }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                chatInputText = matches[0]
                viewModel.showToast("Speech recognized successfully!")
            }
        }
    }

    val launchSystemSpeechInput: () -> Unit = {
        try {
            systemSpeechLauncher.launch(systemSpeechIntent)
        } catch (e: ActivityNotFoundException) {
            viewModel.showToast("Speech Recognition not supported on this device.")
        }
    }

    val speechListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                val errMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client/Connection error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input timeout"
                    else -> "Speech recognition error"
                }
                viewModel.showToast("$errMsg - launching system voice keyboard")
                launchSystemSpeechInput()
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    chatInputText = matches[0]
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    chatInputText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(speechListener)
        onDispose {
            speechRecognizer.destroy()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.showToast("Microphone permission granted! Tap to speak.")
        } else {
            viewModel.showToast("Microphone permission denied.")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.background,
                drawerContentColor = colors.textPrimary,
                modifier = Modifier
                    .width(300.dp)
                    .border(2.dp, colors.border, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Drawer Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 24.dp, top = 8.dp)
                    ) {
                        Text(
                            text = "✧ BYOK OS",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = colors.primaryAccent,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Button(
                        onClick = {
                            viewModel.createNewChat()
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryAccent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Conversation", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Text(
                        text = "RECENTS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // History list
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sessions) { s ->
                            val isSelected = s.id == currentSessionId
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) colors.cardBackground else Color.Transparent
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectSession(s.id)
                                        coroutineScope.launch { drawerState.close() }
                                    }
                                    .then(
                                        if (isSelected) Modifier.border(1.dp, colors.primaryAccent, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MailOutline,
                                        contentDescription = "Chat",
                                        tint = if (isSelected) colors.primaryAccent else colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = s.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        IconButton(
                                            onClick = { viewModel.deleteSession(s.id) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = colors.textPlaceholder,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = colors.border, 
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Bottom Navigation triggers (Settings Page & Autonomous Agent Screen)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onNavigateToSettings()
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.fieldBackground,
                                contentColor = colors.textPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(15.dp), tint = colors.textSecondary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                onNavigateToAgent()
                                coroutineScope.launch { drawerState.close() }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryAccent.copy(alpha = 0.15f),
                                contentColor = colors.primaryAccent
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(46.dp).border(1.dp, colors.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Agent", modifier = Modifier.size(15.dp), tint = colors.primaryAccent)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Agent", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable(enabled = !isStreaming) { showProviderDropdown = !showProviderDropdown }
                                    .background(
                                        if (isStreaming) colors.fieldBackground.copy(alpha = 0.5f)
                                        else colors.fieldBackground
                                    )
                                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .heightIn(min = 36.dp)
                                    .testTag("provider_switcher_button"),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isStreaming) Icons.Default.Lock else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Dropdown Indicator",
                                    tint = if (isStreaming) colors.textPlaceholder else colors.primaryAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Model: ${activeProvider?.displayName ?: "None"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStreaming) colors.textPlaceholder else colors.textPrimary
                                )

                                DropdownMenu(
                                    expanded = showProviderDropdown,
                                    onDismissRequest = { showProviderDropdown = false },
                                    modifier = Modifier
                                        .background(colors.cardBackground)
                                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
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
                                                            tint = colors.primaryAccent,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = provider.displayName,
                                                        color = if (isSelected) colors.primaryAccent else colors.textPrimary,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.selectProvider(provider.id)
                                                showProviderDropdown = false
                                            }
                                        )
                                    }
                                    HorizontalDivider(color = colors.border)
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = "＋ Manage Keys", 
                                                color = colors.primaryAccent, 
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            ) 
                                        },
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
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "MenuToggle", tint = colors.textPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onNavigateToSettings() }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = colors.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background
                    )
                )
            },
            containerColor = colors.background
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
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .border(1.dp, colors.primaryAccent.copy(alpha = 0.4f), CircleShape),
                            color = colors.primaryAccent.copy(alpha = 0.15f),
                            contentColor = colors.primaryAccent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                textTagHelper("✧", 42.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Intelligent AI Engine",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Connect custom API keys, enable local MCP tools, and deploy self-contained prompts.",
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp)
                        )

                        // Preset Prompts
                        listOf("Explain modern state management patterns", "Simulate read_file using MCP filesystem", "Synthesize memory context logic").forEach { prompt ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (currentSessionId == null) {
                                            viewModel.createNewChat()
                                        }
                                        chatInputText = prompt
                                    }
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 14.sp,
                                    color = colors.primaryAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
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
                                themeModeStr = themeModeStr,
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
                                MessageStreamBubble(content = streamingContent, themeModeStr = themeModeStr)
                            }
                        }
                    }
                }

                // Chat Input controllers
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background)
                ) {
                    HorizontalDivider(color = colors.border)

                    if (isProcessingImage) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.fieldBackground)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = colors.primaryAccent,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Evaluating optical assets...",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        HorizontalDivider(color = colors.border)
                    }

                    if (attachedImageUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.fieldBackground)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                                        .background(colors.background),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Ready to upload: Image captured",
                                    fontSize = 13.sp,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearAttachedImage() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Attachment",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = colors.border)
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
                                .size(44.dp)
                                .testTag("attachment_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Attach Image",
                                tint = colors.primaryAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        OutlinedTextField(
                            value = chatInputText,
                            onValueChange = { chatInputText = it },
                            placeholder = { Text("Ask something...", color = colors.textPlaceholder, fontSize = 14.sp) },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedContainerColor = colors.fieldBackground,
                                unfocusedContainerColor = colors.fieldBackground,
                                focusedBorderColor = colors.primaryAccent,
                                unfocusedBorderColor = colors.border
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.RECORD_AUDIO
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        
                                        if (hasMicPermission) {
                                            if (isListening) {
                                                try {
                                                    speechRecognizer.stopListening()
                                                } catch (e: Exception) {
                                                    // ignore
                                                }
                                                isListening = false
                                            } else {
                                                try {
                                                    speechRecognizer.startListening(speechRecognizerIntent)
                                                    isListening = true
                                                } catch (e: Exception) {
                                                    isListening = false
                                                    launchSystemSpeechInput()
                                                }
                                            }
                                        } else {
                                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).testTag("voice_input_button")
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.Close else Icons.Default.Mic,
                                        contentDescription = "Voice Input",
                                        tint = if (isListening) Color.Red else colors.primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
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
                                .size(40.dp)
                                .background(colors.primaryAccent, CircleShape)
                                .testTag("send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
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
                            color = if ((activeMcpToolsCountVal ?: 0) > 0) EmeraldGlow else colors.textPlaceholder
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: Message,
    themeModeStr: String = "DARK",
    onCopyClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"
    val isToolOutput = message.role == "tool"
    val colors = getByokColors(themeModeStr)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isSystem) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.fieldBackground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📋 SYSTEM CONTEXT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = colors.primaryAccent.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = message.content,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        } else if (isToolOutput) {
            val toolNameRaw = message.toolName ?: "MCP Server"
            val parsedId = com.example.core.data.service.ToolIdentifier.parse(toolNameRaw)
            val displayName = if (parsedId != null) {
                "🔧 MCP Tool Executed [${parsedId.toolName}] (Server: ${parsedId.serverId})"
            } else {
                "🔧 MCP Tool Executed [$toolNameRaw]"
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.fieldBackground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryAccent,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message.content,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(0.88f)
            ) {
                if (!isUser) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(colors.primaryAccent.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, colors.primaryAccent.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✦",
                            fontSize = 15.sp,
                            color = colors.primaryAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                val currentBubbleColors = if (isUser) {
                    colors.copy(
                        textPrimary = Color.White,
                        textSecondary = Color.White.copy(alpha = 0.82f),
                        border = Color.White.copy(alpha = 0.3f),
                        fieldBackground = Color.White.copy(alpha = 0.15f),
                        cardBackground = Color.White.copy(alpha = 0.15f)
                    )
                } else {
                    colors
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) colors.primaryAccent else colors.cardBackground
                    ),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    ),
                    modifier = Modifier.then(
                        if (!isUser) Modifier.border(1.dp, colors.border, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp))
                        else Modifier
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (message.isToolCall && !message.toolName.isNullOrBlank()) {
                            val toolNameRaw = message.toolName
                            val parsedId = com.example.core.data.service.ToolIdentifier.parse(toolNameRaw)
                            val displayCall = if (parsedId != null) {
                                "⚡ Call MCP Tool '${parsedId.toolName}' on server '${parsedId.serverId}'..."
                            } else {
                                "⚡ Call MCP Tool '$toolNameRaw'..."
                            }
                            Text(
                                text = displayCall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) Color.White else colors.primaryAccent,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 6.dp)
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
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isUser) Color.White.copy(alpha = 0.15f) else colors.background),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .border(1.dp, if (isUser) Color.White.copy(alpha = 0.3f) else colors.border, RoundedCornerShape(10.dp))
                            ) {
                                coil.compose.AsyncImage(
                                    model = imageUriStr,
                                    contentDescription = "Attached Media Content",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }

                        if (cleanContentText.isNotEmpty()) {
                            MarkdownText(
                                text = cleanContentText,
                                colors = currentBubbleColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (!isUser) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Elegant Action copy pill
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.fieldBackground)
                                        .clickable { onCopyClick() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📋 Copy",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.fieldBackground)
                                        .clickable { onRetryClick() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🔄 Retry",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStreamBubble(content: String, themeModeStr: String = "DARK") {
    val colors = getByokColors(themeModeStr)
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(colors.primaryAccent.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, colors.primaryAccent.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦",
                fontSize = 15.sp,
                color = colors.primaryAccent,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 2.dp,
                bottomEnd = 16.dp
            ),
            modifier = Modifier.border(
                1.dp,
                colors.primaryAccent.copy(alpha = 0.25f),
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 2.dp,
                    bottomEnd = 16.dp
                )
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                MarkdownText(
                    text = content,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(colors.primaryAccent, CircleShape))
                    Box(modifier = Modifier.size(6.dp).background(colors.primaryAccent.copy(alpha=0.6f), CircleShape))
                    Box(modifier = Modifier.size(6.dp).background(colors.primaryAccent.copy(alpha=0.2f), CircleShape))
                }
            }
        }
    }
}

@Composable
private fun textTagHelper(emoji: String, size: androidx.compose.ui.unit.TextUnit) {
    Text(text = emoji, fontSize = size)
}
