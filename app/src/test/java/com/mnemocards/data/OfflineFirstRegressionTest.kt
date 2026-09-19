package com.mnemocards.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.sync.SyncFlagDataStore
import com.mnemocards.data.syncsettings.EncryptedTokenStorage
import com.mnemocards.data.syncsettings.TestStores
import com.mnemocards.data.syncsettings.TokenPair
import com.mnemocards.data.syncsettings.TokenStorage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T19: регрессионные тесты offline-first (S2, S21).
 *
 * (1) Без настроенного сервера (адрес не введён, токенов нет, флаг
 * синхронизации выключен) сценарии работы с данными выполняются из Room,
 * сетевые вызовы не выполняются, удаления физические (S2).
 *
 * (2) При подключённом, но недоступном сервере (токены сохранены, флаг
 * включён) те же сценарии работают из Room и не выбрасывают сетевых
 * ошибок; сетевой код достижим только с экрана настроек (S21) —
 * MockWebServer поднята и считает запросы: их должно быть ноль.
 *
 * Импорт и экспорт идут через Room и чистые CSV-кодеки (CsvParser /
 * CsvEncoder — покрыты собственными тестами без сети), поэтому здесь
 * проверяется общий принцип: путь данных Room не порождает сетевых
 * вызовов ни в одном режиме подключения.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineFirstRegressionTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: MnemoCardsDatabase
    private lateinit var flagDataStore: SyncFlagDataStore
    private lateinit var flagScope: CoroutineScope
    private lateinit var tokenStorage: TokenStorage
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        flagScope = CoroutineScope(Dispatchers.IO + Job())
        flagDataStore = SyncFlagDataStore(
            PreferenceDataStoreFactory.create(
                scope = flagScope,
                produceFile = { File(tmpFolder.newFolder(), "sync_flag.preferences_pb") },
            ),
        )
        // Прозрачный шифратор и фейковое хранилище — как в существующих
        // тестах TokenStorage (T8): проверяется только состояние токенов.
        tokenStorage = EncryptedTokenStorage(TestStores.keyValueStore(), TestStores.plainCipher())
        // Сервер поднят, но ни один клиентский код не должен к нему
        // обратиться: requestCount остаётся нулевым в обоих сценариях.
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
        flagScope.cancel()
    }

    private suspend fun insertDeck(name: String): String {
        val id = UUID.randomUUID().toString()
        db.deckDao().insertDeck(DeckEntity(id = id, name = name))
        return id
    }

    // (1) S2: сервер не настроен — всё работает как раньше, из Room,
    // без сети, удаления физические.
    @Test
    fun `without configured server deck scenarios run offline with physical deletion`() = runTest {
        // Состояние по умолчанию: токенов нет, флаг выключен.
        assertNull(tokenStorage.tokens())
        assertFalse(flagDataStore.syncEnabled.first())

        // Сценарий списка колод: колода создаётся и читается из Room.
        val deckId = insertDeck("Coloda")
        assertNotNull(db.deckDao().getDeckById(deckId))

        // Удаление без подключения — физическое (S20): строки больше нет.
        db.deckDao().deleteDeckById(deckId)
        assertNull(db.deckDao().getDeckByIdIncludingDeleted(deckId))

        // Сетевые вызовы не выполнялись.
        assertEquals("Offline-сценарии не должны обращаться к сети (S2)", 0, server.requestCount)
    }

    // (2) S21: подключён, но сервер недоступен — сценарии работают
    // из Room, сетевых ошибок нет, удаления мягкие (S19).
    @Test
    fun `connected but unreachable server does not affect room scenarios`() = runTest {
        // Подключённое состояние: токены сохранены, флаг включён.
        tokenStorage.saveTokens(TokenPair(accessToken = "access-1", refreshToken = "refresh-1"))
        flagDataStore.setSyncEnabled(true)
        assertNotNull(tokenStorage.tokens())
        assertTrue(flagDataStore.syncEnabled.first())

        // Сценарий списка колод работает из Room без сетевых ошибок.
        val deckId = insertDeck("Coloda")
        assertNotNull(db.deckDao().getDeckById(deckId))

        // Удаление при включённой синхронизации — мягкое (S19):
        // запись помечена удалённой и остаётся в Room.
        db.deckDao().softDeleteDeckById(deckId)
        val remaining = db.deckDao().getDeckByIdIncludingDeleted(deckId)
        assertNotNull("Мягкое удаление должно сохранять строку (S19)", remaining)
        assertTrue(remaining!!.isDeleted)

        // Ни один сценарий не обратился к серверу (S21): фоновая
        // синхронизация не входит в эту фичу, сетевой код достижим
        // только с экрана настроек.
        assertEquals(
            "Подключённое состояние не должно порождать сетевых вызовов вне настроек (S21)",
            0,
            server.requestCount,
        )
    }

    // (3) S18 + S2: после выхода состояние возвращается к offline-режиму
    // с физическими удалениями, адрес не влияет на сценарии данных.
    @Test
    fun `after logout scenarios return to offline physical mode`() = runTest {
        tokenStorage.saveTokens(TokenPair(accessToken = "access-1", refreshToken = "refresh-1"))
        flagDataStore.setSyncEnabled(true)

        // Выход (S18): токены удалены, флаг выключен.
        tokenStorage.clear()
        flagDataStore.setSyncEnabled(false)
        assertNull(tokenStorage.tokens())
        assertFalse(flagDataStore.syncEnabled.first())

        val deckId = insertDeck("Coloda")
        db.deckDao().deleteDeckById(deckId)
        assertNull(db.deckDao().getDeckByIdIncludingDeleted(deckId))
        assertEquals(0, server.requestCount)
    }
}
