package com.mnemocards.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Локальная база Room — основной источник данных приложения (offline-first).
 */
@Database(
    entities = [DeckEntity::class, CardEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MnemoCardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao

    companion object {

        const val DATABASE_NAME = "mnemocards.db"

        fun build(context: Context): MnemoCardsDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MnemoCardsDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
