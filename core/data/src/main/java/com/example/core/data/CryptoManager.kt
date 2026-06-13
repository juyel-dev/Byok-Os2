package com.example.core.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager(private val context: Context) {
    private val isAndroidKeyStoreSupported: Boolean = try {
        KeyStore.getInstance("AndroidKeyStore")
        true
    } catch (e: Throwable) {
        false
    }

    private val masterKey = if (isAndroidKeyStoreSupported) {
        try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: Throwable) {
            null
        }
    } else {
        null
    }

    private val keyStore = if (isAndroidKeyStoreSupported) {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }
        } catch (e: Throwable) {
            null
        }
    } else {
        null
    }

    private val fallbackSecretKey: SecretKey by lazy {
        javax.crypto.spec.SecretKeySpec("byok_fallb_16_by".toByteArray(Charsets.UTF_8), "AES")
    }

    private fun getSecretKey(): SecretKey {
        return try {
            if (keyStore != null) {
                val key = keyStore.getKey(MasterKey.DEFAULT_MASTER_KEY_ALIAS, null)
                if (key is SecretKey) {
                    key
                } else {
                    fallbackSecretKey
                }
            } else {
                fallbackSecretKey
            }
        } catch (e: Throwable) {
            fallbackSecretKey
        }
    }

    private fun getCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    fun encrypt(plaintext: String): String {
        if (plaintext.isBlank()) return ""
        val cipher = getCipher()
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        val combined = ByteArray(iv.size + encryptedData.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)
        
        return "v1:" + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(ciphertext: String): String {
        if (ciphertext.isBlank()) return ""
        try {
            if (!ciphertext.startsWith("v1:")) {
                // If it doesn't have the version prefix, it represents legacy/plaintext data
                return ciphertext
            }
            val base64Data = ciphertext.substring(3)
            val combined = Base64.decode(base64Data, Base64.NO_WRAP)
            
            val ivSize = 12 // AES GCM standard IV is 12 bytes
            if (combined.size <= ivSize) return ""
            
            val iv = ByteArray(ivSize)
            System.arraycopy(combined, 0, iv, 0, ivSize)
            
            val encryptedData = ByteArray(combined.size - ivSize)
            System.arraycopy(combined, ivSize, encryptedData, 0, encryptedData.size)
            
            val cipher = getCipher()
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
            val decryptedData = cipher.doFinal(encryptedData)
            return String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}
