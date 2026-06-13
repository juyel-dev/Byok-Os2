package com.example.core.data.service

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SupabaseSecurePrefs(private val context: Context) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "supabase_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveUrl(url: String) {
        prefs.edit().putString("url", url.trim()).apply()
    }

    fun getUrl(): String {
        return prefs.getString("url", "") ?: ""
    }

    fun saveServiceRoleKey(key: String) {
        prefs.edit().putString("service_role_key", key.trim()).apply()
    }

    fun getServiceRoleKey(): String {
        return prefs.getString("service_role_key", "") ?: ""
    }

    fun savePat(pat: String) {
        prefs.edit().putString("pat", pat.trim()).apply()
    }

    fun getPat(): String {
        return prefs.getString("pat", "") ?: ""
    }

    fun saveConfigured(configured: Boolean) {
        prefs.edit().putBoolean("configured", configured).apply()
    }

    fun isConfigured(): Boolean {
        return prefs.getBoolean("configured", false)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
