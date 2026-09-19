package com.mnemocards.data.db.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ревизия потока синхронизации (change add-background-sync).
 * Ключ потока: "decks" для списка колод, "cards:{deckUuid}" для карточек колоды.
 * Отсутствие записи трактуется как ревизия 0 (S4, S24).
 */
@Entity(tableName = "sync_states")
data class SyncStateEntity(
    @PrimaryKey
    val streamKey: String,
    val revision: Long,
)
