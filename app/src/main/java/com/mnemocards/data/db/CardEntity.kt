package com.mnemocards.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
)
