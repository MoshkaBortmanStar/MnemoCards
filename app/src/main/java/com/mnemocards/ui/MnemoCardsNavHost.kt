package com.mnemocards.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mnemocards.ui.decks.DeckListScreen
import com.mnemocards.ui.decks.DeckListViewModel
import com.mnemocards.ui.review.ReviewScreen
import com.mnemocards.ui.review.ReviewViewModel

/**
 * Навигационный граф приложения (T13, change `add-offline-storage`).
 *
 * Маршруты:
 * - `decks` — стартовый экран со списком колод (S3);
 * - `review/{deckId}` — тренировка выбранной колоды (S4).
 *
 * Системная кнопка «назад» обрабатывается NavController (S6):
 * из `review/{deckId}` выполняется `popBackStack` на `decks`,
 * список колод подписан на Flow Room и актуализируется при возврате.
 *
 * Событие `navigateBack` из [ReviewViewModel] (в том числе `NotFound`,
 * S8 — тап по устаревшей колоде) также приводит к `popBackStack`,
 * поэтому тренировка никогда не открывается на пустом наборе карточек.
 */
@Composable
fun MnemoCardsNavHost(
    navController: NavHostController,
    deckListViewModel: DeckListViewModel,
    reviewViewModelFactory: (deckId: Long) -> ReviewViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = DECKS_ROUTE,
    ) {
        composable(DECKS_ROUTE) {
            val uiState by deckListViewModel.uiState.collectAsState()

            // S4: событие навигации из ViewModel (Channel) вместо прямого
            // вызова NavController из экрана — однократность гарантирована.
            LaunchedEffect(deckListViewModel) {
                deckListViewModel.navigationEvents.collect { deckId ->
                    navController.navigate("$REVIEW_ROUTE_BASE/$deckId")
                }
            }

            DeckListScreen(
                uiState = uiState,
                onRetry = deckListViewModel::retry,
                onDeckClick = deckListViewModel::onDeckClick,
            )
        }
        composable(
            route = REVIEW_ROUTE,
            arguments = listOf(
                navArgument(DECK_ID_ARG) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong(DECK_ID_ARG) ?: return@composable
            val reviewViewModel = viewModel<ReviewViewModel>(
                key = "review_$deckId",
                factory = viewModelFactory {
                    initializer { reviewViewModelFactory(deckId) }
                },
            )
            val uiState by reviewViewModel.uiState.collectAsState()

            // UDF-событие навигации «назад»: S6 и S8 (NotFound → popBackStack).
            LaunchedEffect(reviewViewModel) {
                reviewViewModel.navigateBack.collect {
                    navController.popBackStack()
                }
            }

            ReviewScreen(
                uiState = uiState,
                onFlip = reviewViewModel::onFlip,
                onGrade = reviewViewModel::onGrade,
                onBack = reviewViewModel::onBack,
            )
        }
    }
}

private const val DECKS_ROUTE = "decks"
private const val REVIEW_ROUTE_BASE = "review"
private const val DECK_ID_ARG = "deckId"
private const val REVIEW_ROUTE = "$REVIEW_ROUTE_BASE/{$DECK_ID_ARG}"
