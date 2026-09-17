package com.mnemocards.domain.repository

import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.ReviewCard
import kotlinx.coroutines.flow.Flow

/**
 * Единица доступа UI- и domain-слоёв к офлайн-данным: Room — единственный
 * источник истины (offline-first), сетевые источники в этой фиче не участвуют.
 *
 * Контракт по ошибкам (S9): любые исключения источника данных (включая ошибку
 * открытия/чтения БД) оборачиваются в [Result.failure] и отдаются наружу
 * как ошибочный результат — необработанные краши наверх не проходят,
 * а пустые/частичные данные не выдаются за валидные.
 */
interface DeckRepository {

    /**
     * Наблюдение за списком колод (S3): каждая колода — идентификатор,
     * название и количество карточек. Обновляется при изменении БД.
     */
    fun observeDecks(): Flow<Result<List<Deck>>>

    /**
     * Наблюдение за карточками выбранной колоды (S5): только карточки
     * с идентификатором [deckId]; источник данных сессии `cards.review`.
     */
    fun observeCards(deckId: Long): Flow<Result<List<ReviewCard>>>
}
