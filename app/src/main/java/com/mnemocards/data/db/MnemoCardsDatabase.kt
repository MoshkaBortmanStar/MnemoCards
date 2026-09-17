package com.mnemocards.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Локальная БД (offline-first): Room — single source of truth для UI.
 * version 2 — после добавления поля `createdAt` в CardEntity
 * (миграция MIGRATION_1_2, см. Migrations.kt).
 */
@Database(
    entities = [DeckEntity::class, CardEntity::class],
    version = 2,
    exportSchema = false,
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
