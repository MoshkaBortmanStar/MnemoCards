package com.mnemocards.data.syncsettings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mnemocards.data.sync.SyncFlagDataStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * T17 (продолжение): сеттер флага, который передаётся хранилищем настроек
 * (и используется в AppContainer для существующего SyncFlagDataStore),
 * действительно управляет режимом удаления: значение флага после входа —
 * мягкое удаление (S19), после выхода — физическое (S20).
 */
class SyncFlagDataStoreSwitchTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var flagDataStore: SyncFlagDataStore
    private lateinit var flagScope: CoroutineScope

    @Before
    fun setUp() {
        flagScope = CoroutineScope(Dispatchers.IO + Job())
        flagDataStore = SyncFlagDataStore(
            PreferenceDataStoreFactory.create(
                scope = flagScope,
                produceFile = { File(tmpFolder.newFolder(), "sync_flag.preferences_pb") },
            ),
        )
    }

    @After
    fun tearDown() {
        flagScope.cancel()
    }

    @Test
    fun `storage connect enables flag and logout disables it`() = runTest {
        val storage = DefaultSyncSettingsStorage(
            store = TestStores.keyValueStore(),
            tokenStorage = EncryptedTokenStorage(TestStores.keyValueStore(), TestStores.plainCipher()),
            onSyncFlagChanged = { flagDataStore.setSyncEnabled(it) },
        )

        // До входа флаг выключен — режим удаления физический (S20).
        assertFalse(flagDataStore.syncEnabled.first())

        // Вход: connect() включает флаг — мягкий режим удаления (S19).
        storage.connect("https://sync.example.com")
        assertTrue(flagDataStore.syncEnabled.first())

        // Выход: флаг выключен — возврат к физическому режиму (S18).
        storage.logout()
        assertFalse(flagDataStore.syncEnabled.first())
    }
}
