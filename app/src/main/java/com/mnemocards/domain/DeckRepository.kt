package com.mnemocards.domain

import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.flow.Flow

/**
 * Операции над колодами (offline-first: Room — основной источник данных).
 *
 * Контракт ошибок: исключения БД пробрасываются наружу (S22);
 * операции с несуществующим id — безопасный no-op (S21).
 *
 * change-007: идентификаторы — UUID (String), генерируются приложением;
 * createDeck больше не возвращает id — вызывающим он не нужен.
 */
interface DeckRepository {

    /** Живые колоды; мягко удалённые скрыты (S21). */
    fun observeDecks(): Flow<List<Deck>>

    /** Создаёт колоду; UUID генерируется на устройстве (S6). */
    suspend fun createDeck(name: String)

    /** Переименовывает; isChanged = true, revision + 1 (S11). */
    suspend fun renameDeck(id: String, name: String)

    /**
     * Удаляет колоду и все её карточки. Режим зависит от флага
     * синхронизации: выключен — физически (S18), включён — мягко (S20).
     */
    suspend fun deleteDeck(id: String)
}
