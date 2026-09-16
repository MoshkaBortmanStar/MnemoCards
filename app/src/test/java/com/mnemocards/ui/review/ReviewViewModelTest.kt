package com.mnemocards.ui.review

import com.mnemocards.data.HardcodedDeck
import com.mnemocards.domain.model.Grade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit-тесты ReviewViewModel по сценариям S1, S2, S4–S8, S12
 * из spec-bdd.md (change `add-review-screen`).
 *
 * Задача T3: красное состояние TDD — ReviewViewModel ещё не реализован (задача T4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** ViewModel, продвинутая на `cardIndex` карточек вперёд (каждая оценена «Хорошо»). */
    private fun viewModelAt(cardIndex: Int): ReviewViewModel =
        ReviewViewModel().apply {
            repeat(cardIndex) {
                onCardTap()
                onRate(Grade.GOOD)
            }
        }

    /** S1: начальное состояние — индекс 0, сторона «лицо», кнопки скрыты. */
    @Test
    fun `initial state - index 0, face side, buttons hidden`() {
        val viewModel = ReviewViewModel()

        val state = viewModel.state.value

        assertEquals(0, state.cardIndex)
        assertFalse(state.isFlipped)
        assertFalse(state.isFinished)
        assertEquals(HardcodedDeck.cards.first(), state.currentCard)
    }

    /** S2: onCardTap из состояния «лицо» — переворот, кнопки видимы. */
    @Test
    fun `onCardTap from face side flips the card`() {
        val viewModel = ReviewViewModel()

        viewModel.onCardTap()

        val state = viewModel.state.value
        assertTrue(state.isFlipped)
        assertEquals(0, state.cardIndex)
        assertFalse(state.isFinished)
    }

    /** S4: onCardTap по перевёрнутой карточке — состояние не меняется. */
    @Test
    fun `onCardTap on flipped card does not change state`() {
        val viewModel = ReviewViewModel()
        viewModel.onCardTap()
        val before = viewModel.state.value

        viewModel.onCardTap()

        assertEquals(before, viewModel.state.value)
    }

    /** S5: onRate(GOOD) — индекс +1, сторона «лицо», порядок карточек фиксирован. */
    @Test
    fun `onRate GOOD advances to next card face side`() {
        val viewModel = ReviewViewModel()
        viewModel.onCardTap()

        viewModel.onRate(Grade.GOOD)

        val state = viewModel.state.value
        assertEquals(1, state.cardIndex)
        assertFalse(state.isFlipped)
        assertFalse(state.isFinished)
        assertEquals(HardcodedDeck.cards[1], state.currentCard)
    }

    /** S6: все четыре оценки эквивалентны — одна и та же следующая карточка. */
    @Test
    fun `all four grades lead to the same next card in fixed order`() {
        Grade.entries.forEach { grade ->
            val viewModel = ReviewViewModel()
            viewModel.onCardTap()

            viewModel.onRate(grade)

            val state = viewModel.state.value
            assertEquals(1, state.cardIndex)
            assertFalse(state.isFlipped)
            assertFalse(state.isFinished)
            assertEquals(HardcodedDeck.cards[1], state.currentCard)
        }
    }

    /** Guard-условие (поддержка S12): onRate до переворота состояние не меняет. */
    @Test
    fun `onRate before flip does not change state`() {
        val viewModel = ReviewViewModel()
        val before = viewModel.state.value

        viewModel.onRate(Grade.GOOD)

        assertEquals(before, viewModel.state.value)
    }

    /** S7: onRate на 5-й (последней) карточке — сессия завершена. */
    @Test
    fun `onRate on last card finishes the session`() {
        val viewModel = viewModelAt(HardcodedDeck.cards.size - 1)
        viewModel.onCardTap()
        assertFalse(viewModel.state.value.isFinished)

        viewModel.onRate(Grade.FORGOT)

        assertTrue(viewModel.state.value.isFinished)
    }

    /** S8: в состоянии finished события onCardTap/onRate состояние не меняют. */
    @Test
    fun `events in finished state do not change state`() {
        val viewModel = viewModelAt(HardcodedDeck.cards.size)
        val before = viewModel.state.value
        assertTrue(before.isFinished)

        viewModel.onCardTap()
        viewModel.onRate(Grade.EASY)

        assertEquals(before, viewModel.state.value)
    }

    /** S12: двойной быстрый вызов onRate для одной карточки — индекс растёт ровно на 1. */
    @Test
    fun `double rapid onRate advances exactly one card`() {
        val viewModel = ReviewViewModel()
        viewModel.onCardTap()

        viewModel.onRate(Grade.GOOD)
        viewModel.onRate(Grade.GOOD)

        val state = viewModel.state.value
        assertEquals(1, state.cardIndex)
        assertFalse(state.isFlipped)
        assertFalse(state.isFinished)
        assertEquals(HardcodedDeck.cards[1], state.currentCard)
    }
}
