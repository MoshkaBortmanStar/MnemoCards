package com.mnemocards.ui.decklist

import com.mnemocards.data.DeckCountsRepository
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.DeckCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты DeckListViewModel: создание колоды и валидация названия
 * (S1–S5), контекстное меню, переименование и удаление колоды
 * (S6–S13, S16, S21, S22).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Фейковый репозиторий с записью вызовов. */
    private class FakeDeckRepository : DeckRepository {
        val decks = MutableStateFlow<List<Deck>>(emptyList())
        val createdNames = mutableListOf<String>()
        val renamedCalls = mutableListOf<Pair<String, String>>()
        val deletedIds = mutableListOf<String>()
        var failOnCreate = false
        var failOnRename = false
        var failOnDelete = false

        private var nextId = 0

        override fun observeDecks(): Flow<List<Deck>> = decks

        override suspend fun createDeck(name: String) {
            if (failOnCreate) throw RuntimeException("disk error")
            createdNames.add(name)
            val id = "deck-${++nextId}"
            decks.value = decks.value + Deck(id = id, name = name, cardCount = 0)
        }

        override suspend fun renameDeck(id: String, name: String) {
            if (failOnRename) throw RuntimeException("disk error")
            renamedCalls.add(id to name)
            // Несуществующий id — no-op, «призрачных» записей не создаётся.
            decks.value = decks.value.map {
                if (it.id == id) it.copy(name = name) else it
            }
        }

        override suspend fun deleteDeck(id: String) {
            if (failOnDelete) throw RuntimeException("disk error")
            deletedIds.add(id)
            decks.value = decks.value.filterNot { it.id == id }
        }
    }

    /** Фейковый источник счётчиков (total, dueToday) — S24–S27. */
    private class FakeDeckCountsRepository : DeckCountsRepository {
        val counts = MutableStateFlow<List<DeckCounts>>(emptyList())
        override fun observeDecksWithCounts(now: Long): Flow<List<DeckCounts>> = counts
    }

    private lateinit var repository: FakeDeckRepository
    private lateinit var counts: FakeDeckCountsRepository
    private lateinit var viewModel: DeckListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDeckRepository()
        counts = FakeDeckCountsRepository()
        viewModel = DeckListViewModel(repository, counts)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun state(): DeckListUiState = viewModel.uiState.first()

    private companion object {
        const val GERMAN_ID = "deck-7"
    }

    private val germanDeck = Deck(id = GERMAN_ID, name = "Немецкий", cardCount = 5)

    /** Ожидаемый элемент списка: счётчиков нет — total из cardCount, dueToday=0. */
    private val Deck.expectedItem: DeckListItem
        get() = DeckListItem(id = id, name = name, total = cardCount, dueToday = 0)

    /** Долгое нажатие по доменной колоде — как его видит UI (S6). */
    private fun longPress(deck: Deck) =
        viewModel.onDeckLongPressed(deck.expectedItem)

    // S1: валидное название → createDeck вызван, диалог закрыт
    @Test
    fun validName_createsDeckAndClosesDialog() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("Английские глаголы")

        viewModel.createDeck()
        advanceUntilIdle()

        val state = state()
        assertEquals(listOf("Английские глаголы"), repository.createdNames)
        assertFalse(state.isCreateDialogShown)
        assertFalse(state.isCreateNameError)
    }

    // S2: пустое название → ошибка, createDeck НЕ вызван, диалог открыт
    @Test
    fun emptyName_showsErrorAndDoesNotCreate() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("")

        viewModel.createDeck()

        val state = state()
        assertTrue(state.isCreateDialogShown)
        assertTrue(state.isCreateNameError)
        assertTrue(repository.createdNames.isEmpty())
    }

    // S3: название только из пробелов → трактуется как пустое
    @Test
    fun blankName_isRejectedLikeEmpty() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("   ")

        viewModel.createDeck()

        val state = state()
        assertTrue(state.isCreateDialogShown)
        assertTrue(state.isCreateNameError)
        assertTrue(repository.createdNames.isEmpty())
    }

    // S1: название обрезается trim перед сохранением
    @Test
    fun createDeck_savesTrimmedName() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("  Английские глаголы  ")

        viewModel.createDeck()
        advanceUntilIdle()

        assertEquals(listOf("Английские глаголы"), repository.createdNames)
    }

    // S4: дубликат существующего названия — создание разрешено
    @Test
    fun duplicateName_creationAllowed() = runTest(dispatcher) {
        repository.decks.value = listOf(Deck(id = "deck-1", name = "Немецкий", cardCount = 5))

        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("Немецкий")
        viewModel.createDeck()
        advanceUntilIdle()

        assertEquals(listOf("Немецкий"), repository.createdNames)
        assertFalse(state().isCreateNameError)
    }

    // S5: очень длинное название (100+ символов) проходит валидацию
    @Test
    fun longName_passesValidation() = runTest(dispatcher) {
        val longName = "Н".repeat(120)

        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName(longName)

        viewModel.createDeck()
        advanceUntilIdle()

        assertEquals(listOf(longName), repository.createdNames)
        assertFalse(state().isCreateDialogShown)
    }

    // Ошибка диалога сбрасывается при переоткрытии
    @Test
    fun openCreateDialog_resetsPreviousError() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("")
        viewModel.createDeck()
        assertTrue(state().isCreateNameError)

        viewModel.dismissCreateDialog()
        viewModel.openCreateDialog()

        val state = state()
        assertTrue(state.isCreateDialogShown)
        assertFalse(state.isCreateNameError)
        assertEquals("", state.createDeckName)
    }

    // S22 (частично): ошибка репозитория ловится в состояние ошибки
    @Test
    fun repositoryError_isCaughtIntoErrorMessage() = runTest(dispatcher) {
        repository.failOnCreate = true
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("A")

        viewModel.createDeck()
        advanceUntilIdle()

        val state = state()
        assertTrue(state.errorMessage != null)
        assertTrue(repository.createdNames.isEmpty())
    }

    // S6: долгое нажатие → выбранная колода, меню открыто
    @Test
    fun longPress_selectsDeckAndOpensMenu() = runTest(dispatcher) {
        longPress(germanDeck)

        val state = state()
        assertEquals(germanDeck.expectedItem, state.selectedDeck)
        assertTrue(state.isMenuShown)
    }

    // S7: закрытие меню без выбора ничего не меняет
    @Test
    fun dismissMenu_changesNothing() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        advanceUntilIdle()
        longPress(germanDeck)

        viewModel.dismissMenu()

        val state = state()
        assertFalse(state.isMenuShown)
        assertNull(state.selectedDeck)
        assertTrue(repository.renamedCalls.isEmpty())
        assertTrue(repository.deletedIds.isEmpty())
        assertEquals(listOf(germanDeck.expectedItem), state.decks)
    }

    // S8: переименование вызывает renameDeck(id, название), счётчик не меняется
    @Test
    fun rename_callsRepositoryWithDeckId() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        longPress(germanDeck)
        viewModel.openRenameDialog()
        viewModel.updateRenameDeckName("Немецкий B1")

        viewModel.renameDeck()
        advanceUntilIdle()

        assertEquals(listOf(GERMAN_ID to "Немецкий B1"), repository.renamedCalls)
        val state = state()
        assertFalse(state.isRenameDialogShown)
        val renamed = state.decks.single()
        assertEquals("Немецкий B1", renamed.name)
        assertEquals(5, renamed.total)
    }

    // S8: форма переименования открывается с текущим названием в поле
    @Test
    fun openRenameDialog_prefillsCurrentName() = runTest(dispatcher) {
        longPress(germanDeck)

        viewModel.openRenameDialog()

        val state = state()
        assertTrue(state.isRenameDialogShown)
        assertEquals("Немецкий", state.renameDeckName)
        assertFalse(state.isRenameNameError)
    }

    // S9: пустое название при переименовании → ошибка, репозиторий не вызван
    @Test
    fun renameWithEmptyName_showsErrorWithoutRepositoryCall() = runTest(dispatcher) {
        longPress(germanDeck)
        viewModel.openRenameDialog()
        viewModel.updateRenameDeckName("")

        viewModel.renameDeck()

        val state = state()
        assertTrue(state.isRenameDialogShown)
        assertTrue(state.isRenameNameError)
        assertTrue(repository.renamedCalls.isEmpty())
        assertEquals("Немецкий", state.decks.singleOrNull()?.name ?: germanDeck.name)
    }

    // S10: «Отмена» переименования — вызовов репозитория нет
    @Test
    fun renameCancel_noRepositoryCalls() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        advanceUntilIdle()
        longPress(germanDeck)
        viewModel.openRenameDialog()
        viewModel.updateRenameDeckName("Немецкий B1")

        viewModel.dismissRenameDialog()

        val state = state()
        assertFalse(state.isRenameDialogShown)
        assertTrue(repository.renamedCalls.isEmpty())
        assertEquals("Немецкий", state.decks.single().name)
    }

    // S11: сохранение без изменений завершается без ошибки
    @Test
    fun renameWithSameName_completesWithoutError() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        longPress(germanDeck)
        viewModel.openRenameDialog()
        // Поле содержит текущее название без изменений.

        viewModel.renameDeck()
        advanceUntilIdle()

        val state = state()
        assertFalse(state.isRenameDialogShown)
        assertFalse(state.isRenameNameError)
        assertEquals(listOf(GERMAN_ID to "Немецкий"), repository.renamedCalls)
    }

    // S12: подтверждение удаления вызывает deleteDeck(id)
    @Test
    fun deleteConfirm_callsDeleteDeck() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        longPress(germanDeck)

        viewModel.openDeleteDialog()
        viewModel.deleteDeck()
        advanceUntilIdle()

        val state = state()
        assertEquals(listOf(GERMAN_ID), repository.deletedIds)
        assertFalse(state.isDeleteDialogShown)
        assertTrue(state.decks.isEmpty())
    }

    // S13: отмена удаления — deleteDeck не вызван
    @Test
    fun deleteCancel_doesNotCallRepository() = runTest(dispatcher) {
        longPress(germanDeck)

        viewModel.openDeleteDialog()
        viewModel.dismissDeleteDialog()

        val state = state()
        assertFalse(state.isDeleteDialogShown)
        assertTrue(repository.deletedIds.isEmpty())
    }

    // S16: пустой список → флаг пустого состояния (decks пуст, загрузка завершена)
    @Test
    fun emptyDeckList_isExposedAsEmptyState() = runTest(dispatcher) {
        repository.decks.value = emptyList()
        advanceUntilIdle()

        val state = state()
        assertTrue(state.decks.isEmpty())
        assertFalse(state.isLoading)
    }

    // S22: ошибка репозитория при переименовании/удалении → сообщение, состояние консистентно
    @Test
    fun repositoryMutationError_isCaughtIntoErrorMessage() = runTest(dispatcher) {
        repository.failOnRename = true
        repository.failOnDelete = true
        repository.decks.value = listOf(germanDeck)

        longPress(germanDeck)
        viewModel.openRenameDialog()
        viewModel.updateRenameDeckName("Немецкий B1")
        viewModel.renameDeck()
        advanceUntilIdle()

        var state = state()
        assertTrue(state.errorMessage != null)
        // Экран консистентен: колода не переименована, изменение не применено.
        assertEquals("Немецкий", state.decks.single().name)

        viewModel.consumeErrorMessage()
        longPress(germanDeck)
        viewModel.openDeleteDialog()
        viewModel.deleteDeck()
        advanceUntilIdle()

        state = state()
        assertTrue(state.errorMessage != null)
        assertEquals(listOf(germanDeck.expectedItem), state.decks)
    }

    // S21 (аналог 404): действие над уже отсутствующим id — без исключения
    @Test
    fun operationsWithStaleDeck_areSafeNoOp() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        longPress(germanDeck)

        // Колода удалена из Room уже после открытия меню (устаревший элемент).
        repository.decks.value = emptyList()

        viewModel.openRenameDialog()
        viewModel.updateRenameDeckName("Немецкий B1")
        viewModel.renameDeck()

        longPress(germanDeck)
        viewModel.openDeleteDialog()
        viewModel.deleteDeck()
        advanceUntilIdle()

        // No-op по несуществующему id: вызовы прошли, исключений нет,
        // «призрачных» записей не появилось.
        assertEquals(listOf(GERMAN_ID to "Немецкий B1"), repository.renamedCalls)
        assertEquals(listOf(GERMAN_ID), repository.deletedIds)
        val state = state()
        assertTrue(state.decks.isEmpty())
        assertNull(state.errorMessage)
    }

    // S25: элемент списка несёт total=5 и dueToday=2
    @Test
    fun deckItem_carriesTotalAndDueToday() = runTest(dispatcher) {
        repository.decks.value = listOf(Deck(id = "deck-1", name = "Базовая", cardCount = 5))
        counts.counts.value = listOf(DeckCounts(deckId = "deck-1", total = 5, dueToday = 2))
        advanceUntilIdle()

        val item = state().decks.single()
        assertEquals(5, item.total)
        assertEquals(2, item.dueToday)
    }

    // S27: все карточки в будущем — счётчик явно 0, колода не исчезает
    @Test
    fun dueToday_isZeroWhenAllCardsInFuture() = runTest(dispatcher) {
        repository.decks.value = listOf(Deck(id = "deck-1", name = "Базовая", cardCount = 5))
        counts.counts.value = listOf(DeckCounts(deckId = "deck-1", total = 5, dueToday = 0))
        advanceUntilIdle()

        val item = state().decks.single()
        assertEquals(0, item.dueToday)
        assertEquals(5, item.total)
    }

    // S26: после оценок dueToday приходит из Room автоматически
    @Test
    fun dueToday_updatesAutomaticallyAfterGrades() = runTest(dispatcher) {
        repository.decks.value = listOf(Deck(id = "deck-1", name = "Базовая", cardCount = 5))
        counts.counts.value = listOf(DeckCounts(deckId = "deck-1", total = 5, dueToday = 2))
        advanceUntilIdle()
        assertEquals(2, state().decks.single().dueToday)

        // Обе назревшие карточки оценены — due ушёл в будущее.
        counts.counts.value = listOf(DeckCounts(deckId = "deck-1", total = 5, dueToday = 0))
        advanceUntilIdle()

        val item = state().decks.single()
        assertEquals(0, item.dueToday)
        assertEquals(5, item.total)
    }

    // S24: новая карточка (due=«сейчас») сразу увеличивает счётчик
    @Test
    fun newCard_isDueImmediately() = runTest(dispatcher) {
        repository.decks.value = listOf(Deck(id = "deck-1", name = "Базовая", cardCount = 5))
        counts.counts.value = listOf(DeckCounts(deckId = "deck-1", total = 5, dueToday = 2))
        advanceUntilIdle()

        counts.counts.value = listOf(DeckCounts(deckId = "deck-1", total = 6, dueToday = 3))
        advanceUntilIdle()

        assertEquals(3, state().decks.single().dueToday)
    }
}
