package com.mnemocards.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    @Test
    fun migration_1_2_keepsSeedData_andFillsCreatedAt() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File.createTempFile("migration", ".db")
        dbFile.deleteOnExit()

        // Создаём БД версии 1 (без createdAt) и заполняем её как посев v1.
        val v1 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v1.execSQL(
            "CREATE TABLE decks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)"
        )
        v1.execSQL(
            "CREATE TABLE cards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "deckId INTEGER NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, " +
                "FOREIGN KEY(deckId) REFERENCES decks(id) ON DELETE CASCADE)"
        )
        v1.execSQL("INSERT INTO decks (name) VALUES ('Английский базовый')")
        v1.execSQL("INSERT INTO cards (deckId, front, back) VALUES (1, 'hello', 'привет')")
        v1.execSQL("INSERT INTO cards (deckId, front, back) VALUES (1, 'world', 'мир')")
        v1.version = 1
        v1.close()

        // Открываем той же Room-схемой — применяется вся цепочка до v4.
        val db = Room.databaseBuilder(context, MnemoCardsDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        // После перехода на UUID колода ищется по сгенерированному id.
        val deck = db.deckDao().observeDecksWithCardCounts().first().single()
        val cards = db.cardDao().observeCardsByDeck(deck.deckId).first()
        assertEquals(2, cards.size)
        assertEquals(setOf("hello", "world"), cards.map { it.front }.toSet())
        // createdAt заполнен различными значениями по id — порядок детерминирован.
        assertTrue(cards.all { it.createdAt > 0 })
        assertEquals(cards.map { it.createdAt }.distinct().size, cards.size)

        db.close()
    }

    /**
     * change-007, S1–S3: миграция v3 -> v4 выдаёт существующим записям UUID,
     * пересчитывает ссылки карточек на колоды и сохраняет статистику;
     * перенесённые записи помечаются isChanged = 1, revision = 1.
     */
    @Test
    fun migration_3_4_generatesUuids_andRecalculatesReferences() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File.createTempFile("migration34", ".db")
        dbFile.deleteOnExit()

        // БД версии 3: числовые id + createdAt + параметры SM-2.
        val v3 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v3.execSQL(
            "CREATE TABLE decks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)"
        )
        v3.execSQL(
            "CREATE TABLE cards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "deckId INTEGER NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL DEFAULT 0, " +
                "easinessFactor REAL NOT NULL DEFAULT 2.5, " +
                "repetitionNumber INTEGER NOT NULL DEFAULT 0, " +
                "intervalDays INTEGER NOT NULL DEFAULT 0, " +
                "dueDate INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(deckId) REFERENCES decks(id) ON DELETE CASCADE)"
        )
        v3.execSQL("CREATE INDEX IF NOT EXISTS index_cards_deckId ON cards(deckId)")
        v3.execSQL("INSERT INTO decks (id, name) VALUES (1, 'Глаголы')")
        v3.execSQL("INSERT INTO decks (id, name) VALUES (2, 'Фразы')")
        // Карточка с накопленной статистикой повторений (S1).
        v3.execSQL(
            "INSERT INTO cards (deckId, front, back, createdAt, easinessFactor, " +
                "repetitionNumber, intervalDays, dueDate) " +
                "VALUES (1, 'go', 'идти', 100, 2.6, 3, 7, 5000)"
        )
        v3.execSQL(
            "INSERT INTO cards (deckId, front, back, createdAt) VALUES (2, 'give up', 'сдаваться', 200)"
        )
        v3.version = 3
        v3.close()

        val db = Room.databaseBuilder(context, MnemoCardsDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val decks = db.deckDao().observeDecksWithCardCounts().first()
        assertEquals(2, decks.size)
        // Все колоды получили различные UUID.
        assertEquals(2, decks.map { it.deckId }.distinct().size)
        decks.forEach { assertTrue(isUuid(it.deckId)) }

        // Ссылка карточки пересчитана на UUID колоды, статистика сохранена.
        val verbsDeck = decks.first { it.deckName == "Глаголы" }
        val card = db.cardDao().observeCardsByDeck(verbsDeck.deckId).first().single()
        assertEquals("go", card.front)
        assertEquals(2.6, card.easinessFactor, 0.0001)
        assertEquals(3, card.repetitionNumber)
        assertEquals(7, card.intervalDays)
        assertEquals(5000L, card.dueDate)
        assertTrue(isUuid(card.id))
        assertNotEquals(card.id, card.deckId)

        // S3: перенесённые записи — локально изменённые, первая правка.
        val deckRow = db.deckDao().getDeckByIdIncludingDeleted(verbsDeck.deckId)!!
        assertTrue(deckRow.isChanged)
        assertEquals(1, deckRow.revision)
        assertTrue(card.isChanged)
        assertEquals(1, card.revision)

        db.close()
    }

    /** S4: миграция пустой БД — без ошибок и без записей. */
    @Test
    fun migration_3_4_onEmptyDatabase_isNoop() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File.createTempFile("migration34empty", ".db")
        dbFile.deleteOnExit()

        val v3 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v3.execSQL(
            "CREATE TABLE decks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)"
        )
        v3.execSQL(
            "CREATE TABLE cards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "deckId INTEGER NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL DEFAULT 0, " +
                "easinessFactor REAL NOT NULL DEFAULT 2.5, " +
                "repetitionNumber INTEGER NOT NULL DEFAULT 0, " +
                "intervalDays INTEGER NOT NULL DEFAULT 0, " +
                "dueDate INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(deckId) REFERENCES decks(id) ON DELETE CASCADE)"
        )
        v3.version = 3
        v3.close()

        val db = Room.databaseBuilder(context, MnemoCardsDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        assertTrue(db.deckDao().observeDecksWithCardCounts().first().isEmpty())

        db.close()
    }

    @Test
    fun seeding_isIdempotent_andOrdered() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        DatabaseSeeder.seedIfEmpty(db)
        DatabaseSeeder.seedIfEmpty(db) // повторный вызов — без дубликатов

        val deck = db.deckDao().observeDecksWithCardCounts().first().single()
        val cards = db.cardDao().observeCardsByDeck(deck.deckId).first()
        assertEquals(5, cards.size)
        // createdAt DESC: первая в потоке — последняя засеянная ("water").
        assertEquals("water", cards.first().front)
        // Возрастающие createdAt у посева.
        assertEquals(cards.map { it.createdAt }.sortedDescending(), cards.map { it.createdAt })

        db.close()
    }

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess
}
