package com.mnemocards.data.db.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO состояния синхронизации (change-009): ревизии потоков и итог
 * последней сессии. Токены здесь не хранятся — они в зашифрованном
 * TokenStorage (change-008, S16).
 */
@Dao
interface SyncStateDao {

    // --- Ревизии потоков (S1, S4, S24) ---

    /** Ревизия потока; отсутствие записи трактуется как 0 (S4). */
    @Query(
        "SELECT COALESCE((SELECT revision FROM sync_states WHERE streamKey = :streamKey), 0)",
    )
    suspend fun getRevision(streamKey: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRevision(state: SyncStateEntity)

    /**
     * Сброс ревизий всех потоков. Нужен при смене сервера: ревизии выданы
     * ПРЕЖНИМ сервером и для нового ничего не значат. Без сброса клиент
     * просит `since` из чужой нумерации и получает fullSync с пустым
     * срезом — локальные записи при этом удалялись бы как «отсутствующие
     * на сервере».
     */
    @Query("DELETE FROM sync_states")
    suspend fun clearRevisions()

    @Query("SELECT * FROM sync_states")
    suspend fun getAllSyncStates(): List<SyncStateEntity>

    @Query("SELECT * FROM sync_states")
    fun observeAllSyncStates(): Flow<List<SyncStateEntity>>

    // --- Итог последней синхронизации (S2, S13, S15) ---

    @Query("SELECT * FROM last_sync_result WHERE id = 1")
    suspend fun getLastSyncResult(): LastSyncResultEntity?

    @Query("SELECT * FROM last_sync_result WHERE id = 1")
    fun observeLastSyncResult(): Flow<LastSyncResultEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLastSyncResult(result: LastSyncResultEntity)
}
