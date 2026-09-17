package com.mnemocards.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnemocards.R
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.ui.theme.Blue
import com.mnemocards.ui.theme.Green
import com.mnemocards.ui.theme.Ink
import com.mnemocards.ui.theme.Orange
import com.mnemocards.ui.theme.Red

/**
 * Экран сессии повторения (change add-sm2-scheduling).
 *
 * Оформление принято пользователем вручную и не меняется: карточка —
 * Surface(surfaceVariant, RoundedCornerShape(20.dp)) с отступами и weight(1f);
 * четыре кнопки оценки в один Row с weight(1f) внизу, заливки светлые
 * (Red/Orange/Blue/Green), текст Ink; TopAppBar цвета background с ArrowBack;
 * эмблема akatsuki_cloud 36dp в BottomCenter того же Box, что и текст стороны.
 * Все компоненты живут в этом файле — никуда не выносятся.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onExit: () -> Unit,
    viewModel: ReviewViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тренировка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                // Панель сливается с фоном: выделяться должна карточка.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val state = uiState) {
            ReviewUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            // S32: устаревший идентификатор колоды — возврат к списку.
            ReviewUiState.NotFound -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Колода не найдена", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onBack) {
                    Text("К списку колод")
                }
            }

            // S31: ошибка чтения БД при формировании сессии.
            ReviewUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Не удалось загрузить сессию",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onBack) {
                    Text("К списку колод")
                }
            }

            // S17, S18, S20: терминальный экран «на сегодня всё».
            ReviewUiState.Done -> SessionDone(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onExit = onExit,
            )

            is ReviewUiState.Session -> SessionContent(
                state = state,
                onFlip = viewModel::onFlip,
                onGrade = viewModel::onGrade,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun SessionContent(
    state: ReviewUiState.Session,
    onFlip: () -> Unit,
    onGrade: (ReviewGrade) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Карточка занимает свободную высоту.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onFlip() },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (state.isFlipped) state.card.back else state.card.front,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    // Декоративная эмблема отдельным слоем — на вёрстку
                    // текста не влияет.
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.akatsuki_cloud),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .height(36.dp),
                    )
                }
            }
        }

        // S30: ошибка записи — карточка остаётся неоценённой.
        if (state.saveError) {
            Text(
                text = "Не удалось сохранить оценку — попробуйте ещё раз",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        }

        // S14: подписи слева направо «Забыл — 10 мин» … «Легко — 20 дней»,
        // превью из Sm2Scheduler.preview, кнопки в один ряд.
        if (state.isFlipped) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                GradeButton(
                    label = "Забыл",
                    intervalLabel = formatInterval(state.previews.getValue(ReviewGrade.FORGOT)),
                    containerColor = Red,
                    onClick = { onGrade(ReviewGrade.FORGOT) },
                    modifier = Modifier.weight(1f),
                )
                GradeButton(
                    label = "Трудно",
                    intervalLabel = formatInterval(state.previews.getValue(ReviewGrade.HARD)),
                    containerColor = Orange,
                    onClick = { onGrade(ReviewGrade.HARD) },
                    modifier = Modifier.weight(1f),
                )
                GradeButton(
                    label = "Хорошо",
                    intervalLabel = formatInterval(state.previews.getValue(ReviewGrade.GOOD)),
                    containerColor = Blue,
                    onClick = { onGrade(ReviewGrade.GOOD) },
                    modifier = Modifier.weight(1f),
                )
                GradeButton(
                    label = "Легко",
                    intervalLabel = formatInterval(state.previews.getValue(ReviewGrade.EASY)),
                    containerColor = Green,
                    onClick = { onGrade(ReviewGrade.EASY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Терминальный экран сессии (S17, S18, S20): выход сразу к списку колод.
 */
@Composable
private fun SessionDone(
    modifier: Modifier = Modifier,
    onExit: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "На сегодня всё",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onExit,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("К списку колод", maxLines = 1)
        }
    }
}

/** Кнопка оценки: светлая заливка, тёмный текст Ink, подпись с интервалом. */
@Composable
private fun GradeButton(
    label: String,
    intervalLabel: String,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Ink,
        ),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label)
            Text(intervalLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}
