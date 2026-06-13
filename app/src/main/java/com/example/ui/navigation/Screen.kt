package com.example.ui.navigation

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavDeepLink
import androidx.navigation.navDeepLink

sealed class Screen(
    val route: String,
    val arguments: List<NamedNavArgument> = emptyList(),
    val deepLinks: List<NavDeepLink> = emptyList()
) {
    object Welcome : Screen("welcome")
    object Chat : Screen(
        route = "chat",
        deepLinks = listOf(
            navDeepLink {
                uriPattern = "byok://chat"
            }
        )
    )
    object SettingsHome : Screen("settings_home")
    object CloudSync : Screen("cloud_sync")
    object Providers : Screen("providers")
    object McpManager : Screen("mcp_manager")
    object Memory : Screen("memory")
    object ModelSettings : Screen("model_settings")
    object AutoCompress : Screen("auto_compress")
    object Appearance : Screen("appearance")
    object DataPort : Screen(
        route = "data_port",
        deepLinks = listOf(
            navDeepLink {
                uriPattern = "byok://import_export"
            }
        )
    )
    object Agent : Screen(
        route = "agent",
        deepLinks = listOf(
            navDeepLink {
                uriPattern = "byok://agent?goal={goal}"
            }
        )
    )
}
