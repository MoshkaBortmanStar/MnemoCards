package com.mnemocards.ui.deckcontent

import app.cash.turbine.test
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.repository.CardOperationResult
import com.mnemocards.data.repository.CardRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class DeckContentViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Fake-репозиторий: потоки через MutableStateFlow, запись фиксируется списками. */
    private class FakeCardRepository : CardRepository {
        val deck = MutableStateFlow<DeckEntity?>(null)
        val cards = MutableStateFlow<List<CardEntity>>(emptyList())

        /** Если не null — потоки наблюдения бросают исключение (500-аналог, S26). */
        var observeError: Throwable? = null

        val addedDecks = mutableListOf<Long>()
        val updatedCards = mutableListOf<Long>()
        val deletedCards = mutableListOf<Long>()

        var addResult: CardOperationResult = CardOperationResult.Success(1L)
        var updateResult: CardOperationResult = CardOperationResult.Success(1L)
        var deleteResult: CardOperationResult = CardOperationResult.Success(1L)

        private fun <T> throwingOr(flow: Flow<T>): Flow<T> =
            if (observeError != null) {
                kotlinx.coroutines.flow.flow<T> { throw observeError!! }
            } else {
                flow
            }

        override fun observeDeck(deckId: Long): Flow<DeckEntity?> =
            if (observeError != null) throwingOr(deck) else deck

        override fun observeCards(deckId: Long): Flow<List<CardEntity>> =
            if (observeError != null) throwingOr(cards) else cards

        override fun observeCardCount(deckId: Long): Flow<Int> =
            kotlinx.coroutines.flow.flowOf(cards.value.size)

        override suspend fun getCardById(id: Long): CardEntity? = null

        override suspend fun addCard(
            deckId: Long,
            front: String,
            back: String,
        ): CardOperationResult {
            addedDecks += deckId
            return addResult
        }

        override suspend fun updateCard(
            cardId: Long,
            front: String,
            back: String,
        ): CardOperationResult {
            updatedCards += cardId
            return updateResult
        }

        override suspend fun deleteCard(cardId: Long): CardOperationResult {
            deletedCards += cardId
            return deleteResult
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

    private fun card(id: Long, front: String, back: String) =
        CardEntity(id = id, deckId = 1L, front = front, back = back, createdAt = id)

    // S1: колода с карточками -> Loading, затем Content с названием и списком.
    @Test
    fun deckWithCards_emitsContent() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = 1L, name = "Английский базовый")
        repository.cards.value = listOf(
            card(3, "cat", "кошка"),
            card(2, "world", "мир"),
            card(1, "hello", "привет"),
        )

        val viewModel = DeckContentViewModel(deckId = 1L, repository = repository)

        // Fake с готовыми значениями + Unconfined-диспетчер: состояние
        // устаканивается синхронно, StateFlow конфлетерирует Loading —
        // первая наблюдаемая эмиссия уже Content.
        viewModel.uiState.test {
            assertEquals(
                DeckContentUiState.Content(
                    title = "Английский базовый",
                    cards = listOf(
                        card(3, "cat", "кошка"),
                        card(2, "world", "мир"),
                        card(1, "hello", "привет"),
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
        repository.deck.value = DeckEntity(id = 1L, name = "Английский базовый")
        repository.cards.value = emptyList()

        val viewModel = DeckContentViewModel(deckId = 1L, repository = repository)

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

        val viewModel = DeckContentViewModel(deckId = 99L, repository = repository)

        viewModel.uiState.test {
            assertEquals(DeckContentUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S26: ошибка чтения -> Error; retry() восстанавливает поток после устранения ошибки.
    @Test
    fun readError_emitsError_thenRetryRecovers() = runTest(dispatcher) {
        repository.observeError = RuntimeException("corrupted database")

        val viewModel = DeckContentViewModel(deckId = 1L, repository = repository)

        viewModel.uiState.test {
            assertEquals(DeckContentUiState.Error, awaitItem())

            // Ошибка устранена, данные появились.
            repository.observeError = null
            repository.deck.value = DeckEntity(id = 1L, name = "Английский базовый")
            repository.cards.value = listOf(card(1, "hello", "привет"))

            viewModel.retry()
            assertEquals(
                DeckContentUiState.Content(
                    title = "Английский базовый",
                    cards = listOf(card(1, "hello", "привет")),
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S16: deleteCard делегируется в репозиторий.
    @Test
    fun deleteCard_delegatesToRepository() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = 1L, name = "Английский базовый")
        repository.cards.value = listOf(card(42, "cat", "кошка"))

        val viewModel = DeckContentViewModel(deckId = 1L, repository = repository)

        viewModel.uiState.test {
            awaitItem() // Content (устаканившееся состояние, Loading конфлетерируется)
            viewModel.deleteCard(42L)
            assertEquals(listOf(42L), repository.deletedCards)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S26: ошибка удаления не роняет состояние и не трогает репозиторий повторно.
    @Test
    fun deleteCard_error_keepsState() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = 1L, name = "Английский базовый")
        repository.cards.value = listOf(card(42, "cat", "кошка"))
        repository.deleteResult = CardOperationResult.Error(RuntimeException("delete failed"))

        val viewModel = DeckContentViewModel(deckId = 1L, repository = repository)

        viewModel.uiState.test {
            val content = awaitItem()
            assertTrue(content is DeckContentUiState.Content)
            viewModel.deleteCard(42L)
            assertEquals(listOf(42L), repository.deletedCards)
            // Список не изменился — состояние остаётся прежним Content.
            assertEquals(content, viewModel.uiState.value)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
