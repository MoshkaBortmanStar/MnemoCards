package com.mnemocards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import com.mnemocards.ui.MnemoCardsNavHost
import com.mnemocards.ui.decks.DeckListViewModel

/**
 * Единственная Activity приложения (T13, change `add-offline-storage`).
 *
 * Рендерит [MnemoCardsNavHost]; ViewModel-фабрики получают зависимости
 * из [AppContainer] (ручной DI). Экраны не содержат бизнес-логики (MVVM + UDF).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MnemoCardsApplication).container

        setContent {
            val navController = rememberNavController()
            MnemoCardsNavHost(
                navController = navController,
                deckListViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { DeckListViewModel(container.deckRepository) }
                    },
                ),
                reviewViewModelFactory = { deckId ->
                    ReviewViewModelFactory.create(
                        deckId = deckId,
                        repository = container.deckRepository,
                    )
                },
            )
        }
    }
}

/**
 * Утилита создания [com.mnemocards.ui.review.ReviewViewModel] с `deckId`
 * из аргумента маршрута `review/{deckId}` (T13/T14).
 */
private object ReviewViewModelFactory {

    fun create(
        deckId: Long,
        repository: com.mnemocards.domain.repository.DeckRepository,
    ): com.mnemocards.ui.review.ReviewViewModel =
        com.mnemocards.ui.review.ReviewViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(
                mapOf("deckId" to deckId),
            ),
            repository = repository,
        )
}
