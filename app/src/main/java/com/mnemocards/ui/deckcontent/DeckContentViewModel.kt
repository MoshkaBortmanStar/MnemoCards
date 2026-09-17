package com.mnemocards.ui.deckcontent

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.csv.CsvEncoder
import com.mnemocards.data.csv.CsvParseResult
import com.mnemocards.data.csv.CsvParser
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.file.CsvFileGateway
import com.mnemocards.data.repository.CardImportResult
import com.mnemocards.data.repository.CardRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Состояние экрана содержимого колоды.
 * NotFound — 404-аналог (S24), Error — 500-аналог при чтении (S26).
 */
sealed interface DeckContentUiState {
    data object Loading : DeckContentUiState
    data class Content(val title: String, val cards: List<CardEntity>) : DeckContentUiState
    data class Empty(val title: String) : DeckContentUiState
    data object NotFound : DeckContentUiState
    data object Error : DeckContentUiState
}

/**
 * Сообщение о результате CSV-переноса (change-006). Семантическое:
 * тексты сообщений подставляет экран из ресурсов, ViewModel остаётся
 * без Android-зависимостей и тестируется фейками.
 */
sealed interface TransferMessage {
    data object Idle : TransferMessage
    data object FileSaved : TransferMessage
    data class ImportSummary(val added: Int, val skipped: Int) : TransferMessage
    data object SaveFailed : TransferMessage
    data object NotCsv : TransferMessage
    data object DeckNotFound : TransferMessage
    data object ImportFailed : TransferMessage
}

/**
 * ViewModel экрана содержимого колоды (MVVM + UDF).
 * Объединяет потоки колоды и её карточек из Room; состояние
 * живёт в StateFlow и переживает смену конфигурации.
 *
 * CSV-перенос (change-006): конвейер «чтение → разбор → вставка»
 * выполняется вне Main-диспетчера (S23); ошибка разбора показывается
 * до обращения к Room (S18, S19).
 */
class DeckContentViewModel(
    private val deckId: Long,
    private val repository: CardRepository,
    private val csvFileGateway: CsvFileGateway,
    private val encoder: CsvEncoder = CsvEncoder,
    private val parser: CsvParser = CsvParser(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** Счётчик попыток загрузки: инкремент в retry() перезапускает потоки. */
    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<DeckContentUiState> = retryTrigger
        .flatMapLatest { attempt ->
            combine(
                repository.observeDeck(deckId),
                repository.observeCards(deckId),
            ) { deck, cards ->
                when {
                    // S24: устаревший идентификатор колоды — не выдаём пустой список за успех.
                    deck == null -> DeckContentUiState.NotFound
                    cards.isEmpty() -> DeckContentUiState.Empty(deck.name)
                    else -> DeckContentUiState.Content(deck.name, cards)
                }
            }.catch {
                // S26: ошибка чтения Room — состояние ошибки, пустой список не выдаётся за успех.
                emit(DeckContentUiState.Error)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckContentUiState.Loading,
        )

    /** Повторная загрузка после ошибки чтения (S26). */
    fun retry() {
        retryTrigger.value = retryTrigger.value + 1
    }

    /** Удаление карточки; делегируется в репозиторий, ошибки не роняют UI (S16, S26). */
    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
        }
    }

    /**
     * Одно сообщение о результате переноса за раз; Idle — нечего показывать.
     * Экран сбрасывает через [onTransferMessageShown] после показа.
     */
    private val transfer = MutableStateFlow<TransferMessage>(TransferMessage.Idle)
    val transferMessage: StateFlow<TransferMessage> = transfer.asStateFlow()

    /** Повторный клик во время выполнения операции игнорируется. */
    private var transferRunning = false

    /**
     * S2, S6–S8: цель экспорта подтверждена в системном диалоге CreateDocument.
     * Отмена диалога сюда не попадает — сообщение остаётся Idle (S7).
     */
    fun onExportTarget(uri: Uri) {
        if (transferRunning) return
        transferRunning = true
        transfer.value = TransferMessage.Idle
        viewModelScope.launch {
            val message = try {
                val rows = withContext(ioDispatcher) { repository.exportCardTexts(deckId) }
                if (rows == null) {
                    TransferMessage.DeckNotFound
                } else {
                    val bytes = withContext(ioDispatcher) { encoder.encode(rows) }
                    csvFileGateway.write(uri, bytes)
                    TransferMessage.FileSaved
                }
            } catch (e: IOException) {
                TransferMessage.SaveFailed // S8: понятная ошибка, данные не тронуты
            } catch (e: Exception) {
                TransferMessage.SaveFailed
            }
            transferRunning = false
            transfer.value = message
        }
    }

    /**
     * S9–S23: файл выбран в системном диалоге OpenDocument. Разбор идёт
     * до записи в Room: некорректный файл не меняет колоду (S18, S19).
     */
    fun onImportFile(uri: Uri) {
        if (transferRunning) return
        transferRunning = true
        transfer.value = TransferMessage.Idle
        viewModelScope.launch {
            val message = try {
                val result = withContext(ioDispatcher) {
                    parser.parse(csvFileGateway.read(uri))
                }
                when (result) {
                    CsvParseResult.NotCsv -> TransferMessage.NotCsv
                    is CsvParseResult.Parsed -> {
                        val import = withContext(ioDispatcher) {
                            repository.importCards(deckId, result.cards)
                        }
                        when (import) {
                            CardImportResult.DeckNotFound -> TransferMessage.DeckNotFound
                            is CardImportResult.Imported -> TransferMessage.ImportSummary(
                                added = import.added,
                                skipped = result.skipped,
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                TransferMessage.NotCsv // S18: нечитаемый файл — ошибка формата
            } catch (e: Exception) {
                TransferMessage.ImportFailed // S22: ошибка БД, вставка откачена
            }
            transferRunning = false
            transfer.value = message
        }
    }

    /** Экран сообщил, что сообщение показано — возвращаемся в Idle. */
    fun onTransferMessageShown() {
        transfer.value = TransferMessage.Idle
    }
}
