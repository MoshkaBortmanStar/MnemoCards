package com.mnemocards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnemocards.ui.review.ReviewScreen
import com.mnemocards.ui.review.ReviewViewModel
import com.mnemocards.ui.theme.MnemoCardsTheme

/**
 * Точка входа приложения (задача T8): в `setContent` получается
 * `ReviewViewModel` (состояние переживает смену конфигурации, S9)
 * и отрисовывается `ReviewScreen` — полный пользовательский цикл
 * сессии S1→S7 на захардкоженной колоде.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MnemoCardsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val reviewViewModel: ReviewViewModel = viewModel()
                    ReviewScreen(viewModel = reviewViewModel)
                }
            }
        }
    }
}
