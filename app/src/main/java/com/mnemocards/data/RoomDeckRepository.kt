package com.mnemocards.data

import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.sync.SyncFlagDataStore
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Room-реализация DeckRepository: Room — single source of truth,
 * UI наблюдает изменения через Flow. Ошибки БД пробрасываются наружу (S22);
 * отсутствующий id — no-op (S21).
 *
 * change-007: UUID генерируется при создании (S6); переименование ставит
 * isChanged = true и revision + 1 (S11); удаление ветвится по флагу
 * синхронизации (S17–S20).
 */
class RoomDeckRepository(
    private val deckDao: DeckDao,
    private val syncFlagDataStore: SyncFlagDataStore? = null,
) : DeckRepository {

    override fun observeDecks(): Flow<List<Deck>> =
        deckDao.observeDecksWithStats(System.currentTimeMillis()).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun createDeck(name: String) {
        deckDao.insertDeck(
            DeckEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                isChanged = true,
                revision = 1,
                isDeleted = false,
            ),
        )
    }

    override suspend fun renameDeck(id: String, name: String) {
        deckDao.renameDeck(id, name.trim())
    }

    override suspend fun deleteDeck(id: String) {
        if (isSyncEnabled()) {
            // S20: мягкое удаление — колода и карточки остаются в БД
            // с пометками до будущей отправки на сервер.
            deckDao.softDeleteDeckWithCards(id)
        } else {
            // S18: физическое удаление, как в версии до change-007.
            deckDao.deleteDeckWithCards(id)
        }
    }

    /**
     * S16: ошибка чтения DataStore трактуется как выключенный флаг —
     * удаление выполняется в дефолтном (физическом) режиме.
     */
    private suspend fun isSyncEnabled(): Boolean =
        syncFlagDataStore?.let { store ->
            runCatching { store.syncEnabled.first() }.getOrDefault(false)
        } ?: false

    private fun com.mnemocards.data.db.DeckWithStats.toDomain(): Deck =
        Deck(
            id = deckId,
            name = deckName,
            cardCount = total,
        )
}
