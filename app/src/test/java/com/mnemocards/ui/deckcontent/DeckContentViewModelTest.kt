package com.mnemocards.ui.deckcontent

import android.net.Uri
import app.cash.turbine.test
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.file.CsvFileGateway
import com.mnemocards.data.repository.CardImportResult
import com.mnemocards.data.repository.CardOperationResult
import com.mnemocards.data.repository.CardRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric: android.net.Uri в тестах переноса (change-006). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class DeckContentViewModelTest {

    private companion object {
        const val DECK_ID = "deck-1"
    }

    private val dispatcher = UnconfinedTestDispatcher()

    /** Fake-репозиторий: потоки через MutableStateFlow, запись фиксируется списками. */
    private class FakeCardRepository : CardRepository {
        val deck = MutableStateFlow<DeckEntity?>(null)
        val cards = MutableStateFlow<List<CardEntity>>(emptyList())

        /** Если не null — потоки наблюдения бросают исключение (500-аналог, S26). */
        var observeError: Throwable? = null

        val addedDecks = mutableListOf<String>()
        val updatedCards = mutableListOf<String>()
        val deletedCards = mutableListOf<String>()

        var addResult: CardOperationResult = CardOperationResult.Success("c1")
        var updateResult: CardOperationResult = CardOperationResult.Success("c1")
        var deleteResult: CardOperationResult = CardOperationResult.Success("c1")

        private fun <T> throwingOr(flow: Flow<T>): Flow<T> =
            if (observeError != null) {
                kotlinx.coroutines.flow.flow<T> { throw observeError!! }
            } else {
                flow
            }

        override fun observeDeck(deckId: String): Flow<DeckEntity?> =
            if (observeError != null) throwingOr(deck) else deck

        override fun observeCards(deckId: String): Flow<List<CardEntity>> =
            if (observeError != null) throwingOr(cards) else cards

        override fun observeCardCount(deckId: String): Flow<Int> =
            kotlinx.coroutines.flow.flowOf(cards.value.size)

        override suspend fun getCardById(id: String): CardEntity? = null

        override suspend fun addCard(
            deckId: String,
            front: String,
            back: String,
        ): CardOperationResult {
            addedDecks += deckId
            return addResult
        }

        override suspend fun updateCard(
            cardId: String,
            front: String,
            back: String,
        ): CardOperationResult {
            updatedCards += cardId
            return updateResult
        }

        override suspend fun deleteCard(cardId: String): CardOperationResult {
            deletedCards += cardId
            return deleteResult
        }

        /** CSV-перенос (change-006). */
        var exportTexts: List<Pair<String, String>>? = emptyList()
        var importResult: CardImportResult = CardImportResult.Imported(0)
        var importError: Exception? = null
        val importedBatches = mutableListOf<Pair<String, List<Pair<String, String>>>>()

        override suspend fun exportCardTexts(deckId: String): List<Pair<String, String>>? =
            exportTexts

        override suspend fun importCards(
            deckId: String,
            cards: List<Pair<String, String>>,
        ): CardImportResult {
            importedBatches += deckId to cards
            importError?.let { throw it }
            return importResult
        }
    }

    /** Fake SAF-шлюза: фиксирует запись, отдаёт настроенное содержимое. */
    private class FakeCsvFileGateway : CsvFileGateway {
        val written = mutableListOf<Pair<Uri, ByteArray>>()
        var readBytes: ByteArray = ByteArray(0)
        var writeError: Exception? = null
        var readError: Exception? = null

        override suspend fun write(uri: Uri, bytes: ByteArray) {
            writeError?.let { throw it }
            written += uri to bytes
        }

        override suspend fun read(uri: Uri): ByteArray {
            readError?.let { throw it }
            return readBytes
        }
    }

    private lateinit var repository: FakeCardRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCardRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun card(id: String, front: String, back: String) =
        CardEntity(id = id, deckId = DECK_ID, front = front, back = back, createdAt = id.hashCode().toLong())

    // S1: колода с карточками -> Loading, затем Content с названием и списком.
    @Test
    fun deckWithCards_emitsContent() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = DECK_ID, name = "Английский базовый")
        repository.cards.value = listOf(
            card("c3", "cat", "кошка"),
            card("c2", "world", "мир"),
            card("c1", "hello", "привет"),
        )

        val viewModel = DeckContentViewModel(deckId = DECK_ID, repository = repository, csvFileGateway = FakeCsvFileGateway())

        // Fake с готовыми значениями + Unconfined-диспетчер: состояние
        // устаканивается синхронно, StateFlow конфлетерирует Loading —
        // первая наблюдаемая эмиссия уже Content.
        viewModel.uiState.test {
            assertEquals(
                DeckContentUiState.Content(
                    title = "Английский базовый",
                    cards = listOf(
                        card("c3", "cat", "кошка"),
                        card("c2", "world", "мир"),
                        card("c1", "hello", "привет"),
                    ),
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S2: колода без карточек -> Empty (не Content с пустым списком).
    @Test
    fun deckWithoutCards_emitsEmpty() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = DECK_ID, name = "Английский базовый")
        repository.cards.value = emptyList()

        val viewModel = DeckContentViewModel(deckId = DECK_ID, repository = repository, csvFileGateway = FakeCsvFileGateway())

        viewModel.uiState.test {
            assertEquals(
                DeckContentUiState.Empty(title = "Английский базовый"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S24: колоды нет (observeDeck == null) -> NotFound, а не пустой список.
    @Test
    fun missingDeck_emitsNotFound() = runTest(dispatcher) {
        repository.deck.value = null
        repository.cards.value = emptyList()

        val viewModel = DeckContentViewModel(deckId = "deck-99", repository = repository, csvFileGateway = FakeCsvFileGateway())

        viewModel.uiState.test {
            assertEquals(DeckContentUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S26: ошибка чтения -> Error; retry() восстанавливает поток после устранения ошибки.
    @Test
    fun readError_emitsError_thenRetryRecovers() = runTest(dispatcher) {
        repository.observeError = RuntimeException("corrupted database")

        val viewModel = DeckContentViewModel(deckId = DECK_ID, repository = repository, csvFileGateway = FakeCsvFileGateway())

        viewModel.uiState.test {
            assertEquals(DeckContentUiState.Error, awaitItem())

            // Ошибка устранена, данные появились.
            repository.observeError = null
            repository.deck.value = DeckEntity(id = DECK_ID, name = "Английский базовый")
            repository.cards.value = listOf(card("c1", "hello", "привет"))

            viewModel.retry()
            assertEquals(
                DeckContentUiState.Content(
                    title = "Английский базовый",
                    cards = listOf(card("c1", "hello", "привет")),
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S16: deleteCard делегируется в репозиторий.
    @Test
    fun deleteCard_delegatesToRepository() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = DECK_ID, name = "Английский базовый")
        repository.cards.value = listOf(card("c42", "cat", "кошка"))

        val viewModel = DeckContentViewModel(deckId = DECK_ID, repository = repository, csvFileGateway = FakeCsvFileGateway())

        viewModel.uiState.test {
            awaitItem() // Content (устаканившееся состояние, Loading конфлетерируется)
            viewModel.deleteCard("c42")
            assertEquals(listOf("c42"), repository.deletedCards)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S26: ошибка удаления не роняет состояние и не трогает репозиторий повторно.
    @Test
    fun deleteCard_error_keepsState() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = DECK_ID, name = "Английский базовый")
        repository.cards.value = listOf(card("c42", "cat", "кошка"))
        repository.deleteResult = CardOperationResult.Error(RuntimeException("delete failed"))

        val viewModel = DeckContentViewModel(deckId = DECK_ID, repository = repository, csvFileGateway = FakeCsvFileGateway())

        viewModel.uiState.test {
            val content = awaitItem()
            assertTrue(content is DeckContentUiState.Content)
            viewModel.deleteCard("c42")
            assertEquals(listOf("c42"), repository.deletedCards)
            // Список не изменился — состояние остаётся прежним Content.
            assertEquals(content, viewModel.uiState.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- CSV-перенос (change-006: add-csv-import-export) ---

    private val exportUri: Uri = Uri.parse("content://test/export.csv")
    private val importUri: Uri = Uri.parse("content://test/import.csv")

    /** ViewModel с IO-диспетчером теста — детерминированное выполнение. */
    private fun transferViewModel(gateway: FakeCsvFileGateway): DeckContentViewModel =
        DeckContentViewModel(
            deckId = DECK_ID,
            repository = repository,
            csvFileGateway = gateway,
            ioDispatcher = dispatcher,
        )

    // S2/S3: экспорт пишет CSV с BOM и заголовком, сообщение FileSaved.
    @Test
    fun export_writesCsvWithBom_andShowsSaved() = runTest(dispatcher) {
        repository.exportTexts = listOf("cat" to "кошка", "hello, world" to "мир")
        val gateway = FakeCsvFileGateway()
        val viewModel = transferViewModel(gateway)

        viewModel.onExportTarget(exportUri)

        assertEquals(TransferMessage.FileSaved, viewModel.transferMessage.value)
        val written = gateway.written.single()
        assertEquals(exportUri, written.first)
        // S3: файл начинается с UTF-8 BOM (EF BB BF).
        assertEquals(listOf(0xEF, 0xBB, 0xBF), written.second.copyOfRange(0, 3).map { it.toInt() and 0xFF })
        // S2: первая строка — заголовок.
        val text = written.second.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        assertTrue(text.startsWith("front,back"))
    }

    // S21-аналог для экспорта: колода не найдена — файл не создаётся.
    @Test
    fun export_deckNotFound_showsMessage_andWritesNothing() = runTest(dispatcher) {
        repository.exportTexts = null
        val gateway = FakeCsvFileGateway()
        val viewModel = transferViewModel(gateway)

        viewModel.onExportTarget(exportUri)

        assertEquals(TransferMessage.DeckNotFound, viewModel.transferMessage.value)
        assertTrue(gateway.written.isEmpty())
    }

    // S8: ошибка записи → SaveFailed.
    @Test
    fun export_writeFails_showsSaveFailed() = runTest(dispatcher) {
        repository.exportTexts = listOf("cat" to "кошка")
        val gateway = FakeCsvFileGateway().apply { writeError = IOException("no space left") }
        val viewModel = transferViewModel(gateway)

        viewModel.onExportTarget(exportUri)

        assertEquals(TransferMessage.SaveFailed, viewModel.transferMessage.value)
    }

    // S9: валидный CSV → вставка в репозиторий, итог с числом пропущенных.
    @Test
    fun import_parsesCsv_andShowsSummary() = runTest(dispatcher) {
        repository.importResult = CardImportResult.Imported(2)
        val gateway = FakeCsvFileGateway().apply {
            readBytes = "front,back\r\ncat,кошка\r\n,пусто\r\nsun,солнце\r\n".toByteArray()
        }
        val viewModel = transferViewModel(gateway)

        viewModel.onImportFile(importUri)

        assertEquals(TransferMessage.ImportSummary(added = 2, skipped = 1), viewModel.transferMessage.value)
        assertEquals(
            listOf(DECK_ID to listOf("cat" to "кошка", "sun" to "солнце")),
            repository.importedBatches,
        )
    }

    // S14: файл Excel в русской локале с разделителем ';' импортируется.
    @Test
    fun import_semicolonDelimiter_isDetected() = runTest(dispatcher) {
        repository.importResult = CardImportResult.Imported(1)
        val gateway = FakeCsvFileGateway().apply {
            readBytes = "front;back\r\ncat;кошка\r\n".toByteArray()
        }
        val viewModel = transferViewModel(gateway)

        viewModel.onImportFile(importUri)

        assertEquals(TransferMessage.ImportSummary(added = 1, skipped = 0), viewModel.transferMessage.value)
        assertEquals(listOf(DECK_ID to listOf("cat" to "кошка")), repository.importedBatches)
    }

    // S11: файл без строки заголовка — первая строка становится карточкой.
    @Test
    fun import_withoutHeader_firstRowIsCard() = runTest(dispatcher) {
        repository.importResult = CardImportResult.Imported(2)
        val gateway = FakeCsvFileGateway().apply {
            readBytes = "cat,кошка\r\ndog,собака\r\n".toByteArray()
        }
        val viewModel = transferViewModel(gateway)

        viewModel.onImportFile(importUri)

        assertEquals(TransferMessage.ImportSummary(added = 2, skipped = 0), viewModel.transferMessage.value)
        assertEquals(
            listOf(DECK_ID to listOf("cat" to "кошка", "dog" to "собака")),
            repository.importedBatches,
        )
    }

    // S18: бинарный мусор / некорректный UTF-8 → NotCsv, к Room не обращаемся.
    @Test
    fun import_invalidUtf8_showsNotCsv() = runTest(dispatcher) {
        val gateway = FakeCsvFileGateway().apply {
            readBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x01)
        }
        val viewModel = transferViewModel(gateway)

        viewModel.onImportFile(importUri)

        assertEquals(TransferMessage.NotCsv, viewModel.transferMessage.value)
        assertTrue(repository.importedBatches.isEmpty())
    }

    // S19: пустой файл → NotCsv.
    @Test
    fun import_emptyFile_showsNotCsv() = runTest(dispatcher) {
        val gateway = FakeCsvFileGateway().apply { readBytes = ByteArray(0) }
        val viewModel = transferViewModel(gateway)

        viewModel.onImportFile(importUri)

        assertEquals(TransferMessage.NotCsv, viewModel.transferMessage.value)
    }

    // S21: колода исчезла к моменту вставки → DeckNotFound.
    @Test
    fun import_deckNotFound_showsMessage() = runTest(dispatcher) {
        repository.importResult = CardImportResult.DeckNotFound
        val gateway = FakeCsvFileGateway().apply {
            readBytes = "front,back\r\ncat,кошка\r\n".toByteArray()
        }
        val viewModel = transferViewModel(gateway)

        viewModel.onImportFile(importUri)

        assertEquals(TransferMessage.DeckNotFound, viewModel.transferMessage.value)
    }

    // S22: ошибка БД при вставке → ImportFailed (вставка откачена в DAO).
    @Test
    fun import_repositoryThrows_showsImportFailed() = runTest(dispatcher) {
        repository.importError = RuntimeException("disk I/O error")
        val gateway = FakeCsvFileGateway().apply {
            readBytes = "front,back\r\ncat,кошка\r\n".toByteArray()
        }
        val viewModel = transferViewModel(gateway)

        viewModel.onImportFile(importUri)

        assertEquals(TransferMessage.ImportFailed, viewModel.transferMessage.value)
    }
}
