package com.mnemocards.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Карточка — пара «слово + перевод» с параметрами планирования SM-2
 * (change add-sm2-scheduling).
 *
 * change-007: идентификаторы [id] и ссылка [deckId] — UUID (String),
 * сгенерированные на устройстве; добавлены поля подготовки к
 * синхронизации [isChanged] / [revision] / [isDeleted] (см. [DeckEntity]).
 *
 * defaultValue полей планирования совпадают с DDL миграций v2/v3
 * (ALTER ... NOT NULL DEFAULT), иначе Room считает схемы несовместимыми.
 * defaultValue синхрополей — DDL миграции MIGRATION_3_4.
 */
@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deckId")],
)
data class CardEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    /**
     * Миллисекунды epoch — основа порядка «новые сверху» (createdAt DESC).
     */
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long,
    /** Коэффициент лёгкости SM-2, диапазон [1.3, 2.8]; новая карточка — 2.5. */
    @ColumnInfo(defaultValue = "2.5")
    val easinessFactor: Double = 2.5,
    /** Номер успешного повторения; 0 — карточка без истории (S24). */
    @ColumnInfo(defaultValue = "0")
    val repetitionNumber: Int = 0,
    /** Длина текущего интервала в целых днях; 0 — только до первой оценки. */
    @ColumnInfo(defaultValue = "0")
    val intervalDays: Int = 0,
    /** Момент следующего показа, epochMillis. */
    @ColumnInfo(defaultValue = "0")
    val dueDate: Long = 0L,
    /** Изменена локально и ещё не отправлена на сервер (change-007). */
    @ColumnInfo(defaultValue = "1")
    val isChanged: Boolean = true,
    /** Локальный счётчик правок: +1 на каждое изменение (change-007). */
    @ColumnInfo(defaultValue = "1")
    val revision: Int = 1,
    /** Мягкое удаление: скрыта из UI, остаётся в БД до отправки (change-007). */
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
)
