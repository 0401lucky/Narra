package com.example.myapplication.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureValueStore(
    context: Context,
) {
    private val gson = Gson()
    private val stringMapType = object : TypeToken<Map<String, String>>() {}.type
    private val sharedPreferences = context.getSharedPreferences(
        SECURE_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun getString(key: String): String {
        val encrypted = sharedPreferences.getString(key, null).orEmpty()
        if (encrypted.isBlank()) {
            return ""
        }
        return decrypt(encrypted).orEmpty()
    }

    fun putString(key: String, value: String) {
        if (value.isBlank()) {
            sharedPreferences.edit().remove(key).apply()
            return
        }
        val encrypted = encrypt(value) ?: return
        sharedPreferences.edit().putString(key, encrypted).apply()
    }

    fun getStringMap(key: String): Map<String, String> {
        val raw = getString(key)
        if (raw.isBlank()) {
            return emptyMap()
        }
        return runCatching {
            gson.fromJson<Map<String, String>>(raw, stringMapType).orEmpty()
        }.getOrDefault(emptyMap())
    }

    fun putStringMap(
        key: String,
        values: Map<String, String>,
    ) {
        val normalized = values
            .mapValues { (_, value) -> value.trim() }
            .filterValues { it.isNotBlank() }
        if (normalized.isEmpty()) {
            sharedPreferences.edit().remove(key).apply()
            return
        }
        putString(key, gson.toJson(normalized, stringMapType))
    }

    private fun encrypt(plainText: String): String? {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            "${encode(iv)}:${encode(encryptedBytes)}"
        }.getOrNull()
    }

    private fun decrypt(encryptedText: String): String? {
        return runCatching {
            val parts = encryptedText.split(':', limit = 2)
            if (parts.size != 2) {
                return@runCatching null
            }
            val iv = decode(parts[0])
            val encryptedBytes = decode(parts[1])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun encode(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decode(value: String): ByteArray {
        return Base64.decode(value, Base64.DEFAULT)
    }

    companion object {
        private const val SECURE_PREFERENCES_NAME = "secure_settings"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "myapplication.secure.settings"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
