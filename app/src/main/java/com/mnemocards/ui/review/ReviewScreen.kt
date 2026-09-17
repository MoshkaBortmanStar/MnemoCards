package com.mnemocards.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnemocards.ui.theme.Blue
import com.mnemocards.ui.theme.Green
import com.mnemocards.ui.theme.Ink
import com.mnemocards.ui.theme.Orange
import com.mnemocards.ui.theme.Red

/**
 * Экран сессии повторения (оформление принято пользователем вручную
 * и воспроизводится как есть). Stateless по бизнес-данным: состояние
 * приходит из [ReviewUiState], события текут вверх через коллбеки (UDF).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onFlip: () -> Unit,
    onRate: () -> Unit,
    onBack: () -> Unit,
    onExit: () -> Unit,
) {
    Scaffold(
        topBar = {
            // Панель сливается с фоном: выделяться должна карточка, а не TopAppBar.
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            if (state.isFinished) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "На сегодня всё",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    // Выход из завершённой сессии сразу к списку колод.
                    Button(
                        onClick = onExit,
                        modifier = Modifier.padding(top = 24.dp),
                    ) {
                        Text("К списку колод", maxLines = 1)
                    }
                }
            } else {
                // Карточка: Surface цвета surfaceVariant, скруглённая,
                // с боковыми отступами и weight(1f) — занимает свободную высоту.
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(onClick = onFlip),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Цвет onSurfaceVariant (бирюзовый) берётся автоматически.
                        Text(
                            text = if (state.isRevealed) {
                                state.back.orEmpty()
                            } else {
                                state.front.orEmpty()
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Четыре кнопки оценки — в один ряд, прижаты к низу под карточкой.
                // contentPadding уже дефолтных 24dp и maxLines = 1: короткие
                // слова («Хорошо», «Трудно») не переносятся на вторую строку.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onRate,
                        enabled = state.isRevealed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Red,
                            contentColor = Ink,
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Забыл", maxLines = 1)
                    }
                    Button(
                        onClick = onRate,
                        enabled = state.isRevealed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Orange,
                            contentColor = Ink,
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Трудно", maxLines = 1)
                    }
                    Button(
                        onClick = onRate,
                        enabled = state.isRevealed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue,
                            contentColor = Ink,
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Хорошо", maxLines = 1)
                    }
                    Button(
                        onClick = onRate,
                        enabled = state.isRevealed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Green,
                            contentColor = Ink,
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Легко", maxLines = 1)
                    }
                }
            }
        }
    }
}
