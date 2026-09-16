package com.mnemocards.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Экран тренировки карточек (MVVM + UDF): экран не содержит бизнес-логики —
 * собирает `state` из [ReviewViewModel] через `collectAsStateWithLifecycle`
 * и отправляет события вверх (`onCardTap`, `onRate`).
 *
 * - карточка отрисована [ReviewCard] по центру; clickable — только на её
 *   области, тап по остальному экрану ничего не делает (S3);
 * - [RatingBar] появляется под карточкой только при перевёрнутой карточке
 *   и незавершённой сессии (S2, S12);
 * - при `isFinished` вместо карточки и кнопок показывается только
 *   терминальный экран [SessionDone] «на сегодня всё» (S7, S8).
 */
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isFinished) {
        SessionDone(modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ReviewCard(
            card = state.currentCard,
            isFlipped = state.isFlipped,
            onTap = viewModel::onCardTap,
        )
        if (state.isFlipped && !state.isFinished) {
            RatingBar(
                onRate = viewModel::onRate,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
