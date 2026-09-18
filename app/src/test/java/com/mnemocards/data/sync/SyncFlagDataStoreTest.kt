package com.mnemocards.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model.
 * S14 — чистый DataStore: флаг по умолчанию выключен (false).
 * S15 — значение флага персистентно, переживает создание нового
 *        экземпляра хранилища (аналог перезапуска приложения).
 * S16 — повреждённый файл хранилища: чтение возвращает false без исключения.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncFlagDataStoreTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private fun newStore(file: File): Pair<SyncFlagDataStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return SyncFlagDataStore(dataStore) to scope
    }

    // --- S14: флаг по умолчанию выключен ---

    @Test
    fun cleanDataStore_flagDefaultsToFalse() = runBlocking {
        val file = File(tmpFolder.newFolder(), "sync_settings.preferences_pb")
        val (store, scope) = newStore(file)

        val value = store.syncEnabled.first()

        assertFalse("S14: чистая установка — флаг выключен", value)

        scope.cancel()
    }

    // --- S15: значение персистентно ---

    @Test
    fun writtenFlagTrue_persistedForNewStoreInstance() = runBlocking {
        val file = File(tmpFolder.newFolder(), "sync_settings.preferences_pb")
        val (storeA, scopeA) = newStore(file)

        storeA.setSyncEnabled(true)
        // Закрываем первый экземпляр — аналог перезапуска приложения.
        // join обязателен: cancel асинхронный, иначе новый стор на том же
        // файле увидит «multiple DataStores active».
        scopeA.cancel()
        scopeA.coroutineContext[Job]!!.join()

        val (storeB, scopeB) = newStore(file)
        val value = storeB.syncEnabled.first()

        assertTrue("S15: флаг true переживает новый экземпляр хранилища", value)

        scopeB.cancel()
    }

    @Test
    fun writtenFlagFalse_persistedForNewStoreInstance() = runBlocking {
        val file = File(tmpFolder.newFolder(), "sync_settings.preferences_pb")
        val (storeA, scopeA) = newStore(file)

        storeA.setSyncEnabled(false)
        scopeA.cancel()
        scopeA.coroutineContext[Job]!!.join()

        val (storeB, scopeB) = newStore(file)
        val value = storeB.syncEnabled.first()

        assertFalse("S15: явный false тоже персистентен", value)

        scopeB.cancel()
    }

    // --- S16: повреждённое хранилище (аналог 500) ---

    @Test
    fun corruptedFile_readReturnsFalseWithoutCrash() = runBlocking {
        val dir = tmpFolder.newFolder()
        val file = File(dir, "sync_settings.preferences_pb")
        // Мусорные байты: файл существует, но не парсится как preferences_pb.
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x7F, -0x80, 0x33, 0x33, 0x33))

        val (store, scope) = newStore(file)

        val value = store.syncEnabled.first()

        assertFalse("S16: ошибка чтения трактуется как выключенный флаг", value)

        scope.cancel()
    }
}
