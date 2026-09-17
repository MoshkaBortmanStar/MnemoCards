package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Одноразовый сидинг (S14, S15, S19): базовая колода создаётся при первом
 * запуске, но не воскрешается после удаления и повторного запуска сидинга.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeckSeederTest {

    private lateinit var db: MnemoCardsDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun deckCount(): Int {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM decks").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun cardCount(): Int {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM cards").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun firstDeckId(): Long {
        db.openHelper.readableDatabase.query("SELECT id FROM decks LIMIT 1").use { cursor ->
            cursor.moveToFirst()
            return cursor.getLong(0)
        }
    }

    @Test
    fun firstSeed_createsBaseDeckWithFiveCards() = runTest {
        DatabaseSeeder.seedIfEmpty(db)

        assertEquals(1, deckCount())
        assertEquals(5, cardCount())
    }

    @Test
    fun reSeed_afterBaseDeckDeleted_doesNotRecreateIt() = runTest {
        DatabaseSeeder.seedIfEmpty(db)
        val deckId = firstDeckId()
        db.deckDao().deleteDeckWithCards(deckId)
        assertEquals(0, deckCount())
        assertEquals(0, cardCount())

        // Эмуляция перезапуска приложения: сидинг выполняется снова.
        DatabaseSeeder.seedIfEmpty(db)

        assertEquals(0, deckCount())
        assertEquals(0, cardCount())
    }

    @Test
    fun reSeed_withEmptyDatabase_doesNotRestoreSeedList() = runTest {
        DatabaseSeeder.seedIfEmpty(db)
        db.deckDao().deleteDeckWithCards(firstDeckId())

        DatabaseSeeder.seedIfEmpty(db)

        // Пустое состояние («Пока нет колод»), а не засиденный список.
        assertEquals(0, deckCount())
    }
}
