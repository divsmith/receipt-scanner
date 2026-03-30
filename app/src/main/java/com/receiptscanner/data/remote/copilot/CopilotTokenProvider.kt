package com.receiptscanner.data.remote.copilot

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface CopilotTokenProvider {
    fun getToken(): String?
    fun setToken(token: String?)
}

@Singleton
class CopilotTokenProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CopilotTokenProvider {

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "copilot_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun getToken(): String? {
        return prefs.getString(KEY_GITHUB_PAT, null)
    }

    override fun setToken(token: String?) {
        prefs.edit().putString(KEY_GITHUB_PAT, token).apply()
    }

    companion object {
        private const val KEY_GITHUB_PAT = "github_pat"
    }
}
