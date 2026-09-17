package com.mnemocards.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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

        // Открываем той же Room-схемой — применяется MIGRATION_1_2.
        val db = Room.databaseBuilder(context, MnemoCardsDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val cards = db.cardDao().observeCardsByDeck(1L).first()
        assertEquals(2, cards.size)
        assertEquals(setOf("hello", "world"), cards.map { it.front }.toSet())
        // createdAt заполнен различными значениями по id — порядок детерминирован.
        assertTrue(cards.all { it.createdAt > 0 })
        assertEquals(cards.map { it.createdAt }.distinct().size, cards.size)

        db.close()
    }

    @Test
    fun seeding_isIdempotent_andOrdered() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        DatabaseSeeder.seed(db)
        DatabaseSeeder.seed(db) // повторный вызов — без дубликатов

        val cards = db.cardDao().observeCardsByDeck(1L).first()
        assertEquals(5, cards.size)
        // createdAt DESC: первая в потоке — последняя засеянная ("book").
        assertEquals("book", cards.first().front)
        // Возрастающие createdAt у посева.
        assertEquals(cards.map { it.createdAt }.sortedDescending(), cards.map { it.createdAt })

        db.close()
    }
}
