package com.example

import android.app.Application
import com.example.di.applicationModule
import com.example.di.networkModule
import com.example.core.data.di.dataModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class ByokApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                androidLogger(Level.INFO)
                androidContext(this@ByokApplication)
                modules(
                    listOf(
                        dataModule,
                        networkModule,
                        applicationModule
                    )
                )
            }
        }
    }
}
