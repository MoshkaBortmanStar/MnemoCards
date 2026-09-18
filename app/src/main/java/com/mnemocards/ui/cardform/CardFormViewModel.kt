package com.mnemocards.ui.cardform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.repository.CardOperationResult
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.domain.CardFormValidator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot события формы карточки. */
sealed interface CardFormEvent {
    /** Успешное сохранение или отмена — закрыть форму (S5, S12, S14). */
    data object Closed : CardFormEvent

    /** 404-аналог: редактируемая карточка не найдена в Room (S25). */
    data object NotFound : CardFormEvent
}

/** Состояние формы карточки: значения полей, ошибки валидации и записи. */
data class CardFormUiState(
    val front: String = "",
    val back: String = "",
    val frontError: String? = null,
    val backError: String? = null,
    val isSaving: Boolean = false,
    /** 500-аналог: сообщение при ошибке записи в Room (S26). */
    val saveError: String? = null,
)

/**
 * ViewModel формы карточки (MVVM + UDF).
 * Режим добавления: cardId == null, поля пусты (S4).
 * Режим редактирования: предзаполнение по cardId (S11).
 * При сохранении: validate -> trim -> репозиторий -> маппинг результата.
 */
class CardFormViewModel(
    private val deckId: String,
    private val cardId: String?,
    private val repository: CardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardFormUiState())
    val uiState: StateFlow<CardFormUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CardFormEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<CardFormEvent> = _events.asSharedFlow()

    init {
        if (cardId != null) {
            // S11: предзаполнение полей текущими значениями карточки.
            viewModelScope.launch {
                val card = repository.getCardById(cardId)
                if (card != null) {
                    _uiState.update {
                        it.copy(front = card.front, back = card.back)
                    }
                } else {
                    // S25: карточка уже удалена — форму открывать нечем.
                    _events.tryEmit(CardFormEvent.NotFound)
                }
            }
        }
    }

    /** Ввод лицевой стороны; сбрасывает ошибку поля и ошибку записи. */
    fun onFrontChange(value: String) {
        _uiState.update {
            it.copy(front = value, frontError = null, saveError = null)
        }
    }

    /** Ввод перевода; сбрасывает ошибку поля и ошибку записи. */
    fun onBackChange(value: String) {
        _uiState.update {
            it.copy(back = value, backError = null, saveError = null)
        }
    }

    /**
     * Сохранение: валидация (400-аналоги S6–S8, S13), trim, затем
     * addCard или updateCard в зависимости от режима (S5, S12).
     */
    fun save() {
        val current = _uiState.value
        if (current.isSaving) return

        val errors = CardFormValidator.validateCardInput(current.front, current.back)
        if (!errors.isEmpty) {
            // Форма остаётся открытой, введённые значения не теряются (S6–S8).
            _uiState.update {
                it.copy(frontError = errors.front, backError = errors.back)
            }
            return
        }

        val front = current.front.trim()
        val back = current.back.trim()

        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            val result = if (cardId == null) {
                repository.addCard(deckId, front, back)
            } else {
                repository.updateCard(cardId, front, back)
            }
            _uiState.update { it.copy(isSaving = false) }
            when (result) {
                is CardOperationResult.Success ->
                    _events.tryEmit(CardFormEvent.Closed)
                is CardOperationResult.NotFound ->
                    // S25: update несуществующей записи не превращается в insert.
                    _events.tryEmit(CardFormEvent.NotFound)
                is CardOperationResult.Error ->
                    // S26: форма остаётся открытой, ввод не теряется.
                    _uiState.update {
                        it.copy(saveError = "Не удалось сохранить")
                    }
            }
        }
    }

    /** Закрытие формы без сохранения (S14): репозиторий не вызывается. */
    fun dismiss() {
        _events.tryEmit(CardFormEvent.Closed)
    }
}

/** Фабрика формы карточки: параметры навигации + репозиторий. */
class CardFormViewModelFactory(
    private val deckId: String,
    private val cardId: String?,
    private val repository: CardRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == CardFormViewModel::class.java)
        return CardFormViewModel(deckId, cardId, repository) as T
    }
}
