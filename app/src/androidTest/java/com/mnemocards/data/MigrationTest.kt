package com.mnemocards.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mnemocards.data.db.MIGRATION_1_2
import com.mnemocards.data.db.MIGRATION_2_3
import com.mnemocards.data.db.MnemoCardsDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S28: миграции Room без потери данных — деструктивная миграция
 * не используется, существующие карточки сохраняются и получают
 * параметры планирования SM-2 по умолчанию.
 *
 * История версий: v2 (createdAt) — последний релиз до SM-2; v3 —
 * параметры планирования. Покрыты оба пути обновления: 2→3 (реальный
 * пользовательский путь) и полный чейн 1→3.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MnemoCardsDatabase::class.java,
    )

    private fun seedCards(dbName: String, withCreatedAtColumn: Boolean) {
        helper.createDatabase(dbName, if (withCreatedAtColumn) 2 else 1).apply {
            execSQL("INSERT INTO decks (name) VALUES ('Базовая')")
            listOf(
                "cat" to "кошка",
                "dog" to "собака",
                "house" to "дом",
                "water" to "вода",
                "book" to "книга",
            ).forEach { (front, back) ->
                execSQL("INSERT INTO cards (deckId, front, back) VALUES (1, '$front', '$back')")
            }
            close()
        }
    }

    private fun assertSchedulingDefaults(dbName: String, version: Int, vararg migrations: androidx.room.migration.Migration) {
        val before = System.currentTimeMillis()
        val db = helper.runMigrationsAndValidate(dbName, version, true, *migrations)
        val after = System.currentTimeMillis()

        db.query(
            "SELECT front, easinessFactor, repetitionNumber, intervalDays, dueDate FROM cards",
        ).use { cursor ->
            var count = 0
            val fronts = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                count++
                fronts.add(cursor.getString(0))
                assertEquals(2.5, cursor.getDouble(1), 1e-9)
                assertEquals(0, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                // dueDate = момент миграции: карточка сразу готова к показу (S28).
                val due = cursor.getLong(4)
                assertTrue("dueDate не раньше начала миграции", due in before..after)
            }
            assertEquals(5, count)
            assertEquals(setOf("cat", "dog", "house", "water", "book"), fronts)
        }
    }

    @Test
    fun `S28 migration from release schema v2 preserves data and assigns defaults`() {
        val dbName = "migration-2to3-test.db"
        seedCards(dbName, withCreatedAtColumn = true)
        assertSchedulingDefaults(dbName, 3, MIGRATION_2_3)
    }

    @Test
    fun `S28 full chain 1 to 3 preserves data and assigns defaults`() {
        val dbName = "migration-1to3-test.db"
        seedCards(dbName, withCreatedAtColumn = false)
        assertSchedulingDefaults(dbName, 3, MIGRATION_1_2, MIGRATION_2_3)
    }
}
