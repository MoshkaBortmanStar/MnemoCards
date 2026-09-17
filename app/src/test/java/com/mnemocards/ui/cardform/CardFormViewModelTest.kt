package com.mnemocards.ui.cardform

import app.cash.turbine.test
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.repository.CardImportResult
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardFormViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Fake-репозиторий: фиксирует вызовы записи, результаты настраиваются. */
    private class FakeCardRepository : CardRepository {
        val deck = MutableStateFlow<DeckEntity?>(DeckEntity(id = 1L, name = "Английский базовый"))
        val cards = MutableStateFlow<List<CardEntity>>(emptyList())

        var cardById: CardEntity? = null

        data class AddCall(val deckId: Long, val front: String, val back: String)
        data class UpdateCall(val cardId: Long, val front: String, val back: String)

        val addCalls = mutableListOf<AddCall>()
        val updateCalls = mutableListOf<UpdateCall>()
        val deleteCalls = mutableListOf<Long>()

        var addResult: CardOperationResult = CardOperationResult.Success(101L)
        var updateResult: CardOperationResult = CardOperationResult.Success(42L)
        var deleteResult: CardOperationResult = CardOperationResult.Success(42L)

        override fun observeDeck(deckId: Long): Flow<DeckEntity?> = deck

        override fun observeCards(deckId: Long): Flow<List<CardEntity>> = cards

        override fun observeCardCount(deckId: Long): Flow<Int> =
            kotlinx.coroutines.flow.flowOf(cards.value.size)

        override suspend fun getCardById(id: Long): CardEntity? = cardById

        // CSV-перенос (change-006): форме не нужен, заглушки для интерфейса.
        override suspend fun exportCardTexts(deckId: Long): List<Pair<String, String>>? = null

        override suspend fun importCards(
            deckId: Long,
            cards: List<Pair<String, String>>,
        ): CardImportResult = CardImportResult.Imported(0)

        override suspend fun addCard(
            deckId: Long,
            front: String,
            back: String,
        ): CardOperationResult {
            addCalls += AddCall(deckId, front, back)
            return addResult
        }

        override suspend fun updateCard(
            cardId: Long,
            front: String,
            back: String,
        ): CardOperationResult {
            updateCalls += UpdateCall(cardId, front, back)
            return updateResult
        }

        override suspend fun deleteCard(cardId: Long): CardOperationResult {
            deleteCalls += cardId
            return deleteResult
        }
    }

    private lateinit var repository: FakeCardRepository

    private val existingCard = CardEntity(
        id = 42L,
        deckId = 1L,
        front = "cat",
        back = "кошка",
        createdAt = 1L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCardRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // S4: режим добавления — оба поля пусты, без ошибок валидации.
    @Test
    fun addMode_fieldsAreEmpty() = runTest(dispatcher) {
        val viewModel = CardFormViewModel(deckId = 1L, cardId = null, repository = repository)

        val state = viewModel.uiState.value
        assertEquals("", state.front)
        assertEquals("", state.back)
        assertNull(state.frontError)
        assertNull(state.backError)
    }

    // S11: режим редактирования — предзаполнение по cardId.
    @Test
    fun editMode_prefilledFromCard() = runTest(dispatcher) {
        repository.cardById = existingCard

        val viewModel = CardFormViewModel(deckId = 1L, cardId = 42L, repository = repository)

        val state = viewModel.uiState.value
        assertEquals("cat", state.front)
        assertEquals("кошка", state.back)
        assertNull(state.frontError)
        assertNull(state.backError)
    }

    // S5: валидное сохранение в режиме добавления — ровно один addCard и событие закрытия.
    @Test
    fun save_validAdd_callsAddOnce_andCloses() = runTest(dispatcher) {
        val viewModel = CardFormViewModel(deckId = 1L, cardId = null, repository = repository)

        viewModel.events.test {
            viewModel.onFrontChange("cat")
            viewModel.onBackChange("кошка")
            viewModel.save()

            assertEquals(1, repository.addCalls.size)
            assertEquals(FakeCardRepository.AddCall(1L, "cat", "кошка"), repository.addCalls.single())
            assertEquals(0, repository.updateCalls.size)
            assertEquals(CardFormEvent.Closed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S12: валидное сохранение в режиме редактирования — ровно один updateCard.
    @Test
    fun save_validEdit_callsUpdateOnce_andCloses() = runTest(dispatcher) {
        repository.cardById = existingCard

        val viewModel = CardFormViewModel(deckId = 1L, cardId = 42L, repository = repository)

        viewModel.events.test {
            viewModel.onFrontChange("a cat")
            viewModel.onBackChange("кошка")
            viewModel.save()

            assertEquals(1, repository.updateCalls.size)
            assertEquals(
                FakeCardRepository.UpdateCall(42L, "a cat", "кошка"),
                repository.updateCalls.single(),
            )
            assertEquals(0, repository.addCalls.size)
            assertEquals(CardFormEvent.Closed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S6, S7: невалидные данные — ошибки полей, репозиторий не вызывается, ввод сохраняется.
    @Test
    fun save_invalidFields_showsErrors_noRepoCall() = runTest(dispatcher) {
        val viewModel = CardFormViewModel(deckId = 1L, cardId = null, repository = repository)

        viewModel.onFrontChange("")
        viewModel.onBackChange("собака")
        viewModel.save()

        var state = viewModel.uiState.value
        assertNotNull(state.frontError)
        assertNull(state.backError)
        // Введённые значения не потерялись.
        assertEquals("", state.front)
        assertEquals("собака", state.back)
        assertTrue(repository.addCalls.isEmpty())
        assertTrue(repository.updateCalls.isEmpty())

        // Оба поля пусты (S7).
        viewModel.onBackChange("")
        viewModel.save()

        state = viewModel.uiState.value
        assertNotNull(state.frontError)
        assertNotNull(state.backError)
        assertTrue(repository.addCalls.isEmpty())
    }

    // S8: только пробельные символы — валидация блокирует сохранение.
    @Test
    fun save_whitespaceOnly_blocked() = runTest(dispatcher) {
        val viewModel = CardFormViewModel(deckId = 1L, cardId = null, repository = repository)

        viewModel.onFrontChange("   ")
        viewModel.onBackChange(" \n\t ")
        viewModel.save()

        val state = viewModel.uiState.value
        assertNotNull(state.frontError)
        assertNotNull(state.backError)
        assertTrue(repository.addCalls.isEmpty())
    }

    // S13: очистка поля при редактировании — ошибка, прежние значения в Room не тронуты.
    @Test
    fun save_editClearedField_blocked() = runTest(dispatcher) {
        repository.cardById = existingCard

        val viewModel = CardFormViewModel(deckId = 1L, cardId = 42L, repository = repository)

        viewModel.onBackChange("")
        viewModel.save()

        val state = viewModel.uiState.value
        assertNull(state.frontError)
        assertNotNull(state.backError)
        assertTrue(repository.updateCalls.isEmpty())
        assertTrue(repository.addCalls.isEmpty())
    }

    // S26: ошибка записи — форма открыта, ввод не потерян, закрытия нет.
    @Test
    fun save_writeError_formStaysOpen_inputKept() = runTest(dispatcher) {
        repository.addResult = CardOperationResult.Error(RuntimeException("write failed"))

        val viewModel = CardFormViewModel(deckId = 1L, cardId = null, repository = repository)

        viewModel.events.test {
            viewModel.onFrontChange("cat")
            viewModel.onBackChange("кошка")
            viewModel.save()

            val state = viewModel.uiState.value
            assertEquals("cat", state.front)
            assertEquals("кошка", state.back)
            assertNotNull(state.saveError)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S25: update удалённой карточки — NotFound, insert не происходит.
    @Test
    fun save_editDeletedCard_notFound_noInsert() = runTest(dispatcher) {
        repository.cardById = existingCard
        repository.updateResult = CardOperationResult.NotFound

        val viewModel = CardFormViewModel(deckId = 1L, cardId = 42L, repository = repository)

        viewModel.events.test {
            viewModel.onFrontChange("a cat")
            viewModel.onBackChange("кошка")
            viewModel.save()

            assertEquals(1, repository.updateCalls.size)
            assertEquals(0, repository.addCalls.size)
            assertEquals(CardFormEvent.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S14: закрытие без сохранения — ноль вызовов репозитория.
    @Test
    fun dismiss_withoutSave_noRepoCalls() = runTest(dispatcher) {
        repository.cardById = existingCard

        val viewModel = CardFormViewModel(deckId = 1L, cardId = 42L, repository = repository)

        viewModel.events.test {
            viewModel.onFrontChange("изменено, но не сохранено")
            viewModel.dismiss()

            assertTrue(repository.addCalls.isEmpty())
            assertTrue(repository.updateCalls.isEmpty())
            assertTrue(repository.deleteCalls.isEmpty())
            assertEquals(CardFormEvent.Closed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
