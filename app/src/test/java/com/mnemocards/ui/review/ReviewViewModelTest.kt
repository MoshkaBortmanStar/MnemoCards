package com.mnemocards.ui.review

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.RoomCardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S22/S23: сессия тренировки читает колоду из Room через Flow —
 * новая карточка входит в сессию, правка текста отражается в открытой
 * сессии без её пересоздания.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReviewViewModelTest {

    private lateinit var db: MnemoCardsDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // S22: добавленная карточка входит в сессию вместе с остальными.
    @Test
    fun newCard_entersReviewSession() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(name = "Английский базовый"),
        )
        repository.addCard(deckId, "hello", "привет")
        repository.addCard(deckId, "cat", "кошка")

        val viewModel = ReviewViewModel(deckId, repository)

        viewModel.uiState.test {
            // initialValue (isFinished=true до первой эмиссии Room) — проглатываем.
            awaitItem()

            // Новые сверху: только что добавленная «cat» — первая.
            val first = awaitItem()
            assertEquals("cat", first.front)
            assertFalse(first.isFinished)

            // Оценка — переход к следующей карточке колоды.
            viewModel.rate()
            assertEquals("hello", awaitItem().front)

            // Следующая оценка завершает сессию.
            viewModel.rate()
            assertTrue(awaitItem().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S23: правка текста карточки отражается в открытой сессии.
    @Test
    fun editedCard_updatesOpenSession_withoutRecreation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(name = "Английский базовый"),
        )
        repository.addCard(deckId, "cat", "кошка")

        val viewModel = ReviewViewModel(deckId, repository)

        viewModel.uiState.test {
            // initialValue (isFinished=true до первой эмиссии Room) — проглатываем.
            awaitItem()
            assertEquals("cat", awaitItem().front)

            val card = repository.observeCards(deckId).first().first()
            repository.updateCard(card.id, "a cat", card.back)

            val updated = awaitItem()
            assertEquals("a cat", updated.front)
            assertEquals("кошка", updated.back)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Пустая колода — терминальное состояние, приложение не падает.
    @Test
    fun emptyDeck_immediatelyFinished() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(name = "Пустая"),
        )

        val viewModel = ReviewViewModel(deckId, repository)

        viewModel.uiState.test {
            assertTrue(awaitItem().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
