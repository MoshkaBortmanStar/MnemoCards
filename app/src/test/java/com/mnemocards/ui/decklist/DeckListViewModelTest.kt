package com.mnemocards.ui.decklist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.RoomCardRepository
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S20/S21: счётчик карточек колоды — реактивный Flow из Room;
 * после addCard поток эмитит 4, после deleteCard — 2, без
 * пересоздания экрана списка (Room — single source of truth).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeckListViewModelTest {

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

    private fun deckCount(state: DeckListUiState): Int =
        (state as DeckListUiState.Content).decks.first().cardCount

    @Test
    fun counter_emits4_afterAddCard_s20() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(name = "Английский базовый"),
        )
        repeat(3) { i -> repository.addCard(deckId, "word$i", "перевод$i") }

        val viewModel = DeckListViewModel(db.deckDao())

        viewModel.uiState.test {
            // Первая эмиссия — initialValue (Loading): Room отвечает асинхронно.
            awaitItem()
            assertEquals(3, deckCount(awaitItem()))

            repository.addCard(deckId, "cat", "кошка")

            assertEquals(4, deckCount(awaitItem()))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun counter_emits2_afterDeleteCard_s21() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(name = "Английский базовый"),
        )
        repeat(3) { i -> repository.addCard(deckId, "word$i", "перевод$i") }

        val viewModel = DeckListViewModel(db.deckDao())

        viewModel.uiState.test {
            // Первая эмиссия — initialValue (Loading): Room отвечает асинхронно.
            awaitItem()
            assertEquals(3, deckCount(awaitItem()))

            val card = repository.observeCards(deckId).first().first()
            repository.deleteCard(card.id)

            assertEquals(2, deckCount(awaitItem()))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
