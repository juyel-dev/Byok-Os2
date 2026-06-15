package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.koinViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.feature.chat.ui.WelcomeScreen
import com.example.feature.chat.ui.ChatScreen
import com.example.feature.chat.ui.AgentScreen
import com.example.feature.settings.ui.SettingsScreen
import com.example.feature.settings.ui.SettingsPageWrapper
import com.example.feature.settings.ui.CloudSyncPage
import com.example.feature.settings.ui.ProvidersPage
import com.example.feature.settings.ui.McpManagerPage
import com.example.feature.settings.ui.MemoryPage
import com.example.feature.settings.ui.ModelSettingsPage
import com.example.feature.settings.ui.AutoCompressPage
import com.example.feature.settings.ui.AppearancePage
import com.example.feature.settings.ui.DataPortPage
import com.example.ui.navigation.Screen
import com.example.feature.chat.viewmodel.ChatViewModel
import com.example.feature.chat.viewmodel.AgentViewModel
import com.example.feature.chat.viewmodel.NavigationEvent
import com.example.feature.settings.viewmodel.SettingsViewModel
import com.example.feature.settings.viewmodel.McpViewModel
import com.example.feature.settings.viewmodel.ProviderViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val settingsViewModel: SettingsViewModel = koinViewModel()
      val themeMode by settingsViewModel.themeMode.collectAsState()
      val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
      val isDark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemDark
      }

      MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
        val chatViewModel: ChatViewModel = koinViewModel()
        val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsState()
        val colors = com.example.ui.theme.getByokColors(themeMode)

        val navController = rememberNavController()

        // Core ViewModel-Driven Navigation Event Handling
        LaunchedEffect(chatViewModel.navigationEvents) {
          chatViewModel.navigationEvents.collect { event ->
            when (event) {
              is NavigationEvent.NavigateToProviders -> {
                navController.navigate(Screen.Providers.route)
              }
            }
          }
        }

        // Placeholder for Android system deep link handling
        LaunchedEffect(intent) {
          intent?.data?.let { uri ->
            // Android platform deep link routing can be printed and inspected here
            android.util.Log.d("BYOK_Navigation", "Deep link matching detected custom URI: $uri")
          }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background
        ) { innerPadding ->
          NavHost(
            navController = navController,
            startDestination = if (onboardingCompleted) Screen.Chat.route else Screen.Welcome.route
          ) {
            // S1 - Onboarding Welcome Screen
            composable(
              route = Screen.Welcome.route,
              deepLinks = Screen.Welcome.deepLinks
            ) {
              WelcomeScreen(
                viewModel = settingsViewModel,
                modifier = Modifier.padding(innerPadding),
                onOnboardingComplete = {
                  navController.navigate(Screen.Chat.route) {
                    popUpTo(Screen.Welcome.route) { inclusive = true }
                  }
                }
              )
            }

            // S2 - Chat Hub Screen
            composable(
              route = Screen.Chat.route,
              deepLinks = Screen.Chat.deepLinks
            ) {
              ChatScreen(
                viewModel = chatViewModel,
                themeModeStr = themeMode,
                modifier = Modifier.padding(innerPadding),
                onNavigateToSettings = {
                  navController.navigate(Screen.SettingsHome.route)
                },
                onNavigateToAgent = {
                  navController.navigate(Screen.Agent.route)
                },
                onNavigateToProviders = {
                  navController.navigate(Screen.Providers.route)
                }
              )
            }

            // S3 - Settings Root Screen
            composable(
              route = Screen.SettingsHome.route,
              deepLinks = Screen.SettingsHome.deepLinks
            ) {
              SettingsScreen(
                viewModel = settingsViewModel,
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                onNavigateToCloudSync = { navController.navigate(Screen.CloudSync.route) },
                onNavigateToProviders = { navController.navigate(Screen.Providers.route) },
                onNavigateToMcp = { navController.navigate(Screen.McpManager.route) },
                onNavigateToMemory = { navController.navigate(Screen.Memory.route) },
                onNavigateToModelSettings = { navController.navigate(Screen.ModelSettings.route) },
                onNavigateToAutoCompress = { navController.navigate(Screen.AutoCompress.route) },
                onNavigateToDataPort = { navController.navigate(Screen.DataPort.route) },
                onNavigateToAppearance = { navController.navigate(Screen.Appearance.route) }
              )
            }

            // S3.1 - Settings Subpages Managed by Type-Safe compose wrapper
            composable(
              route = Screen.CloudSync.route,
              deepLinks = Screen.CloudSync.deepLinks
            ) {
              SettingsPageWrapper(
                title = "Cloud Sync",
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
              ) {
                CloudSyncPage(viewModel = settingsViewModel)
              }
            }

            composable(
              route = Screen.Providers.route,
              deepLinks = Screen.Providers.deepLinks
            ) {
              val providerViewModel: ProviderViewModel = koinViewModel()
              SettingsPageWrapper(
                title = "Providers",
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
              ) {
                ProvidersPage(viewModel = providerViewModel)
              }
            }

            composable(
              route = Screen.McpManager.route,
              deepLinks = Screen.McpManager.deepLinks
            ) {
              val mcpViewModel: McpViewModel = koinViewModel()
              SettingsPageWrapper(
                title = "MCP Manager",
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
              ) {
                McpManagerPage(viewModel = mcpViewModel)
              }
            }

            composable(
              route = Screen.Memory.route,
              deepLinks = Screen.Memory.deepLinks
            ) {
              SettingsPageWrapper(
                title = "Memory",
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
              ) {
                MemoryPage(viewModel = settingsViewModel)
              }
            }

            composable(
              route = Screen.ModelSettings.route,
              deepLinks = Screen.ModelSettings.deepLinks
            ) {
              SettingsPageWrapper(
                title = "Model Settings",
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
              ) {
                ModelSettingsPage(viewModel = settingsViewModel)
              }
            }

            composable(
              route = Screen.AutoCompress.route,
              deepLinks = Screen.AutoCompress.deepLinks
            ) {
              SettingsPageWrapper(
                title = "Auto Compress",
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
              ) {
                AutoCompressPage(viewModel = settingsViewModel)
              }
            }

            composable(
              route = Screen.Appearance.route,
              deepLinks = Screen.Appearance.deepLinks
            ) {
              SettingsPageWrapper(
                title = "Appearance",
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
              ) {
                AppearancePage(viewModel = settingsViewModel)
              }
            }

            composable(
              route = Screen.DataPort.route,
              deepLinks = Screen.DataPort.deepLinks
            ) {
              SettingsPageWrapper(
                title = "Data Export/Import",
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
              ) {
                DataPortPage(viewModel = settingsViewModel)
              }
            }

            // S4 - Autonomous AI ReAct Agent
            composable(
              route = Screen.Agent.route,
              deepLinks = Screen.Agent.deepLinks
            ) {
              val agentViewModel: AgentViewModel = koinViewModel()
              AgentScreen(
                viewModel = agentViewModel,
                themeModeStr = themeMode,
                modifier = Modifier.padding(innerPadding),
                onBackClick = {
                  navController.popBackStack()
                }
              )
            }
          }
        }
      }
    }
  }
}
