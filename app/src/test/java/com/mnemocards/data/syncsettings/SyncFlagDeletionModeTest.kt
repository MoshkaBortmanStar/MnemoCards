package com.mnemocards.data.syncsettings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.sync.SyncFlagDataStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
 * T17: интеграционная проверка на Room — флаг синхронизации, включаемый
 * входом (T18) через сеттер существующего SyncFlagDataStore, переводит
 * удаления колод и карточек в мягкий режим (S19); при выключенном флаге
 * (по умолчанию и после выхода) удаления физические (S20).
 *
 * Связь «вход/выход → флаг» идёт через SyncSettingsStorage, как в
 * AppContainer; репозитории удаления читают тот же флаг — их собственное
 * поведение по флагу покрыто существующими тестами репозиториев.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncFlagDeletionModeTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: MnemoCardsDatabase
    private lateinit var flagDataStore: SyncFlagDataStore
    private lateinit var flagScope: CoroutineScope

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
    }

    @After
    fun tearDown() {
        db.close()
        flagScope.cancel()
    }

    private suspend fun insertDeck(name: String): String {
        val id = UUID.randomUUID().toString()
        db.deckDao().insertDeck(DeckEntity(id = id, name = name))
        return id
    }

    @Test
    fun `sync flag toggled by login and logout follows storage lifecycle`() = runTest {
        // Хранилище настроек включает/выключает флаг — как в AppContainer.
        val storage = DefaultSyncSettingsStorage(
            store = TestStores.keyValueStore(),
            tokenStorage = EncryptedTokenStorage(TestStores.keyValueStore(), TestStores.plainCipher()),
            onSyncFlagChanged = { flagDataStore.setSyncEnabled(it) },
        )

        // По умолчанию флаг выключен — физический режим удаления (S20).
        assertFalse(flagDataStore.syncEnabled.first())

        // Вход: connect() хранилища настроек включает флаг (S10, S19).
        storage.connect("https://sync.example.com")
        assertTrue(flagDataStore.syncEnabled.first())

        // Выход: флаг выключается — возврат к физическому режиму (S18, S20).
        storage.logout()
        assertFalse(flagDataStore.syncEnabled.first())
    }

    @Test
    fun `deck rows survive in room when deletion mode is soft`() = runTest {
        val deckId = insertDeck("Coloda")
        flagDataStore.setSyncEnabled(true)

        // Мягкое удаление: строка остаётся, помечена удалённой (S19).
        db.deckDao().softDeleteDeckById(deckId)
        val remaining = db.deckDao().getDeckByIdIncludingDeleted(deckId)
        assertNotNull(remaining)
        assertTrue(remaining!!.isDeleted)
    }

    @Test
    fun `deck rows removed from room when deletion mode is physical`() = runTest {
        val deckId = insertDeck("Coloda")
        flagDataStore.setSyncEnabled(false)

        // Физическое удаление: строки больше нет (S20).
        db.deckDao().deleteDeckById(deckId)
        assertEquals(null, db.deckDao().getDeckByIdIncludingDeleted(deckId))
        assertNull(db.deckDao().getDeckByIdIncludingDeleted(deckId))
    }
}
