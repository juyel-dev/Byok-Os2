package com.example.feature.chat.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.domain.models.LlmProviderModel
import com.example.core.common.theme.EmeraldGlow
import com.example.core.common.theme.TealGlow
import com.example.feature.settings.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onOnboardingComplete: () -> Unit = {}
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val colors = com.example.core.common.theme.getByokColors(themeMode)
    var onboardingStep by remember { mutableStateOf(1) } // 1: Welcome message, 2: Insert provider state

    // Form states
    var displayName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("gpt-4o") }

    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        // Aesthetic ambient aura background
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-40).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.primaryAccent.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        AnimatedContent(
            targetState = onboardingStep,
            transitionSpec = {
                (slideInHorizontally { width -> width / 2 } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width / 2 } + fadeOut()
                )
            },
            label = "OnboardingTransition"
        ) { step ->
            when (step) {
                1 -> {
                    // S01 - Welcome screen
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(32.dp)),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "✦",
                                    fontSize = 42.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "BYOK OS",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            color = colors.textPrimary,
                            letterSpacing = (-0.5).sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Bring Your Own Key\nAI Operating System",
                            fontSize = 16.sp,
                            color = colors.textSecondary,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(56.dp))

                        Button(
                            onClick = { onboardingStep = 2 },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Get Started",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "ForwardArrow",
                                    tint = Color.White,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Your keys. Your data. Your AI.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                2 -> {
                    // S02 - Add First Provider Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = "Add Your First Provider",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Configure your proprietary LLM credentials here. We never store or log your personal access tokens.",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                        )

                        Text(
                            text = "Display Name (optional)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryAccent
                        )
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            placeholder = { Text("e.g. My Provider", color = colors.textPlaceholder) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedContainerColor = colors.fieldBackground,
                                unfocusedContainerColor = colors.fieldBackground,
                                focusedBorderColor = colors.primaryAccent,
                                unfocusedBorderColor = colors.border
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Base URL *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryAccent
                        )
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            placeholder = { Text("https://api.openai.com/v1", color = colors.textPlaceholder) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedContainerColor = colors.fieldBackground,
                                unfocusedContainerColor = colors.fieldBackground,
                                focusedBorderColor = colors.primaryAccent,
                                unfocusedBorderColor = colors.border
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "API Key *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryAccent
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            placeholder = { Text("Secret proprietary sk-key...", color = colors.textPlaceholder) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedContainerColor = colors.fieldBackground,
                                unfocusedContainerColor = colors.fieldBackground,
                                focusedBorderColor = colors.primaryAccent,
                                unfocusedBorderColor = colors.border
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Model Name *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryAccent
                        )
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            placeholder = { Text("gpt-4o", color = colors.textPlaceholder) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedContainerColor = colors.fieldBackground,
                                unfocusedContainerColor = colors.fieldBackground,
                                focusedBorderColor = colors.primaryAccent,
                                unfocusedBorderColor = colors.border
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        )

                        // Connection tester log banner
                        testStatusMessage?.let { status ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .border(1.dp, if (status.startsWith("Success")) EmeraldGlow else Color(0xFFEF4444), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (status.startsWith("Success")) "🟢" else "🔴",
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = status,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (status.startsWith("Success")) EmeraldGlow else Color(0xFFEF4444)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (apiKey.isBlank() || baseUrl.isBlank()) {
                                        testStatusMessage = "Fail: API Key and Base URL cannot be empty."
                                        return@OutlinedButton
                                    }
                                    coroutineScope.launch {
                                        isTesting = true
                                        testStatusMessage = "Testing connection endpoint..."
                                        val tempProvider = LlmProviderModel(
                                             id = java.util.UUID.randomUUID().toString(),
                                            displayName = "Temporary Test",
                                            baseUrl = baseUrl,
                                            encryptedApiKey = apiKey,
                                            modelName = modelName
                                        )
                                        val testRes = viewModel.testProviderConnection(tempProvider)
                                        isTesting = false
                                        testRes.onSuccess {
                                            testStatusMessage = "Success: Endpoint validation validated correctly."
                                        }.onFailure {
                                            testStatusMessage = "Fail: ${it.localizedMessage ?: "Connection failure"}"
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isTesting,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primaryAccent),
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.primaryAccent)
                                } else {
                                    Text("Test Endpoint", fontSize = 14.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    if (apiKey.isBlank() || baseUrl.isBlank()) {
                                        viewModel.showToast("API Key and Base URL are required parameters.")
                                        return@Button
                                    }
                                    // Save the new connection and complete onboarding if validation succeeded!
                                    if (viewModel.addProvider(displayName, baseUrl, apiKey, modelName)) {
                                        viewModel.completeOnboarding()
                                        onOnboardingComplete()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Save & Continue", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = " arrow", modifier = Modifier.size(16.dp), tint = Color.White)
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                viewModel.completeOnboarding()
                                onOnboardingComplete()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Skip for now", color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
