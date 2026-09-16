package com.mnemocards.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Экран завершения сессии «На сегодня всё» (S7).
 *
 * - карточек, слов, перевода и кнопок оценки на экране нет — `ReviewScreen`
 *   показывает этот composable вместо карточки и панели оценки;
 * - экран терминален для сессии (S8): ни одного clickable-элемента,
 *   автоперезапуск сессии отсутствует — произвольные тапы ничего не меняют.
 */
@Composable
fun SessionDone(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("doneScreen")
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "На сегодня всё",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Сессия завершена — карточки на сегодня закончились",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
