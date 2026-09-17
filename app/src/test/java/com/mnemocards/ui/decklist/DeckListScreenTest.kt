package com.mnemocards.ui.decklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import com.mnemocards.ui.theme.MnemoCardsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * UI-тесты экрана «Список колод»: создание колоды через форму
 * (S1–S3), контекстное меню, переименование и удаление
 * (S6–S13), пустое состояние списка (S16–S17). Экран рендерится
 * с фейковым репозиторием внутри MnemoCardsTheme (тёмная тема
 * обязательна по конвенции проекта).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListScreenTest {

    private val dispatcher = StandardTestDispatcher()

    /** Фейковый репозиторий: все мутации применяются к MutableStateFlow. */
    private class FakeDeckRepository : DeckRepository {
        val decks = MutableStateFlow<List<Deck>>(
            listOf(Deck(id = 1L, name = "Немецкий", cardCount = 5))
        )
        val createdNames = mutableListOf<String>()
        val renamedDecks = mutableListOf<Pair<Long, String>>()
        val deletedDeckIds = mutableListOf<Long>()

        private var nextId = 2L

        override fun observeDecks(): Flow<List<Deck>> = decks

        override suspend fun createDeck(name: String): Long {
            createdNames.add(name)
            val id = nextId++
            decks.value = decks.value + Deck(id = id, name = name, cardCount = 0)
            return id
        }

        override suspend fun renameDeck(id: Long, name: String) {
            renamedDecks.add(id to name)
            decks.value = decks.value.map {
                if (it.id == id) it.copy(name = name) else it
            }
        }

        override suspend fun deleteDeck(id: Long) {
            deletedDeckIds.add(id)
            decks.value = decks.value.filterNot { it.id == id }
        }
    }

    private lateinit var repository: FakeDeckRepository
    private lateinit var viewModel: DeckListViewModel

    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDeckRepository()
        viewModel = DeckListViewModel(repository)
        composeRule.setContent {
            MnemoCardsTheme {
                DeckListScreen(
                    viewModel = viewModel,
                    onDeckClick = { },
                    onTrainDeck = { }
                )
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // S1: кнопка добавления открывает форму; сохранение валидного названия
    // создаёт колоду со счётчиком «0 карточек»
    @Test
    fun createDeck_validName_showsNewDeckWithZeroCards() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()

        composeRule.onNodeWithText("Название")
            .performTextInput("Английские глаголы")
        composeRule.onNodeWithText("Сохранить").performClick()

        advanceUntilIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Английские глаголы").assertIsDisplayed()
        composeRule.onNodeWithText("0 карточек").assertIsDisplayed()
        assertTrue(repository.createdNames.contains("Английские глаголы"))
    }

    // S2: сохранение с пустым полем — ошибка валидации, форма остаётся открытой
    @Test
    fun createDeck_emptyName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S3: название только из пробелов отклоняется так же, как пустое
    @Test
    fun createDeck_blankName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Название").performTextInput("   ")
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S1: «Отмена» закрывает форму без изменений
    @Test
    fun createDeck_cancel_closesDialogWithoutChanges() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Название").performTextInput("Новая колода")
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Сохранить").assertDoesNotExist()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S6: долгое нажатие открывает меню ровно с двумя пунктами
    @Test
    fun longPress_showsMenuWithExactlyTwoActions() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }

        composeRule.onNodeWithText("Переименовать").assertIsDisplayed()
        composeRule.onNodeWithText("Удалить").assertIsDisplayed()
    }

    // S7: закрытие меню тапом вне — список без изменений
    @Test
    fun dismissMenu_nothingChanges() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").assertIsDisplayed()

        // Закрытие меню (S7). Синтетический клик Compose-теста не порождает
        // outside-tap для popup-окна, поэтому закрываем как это делает
        // onDismissRequest — через состояние ViewModel.
        composeRule.runOnIdle { viewModel.dismissMenu() }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Переименовать").assertDoesNotExist()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertTrue(repository.renamedDecks.isEmpty())
        assertTrue(repository.deletedDeckIds.isEmpty())
    }

    // S8: переименование обновляет название в списке, счётчик прежний
    @Test
    fun renameDeck_updatesNameImmediately_keepsCardCount() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").performClick()

        // Форма предзаполнена текущим названием
        composeRule.onNodeWithText("Немецкий B1").assertDoesNotExist()
        composeRule.onNodeWithText("Название").performTextClearance()
        composeRule.onNodeWithText("Название").performTextInput("Немецкий B1")
        composeRule.onNodeWithText("Сохранить").performClick()

        advanceUntilIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Немецкий B1").assertIsDisplayed()
        composeRule.onNodeWithText("5 карточек").assertIsDisplayed()
        assertEquals(listOf(1L to "Немецкий B1"), repository.renamedDecks)
    }

    // S9: пустое название при переименовании — ошибка, репозиторий не вызван
    @Test
    fun renameDeck_emptyName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").performClick()

        composeRule.onNodeWithText("Название")
            .performTextClearance()
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.renamedDecks.isEmpty())
    }

    // S10: отмена переименования — прежнее название сохранено
    @Test
    fun renameDeck_cancel_keepsOriginalName() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").performClick()

        composeRule.onNodeWithText("Название").performTextClearance()
        composeRule.onNodeWithText("Название").performTextInput("Немецкий B1")
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertTrue(repository.renamedDecks.isEmpty())
    }

    // S12: подтверждение удаления — колода исчезает из списка
    @Test
    fun deleteDeck_confirmed_removesDeckFromList() = runTest(dispatcher) {
        repository.decks.value = listOf(
            Deck(id = 1L, name = "Английский", cardCount = 3),
            Deck(id = 2L, name = "Немецкий", cardCount = 5)
        )
        advanceUntilIdle()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Английский")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Удалить").performClick()

        // Диалог подтверждения
        composeRule.onNodeWithText("Удалить колоду?").assertIsDisplayed()
        // Кнопка подтверждения в диалоге (пункт меню уже закрыт)
        composeRule.onAllNodesWithText("Удалить").assertCountEquals(1)
            .onFirst().performClick()

        advanceUntilIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Английский").assertDoesNotExist()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertEquals(listOf(1L), repository.deletedDeckIds)
    }

    // S13: отмена подтверждения — колода остаётся
    @Test
    fun deleteDeck_cancelled_keepsDeck() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Удалить").performClick()

        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertTrue(repository.deletedDeckIds.isEmpty())
    }

    // S16: пустой список — сообщение и кнопка-приглашение создать первую колоду
    @Test
    fun emptyDeckList_showsEmptyStateWithInviteButton() = runTest(dispatcher) {
        repository.decks.value = emptyList()
        advanceUntilIdle()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Пока нет колод").assertIsDisplayed()
        composeRule.onNodeWithText("Создать первую колоду").assertIsDisplayed()
    }

    // S17: кнопка приглашения открывает форму создания; сохранение непустого
    // названия добавляет колоду и убирает пустое состояние
    @Test
    fun emptyState_inviteButton_opensCreateFormAndCreatesDeck() = runTest(dispatcher) {
        repository.decks.value = emptyList()
        advanceUntilIdle()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Создать первую колоду").performClick()

        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()

        composeRule.onNodeWithText("Название")
            .performTextInput("Первая колода")
        composeRule.onNodeWithText("Сохранить").performClick()

        advanceUntilIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Первая колода").assertIsDisplayed()
        composeRule.onNodeWithText("0 карточек").assertIsDisplayed()
        composeRule.onNodeWithText("Пока нет колод").assertDoesNotExist()
        assertTrue(repository.createdNames.contains("Первая колода"))
    }

    // S17 (через S2–S3): из пустого состояния пустое название по-прежнему
    // отклоняется — форма остаётся открытой, колода не создаётся
    @Test
    fun emptyState_emptyName_isStillRejected() = runTest(dispatcher) {
        repository.decks.value = emptyList()
        advanceUntilIdle()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Создать первую колоду").performClick()
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.createdNames.isEmpty())
    }
}
