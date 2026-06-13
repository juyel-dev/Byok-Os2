package com.example.di

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import org.koin.dsl.module

val networkModule = module {
    // Shared OkHttpClient for network layers
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
