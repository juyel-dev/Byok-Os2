package com.example.core.data.di

import com.example.core.data.AppDatabase
import com.example.core.data.CryptoManager
import com.example.core.data.ChatRepositoryImpl
import com.example.core.domain.repository.ChatRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
    // Single instance of AppDatabase
    single { AppDatabase.getDatabase(androidContext()) }

    // CryptoManager as singleton scope
    single { CryptoManager(androidContext()) }

    // ChatRepository as singleton scope dependency bound to ChatRepositoryImpl
    single<ChatRepository> { ChatRepositoryImpl(get(), get()) }
}
