package com.receiptscanner.data.remote.openrouter

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface OpenRouterTokenProvider {
    fun getToken(): String?
    fun setToken(token: String?)
}

@Singleton
class OpenRouterTokenProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : OpenRouterTokenProvider {

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "openrouter_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun getToken(): String? = prefs.getString(KEY_API_KEY, null)

    override fun setToken(token: String?) {
        prefs.edit().putString(KEY_API_KEY, token).apply()
    }

    companion object {
        private const val KEY_API_KEY = "openrouter_api_key"
    }
}
