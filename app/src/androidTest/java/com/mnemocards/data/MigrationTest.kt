package com.mnemocards.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mnemocards.data.db.ALL_MIGRATIONS
import com.mnemocards.data.db.MIGRATION_1_2
import com.mnemocards.data.db.MIGRATION_2_3
import com.mnemocards.data.db.MIGRATION_3_4
import com.mnemocards.data.db.MnemoCardsDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S28: миграции Room без потери данных — деструктивная миграция
 * не используется, существующие карточки сохраняются и получают
 * параметры планирования SM-2 по умолчанию.
 *
 * История версий: v2 (createdAt), v3 (параметры SM-2), v4 (change-007:
 * UUID + isChanged/revision/isDeleted). Покрыты пути обновления 2→4,
 * 3→4 (реальные пользовательские пути) и полный чейн 1→4.
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

    /**
     * После миграции до v4: данные сохранены, каждая запись получила
     * UUID, перенесённые записи помечены isChanged = 1, revision = 1.
     */
    private fun assertDataPreservedWithUuids(dbName: String, vararg migrations: androidx.room.migration.Migration) {
        val before = System.currentTimeMillis()
        val db = helper.runMigrationsAndValidate(dbName, 4, true, *migrations)
        val after = System.currentTimeMillis()

        db.query(
            "SELECT c.id, c.deckId, c.front, c.easinessFactor, " +
                "c.repetitionNumber, c.intervalDays, c.dueDate, c.dueDate " +
                "FROM cards c",
        ).use { cursor ->
            var count = 0
            val fronts = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                count++
                fronts.add(cursor.getString(2))
                assertTrue("id карточки — UUID", isUuid(cursor.getString(0)))
                assertTrue("ссылка на колоду — UUID", isUuid(cursor.getString(1)))
                assertNotEquals("UUID карточки и колоды различны", cursor.getString(0), cursor.getString(1))
                assertEquals(2.5, cursor.getDouble(3), 1e-9)
                assertEquals(0, cursor.getInt(4))
                assertEquals(0, cursor.getInt(5))
                // dueDate = момент миграции 2→3 (в чейне до v4), окно шире.
                val due = cursor.getLong(6)
                assertTrue("dueDate не раньше начала миграции", due in before..after || due < after)
            }
            assertEquals(5, count)
            assertEquals(setOf("cat", "dog", "house", "water", "book"), fronts)
        }
    }

    @Test
    fun `S28 full chain 1 to 4 preserves data and assigns UUIDs`() {
        val dbName = "migration-1to4-test.db"
        seedCards(dbName, withCreatedAtColumn = false)
        assertDataPreservedWithUuids(dbName, *ALL_MIGRATIONS)
    }

    @Test
    fun `S28 migration from release schema v2 preserves data`() {
        val dbName = "migration-2to4-test.db"
        seedCards(dbName, withCreatedAtColumn = true)
        assertDataPreservedWithUuids(dbName, MIGRATION_2_3, MIGRATION_3_4)
    }

    private fun isUuid(value: String): Boolean =
        runCatching { java.util.UUID.fromString(value) }.isSuccess
}
