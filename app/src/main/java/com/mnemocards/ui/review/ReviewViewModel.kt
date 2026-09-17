package com.mnemocards.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration
import com.mnemocards.data.CardRepository
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.domain.scheduler.SchedulingState
import com.mnemocards.domain.scheduler.Sm2Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel сессии повторения (change add-sm2-scheduling).
 *
 * Очередь карточек живёт в ViewModel (переживает смену конфигурации);
 * сессия собирается из назревших карточек (dueDate <= now) с инъекцией
 * времени; состояния Loading / NotFound / Error / Session / Done — S16–S18,
 * S31, S32. Оценки: «Забыл» переносит карточку в конец очереди (S19, S21),
 * graduating-оценки закрывают карточку (S1–S3), ошибка записи — флаг
 * saveError без изменения очереди (S30).
 */
class ReviewViewModel(
    private val deckId: Long,
    private val repository: CardRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val queue = ArrayDeque<Card>()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            val now = nowProvider()
            try {
                // S32: устаревший идентификатор колоды — состояние NotFound.
                if (!repository.deckExists(deckId)) {
                    _uiState.value = ReviewUiState.NotFound
                    return@launch
                }
                // S16: в сессию попадают только карточки с due <= now.
                val dueCards = repository.dueCards(deckId, now)
                if (dueCards.isEmpty()) {
                    // S17, S18: нет назревших или колода пуста — сразу Done.
                    _uiState.value = ReviewUiState.Done
                    return@launch
                }
                queue.clear()
                queue.addAll(dueCards)
                _uiState.value = sessionState(queue.first())
            } catch (e: Exception) {
                // S31: ошибка чтения — состояние ошибки вместо сессии.
                _uiState.value = ReviewUiState.Error
            }
        }
    }

    /** Переворот карточки: лицевая сторона ↔ перевод. */
    fun onFlip() {
        val state = _uiState.value as? ReviewUiState.Session ?: return
        _uiState.value = state.copy(isFlipped = !state.isFlipped, saveError = false)
    }

    /**
     * Оценка ответа (S1–S4, S19–S21, S30). «Забыл» — applyGrade и перенос
     * карточки (с обновлёнными параметрами SM-2) в конец очереди; graduating
     * — applyGrade и удаление из очереди; пустая очередь — Done. При ошибке
     * записи карточка остаётся неоценённой, очередь не меняется.
     */
    fun onGrade(grade: ReviewGrade) {
        val state = _uiState.value as? ReviewUiState.Session ?: return
        viewModelScope.launch {
            val now = nowProvider()
            try {
                repository.applyGrade(state.card, grade, now)
                queue.removeFirst()
                if (grade == ReviewGrade.FORGOT) {
                    queue.addLast(withScheduling(state.card, grade, now))
                }
                _uiState.value =
                    if (queue.isEmpty()) ReviewUiState.Done else sessionState(queue.first())
            } catch (e: Exception) {
                // S30: карточка остаётся в сессии неоценённой, повторное
                // нажатие применяется после восстановления БД.
                _uiState.value = state.copy(saveError = true)
            }
        }
    }

    /**
     * Прогнозируемые интервалы четырёх кнопок (S14, S15): считаются тем же
     * Sm2Scheduler.next, который применяется при оценке, — подпись на кнопке
     * совпадает с записанным результатом.
     */
    private fun previewIntervals(card: Card, now: Long): Map<ReviewGrade, Duration> =
        Sm2Scheduler.preview(card.toSchedulingState(), now)

    private fun sessionState(
        card: Card,
        isFlipped: Boolean = false,
        saveError: Boolean = false,
    ): ReviewUiState.Session {
        val now = nowProvider()
        return ReviewUiState.Session(
            card = card,
            isFlipped = isFlipped,
            previews = previewIntervals(card, now),
            saveError = saveError,
        )
    }

    /**
     * Копия карточки с параметрами, которые записал Sm2Scheduler при
     * «Забыл»: EF уменьшился, прогресс сброшен — повторный показ в этой же
     * сессии считает превью от актуальных значений (S21).
     */
    private fun withScheduling(card: Card, grade: ReviewGrade, now: Long): Card {
        val next = Sm2Scheduler.next(card.toSchedulingState(), grade, now)
        return card.copy(
            easinessFactor = next.easinessFactor,
            repetitionNumber = next.repetitionNumber,
            intervalDays = next.intervalDays,
            dueDate = next.dueDate,
        )
    }

    private fun Card.toSchedulingState(): SchedulingState = SchedulingState(
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )
}

/**
 * Фабрика ViewModel с параметрами навигации (deckId) и зависимостями
 * из AppContainer.
 */
class ReviewViewModelFactory(
    private val deckId: Long,
    private val repository: CardRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReviewViewModel(deckId, repository) as T
}
