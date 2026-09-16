package com.mnemocards.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mnemocards.domain.model.Grade

/**
 * Панель оценки из четырёх кнопок под карточкой (S2).
 *
 * - точные подписи «Забыл», «Трудно», «Хорошо», «Легко» с testTag-ами
 *   `btnForgot`, `btnHard`, `btnGood`, `btnEasy`;
 * - все четыре кнопки ведут к одинаковому переходу — оценка передаётся
 *   в [onRate] и не влияет на порядок карточек (S6);
 * - кнопки равномерно делят ширину экрана, поэтому остаются полностью видимыми
 *   и кликабельными при любой длине текста карточки (S11);
 * - панель показывается только при `isFlipped && !isFinished`: после нажатия
 *   `isFlipped` сбрасывается и кнопки исчезают из композиции — второй клик
 *   double-tap уже некуда попасть (S12).
 */
@Composable
fun RatingBar(
    onRate: (Grade) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = Alignment.CenterHorizontally,
        ),
    ) {
        RatingButton(
            tag = "btnForgot",
            label = "Забыл",
            modifier = Modifier.weight(1f),
        ) { onRate(Grade.FORGOT) }
        RatingButton(
            tag = "btnHard",
            label = "Трудно",
            modifier = Modifier.weight(1f),
        ) { onRate(Grade.HARD) }
        RatingButton(
            tag = "btnGood",
            label = "Хорошо",
            modifier = Modifier.weight(1f),
        ) { onRate(Grade.GOOD) }
        RatingButton(
            tag = "btnEasy",
            label = "Легко",
            modifier = Modifier.weight(1f),
        ) { onRate(Grade.EASY) }
    }
}

@Composable
private fun RowScope.RatingButton(
    tag: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag(tag),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Text(text = label)
    }
}
