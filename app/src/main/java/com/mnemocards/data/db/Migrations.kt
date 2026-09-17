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

/**
 * v2 -> v3 (change add-sm2-scheduling): добавлены параметры планирования
 * SM-2. Значения по умолчанию соответствуют новой карточке: EF=2.5, №0,
 * интервал 0, dueDate=момент миграции — все существующие карточки
 * становятся готовыми к повторению сегодня (S28).
 *
 * Деструктивная миграция не используется — данные сохраняются.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cards ADD COLUMN easinessFactor REAL NOT NULL DEFAULT 2.5")
        db.execSQL("ALTER TABLE cards ADD COLUMN repetitionNumber INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cards ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE cards ADD COLUMN dueDate INTEGER NOT NULL DEFAULT 0")
        // Backfill момента миграции — паттерн MIGRATION_1_2 (createdAt).
        val now = System.currentTimeMillis()
        db.execSQL("UPDATE cards SET dueDate = $now")
    }
}

val ALL_MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3)
