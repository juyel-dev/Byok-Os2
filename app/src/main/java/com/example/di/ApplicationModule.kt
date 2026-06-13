package com.example.di

import com.example.core.data.service.*
import com.example.feature.chat.viewmodel.ChatViewModel
import com.example.feature.chat.viewmodel.AgentViewModel
import com.example.feature.settings.viewmodel.SettingsViewModel
import com.example.feature.settings.viewmodel.McpViewModel
import com.example.feature.settings.viewmodel.ProviderViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val applicationModule = module {
    // Singleton services
    single { LlmService(get()) }
    single { McpService(get()) }
    single { CompressService() }
    single { MemoryService() }
    
    // Secure preferences and admin clients for cloud provisioning
    single { SupabaseSecurePrefs(get()) }
    single { SupabaseAdminClient(get()) }
    single { SupabaseSetupService(get(), get(), get(), get()) }

    // Decomposed viewmodels registration
    viewModel {
        ChatViewModel(
            application = get(),
            repository = get(),
            llmService = get(),
            mcpService = get(),
            compressService = get(),
            memoryService = get()
        )
    }

    viewModel {
        SettingsViewModel(
            application = get(),
            repository = get(),
            llmService = get(),
            supabaseService = get(),
            securePrefs = get()
        )
    }

    viewModel {
        AgentViewModel(
            application = get(),
            repository = get(),
            llmService = get(),
            mcpService = get()
        )
    }

    viewModel {
        McpViewModel(
            application = get(),
            repository = get(),
            mcpService = get()
        )
    }

    viewModel {
        ProviderViewModel(
            application = get(),
            repository = get(),
            llmService = get()
        )
    }
}
