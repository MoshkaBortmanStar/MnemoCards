package com.mnemocards.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mnemocards.data.db.sync.LastSyncResultEntity
import com.mnemocards.data.db.sync.SyncStateDao
import com.mnemocards.data.db.sync.SyncStateEntity

/**
 * Локальная БД (offline-first): Room — single source of truth для UI.
 *
 * version 5 — состояние синхронизации: ревизии потоков и итог последней
 * сессии (change-009, MIGRATION_4_5); история версий: v2 — createdAt
 * (MIGRATION_1_2), v3 — параметры SM-2 (MIGRATION_2_3), v4 — UUID +
 * isChanged/revision/isDeleted (MIGRATION_3_4, change-007).
 *
 * exportSchema = true: JSON схем нужны MigrationTestHelper (S28).
 */
@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        SyncStateEntity::class,
        LastSyncResultEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class MnemoCardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun syncStateDao(): SyncStateDao

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
