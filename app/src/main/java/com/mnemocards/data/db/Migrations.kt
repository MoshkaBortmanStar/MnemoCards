package com.mnemocards.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

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

/**
 * v3 -> v4 (change-007): переход с числовых автоинкрементных PK на UUID
 * (String) и добавление полей isChanged / revision / isDeleted в обе
 * таблицы. Числовые id уникальны только внутри одного устройства и
 * коллизируют при синхронизации, поэтому записи переводятся на
 * сгенерированные приложением UUID.
 *
 * Пересоздание таблиц вместо ALTER: тип PRIMARY KEY меняется с INTEGER
 * на TEXT. Каждой колоде и карточке генерируется UUID, ссылки карточек
 * на колоды пересчитываются по карте oldId -> UUID (S2), остальные
 * колонки (включая статистику SM-2 и createdAt) переносятся как есть.
 * Перенесённые записи получают isChanged = 1, revision = 1,
 * isDeleted = 0 — они никогда не отправлялись на сервер и считаются
 * неподтверждёнными локальными правками (S3).
 *
 * FK отключается на время пересоздания: старые карточки ссылаются на
 * числовые id, которые не существуют в новой таблице колод. Миграции
 * Room выполняются вне транзакции, поэтому PRAGMA действует.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = OFF")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `decks_new` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`isChanged` INTEGER NOT NULL DEFAULT 1, " +
                "`revision` INTEGER NOT NULL DEFAULT 1, " +
                "`isDeleted` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cards_new` (" +
                "`id` TEXT NOT NULL, " +
                "`deckId` TEXT NOT NULL, " +
                "`front` TEXT NOT NULL, " +
                "`back` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                "`easinessFactor` REAL NOT NULL DEFAULT 2.5, " +
                "`repetitionNumber` INTEGER NOT NULL DEFAULT 0, " +
                "`intervalDays` INTEGER NOT NULL DEFAULT 0, " +
                "`dueDate` INTEGER NOT NULL DEFAULT 0, " +
                "`isChanged` INTEGER NOT NULL DEFAULT 1, " +
                "`revision` INTEGER NOT NULL DEFAULT 1, " +
                "`isDeleted` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )

        // Карта старых числовых id колод -> новые UUID (S2): по ней
        // пересчитываются ссылки карточек на колоды.
        val deckIdMap = mutableMapOf<Long, String>()
        db.query("SELECT id FROM decks").use { cursor ->
            while (cursor.moveToNext()) {
                deckIdMap[cursor.getLong(0)] = UUID.randomUUID().toString()
            }
        }

        for ((oldId, uuid) in deckIdMap) {
            db.execSQL(
                "INSERT INTO decks_new (id, name, isChanged, revision, isDeleted) " +
                    "SELECT ?, name, 1, 1, 0 FROM decks WHERE id = ?",
                arrayOf(uuid, oldId),
            )
        }

        db.query(
            "SELECT id, deckId, front, back, createdAt, easinessFactor, " +
                "repetitionNumber, intervalDays, dueDate FROM cards",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                db.execSQL(
                    "INSERT INTO cards_new (id, deckId, front, back, createdAt, " +
                        "easinessFactor, repetitionNumber, intervalDays, dueDate, " +
                        "isChanged, revision, isDeleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0)",
                    arrayOf(
                        UUID.randomUUID().toString(),
                        deckIdMap.getValue(cursor.getLong(1)),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        cursor.getDouble(5),
                        cursor.getInt(6),
                        cursor.getInt(7),
                        cursor.getLong(8),
                    ),
                )
            }
        }

        db.execSQL("DROP TABLE cards")
        db.execSQL("DROP TABLE decks")
        db.execSQL("ALTER TABLE decks_new RENAME TO decks")
        db.execSQL("ALTER TABLE cards_new RENAME TO cards")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_deckId` ON `cards` (`deckId`)")

        db.execSQL("PRAGMA foreign_keys = ON")
    }
}

/**
 * v4 -> v5 (change-009): состояние фоновой синхронизации — ревизии
 * потоков и итог последней сессии. Только новые таблицы, существующие
 * данные не затрагиваются; синх-поля записей (isChanged/revision/
 * isDeleted) существуют с v4 (change-007) и переиспользуются.
 *
 * Токены в Room не переносятся: они хранятся зашифрованными в
 * TokenStorage (change-008, S16), дублирование открытым текстом
 * недопустимо.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_states` (" +
                "`streamKey` TEXT NOT NULL, " +
                "`revision` INTEGER NOT NULL, " +
                "PRIMARY KEY(`streamKey`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `last_sync_result` (" +
                "`id` INTEGER NOT NULL, " +
                "`finishedAtMillis` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`errorMessage` TEXT, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

val ALL_MIGRATIONS = arrayOf<Migration>(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
)
