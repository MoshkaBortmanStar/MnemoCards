package com.mnemocards.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.DeckCountsRepository
import com.mnemocards.domain.DeckRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Элемент списка колод (change add-sm2-scheduling): общее количество
 * карточек и сколько из них готово к повторению сегодня (S24–S27).
 */
data class DeckListItem(
    val id: String,
    val name: String,
    val total: Int,
    val dueToday: Int,
)

/** Состояние экрана «Список колод» (UDF: состояние течёт вниз). */
interface DeckListUiState {
    val decks: List<DeckListItem>
    val isLoading: Boolean
    val errorMessage: String?
    val isCreateDialogShown: Boolean
    val createDeckName: String
    val isCreateNameError: Boolean
    val selectedDeck: DeckListItem?
    val isMenuShown: Boolean
    val isRenameDialogShown: Boolean
    val renameDeckName: String
    val isRenameNameError: Boolean
    val isDeleteDialogShown: Boolean
}

private data class DeckListUiStateImpl(
    override val decks: List<DeckListItem> = emptyList(),
    override val isLoading: Boolean = true,
    override val errorMessage: String? = null,
    override val isCreateDialogShown: Boolean = false,
    override val createDeckName: String = "",
    override val isCreateNameError: Boolean = false,
    override val selectedDeck: DeckListItem? = null,
    override val isMenuShown: Boolean = false,
    override val isRenameDialogShown: Boolean = false,
    override val renameDeckName: String = "",
    override val isRenameNameError: Boolean = false,
    override val isDeleteDialogShown: Boolean = false,
) : DeckListUiState

/**
 * ViewModel списка колод. Room — single source of truth: список колод и
 * счётчики (total, dueToday) наблюдаются через Flow, после оценок значения
 * обновляются автоматически, без ручного обновления (S26). Устаревший
 * элемент (колода удалена из Room уже после открытия меню/диалога)
 * исчезает из списка сам — операции по отсутствующему id безопасны как
 * no-op (S21). Специальных веток для базовой колоды нет (S14, S15).
 */
class DeckListViewModel(
    private val deckRepository: DeckRepository,
    countsRepository: DeckCountsRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckListUiStateImpl())
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    init {
        combine(
            deckRepository.observeDecks(),
            countsRepository.observeDecksWithCounts(nowProvider()),
        ) { decks, counts ->
            // Счётчики приходят из Room одним запросом (S25–S27);
            // для колоды без записи (нет карточек) — total из доменной
            // модели, dueToday = 0.
            val countsById = counts.associateBy { it.deckId }
            decks.map { deck ->
                val deckCounts = countsById[deck.id]
                DeckListItem(
                    id = deck.id,
                    name = deck.name,
                    total = deckCounts?.total ?: deck.cardCount,
                    dueToday = deckCounts?.dueToday ?: 0,
                )
            }
        }
            .onEach { items ->
                _uiState.update { it.copy(decks = items, isLoading = false) }
            }
            .catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Ошибка чтения базы данных",
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // --- Создание колоды (S1–S5) ---

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                isCreateDialogShown = true,
                createDeckName = "",
                isCreateNameError = false
            )
        }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(isCreateDialogShown = false, isCreateNameError = false) }
    }

    fun updateCreateDeckName(name: String) {
        _uiState.update { it.copy(createDeckName = name) }
    }

    fun createDeck() {
        val name = _uiState.value.createDeckName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(isCreateNameError = true) }
            return
        }
        viewModelScope.launch {
            try {
                deckRepository.createDeck(name)
                _uiState.update { it.copy(isCreateDialogShown = false, isCreateNameError = false) }
            } catch (error: RuntimeException) {
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Ошибка записи в базу данных")
                }
            }
        }
    }

    // --- Контекстное меню (S6, S7) ---

    fun onDeckLongPressed(deck: DeckListItem) {
        _uiState.update { it.copy(selectedDeck = deck, isMenuShown = true) }
    }

    /** Закрытие меню без выбора: список и Room не меняются (S7). */
    fun dismissMenu() {
        _uiState.update { it.copy(isMenuShown = false, selectedDeck = null) }
    }

    // --- Переименование (S8–S11) ---

    fun openRenameDialog() {
        val deck = _uiState.value.selectedDeck ?: return
        _uiState.update {
            it.copy(
                isMenuShown = false,
                isRenameDialogShown = true,
                renameDeckName = deck.name,
                isRenameNameError = false
            )
        }
    }

    fun dismissRenameDialog() {
        _uiState.update {
            it.copy(
                isRenameDialogShown = false,
                isRenameNameError = false,
                selectedDeck = null
            )
        }
    }

    fun updateRenameDeckName(name: String) {
        _uiState.update { it.copy(renameDeckName = name) }
    }

    fun renameDeck() {
        val state = _uiState.value
        val deck = state.selectedDeck ?: return
        val name = state.renameDeckName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(isRenameNameError = true) }
            return
        }
        viewModelScope.launch {
            try {
                deckRepository.renameDeck(deck.id, name)
                _uiState.update {
                    it.copy(
                        isRenameDialogShown = false,
                        isRenameNameError = false,
                        selectedDeck = null
                    )
                }
            } catch (error: RuntimeException) {
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Ошибка записи в базу данных")
                }
            }
        }
    }

    // --- Удаление (S12, S13) ---

    fun openDeleteDialog() {
        if (_uiState.value.selectedDeck == null) return
        _uiState.update { it.copy(isMenuShown = false, isDeleteDialogShown = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(isDeleteDialogShown = false, selectedDeck = null) }
    }

    fun deleteDeck() {
        val deck = _uiState.value.selectedDeck ?: return
        viewModelScope.launch {
            try {
                deckRepository.deleteDeck(deck.id)
                _uiState.update { it.copy(isDeleteDialogShown = false, selectedDeck = null) }
            } catch (error: RuntimeException) {
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Ошибка записи в базу данных")
                }
            }
        }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val deckRepository: DeckRepository,
        private val countsRepository: DeckCountsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DeckListViewModel(deckRepository, countsRepository) as T
    }
}
