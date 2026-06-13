package com.example.feature.settings.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.core.common.theme.*
import com.example.feature.settings.viewmodel.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageWrapper(
    title: String,
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val colors = getByokColors(themeMode)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.topBarBackground
                )
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content(innerPadding)
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    onNavigateToCloudSync: () -> Unit = {},
    onNavigateToProviders: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToModelSettings: () -> Unit = {},
    onNavigateToAutoCompress: () -> Unit = {},
    onNavigateToDataPort: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {}
) {
    SettingsPageWrapper(
        title = "Settings",
        viewModel = viewModel,
        onBackClick = { navController.popBackStack() }
    ) {
        val syncEnabled by viewModel.supabaseSyncEnabled.collectAsState()
        val isCloudOk by viewModel.isSupabaseConfigured.collectAsState()
        val providersList by viewModel.providers.collectAsState()
        val serversList by viewModel.mcpServers.collectAsState()
        val memoriesList by viewModel.memories.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsOptionCard(
                title = "Cloud Sync",
                subtitle = if (syncEnabled && isCloudOk) "✅ Connected and active" else "Supabase · Not setup yet",
                icon = "☁️",
                onClick = onNavigateToCloudSync
            )

            SettingsOptionCard(
                title = "Providers",
                subtitle = "${providersList.size} configured configurations",
                icon = "🔌",
                onClick = onNavigateToProviders
            )

            SettingsOptionCard(
                title = "MCP Manager",
                subtitle = "${serversList.filter { it.isEnabled }.size} active servers",
                icon = "🔧",
                onClick = onNavigateToMcp
            )

            SettingsOptionCard(
                title = "Memory",
                subtitle = "${memoriesList.size} custom facts saved",
                icon = "🧠",
                onClick = onNavigateToMemory
            )

            SettingsOptionCard(
                title = "Model Settings",
                subtitle = "Temperature, Prompting presets",
                icon = "🤖",
                onClick = onNavigateToModelSettings
            )

            SettingsOptionCard(
                title = "Auto Compress",
                subtitle = "Saves active context windows",
                icon = "📦",
                onClick = onNavigateToAutoCompress
            )

            SettingsOptionCard(
                title = "Data Export/Import",
                subtitle = "Local files backup",
                icon = "💾",
                onClick = onNavigateToDataPort
            )

            SettingsOptionCard(
                title = "Appearance",
                subtitle = "Theme Mode controls",
                icon = "🌙",
                onClick = onNavigateToAppearance
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "BYOK OS v1.0.0 • Native Android • Slate Theme",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SettingsOptionCard(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = subtitle, fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Arrow",
                tint = Color(0xFF475569),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun CloudSyncPage(viewModel: SettingsViewModel) {
    val enabled by viewModel.supabaseSyncEnabled.collectAsState()
    val rawUrl by viewModel.supabaseUrl.collectAsState()
    val rawServiceRoleKey by viewModel.supabaseServiceRoleKey.collectAsState()
    val rawPat by viewModel.supabasePat.collectAsState()
    val logsList by viewModel.supabaseLog.collectAsState()

    var urlInput by remember { mutableStateOf(rawUrl) }
    var serviceRoleKeyInput by remember { mutableStateOf(rawServiceRoleKey) }
    var patInput by remember { mutableStateOf(rawPat) }

    LaunchedEffect(rawUrl) { urlInput = rawUrl }
    LaunchedEffect(rawServiceRoleKey) { serviceRoleKeyInput = rawServiceRoleKey }
    LaunchedEffect(rawPat) { patInput = rawPat }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Enable Cloud Synchronize", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Secure backup with custom Supabase", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
            Switch(
                checked = enabled,
                onCheckedChange = { viewModel.toggleSupabaseSync() },
                colors = SwitchDefaults.colors(checkedThumbColor = TealGlow, checkedTrackColor = Color(0x3314B8A6))
            )
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        Text("Project URL *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealGlow)
        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            placeholder = { Text("https://your-project.supabase.co", color = Color(0xFF64748B)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Slate800,
                unfocusedContainerColor = Slate800,
                focusedBorderColor = TealGlow,
                unfocusedBorderColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Service Role Key *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealGlow)
        OutlinedTextField(
            value = serviceRoleKeyInput,
            onValueChange = { serviceRoleKeyInput = it },
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("your-supabase-service-role-key", color = Color(0xFF64748B)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Slate800,
                unfocusedContainerColor = Slate800,
                focusedBorderColor = TealGlow,
                unfocusedBorderColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Personal Access Token *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealGlow)
        OutlinedTextField(
            value = patInput,
            onValueChange = { patInput = it },
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("sbp_your_secret_pat_key", color = Color(0xFF64748B)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Slate800,
                unfocusedContainerColor = Slate800,
                focusedBorderColor = TealGlow,
                unfocusedBorderColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.triggerSupabaseSetup(urlInput, serviceRoleKeyInput, patInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Teal600),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("⚡ Run Setup", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
            }

            Button(
                onClick = { viewModel.triggerManualCloudSync() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("🔄 Sync & Resolve", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
            }
        }

        if (logsList.isNotEmpty()) {
            Text("Setup Migration Logs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    logsList.forEach { log ->
                        val logColor = when {
                            log.startsWith("SUCCESS") || log.startsWith("DONE") -> EmeraldGlow
                            log.startsWith("ERROR") -> CoralRed
                            else -> Color(0xFF94A3B8)
                        }
                        Text(
                            text = log,
                            color = logColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProvidersPage(viewModel: ProviderViewModel) {
    val providers by viewModel.providers.collectAsState()
    val activeProviderId by viewModel.selectedProviderId.collectAsState()

    var showForm by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://api.openai.com/v1") }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("gpt-4o") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Model Keys & Providers", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = { showForm = !showForm }) {
                Icon(imageVector = if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = "Add", tint = TealGlow)
            }
        }

        AnimatedVisibility(visible = showForm) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Register Model Endpoint", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Label") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Base API Endpoint URL*") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("Bearer Secret Key*") },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Core Model Name*") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (url.isBlank() || key.isBlank() || model.isBlank()) {
                                viewModel.showToast("All fields marked with '*' are required parameters.")
                                return@Button
                            }
                            if (viewModel.addProvider(name, url, key, model)) {
                                name = ""
                                key = ""
                                showForm = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealGlow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add connection", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        providers.forEach { provider ->
            val isActive = provider.id == activeProviderId
            val isUnconfigured = com.example.core.common.ProviderValidator.isUnconfigured(provider.encryptedApiKey)

            var showEditDetailsForm by remember(provider.id) { mutableStateOf(false) }
            var editName by remember(provider.id) { mutableStateOf(provider.displayName) }
            var editUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
            var editModel by remember(provider.id) { mutableStateOf(provider.modelName) }
            var editKey by remember(provider.id) { mutableStateOf(if (isUnconfigured) "" else provider.encryptedApiKey) }
            var testStatusMsg by remember(provider.id) { mutableStateOf<String?>(null) }
            val coroutineScope = rememberCoroutineScope()

            Card(
                colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0x2214B8A6) else Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectProvider(provider.id) }
                    .border(
                        1.dp,
                        if (isUnconfigured) CoralRed else if (isActive) TealGlow else Color(0xFF1E293B),
                        RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (isUnconfigured) CoralRed else if (isActive) TealGlow else Color(0xFF475569), RoundedCornerShape(5.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(provider.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                if (isUnconfigured) {
                                    Box(
                                        modifier = Modifier
                                            .background(CoralRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("MISSING KEY", fontSize = 9.sp, color = CoralRed, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .background(TealGlow.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("READY", fontSize = 9.sp, color = TealGlow, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(provider.baseUrl, fontSize = 11.sp, color = Color(0xFF64748B))
                            Text("Model: ${provider.modelName}", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        }
                        Row {
                            IconButton(onClick = { showEditDetailsForm = !showEditDetailsForm }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Key", tint = Color(0xFF64748B))
                            }
                            IconButton(onClick = { viewModel.deleteProvider(provider.id) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF64748B))
                            }
                        }
                    }

                    AnimatedVisibility(visible = showEditDetailsForm || isUnconfigured) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (isUnconfigured) "⚠️ This preset requires an API Key before it can be used. Please configure it below:" else "Edit Endpoint & Model Parameters:",
                                fontSize = 12.sp,
                                color = if (isUnconfigured) CoralRed else Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Display Label") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editUrl,
                                onValueChange = { editUrl = it },
                                label = { Text("Base API Endpoint URL*") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editModel,
                                onValueChange = { editModel = it },
                                label = { Text("Core Model Name*") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editKey,
                                onValueChange = { editKey = it },
                                label = { Text("Bearer Secret Key*") },
                                placeholder = { Text("Enter API key...") },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (testStatusMsg != null) {
                                Text(
                                    text = testStatusMsg ?: "",
                                    color = if (testStatusMsg?.startsWith("Success") == true) TealGlow else CoralRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (editUrl.isBlank() || editKey.isBlank() || editModel.isBlank()) {
                                            viewModel.showToast("Required fields cannot be blank!")
                                            return@Button
                                        }
                                        coroutineScope.launch {
                                            testStatusMsg = "Testing connection..."
                                            val tempProv = provider.copy(
                                                displayName = editName,
                                                baseUrl = editUrl,
                                                encryptedApiKey = editKey,
                                                modelName = editModel
                                            )
                                            val res = viewModel.testProviderConnection(tempProv)
                                            res.fold(
                                                onSuccess = { testStatusMsg = "Success: Connection validated!" },
                                                onFailure = { err -> testStatusMsg = "Error: ${err.message}" }
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Test", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (editUrl.isBlank() || editKey.isBlank() || editModel.isBlank()) {
                                            viewModel.showToast("Required fields cannot be blank!")
                                            return@Button
                                        }
                                        if (viewModel.updateProviderDetails(provider.id, editName, editUrl, editKey, editModel)) {
                                            showEditDetailsForm = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealGlow),
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    Text("Save Changes", fontWeight = FontWeight.Bold)
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
fun McpManagerPage(viewModel: McpViewModel) {
    val serversList by viewModel.mcpServers.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("HTTP") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Model Context Protocol (MCP)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Connect tools from peripheral endpoints", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
            IconButton(onClick = { showForm = !showForm }) {
                Icon(imageVector = if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = "Add", tint = TealGlow)
            }
        }

        AnimatedVisibility(visible = showForm) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Configure new MCP Host", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server Name") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("Server Base Endpoint URL") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { transport = "HTTP" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (transport == "HTTP") TealGlow else Slate700),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("HTTP REST", color = Color.White)
                        }

                        Button(
                            onClick = { transport = "SSE" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (transport == "SSE") TealGlow else Slate700),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("SSE stream", color = Color.White)
                        }
                    }

                    Button(
                        onClick = {
                            if (name.isBlank() || endpoint.isBlank()) {
                                viewModel.showToast("Compile fields properly first!")
                                return@Button
                            }
                            viewModel.addMcpServer(name, endpoint, transport)
                            name = ""
                            endpoint = ""
                            showForm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealGlow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add MCP Server", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        serversList.forEach { server ->
            val healths by viewModel.mcpServerHealths.collectAsState()
            val health = healths[server.id] ?: com.example.core.data.service.McpHealthStatus.HEALTHY

            val statusColor = if (!server.isEnabled) {
                Color(0xFF64748B)
            } else {
                when (health) {
                    com.example.core.data.service.McpHealthStatus.HEALTHY -> EmeraldGlow
                    com.example.core.data.service.McpHealthStatus.DEGRADED -> Color(0xFFF59E0B)
                    com.example.core.data.service.McpHealthStatus.UNREACHABLE -> CoralRed
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(statusColor, RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(server.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                        
                        Switch(
                            checked = server.isEnabled,
                            onCheckedChange = { viewModel.toggleMcpServer(server) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGlow)
                        )
                    }

                    Text("Endpoint: ${server.endpoint}", fontSize = 12.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(top = 4.dp))
                    Text("Transport Mode: ${server.transport}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF64748B))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val toolsCountText = if (!server.cachedToolsJson.isNullOrBlank()) {
                            try {
                                val len = org.json.JSONArray(server.cachedToolsJson).length()
                                "$len tools loaded"
                            } catch (e: Exception) {
                                "Connected"
                            }
                        } else {
                            if (server.name.lowercase().contains("file")) "3 tools active" else "2 tools active"
                        }

                        val healthText = if (!server.isEnabled) {
                            "Disabled"
                        } else {
                            when (health) {
                                com.example.core.data.service.McpHealthStatus.HEALTHY -> "HEALTHY ($toolsCountText)"
                                com.example.core.data.service.McpHealthStatus.DEGRADED -> "DEGRADED ($toolsCountText)"
                                com.example.core.data.service.McpHealthStatus.UNREACHABLE -> "UNREACHABLE (Circuit Breaker Tripped)"
                            }
                        }

                        val healthTextColor = if (!server.isEnabled) {
                            Color(0xFF64748B)
                        } else {
                            when (health) {
                                com.example.core.data.service.McpHealthStatus.HEALTHY -> EmeraldGlow
                                com.example.core.data.service.McpHealthStatus.DEGRADED -> Color(0xFFF59E0B)
                                com.example.core.data.service.McpHealthStatus.UNREACHABLE -> CoralRed
                            }
                        }

                        Text(
                            text = "Status: $healthText",
                            fontSize = 11.sp,
                            color = healthTextColor,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.deleteMcpServer(server.id) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 8.dp))
        
        Text(
            text = "🛡️ Security Sandbox Settings", 
            fontSize = 15.sp, 
            fontWeight = FontWeight.Bold, 
            color = Color.White
        )
        
        val sandboxNet by viewModel.mcpSandboxNetworkAllowed.collectAsState()
        val sandboxFs by viewModel.mcpSandboxFilesystemAllowed.collectAsState()
        val sandboxNotif by viewModel.mcpSandboxNotificationsAllowed.collectAsState()
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sandbox Network Outbound", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Permit API & network requests", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                    Switch(
                        checked = sandboxNet,
                        onCheckedChange = { viewModel.updateMcpSandboxPermissions(it, sandboxFs, sandboxNotif) },
                        colors = SwitchDefaults.colors(checkedThumbColor = TealGlow)
                    )
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sandbox File System Actions", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Permit writing files to local disk sandbox", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                    Switch(
                        checked = sandboxFs,
                        onCheckedChange = { viewModel.updateMcpSandboxPermissions(sandboxNet, it, sandboxNotif) },
                        colors = SwitchDefaults.colors(checkedThumbColor = TealGlow)
                    )
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sandbox Local Notifications", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Permit triggering native user info overlays", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                    Switch(
                        checked = sandboxNotif,
                        onCheckedChange = { viewModel.updateMcpSandboxPermissions(sandboxNet, sandboxFs, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = TealGlow)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 8.dp))
        
        Text(
            text = "📈 Live Execution Logs & Action History", 
            fontSize = 15.sp, 
            fontWeight = FontWeight.Bold, 
            color = Color.White
        )

        val mcpLogs by viewModel.mcpExecutionLogs.collectAsState()

        if (mcpLogs.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active tool calls registered in current session.", 
                        fontSize = 12.sp, 
                        color = Color(0xFF64748B), 
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                mcpLogs.forEach { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = log.toolName, 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 13.sp, 
                                        color = TealGlow,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${log.serverName} • ${log.durationMs}ms", 
                                        fontSize = 11.sp, 
                                        color = Color(0xFF64748B)
                                    )
                                }
                                
                                val statusColor = when (log.status) {
                                    "SUCCESS" -> EmeraldGlow
                                    "RETRYING" -> Color(0xFFF59E0B)
                                    "SANDBOX_BLOCKED" -> Color(0xFFEF4444)
                                    else -> CoralRed
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = log.status,
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Args: ${log.arguments}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Result: ${log.outputPreview}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF475569),
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )

                            if (!log.error.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Error: ${log.error}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CoralRed
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
fun MemoryPage(viewModel: SettingsViewModel) {
    val memoryList by viewModel.memories.collectAsState()
    var manualInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Memory & Personalization", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("We automatically parse your statements and extract preference entities. Here is what your local memory knows:", fontSize = 12.sp, color = Color(0xFF94A3B8))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                placeholder = { Text("Type custom fact, preferences...", color = Color(0xFF475569)) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (manualInput.isBlank()) return@IconButton
                    viewModel.addManualMemory(manualInput)
                    manualInput = ""
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(TealGlow, RoundedCornerShape(12.dp))
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Save", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Saved Facts (${memoryList.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Clear Archive",
                fontSize = 12.sp,
                color = CoralRed,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { viewModel.clearAllMemories() }
            )
        }

        memoryList.forEach { mem ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (mem.type) {
                            "preference" -> "💼"
                            "context" -> "🌍"
                            else -> "👤"
                        },
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(mem.content, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("System Prompt Memory Preview", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealGlow)
                Text(
                    text = "User context memories:\n" + if (memoryList.isNotEmpty()) {
                        memoryList.joinToString("\n") { "- " + it.content }
                    } else "- No memory units parsed yet.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ModelSettingsPage(viewModel: SettingsViewModel) {
    val temp by viewModel.temperature.collectAsState()
    val maxToks by viewModel.maxTokens.collectAsState()
    val rawPrompt by viewModel.systemPrompt.collectAsState()
    val topP by viewModel.topP.collectAsState()
    val topK by viewModel.topK.collectAsState()
    val presencePenalty by viewModel.presencePenalty.collectAsState()
    val frequencyPenalty by viewModel.frequencyPenalty.collectAsState()

    var tempInput by remember { mutableStateOf(temp) }
    var maxToksInput by remember { mutableStateOf(maxToks.toString()) }
    var systemPromptInput by remember { mutableStateOf(rawPrompt) }
    var topPInput by remember { mutableStateOf(topP) }
    var topKInput by remember { mutableStateOf(topK.toFloat()) }
    var presencePenaltyInput by remember { mutableStateOf(presencePenalty) }
    var frequencyPenaltyInput by remember { mutableStateOf(frequencyPenalty) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Hyperparameters Tune-up", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Temperature", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${"%.2f".format(tempInput)}", fontSize = 13.sp, color = TealGlow, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = tempInput,
                onValueChange = { tempInput = it },
                valueRange = 0f..2f,
                steps = 20,
                colors = SliderDefaults.colors(thumbColor = TealGlow, activeTrackColor = Teal600)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Top P (Nucleus Sampling)", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${"%.2f".format(topPInput)}", fontSize = 13.sp, color = TealGlow, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = topPInput,
                onValueChange = { topPInput = it },
                valueRange = 0f..1f,
                steps = 20,
                colors = SliderDefaults.colors(thumbColor = TealGlow, activeTrackColor = Teal600)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Top K", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${topKInput.toInt()}", fontSize = 13.sp, color = TealGlow, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = topKInput,
                onValueChange = { topKInput = it },
                valueRange = 1f..100f,
                steps = 99,
                colors = SliderDefaults.colors(thumbColor = TealGlow, activeTrackColor = Teal600)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Presence Penalty (Topic Variety)", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${"%.2f".format(presencePenaltyInput)}", fontSize = 13.sp, color = TealGlow, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = presencePenaltyInput,
                onValueChange = { presencePenaltyInput = it },
                valueRange = -2f..2f,
                steps = 40,
                colors = SliderDefaults.colors(thumbColor = TealGlow, activeTrackColor = Teal600)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Frequency Penalty", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${"%.2f".format(frequencyPenaltyInput)}", fontSize = 13.sp, color = TealGlow, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = frequencyPenaltyInput,
                onValueChange = { frequencyPenaltyInput = it },
                valueRange = -2f..2f,
                steps = 40,
                colors = SliderDefaults.colors(thumbColor = TealGlow, activeTrackColor = Teal600)
            )
        }

        Text("Max Token Constraints", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = maxToksInput,
            onValueChange = { maxToksInput = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Global System Prompt Template", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = systemPromptInput,
            onValueChange = { systemPromptInput = it },
            minLines = 4,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                viewModel.updateSystemParameters(
                    systemPromptInput,
                    tempInput,
                    maxToksInput.toIntOrNull() ?: 4096,
                    topPInput,
                    topKInput.toInt(),
                    presencePenaltyInput,
                    frequencyPenaltyInput
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealGlow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update boundary changes", color = Color.White)
        }
    }
}

@Composable
fun AutoCompressPage(viewModel: SettingsViewModel) {
    val enabled by viewModel.autoCompressEnabled.collectAsState()
    val threshold by viewModel.compressThreshold.collectAsState()
    val keepLast by viewModel.keepLastN.collectAsState()

    var enabledInput by remember { mutableStateOf(enabled) }
    var thresholdInput by remember { mutableStateOf(threshold) }
    var keepInput by remember { mutableStateOf(keepLast.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Enable Background Auto Compress", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Saves memory history automatically if threshold is exceeded", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
            Switch(
                checked = enabledInput,
                onCheckedChange = { enabledInput = it },
                colors = SwitchDefaults.colors(checkedThumbColor = TealGlow)
            )
        }

        Text("Trigger Threshold: ${(thresholdInput * 100).toInt()}% of context scale", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
        Slider(
            value = thresholdInput,
            onValueChange = { thresholdInput = it },
            valueRange = 0.5f..1f,
            colors = SliderDefaults.colors(thumbColor = TealGlow, activeTrackColor = Teal600)
        )

        Text("Keep Last N Messages Uncompressed", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = keepInput,
            onValueChange = { keepInput = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                viewModel.updateCompressionParameters(
                    enabledInput,
                    thresholdInput,
                    keepInput.toIntOrNull() ?: 20
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = TealGlow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update behaviors", color = Color.White)
        }
    }
}

@Composable
fun DataPortPage(viewModel: SettingsViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var exportResultJson by remember { mutableStateOf("") }
    var importInputJson by remember { mutableStateOf("") }

    val themeMode by viewModel.themeMode.collectAsState()
    val colors = getByokColors(themeMode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Backup & Database Porting",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = colors.textPrimary
        )
        Text(
            text = "BYOK OS stores every communication stream, personalized model credential, and MCP server configuration securely inside local client tables. Export full JSON archives cleanly, or type/paste backups to restore them.",
            color = colors.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Export Backup Document", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("Prepares a serialized JSON string containing all configured provider list keys, user system settings, memory archives, and session histories.", fontSize = 11.sp, color = colors.textSecondary)
                
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val backup = viewModel.exportDatabaseBackup()
                            exportResultJson = backup
                            if (backup.isNotEmpty()) {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("BYOK_OS_Backup", backup)
                                clipboard.setPrimaryClip(clip)
                                viewModel.showToast("Backup generated and copied to device clipboard!")
                            } else {
                                viewModel.showToast("Failed to compile database states.")
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📤 Generate & Copy JSON Backup", color = Color.White, fontWeight = FontWeight.Bold)
                }

                if (exportResultJson.isNotEmpty()) {
                    Text("Result Backup String Preview (Copied):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primaryAccent)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(colors.background, RoundedCornerShape(8.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        Text(
                            text = exportResultJson,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Restore JSON Backup Data", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("Restore keys, MCP options, sessions, and memory profiles by pasting a valid previously exported BYOK backup payload below.", fontSize = 11.sp, color = colors.textSecondary)

                OutlinedTextField(
                    value = importInputJson,
                    onValueChange = { importInputJson = it },
                    placeholder = { Text("Paste JSON string here...", color = colors.textPlaceholder) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colors.textPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.fieldBackground,
                        unfocusedContainerColor = colors.fieldBackground,
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.border
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = {
                        if (importInputJson.isBlank()) {
                            viewModel.showToast("Paste a valid JSON back-up node first!")
                            return@Button
                        }
                        coroutineScope.launch {
                            val success = viewModel.importDatabaseBackup(importInputJson)
                            if (success) {
                                viewModel.showToast("✅ Database restored cleanly! Loaded state successfully.")
                                importInputJson = ""
                            } else {
                                viewModel.showToast("❌ Refused: Payload contains malformed tables or invalid structures.")
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📥 Validate & Restore Backup Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AppearancePage(viewModel: SettingsViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Theme Mode UI Controls", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

        listOf("DARK", "LIGHT", "SYSTEM SLATE").forEach { mode ->
            val isSelected = themeMode == mode
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0x3314B8A6) else Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setThemeMode(mode) }
                    .border(1.dp, if (isSelected) TealGlow else Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(mode, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.setThemeMode(mode) },
                        colors = RadioButtonDefaults.colors(selectedColor = TealGlow)
                    )
                }
            }
        }
    }
}
