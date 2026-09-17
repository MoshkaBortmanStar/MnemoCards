package com.mnemocards.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mnemocards.ui.theme.Blue
import com.mnemocards.ui.theme.Green
import com.mnemocards.ui.theme.Ink
import com.mnemocards.ui.theme.Orange
import com.mnemocards.ui.theme.Red

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
    onRate: (ReviewGrade) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = Alignment.CenterHorizontally,
        ),
    ) {
        RatingButton("btnForgot", "Забыл", Red, Modifier.weight(1f)) {
            onRate(ReviewGrade.FORGOT)
        }
        RatingButton("btnHard", "Трудно", Orange, Modifier.weight(1f)) {
            onRate(ReviewGrade.HARD)
        }
        RatingButton("btnGood", "Хорошо", Blue, Modifier.weight(1f)) {
            onRate(ReviewGrade.GOOD)
        }
        RatingButton("btnEasy", "Легко", Green, Modifier.weight(1f)) {
            onRate(ReviewGrade.EASY)
        }
    }
}

@Composable
private fun RowScope.RatingButton(
    tag: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag(tag),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Ink,
        ),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
