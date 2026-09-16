package com.mnemocards.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
        topBar = { TopAppBar(title = { Text("Тренировка") }) },
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable { onFlip() },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (uiState.flipped) uiState.back else uiState.front,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                if (uiState.flipped) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onGrade(ReviewGrade.FORGOT) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Забыл") }
                        Button(
                            onClick = { onGrade(ReviewGrade.HARD) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Трудно") }
                        Button(
                            onClick = { onGrade(ReviewGrade.GOOD) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Хорошо") }
                        Button(
                            onClick = { onGrade(ReviewGrade.EASY) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Легко") }
                    }
                }
            }

            ReviewUiState.Finished -> CenteredContent(paddingModifier(padding)) {
                Text(
                    text = "На сегодня всё",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
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
