package com.mnemocards.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние экрана «Список колод» (UDF: состояние течёт вниз). */
interface DeckListUiState {
    val decks: List<Deck>
    val isLoading: Boolean
    val errorMessage: String?
    val isCreateDialogShown: Boolean
    val createDeckName: String
    val isCreateNameError: Boolean
    val selectedDeck: Deck?
    val isMenuShown: Boolean
    val isRenameDialogShown: Boolean
    val renameDeckName: String
    val isRenameNameError: Boolean
    val isDeleteDialogShown: Boolean
}

private data class DeckListUiStateImpl(
    override val decks: List<Deck> = emptyList(),
    override val isLoading: Boolean = true,
    override val errorMessage: String? = null,
    override val isCreateDialogShown: Boolean = false,
    override val createDeckName: String = "",
    override val isCreateNameError: Boolean = false,
    override val selectedDeck: Deck? = null,
    override val isMenuShown: Boolean = false,
    override val isRenameDialogShown: Boolean = false,
    override val renameDeckName: String = "",
    override val isRenameNameError: Boolean = false,
    override val isDeleteDialogShown: Boolean = false
) : DeckListUiState

/**
 * ViewModel списка колод. Room — single source of truth: список колод
 * наблюдается через Flow репозитория; мутации применяются в Room,
 * UI обновляется автоматически. Устаревший элемент (колода удалена
 * из Room уже после открытия меню/диалога) исчезает из списка сам —
 * поэтому операции по отсутствующему id безопасны как no-op (S21).
 * Специальных веток для базовой колоды нет (S14, S15).
 */
class DeckListViewModel(
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckListUiStateImpl())
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    init {
        deckRepository.observeDecks()
            .onEach { decks ->
                _uiState.update { it.copy(decks = decks, isLoading = false) }
            }
            .catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Ошибка чтения базы данных"
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

    fun onDeckLongPressed(deck: Deck) {
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

    class Factory(private val deckRepository: DeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DeckListViewModel(deckRepository) as T
    }
}
