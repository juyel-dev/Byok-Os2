package com.example.feature.chat.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.theme.*
import com.example.feature.chat.viewmodel.AgentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: AgentViewModel,
    themeModeStr: String = "DARK",
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val agentActive by viewModel.agentActive.collectAsState()
    val goal by viewModel.agentGoal.collectAsState()
    val status by viewModel.agentStatus.collectAsState()
    val logs by viewModel.agentLogs.collectAsState()
    val stepsCount by viewModel.agentStepCount.collectAsState()
    val paused by viewModel.agentPaused.collectAsState()

    var goalInput by remember { mutableStateOf("") }
    val colors = getByokColors(themeModeStr)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Autonomous ReAct Agent",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBackClick() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!agentActive && status == "Idle") {
                // Goal Setup space
                Text(
                    text = "Configure Autonomous Agent Goal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "The agent executes a continuous Thinking-Action ReAct loop, automatically discovering and calling registered MCP local filesystem, research or weather tools sequentially until the target boundary is cleared.",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp
                )

                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    placeholder = { Text("e.g. Research Kotlin news and write data summary to a file", color = colors.textPlaceholder) },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = colors.fieldBackground,
                        unfocusedContainerColor = colors.fieldBackground,
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.border
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (goalInput.isBlank()) return@Button
                        viewModel.triggerAgentGoal(goalInput)
                        goalInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("🚀 Wakeup Agent & Execute Goal", fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                // Active Execution Space
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Goal Statement",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryAccent
                        )
                        Text(
                            text = goal,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        HorizontalDivider(color = colors.border)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("AGENT STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textPlaceholder)
                                Text(
                                    text = if (paused) "Paused" else "Executing: $status",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (paused) CoralRed else colors.primaryAccent
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("STEPS EXECUTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textPlaceholder)
                                Text(
                                    text = "$stepsCount / 5 max",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                        }

                        // Agent action buttons loop: Pause/resume, stop
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.pauseAgent() },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.buttonBackground),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (paused) "▶️ Resume" else "⏸️ Pause", color = colors.textPrimary)
                            }

                            Button(
                                onClick = { viewModel.stopAgent() },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🛑 Terminate", color = Color.White)
                            }
                        }
                    }
                }

                Text(
                    text = "Agent Execution Logs Timeline",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(colors.background, RoundedCornerShape(12.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs) { log ->
                        val isFlowIn = log.startsWith("➡️") || log.startsWith("[Step")
                        val textColor = when {
                            log.startsWith("🎯") -> colors.primaryAccent
                            log.startsWith("⏸️") || log.startsWith("🛑") -> CoralRed
                            log.startsWith("➡️ Output:") -> EmeraldGlow
                            else -> colors.textSecondary
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = log,
                                color = textColor,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontFamily = if (isFlowIn) FontFamily.Monospace else FontFamily.SansSerif,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                if (status == "Completed" || status == "Stopped") {
                    Button(
                        onClick = { viewModel.triggerAgentGoal("Restart") /* Reset State */ },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restart")
                            Text("Configure New Goal Tasks", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
