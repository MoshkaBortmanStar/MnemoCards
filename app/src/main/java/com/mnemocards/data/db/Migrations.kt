package com.mnemocards.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: добавлена колонка cards.createdAt (NOT NULL).
 * Существующие строки получают различные значения по id,
 * чтобы порядок «новые сверху» был детерминированным.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cards ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE cards SET createdAt = id * 1000")
        // Индекс внешнего ключа: схема v1 могла быть создана без него,
        // а @Entity его ожидает (быстрый поиск cards по deckId).
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cards_deckId ON cards(deckId)")
    }
}

val ALL_MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)
