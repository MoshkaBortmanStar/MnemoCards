package com.mnemocards.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Карточка тренировки: контейнер по центру экрана с текстом текущей стороны.
 *
 * - лицевая сторона — `card.front` (слово), перевёрнутая — `card.back` (перевод),
 *   выбор по флагу [isFlipped] (S1, S2);
 * - `clickable` повешен только на область карточки — тап вне её не переворачивает (S3),
 *   логика повторного тапа по перевёрнутой карточке закрыта в ViewModel (S4);
 * - текст обеих сторон отображается «как есть», без транслитерации и
 *   трансформации (S10);
 * - длинный текст (S11): `softWrap = true` переносит текст по словам,
 *   неразрывные фрагменты обрезаются с индикатором-многоточием
 *   (`TextOverflow.Ellipsis`); контейнер фиксированной ширины (`fillMaxWidth`
 *   внутри отступов экрана) с внутренними отступами — текст не выходит за
 *   границы карточки, вёрстка экрана не ломается.
 */
@Composable
fun ReviewCard(
    front: String,
    back: String,
    isFlipped: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .testTag("reviewCard")
            .clickable(onClick = onTap),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isFlipped) back else front,
                modifier = Modifier
                    .testTag("cardSideText")
                    .padding(horizontal = CardContentPadding, vertical = CardContentPadding),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val CardMinHeight = 220.dp
private val CardContentPadding = 24.dp
