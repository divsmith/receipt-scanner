package com.receiptscanner.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface TokenProvider {
    fun getToken(): String?
    fun setToken(token: String?)
}

@Singleton
class TokenProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenProvider {

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "ynab_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun getToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    override fun setToken(token: String?) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "ynab_access_token"
    }
}
