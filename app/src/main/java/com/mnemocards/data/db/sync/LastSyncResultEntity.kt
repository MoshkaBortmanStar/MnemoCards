package com.mnemocards.data.db.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Итог последней синхронизации: время, статус, текст ошибки (S2, S13, S15).
 * Единственная строка (id = 1), перезаписывается по завершении сессии.
 */
@Entity(tableName = "last_sync_result")
data class LastSyncResultEntity(
    @PrimaryKey
    val id: Long = SINGLE_ROW_ID,
    val finishedAtMillis: Long,
    /** "success" | "error" | "requires_login" */
    val status: String,
    val errorMessage: String? = null,
) {
    companion object {
        const val SINGLE_ROW_ID = 1L
        const val STATUS_SUCCESS = "success"
        const val STATUS_ERROR = "error"
        const val STATUS_REQUIRES_LOGIN = "requires_login"
    }
}
