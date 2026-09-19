package com.mnemocards.ui.review

import kotlin.time.Duration.Companion.milliseconds

import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import kotlinx.coroutines.flow.Flow
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.domain.scheduler.SchedulingState
import com.mnemocards.domain.scheduler.Sm2Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val DAY = 86_400_000L
private const val MINUTE = 60_000L
private const val TEN_MINUTES_MS = 10 * MINUTE
private const val DECK_ID = "11111111-1111-4111-8111-111111111111"

/**
 * Unit-тесты ReviewViewModel (change add-sm2-scheduling):
 * T13 — построение сессии (S16–S18, S31, S32),
 * T15 — оценки, превью и повторный показ (S1–S4, S14, S15, S19–S21, S23, S30).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = 1_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun card(
        id: String,
        deckId: String = DECK_ID,
        front: String = "card$id",
        easinessFactor: Double = 2.5,
        repetitionNumber: Int = 0,
        intervalDays: Int = 0,
        dueDate: Long = now,
    ) = Card(
        id = id,
        deckId = deckId,
        front = front,
        back = "перевод$id",
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )

    /**
     * Fake-репозиторий: хранит карточки в памяти, честно фильтрует их
     * по dueDate <= now и применяет оценку тем же Sm2Scheduler, что и
     * продакшн-репозиторий.
     */
    private class FakeCardRepository(
        var deckExists: Boolean = true,
        var failRead: Boolean = false,
        var failWrite: Boolean = false,
    ) : CardRepository {

        val cards = mutableListOf<Card>()
        val appliedGrades = mutableListOf<Pair<Card, ReviewGrade>>()

        override suspend fun deckExists(deckId: String): Boolean = deckExists

        override fun observeDeck(deckId: String): Flow<DeckEntity?> = error("not used in this test")

        override fun observeCards(deckId: String): Flow<List<CardEntity>> = error("not used in this test")

        override fun observeCardCount(deckId: String): Flow<Int> = error("not used in this test")

        override suspend fun getCardById(id: String): CardEntity? = error("not used in this test")

        override suspend fun addCard(
            deckId: String,
            front: String,
            back: String,
        ): com.mnemocards.data.repository.CardOperationResult = error("not used in this test")

        override suspend fun updateCard(
            cardId: String,
            front: String,
            back: String,
        ): com.mnemocards.data.repository.CardOperationResult = error("not used in this test")

        override suspend fun deleteCard(cardId: String): com.mnemocards.data.repository.CardOperationResult =
            error("not used in this test")

        override suspend fun exportCardTexts(deckId: String): List<Pair<String, String>>? =
            error("not used in this test")

        override suspend fun importCards(
            deckId: String,
            cards: List<Pair<String, String>>,
        ): com.mnemocards.data.repository.CardImportResult = error("not used in this test")

        override suspend fun dueCards(deckId: String, now: Long): List<Card> {
            if (failRead) throw RuntimeException("db read error")
            return cards.filter { it.deckId == deckId && it.dueDate <= now }
        }

        override suspend fun applyGrade(card: Card, grade: ReviewGrade, now: Long) {
            if (failWrite) throw RuntimeException("db write error")
            appliedGrades += card to grade
            val next = Sm2Scheduler.next(
                SchedulingState(
                    easinessFactor = card.easinessFactor,
                    repetitionNumber = card.repetitionNumber,
                    intervalDays = card.intervalDays,
                    dueDate = card.dueDate,
                ),
                grade,
                now,
            )
            val index = cards.indexOfFirst { it.id == card.id }
            cards[index] = card.copy(
                easinessFactor = next.easinessFactor,
                repetitionNumber = next.repetitionNumber,
                intervalDays = next.intervalDays,
                dueDate = next.dueDate,
            )
        }
    }

    private fun viewModel(repo: FakeCardRepository) =
        ReviewViewModel(deckId = DECK_ID, repository = repo, nowProvider = { now })

    // ------------------------------------------------------------------
    // T13 — построение сессии
    // ------------------------------------------------------------------

    @Test
    fun `S16 session is built only from due cards and starts with first due`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += listOf(
            card("c1", front = "A", dueDate = now - DAY),      // вчера
            card("c2", front = "B", dueDate = now - MINUTE),   // недавно
            card("c3", front = "C", dueDate = now),            // ровно сейчас
            card("c4", front = "D", dueDate = now + DAY),      // завтра
            card("c5", front = "E", dueDate = now + 11 * 3_600_000L), // сегодня 23:59
        )

        val vm = viewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is ReviewUiState.Session)
        assertEquals("A", (state as ReviewUiState.Session).card.front)
        assertFalse(state.isFlipped)
    }

    @Test
    fun `S17 no due cards shows Done immediately`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += listOf(
            card("c1", dueDate = now + DAY),
            card("c2", dueDate = now + 2 * DAY),
        )

        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(ReviewUiState.Done, vm.uiState.value)
        // Данные карточек не изменяются.
        assertTrue(repo.appliedGrades.isEmpty())
    }

    @Test
    fun `S18 empty deck shows Done`() = runTest {
        val repo = FakeCardRepository()

        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(ReviewUiState.Done, vm.uiState.value)
    }

    @Test
    fun `S32 missing deck shows NotFound`() = runTest {
        val repo = FakeCardRepository(deckExists = false)

        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(ReviewUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `S31 read failure shows Error instead of session`() = runTest {
        val repo = FakeCardRepository(failRead = true)
        repo.cards += card("c1")

        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(ReviewUiState.Error, vm.uiState.value)
    }

    // ------------------------------------------------------------------
    // T15 — оценки, превью, повторный показ
    // ------------------------------------------------------------------

    @Test
    fun `flip toggles isFlipped`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card("c1")

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onFlip()
        assertEquals(true, (vm.uiState.value as ReviewUiState.Session).isFlipped)
        vm.onFlip()
        assertEquals(false, (vm.uiState.value as ReviewUiState.Session).isFlipped)
    }

    @Test
    fun `S14 S15 previews match applied intervals for repeating card`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card("c1", repetitionNumber = 2, intervalDays = 6)

        val vm = viewModel(repo)
        advanceUntilIdle()

        val previews = (vm.uiState.value as ReviewUiState.Session).previews
        assertEquals(TEN_MINUTES_MS.milliseconds, previews[ReviewGrade.FORGOT])
        assertEquals((7 * DAY).milliseconds, previews[ReviewGrade.HARD])
        assertEquals((15 * DAY).milliseconds, previews[ReviewGrade.GOOD])
        assertEquals((20 * DAY).milliseconds, previews[ReviewGrade.EASY])

        // Подпись совпадает с применённым результатом (S15).
        vm.onGrade(ReviewGrade.GOOD)
        advanceUntilIdle()

        val applied = repo.cards.single()
        assertEquals(15, applied.intervalDays)
        assertEquals(15 * DAY, applied.dueDate - now)
    }

    @Test
    fun `S1 Easy on new card applies grade and moves to next card`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += listOf(card("c1", front = "cat"), card("c2", front = "dog"))

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.EASY)
        advanceUntilIdle()

        assertEquals(1, repo.appliedGrades.size)
        assertEquals(ReviewGrade.EASY, repo.appliedGrades.single().second)
        val updated = repo.cards.first { it.front == "cat" }
        assertEquals(2.6, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(4, updated.intervalDays)
        assertEquals("dog", (vm.uiState.value as ReviewUiState.Session).card.front)
    }

    @Test
    fun `S2 S3 Good and Hard on new card apply grade`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card("c1", front = "cat")

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.GOOD)
        advanceUntilIdle()

        val updated = repo.cards.single()
        assertEquals(2.5, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(1, updated.intervalDays)
    }

    @Test
    fun `S4 Forgot keeps card in session`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card("c1", front = "cat")

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.FORGOT)
        advanceUntilIdle()

        // Карточка не удалена из сессии — показана снова.
        val state = vm.uiState.value as ReviewUiState.Session
        assertEquals("cat", state.card.front)
        assertEquals(false, state.isFlipped)
        assertEquals(1, repo.appliedGrades.size)
    }

    @Test
    fun `S19 Forgot moves card to the end of the queue`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += listOf(
            card("c1", front = "A"),
            card("c2", front = "B"),
            card("c3", front = "C"),
        )

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.FORGOT) // A — в конец
        advanceUntilIdle()
        assertEquals("B", (vm.uiState.value as ReviewUiState.Session).card.front)

        vm.onGrade(ReviewGrade.GOOD)   // B закрыта
        advanceUntilIdle()
        assertEquals("C", (vm.uiState.value as ReviewUiState.Session).card.front)

        vm.onGrade(ReviewGrade.GOOD)   // C закрыта
        advanceUntilIdle()

        // После C карточка A показана повторно — лицевой стороной.
        val state = vm.uiState.value as ReviewUiState.Session
        assertEquals("A", state.card.front)
        assertEquals(false, state.isFlipped)
    }

    @Test
    fun `S20 graduating the last re-queued card finishes session`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += listOf(card("c1", front = "A"), card("c2", front = "B"))

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.FORGOT) // A — в конец, следующая B
        advanceUntilIdle()
        vm.onGrade(ReviewGrade.GOOD)   // B закрыта, A — повторный показ
        advanceUntilIdle()
        vm.onGrade(ReviewGrade.GOOD)   // A получает №1 и интервал 1 день
        advanceUntilIdle()

        assertEquals(ReviewUiState.Done, vm.uiState.value)
        val updated = repo.cards.first { it.front == "A" }
        assertEquals(1, updated.repetitionNumber)
        assertEquals(1, updated.intervalDays)
    }

    @Test
    fun `S21 repeated Forgot lowers EF but not below floor`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card("c1", front = "A")

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.FORGOT)
        advanceUntilIdle()
        vm.onGrade(ReviewGrade.FORGOT)
        advanceUntilIdle()

        // Серия «Забыл»: EF 2.5 → 2.3 → 2.1.
        assertEquals(2.5, repo.appliedGrades[0].first.easinessFactor, 1e-9)
        assertEquals(2.3, repo.appliedGrades[1].first.easinessFactor, 1e-9)
        val updated = repo.cards.single()
        assertEquals(2.1, updated.easinessFactor, 1e-9)
        assertEquals(0, updated.repetitionNumber)
        // A снова в конце очереди — показана повторно.
        assertEquals("A", (vm.uiState.value as ReviewUiState.Session).card.front)
    }

    @Test
    fun `S23 card with repetition 1 previews 6 days on Good`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card("c1", repetitionNumber = 1, intervalDays = 1)

        val vm = viewModel(repo)
        advanceUntilIdle()

        val previews = (vm.uiState.value as ReviewUiState.Session).previews
        assertEquals((6 * DAY).milliseconds, previews[ReviewGrade.GOOD])

        vm.onGrade(ReviewGrade.GOOD)
        advanceUntilIdle()

        val updated = repo.cards.single()
        assertEquals(6, updated.intervalDays)
        assertEquals(2, updated.repetitionNumber)
    }

    @Test
    fun `S30 write failure keeps card unevaluated and retry applies`() = runTest {
        val repo = FakeCardRepository(failWrite = true)
        repo.cards += listOf(card("c1", front = "A"), card("c2", front = "B"))

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.GOOD)
        advanceUntilIdle()

        // Ошибка сохранения: карточка осталась в очереди неоценённой.
        val state = vm.uiState.value as ReviewUiState.Session
        assertTrue(state.saveError)
        assertEquals("A", state.card.front)
        val unchanged = repo.cards.first { it.front == "A" }
        assertEquals(0, unchanged.repetitionNumber)

        // Повторное нажатие применяется после восстановления БД.
        repo.failWrite = false
        vm.onGrade(ReviewGrade.GOOD)
        advanceUntilIdle()

        assertFalse((vm.uiState.value as ReviewUiState.Session).saveError)
        assertEquals("B", (vm.uiState.value as ReviewUiState.Session).card.front)
        assertEquals(1, repo.cards.first { it.front == "A" }.repetitionNumber)
    }
}
