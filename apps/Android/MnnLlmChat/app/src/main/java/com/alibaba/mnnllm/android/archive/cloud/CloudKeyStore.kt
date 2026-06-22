package com.alibaba.mnnllm.android.archive.cloud

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the optional cloud API key encrypted via Android Keystore (PRD module 7, acceptance).
 */
object CloudKeyStore {

    private const val FILE = "archive_cloud_secure"
    private const val KEY_API = "api_key"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API, key).apply()
    }

    fun apiKey(context: Context): String? = prefs(context).getString(KEY_API, null)

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_API).apply()
    }
}
