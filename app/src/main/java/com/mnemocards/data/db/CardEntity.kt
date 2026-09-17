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
 * Поля планирования (easinessFactor, repetitionNumber, intervalDays,
 * dueDate) добавлены в версии схемы 3 миграцией MIGRATION_2_3 со
 * значениями по умолчанию; defaultValue совпадает с ALTER ... NOT NULL
 * DEFAULT, иначе Room считает схемы несовместимыми. dueDate в миграции
 * заполняется моментом её выполнения (S28).
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
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deckId: Long,
    val front: String,
    val back: String,
    /**
     * Миллисекунды epoch — основа порядка «новые сверху» (createdAt DESC).
     * defaultValue совпадает с MIGRATION_1_2 (ALTER ... NOT NULL DEFAULT 0),
     * иначе Room считает схемы v2 и «свежей» несовместимыми.
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
    /**
     * Момент следующего показа, epochMillis. Дефолт миграции — «сейчас»
     * (backfill после ALTER, как у createdAt): существующие карточки
     * становятся готовыми к повторению сразу (S28).
     */
    @ColumnInfo(defaultValue = "0")
    val dueDate: Long = 0L,
)
