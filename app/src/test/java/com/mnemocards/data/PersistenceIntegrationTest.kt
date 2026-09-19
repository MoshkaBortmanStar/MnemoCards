package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Интеграционный тест персистентности на файловой Room (S18, S19, S20):
 * все изменения переживают закрытие/переоткрытие БД; повторный сидинг
 * не воскрешает удалённую базовую колоду; новая колода с названием
 * удалённой создаётся пустой.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistenceIntegrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dir = context.getDir("persistence_test", Context.MODE_PRIVATE)
        dbFile = File(dir, "mnemocards.db")
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    private fun openDb(): MnemoCardsDatabase =
        Room.databaseBuilder(context, MnemoCardsDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()

    /**
     * Открыть БД, выполнить block и гарантированно закрыть.
     * (Room 2.7 реализует androidx.sqlite.Closeable, а не java.io.Closeable,
     * поэтому стандартный [use] к ней неприменим.)
     */
    private inline fun <R> withDb(block: (MnemoCardsDatabase) -> R): R {
        val db = openDb()
        return try {
            block(db)
        } finally {
            db.close()
        }
    }

    private fun insertCard(db: MnemoCardsDatabase, deckId: String, front: String, back: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO cards (id, deckId, front, back) VALUES (lower(hex(randomblob(16))), ?, ?, ?)",
            arrayOf(deckId, front, back)
        )
    }

    /** Создаёт колоду через репозиторий и возвращает её UUID. */
    private suspend fun createDeck(db: MnemoCardsDatabase, name: String): String {
        RoomDeckRepository(db.deckDao()).createDeck(name)
        return db.deckDao().getDeckByName(name)!!.id
    }

    // S18: изменения (создание, переименование, удаление) переживают перезапуск
    @Test
    fun allChanges_surviveDatabaseReopen() = runTest {
        val deckIds = mutableMapOf<String, String>()
        withDb { db ->
            val repository = RoomDeckRepository(db.deckDao())
            deckIds["A"] = createDeck(db, "A")
            deckIds["B"] = createDeck(db, "B")
            deckIds["C"] = createDeck(db, "C")
            insertCard(db, deckIds.getValue("A"), "aa", "аа")
            insertCard(db, deckIds.getValue("B"), "bb1", "бб1")
            insertCard(db, deckIds.getValue("B"), "bb2", "бб2")
            insertCard(db, deckIds.getValue("C"), "cc", "цц")

            repository.renameDeck(deckIds.getValue("B"), "B2")
            repository.deleteDeck(deckIds.getValue("C"))
        }

        withDb { db ->
            val decks = RoomDeckRepository(db.deckDao()).observeDecks().first()

            assertEquals(2, decks.size)
            val byName = decks.associateBy { it.name }
            assertEquals(1, byName.getValue("A").cardCount)
            assertEquals(2, byName.getValue("B2").cardCount)
            assertFalse(byName.containsKey("C"))
            assertFalse(byName.containsKey("B"))
        }
    }

    // S19: удалённая базовая колода не возвращается после перезапуска
    @Test
    fun deletedBaseDeck_isNotResurrected_afterReopenAndReSeed() = runTest {
        withDb { db ->
            DatabaseSeeder.seedIfEmpty(db)
            val baseDeckId = db.deckDao().observeDecksWithStats(1_000).first().single().deckId
            db.deckDao().deleteDeckWithCards(baseDeckId)
        }

        withDb { db ->
            // Эмуляция перезапуска приложения: сидинг выполняется снова.
            DatabaseSeeder.seedIfEmpty(db)

            val decks = RoomDeckRepository(db.deckDao()).observeDecks().first()
            assertEquals(0, decks.size)
        }
    }

    // S20: новая колода с названием удалённой создаётся пустой
    @Test
    fun newDeckWithDeletedName_isCreatedEmpty() = runTest {
        withDb { db ->
            val repository = RoomDeckRepository(db.deckDao())
            val germanId = createDeck(db, "Немецкий")
            repeat(5) { insertCard(db, germanId, "w$it", "с$it") }
            repository.deleteDeck(germanId)

            repository.createDeck("Немецкий")
        }

        withDb { db ->
            val decks = RoomDeckRepository(db.deckDao()).observeDecks().first()

            assertEquals(1, decks.size)
            assertEquals("Немецкий", decks.single().name)
            assertEquals(0, decks.single().cardCount)
        }
    }
}
