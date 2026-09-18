package com.mnemocards.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Персистентный флаг синхронизации (S14–S16).
 * По умолчанию выключен (false) → режим удаления физический.
 * UI-переключателей нет — флагом будет управлять будущий экран настроек сервера.
 */
private val Context.syncPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_settings",
)

class SyncFlagDataStore(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * Текущее значение флага. Ошибки чтения (IOException, включая
     * CorruptionException от повреждённого файла) транслируются в false —
     * приложение трактует флаг как выключенный и не падает (S16).
     */
    val syncEnabled: Flow<Boolean> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { preferences -> preferences[SYNC_ENABLED] ?: false }

    suspend fun setSyncEnabled(value: Boolean) {
        dataStore.edit { preferences -> preferences[SYNC_ENABLED] = value }
    }

    companion object {
        private val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")

        /**
         * Единственная точка создания DataStore для AppContainer:
         * делегат гарантирует один экземпляр на процесс.
         */
        fun fromContext(context: Context): SyncFlagDataStore =
            SyncFlagDataStore(context.applicationContext.syncPreferencesDataStore)
    }
}
