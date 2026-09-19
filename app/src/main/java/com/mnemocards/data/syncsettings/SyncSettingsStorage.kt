package com.mnemocards.data.syncsettings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Минимальное персистентное хранилище «ключ-значение». Абстракция позволяет
 * подменить хранение фейком в юнит-тестах.
 */
interface KeyValueStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
}

/** Реализация на DataStore Preferences (используется в приложении). */
class DataStoreKeyValueStore(
    private val dataStore: DataStore<Preferences>,
) : KeyValueStore {

    override suspend fun put(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun get(key: String): String? =
        dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }
}

/**
 * Хранение настроек синхронизации: адрес сервера и признак «подключено» —
 * открытым текстом (это не секреты). Токены живут в [TokenStorage] в
 * зашифрованном виде; выход удаляет их и гасит флаг синхронизации через
 * колбэк [onSyncFlagChanged] (проводится к существующему SyncFlagDataStore,
 * новый флаг не создаётся). Пароль нигде не пишется (S16).
 */
interface SyncSettingsStorage {

    /** Адрес сервера или null, если ещё не вводился. Переживает перезапуск (S17). */
    suspend fun serverAddress(): String?

    /** Подключён ли пользователь (токены были сохранены и выход не выполнялся). */
    suspend fun isConnected(): Boolean

    /**
     * Фиксирует успешный вход: сохраняет адрес, признак «подключено» и
     * включает флаг синхронизации (S10, S19/S20 — мягкий режим удаления).
     */
    suspend fun connect(address: String)

    /**
     * Выход: удаляет токены и признак «подключено», выключает флаг
     * синхронизации. Адрес сервера сохраняется (S18).
     */
    suspend fun logout()
}

/** Реализация поверх [KeyValueStore] и [TokenStorage]. */
class DefaultSyncSettingsStorage(
    private val store: KeyValueStore,
    private val tokenStorage: TokenStorage,
    private val onSyncFlagChanged: suspend (Boolean) -> Unit,
) : SyncSettingsStorage {

    override suspend fun serverAddress(): String? = store.get(KEY_ADDRESS)

    override suspend fun isConnected(): Boolean = store.get(KEY_CONNECTED) == VALUE_TRUE

    override suspend fun connect(address: String) {
        store.put(KEY_ADDRESS, address)
        store.put(KEY_CONNECTED, VALUE_TRUE)
        onSyncFlagChanged(true)
    }

    override suspend fun logout() {
        tokenStorage.clear()
        store.remove(KEY_CONNECTED)
        onSyncFlagChanged(false)
    }

    private companion object {
        const val KEY_ADDRESS = "sync_server_address"
        const val KEY_CONNECTED = "sync_connected"
        const val VALUE_TRUE = "true"
    }
}
