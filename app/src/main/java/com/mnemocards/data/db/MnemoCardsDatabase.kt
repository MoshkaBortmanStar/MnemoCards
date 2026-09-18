package com.mnemocards.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Локальная БД (offline-first): Room — single source of truth для UI.
 *
 * version 4 — переход на UUID и поля синхронизации (change-007,
 * миграция MIGRATION_3_4); история версий: v2 — createdAt
 * (MIGRATION_1_2), v3 — параметры SM-2 (MIGRATION_2_3),
 * v4 — UUID + isChanged/revision/isDeleted (MIGRATION_3_4).
 *
 * exportSchema = true: JSON схем нужны MigrationTestHelper (S28).
 */
@Database(
    entities = [DeckEntity::class, CardEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class MnemoCardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var INSTANCE: MnemoCardsDatabase? = null

        fun getInstance(context: Context): MnemoCardsDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MnemoCardsDatabase::class.java,
                    "mnemocards.db",
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
