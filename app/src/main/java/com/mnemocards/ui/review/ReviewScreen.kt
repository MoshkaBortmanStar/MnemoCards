package com.mnemocards.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Stateless-экран сессии повторения (T11, change `add-offline-storage`).
 *
 * Состояние и колбэки приходят параметрами (UDF); бизнес-логики в Compose нет.
 * Логика лицевая сторона → переворот → четыре оценки → «на сегодня всё»
 * сохранена из change `add-review-screen`; данные — из Room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    onFlip: () -> Unit,
    onGrade: (ReviewGrade) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тренировка") },
                // Шапка сливается с фоном: выделяться должна карточка, не панель.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (uiState) {
            ReviewUiState.Loading -> CenteredContent(paddingModifier(padding)) {
                CircularProgressIndicator()
            }

            ReviewUiState.NotFound -> CenteredContent(paddingModifier(padding)) {
                Text("Колода не найдена")
                Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Назад")
                }
            }

            is ReviewUiState.Session -> Column(
                modifier = paddingModifier(padding),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = "${uiState.position} / ${uiState.total}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                )
                // Оформление карточки и кнопок живёт в ReviewCard/RatingBar —
                // экран только раскладывает их и передаёт состояние.
                // Оформление карточки и кнопок живёт в ReviewCard/RatingBar —
                // экран только раскладывает их и передаёт состояние.
                ReviewCard(
                    front = uiState.front,
                    back = uiState.back,
                    isFlipped = uiState.flipped,
                    onTap = onFlip,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
                // Зона кнопок зарезервирована постоянной высотой: после оценки
                // кнопки сменяются заглушкой, карточка не растягивается вниз —
                // «хвост» double-tap по бывшей кнопке не переворачивает
                // следующую карточку (S12).
                if (uiState.flipped) {
                    RatingBar(
                        onRate = onGrade,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RatingBarReservedHeight),
                    )
                }
            }

            ReviewUiState.Finished -> Column(
                modifier = paddingModifier(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SessionDone(modifier = Modifier.weight(1f))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                ) {
                    Text("Назад к колодам")
                }
            }
        }
    }
}

@Composable
private fun CenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

private fun paddingModifier(padding: androidx.compose.foundation.layout.PaddingValues): Modifier =
    Modifier
        .fillMaxSize()
        .padding(padding)

/**
 * Резерв высоты зоны кнопок оценки с запасом: реальная [com.mnemocards.ui.review.RatingBar]
 * (~48dp) всегда меньше — после оценки карточка не вырастает вниз, и «хвост»
 * double-tap по бывшей кнопке попадает в некликабельную заглушку (S12).
 */
private val RatingBarReservedHeight = 64.dp
