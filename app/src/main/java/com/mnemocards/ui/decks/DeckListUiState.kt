package com.mnemocards.ui.decks

import com.mnemocards.domain.model.Deck

/**
 * Состояния экрана списка колод (T7, change `add-offline-storage`).
 *
 * Экран всегда находится ровно в одном из состояний (MVVM + UDF:
 * состояние течёт вниз из `DeckListViewModel` через StateFlow):
 * - [Loading] — идёт первое чтение колод из Room (S3);
 * - [Content] — колоды загружены, для каждой есть название
 *   и счётчик карточек (S3);
 * - [Empty] — защитное пустое состояние: БД инициализирована,
 *   но колод нет, сидинг по какой-то причине не выполнился (S7);
 * - [Error] — ошибка инициализации или чтения БД (аналог 500)
 *   с доступным действием «Повторить» (S9).
 */
sealed interface DeckListUiState {

    /** Первое чтение списка колод из Room ещё не завершилось (S3). */
    data object Loading : DeckListUiState

    /** Колоды загружены: список доменных моделей «колода + счётчик карточек» (S3). */
    data class Content(val decks: List<Deck>) : DeckListUiState

    /** В БД нет ни одной колоды — заглушка «Пока нет колод» (S7). */
    data object Empty : DeckListUiState

    /** Чтение/открытие БД завершилось ошибкой — сообщение и кнопка «Повторить» (S9). */
    data object Error : DeckListUiState
}
