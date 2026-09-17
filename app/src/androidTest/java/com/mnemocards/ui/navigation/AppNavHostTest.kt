package com.mnemocards.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.file.SafCsvFileGateway
import com.mnemocards.data.repository.RoomCardRepository
import com.mnemocards.ui.theme.MnemoCardsTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Навигация (S1, S22): тап по элементу колоды открывает содержимое
 * колоды; кнопка «Тренировать» ведёт на экран тренировки; back-стек
 * список → содержимое → форма (S14) обеспечивается NavController.
 */
@RunWith(AndroidJUnit4::class)
class AppNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: MnemoCardsDatabase

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao()
            .insertDeck(com.mnemocards.data.db.DeckEntity(name = "Английский базовый"))
        repository.addCard(deckId, "hello", "привет")
        repository.addCard(deckId, "dog", "собака")

        composeRule.setContent {
            MnemoCardsTheme {
                AppNavHost(
                    db = db,
                    csvFileGateway = SafCsvFileGateway(
                        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
                    ),
                )
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // S1: тап по колоде открывает её содержимое с карточками.
    @Test
    fun tapDeck_opensDeckContent() {
        composeRule.onNodeWithText("Английский базовый").performClick()

        composeRule.onNodeWithText("hello — привет").assertIsDisplayed()
        composeRule.onNodeWithContentDescriptionLabel("Назад").assertIsDisplayed()
    }

    // S22: кнопка «Тренировать» открывает тренировку этой колоды.
    @Test
    fun trainButton_opensReviewSession() {
        composeRule.onNodeWithText("Тренировать").performClick()

        composeRule.waitForIdle()
        // Сессия открыта: показана лицевая сторона какой-то из карточек колоды
        // (все сеяные карточки готовы сразу, порядок равных due не фиксирован).
        composeRule.waitUntil(5_000) {
            listOf("hello", "dog").any {
                composeRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider
        .onNodeWithContentDescriptionLabel(label: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(label))
}
