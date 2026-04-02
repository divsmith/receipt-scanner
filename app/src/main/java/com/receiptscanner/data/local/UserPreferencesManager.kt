package com.receiptscanner.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.receiptscanner.domain.model.CloudOcrProviderType
import com.receiptscanner.domain.model.CloudOcrResolution
import com.receiptscanner.domain.model.OcrMode
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    val ocrMode: Flow<OcrMode> = dataStore.data.map { prefs ->
        try {
            OcrMode.valueOf(prefs[KEY_OCR_MODE] ?: OcrMode.LOCAL.name)
        } catch (_: IllegalArgumentException) {
            OcrMode.LOCAL
        }
    }

    val budgetId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_BUDGET_ID]
    }

    val budgetName: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_BUDGET_NAME]
    }

    val defaultAccountId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_ACCOUNT_ID]
    }

    suspend fun getBudgetId(): String? = budgetId.first()

    suspend fun getDefaultAccountId(): String? = defaultAccountId.first()

    suspend fun saveBudget(id: String, name: String) {
        dataStore.edit { prefs ->
            prefs[KEY_BUDGET_ID] = id
            prefs[KEY_BUDGET_NAME] = name
        }
    }

    suspend fun saveDefaultAccountId(accountId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_ACCOUNT_ID] = accountId
        }
    }

    suspend fun saveOcrMode(mode: OcrMode) {
        dataStore.edit { prefs ->
            prefs[KEY_OCR_MODE] = mode.name
        }
    }

    val cloudOcrProviderType: Flow<CloudOcrProviderType> = dataStore.data.map { prefs ->
        try {
            CloudOcrProviderType.valueOf(prefs[KEY_CLOUD_OCR_PROVIDER] ?: CloudOcrProviderType.OPENROUTER.name)
        } catch (_: IllegalArgumentException) {
            CloudOcrProviderType.OPENROUTER
        }
    }

    val openRouterModelId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_OPENROUTER_MODEL_ID]
    }

    val debugModeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_MODE] ?: false
    }

    suspend fun saveCloudOcrProviderType(providerType: CloudOcrProviderType) {
        dataStore.edit { prefs ->
            prefs[KEY_CLOUD_OCR_PROVIDER] = providerType.name
        }
    }

    suspend fun saveOpenRouterModelId(modelId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_OPENROUTER_MODEL_ID] = modelId
        }
    }

    suspend fun saveDebugMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DEBUG_MODE] = enabled
        }
    }

    val cloudOcrResolution: Flow<CloudOcrResolution> = dataStore.data.map { prefs ->
        try {
            CloudOcrResolution.valueOf(prefs[KEY_CLOUD_OCR_RESOLUTION] ?: CloudOcrResolution.FULL.name)
        } catch (_: IllegalArgumentException) {
            CloudOcrResolution.FULL
        }
    }

    suspend fun saveCloudOcrResolution(resolution: CloudOcrResolution) {
        dataStore.edit { prefs ->
            prefs[KEY_CLOUD_OCR_RESOLUTION] = resolution.name
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_BUDGET_ID = stringPreferencesKey("selected_budget_id")
        private val KEY_BUDGET_NAME = stringPreferencesKey("selected_budget_name")
        private val KEY_DEFAULT_ACCOUNT_ID = stringPreferencesKey("default_account_id")
        private val KEY_OCR_MODE = stringPreferencesKey("ocr_mode")
        private val KEY_CLOUD_OCR_PROVIDER = stringPreferencesKey("cloud_ocr_provider")
        private val KEY_OPENROUTER_MODEL_ID = stringPreferencesKey("openrouter_model_id")
        private val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode_enabled")
        private val KEY_CLOUD_OCR_RESOLUTION = stringPreferencesKey("cloud_ocr_resolution")
    }
}
