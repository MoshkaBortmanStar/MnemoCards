package com.mnemocards.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mnemocards.data.RoomDeckCountsRepository
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.file.CsvFileGateway
import com.mnemocards.data.repository.RoomCardRepository
import com.mnemocards.data.sync.SyncFlagDataStore
import com.mnemocards.ui.cardform.CardFormEvent
import com.mnemocards.ui.cardform.CardFormScreen
import com.mnemocards.ui.cardform.CardFormViewModel
import com.mnemocards.ui.deckcontent.DeckContentScreen
import com.mnemocards.ui.deckcontent.DeckContentUiState
import com.mnemocards.ui.deckcontent.DeckContentViewModel
import com.mnemocards.ui.decklist.DeckListScreen
import com.mnemocards.ui.decklist.DeckListViewModel
import com.mnemocards.ui.review.ReviewScreen
import com.mnemocards.ui.review.ReviewViewModel

/**
 * Маршруты приложения:
 *  - `decks` — список колод (стартовый);
 *  - `deck/{deckId}` — содержимое колоды;
 *  - `deck/{deckId}/edit?cardId={cardId}` — форма карточки
 *    (cardId опционален: нет — добавление, есть — редактирование);
 *  - `review/{deckId}` — тренировка колоды.
 *
 * change-007: deckId и cardId — строковые UUID (S7).
 *
 * Back-стек: список → содержимое → форма (S14); при NotFound —
 * автоматический возврат popBackStack (S24).
 */
object Routes {
    const val DECKS = "decks"
    const val DECK_CONTENT = "deck/{deckId}"
    const val CARD_FORM = "deck/{deckId}/edit?cardId={cardId}"
    const val REVIEW = "review/{deckId}"

    fun deckContent(deckId: String) = "deck/$deckId"
    fun cardForm(deckId: String, cardId: String? = null) =
        if (cardId == null) "deck/$deckId/edit" else "deck/$deckId/edit?cardId=$cardId"
    fun review(deckId: String) = "review/$deckId"
}

@Composable
fun AppNavHost(
    db: MnemoCardsDatabase,
    csvFileGateway: CsvFileGateway,
    syncFlagDataStore: SyncFlagDataStore,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.DECKS,
        modifier = modifier,
    ) {
        composable(Routes.DECKS) {
            val viewModel: DeckListViewModel = viewModel {
                DeckListViewModel(
                    deckRepository = RoomDeckRepository(db.deckDao(), syncFlagDataStore),
                    countsRepository = RoomDeckCountsRepository(db.deckDao()),
                )
            }
            DeckListScreen(
                viewModel = viewModel,
                onDeckClick = { deck -> navController.navigate(Routes.deckContent(deck.id)) },
                onTrainDeck = { deckId -> navController.navigate(Routes.review(deckId)) },
            )
        }

        composable(
            route = Routes.DECK_CONTENT,
            arguments = listOf(
                navArgument("deckId") { type = NavType.StringType },
            ),
        ) { entry ->
            val deckId = entry.arguments?.getString("deckId").orEmpty()
            val viewModel: DeckContentViewModel = viewModel(
                key = "deck-content-$deckId",
            ) {
                DeckContentViewModel(
                    deckId = deckId,
                    repository = RoomCardRepository(db.deckDao(), db.cardDao(), syncFlagDataStore),
                    csvFileGateway = csvFileGateway,
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val transferMessage by viewModel.transferMessage.collectAsStateWithLifecycle()

            // S24: устаревший deckId — автоматический возврат к списку колод,
            // пустой список не выдаётся за успешную загрузку.
            LaunchedEffect(state) {
                if (state is DeckContentUiState.NotFound) {
                    navController.popBackStack()
                }
            }

            DeckContentScreen(
                state = state,
                transferMessage = transferMessage,
                onBack = { navController.popBackStack() },
                onAddCard = { navController.navigate(Routes.cardForm(deckId)) },
                onEditCard = { cardId -> navController.navigate(Routes.cardForm(deckId, cardId)) },
                onDeleteCard = viewModel::deleteCard,
                onRetry = viewModel::retry,
                onExportTarget = viewModel::onExportTarget,
                onImportFile = viewModel::onImportFile,
                onTransferMessageShown = viewModel::onTransferMessageShown,
            )
        }

        composable(
            route = Routes.CARD_FORM,
            arguments = listOf(
                navArgument("deckId") { type = NavType.StringType },
                navArgument("cardId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val deckId = entry.arguments?.getString("deckId").orEmpty()
            val cardId = entry.arguments?.getString("cardId")
            val viewModel: CardFormViewModel = viewModel(
                key = "card-form-$deckId-$cardId",
            ) {
                CardFormViewModel(
                    deckId = deckId,
                    cardId = cardId,
                    repository = RoomCardRepository(db.deckDao(), db.cardDao(), syncFlagDataStore),
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // S5/S12: форма закрывается после успешного сохранения;
            // S25: карточка удалена другим путём — тоже закрыть форму.
            LaunchedEffect(Unit) {
                viewModel.events.collect { event ->
                    when (event) {
                        CardFormEvent.Closed,
                        CardFormEvent.NotFound,
                        -> navController.popBackStack()
                    }
                }
            }

            CardFormScreen(
                state = state,
                onFrontChange = viewModel::onFrontChange,
                onBackChange = viewModel::onBackChange,
                onSave = viewModel::save,
                // S14: системный «Назад» без «Сохранить» — возврат к содержимому.
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(
                navArgument("deckId") { type = NavType.StringType },
            ),
        ) { entry ->
            val deckId = entry.arguments?.getString("deckId").orEmpty()
            val viewModel: ReviewViewModel = viewModel(
                key = "review-$deckId",
            ) {
                ReviewViewModel(
                    deckId = deckId,
                    repository = RoomCardRepository(db.deckDao(), db.cardDao(), syncFlagDataStore),
                )
            }
            ReviewScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                // Завершённая сессия: выход сразу к списку колод, а не к содержимому.
                onExit = {
                    navController.popBackStack(
                        route = Routes.DECKS,
                        inclusive = false,
                    )
                },
            )
        }
    }
}
