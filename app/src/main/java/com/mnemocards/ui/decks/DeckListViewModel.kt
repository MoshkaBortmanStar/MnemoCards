package com.mnemocards.ui.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel стартового экрана со списком колод (T7, change `add-offline-storage`).
 *
 * UDF: события текут вверх ([retry], [onDeckClick]), состояние — вниз
 * ([uiState] на базе [StateFlow], экран лишь декларативно его отрисовывает).
 *
 * Сценарии spec-bdd:
 * - S3 — список колод «название + счётчик карточек» строится подпиской
 *   на `repository.observeDecks()` (Flow из Room);
 * - S7 — пустая БД → состояние [DeckListUiState.Empty];
 * - S9 — ошибка БД → [DeckListUiState.Error], [retry] переподписывается
 *   на Flow репозитория и восстанавливает данные;
 * - S11 — состояние живёт во ViewModel и переживает смену конфигурации:
 *   повторного сидинга и повторной загрузки при повороте не происходит.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModel(
    private val repository: DeckRepository,
) : ViewModel() {

    /**
     * Счётчик подписок на репозиторий. Инкремент в [retry] даёт новое
     * значение во внешнем Flow, [flatMapLatest] реагирует отменой текущей
     * подписки и открытием новой — повторная загрузка без пересоздания
     * ViewModel и без сброса в Loading.
     */
    private val retryRequests = MutableStateFlow(0)

    val uiState: StateFlow<DeckListUiState> = retryRequests
        .flatMapLatest { repository.observeDecks() }
        .map { result -> result.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = DeckListUiState.Loading,
        )

    /**
     * Одноразовые события навигации «список → тренировка колоды» (S4).
     * Channel вместо StateFlow: событие не должно воспроизводиться повторно
     * при пересоздании подписчика (смена конфигурации).
     */
    private val navigationEventChannel = Channel<Long>(Channel.BUFFERED)

    /** Поток идентификаторов колод для перехода на маршрут `review/{deckId}`. */
    val navigationEvents: Flow<Long> = navigationEventChannel.receiveAsFlow()

    /**
     * Действие «Повторить» из состояния [DeckListUiState.Error] (S9):
     * переподписка на поток репозитория. Промежуточный Loading не эмитится —
     * следующим состоянием будет либо восстановленный контент,
     * либо ошибка снова.
     */
    fun retry() {
        retryRequests.value += 1
    }

    /**
     * Тап по колоде в списке (S4): формирует событие навигации на экран
     * тренировки выбранной колоды.
     */
    fun onDeckClick(deckId: Long) {
        viewModelScope.launch {
            navigationEventChannel.send(deckId)
        }
    }

    private fun Result<List<Deck>>.toUiState(): DeckListUiState = fold(
        onSuccess = { decks ->
            if (decks.isEmpty()) DeckListUiState.Empty else DeckListUiState.Content(decks)
        },
        onFailure = { DeckListUiState.Error },
    )

    private companion object {

        /**
         * Таймаут удержания подписки при отсутствии коллектора (S11):
         * пересоздание Activity не перезапускает загрузку и сидинг,
         * данные перечитываются из Room только по истечении таймаута.
         */
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
