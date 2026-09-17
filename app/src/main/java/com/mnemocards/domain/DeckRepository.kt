package com.mnemocards.domain

import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.flow.Flow

/**
 * Операции над колодами (offline-first: Room — основной источник данных).
 *
 * Контракт ошибок: исключения БД пробрасываются наружу (S22);
 * операции с несуществующим id — безопасный no-op (S21).
 */
interface DeckRepository {

    fun observeDecks(): Flow<List<Deck>>

    /** Создаёт колоду; название обрезается trim. Возвращает id новой записи. */
    suspend fun createDeck(name: String): Long

    /** Переименовывает колоду по id; несуществующий id — no-op. */
    suspend fun renameDeck(id: Long, name: String)

    /** Удаляет колоду и все её карточки; несуществующий id — no-op. */
    suspend fun deleteDeck(id: Long)
}
