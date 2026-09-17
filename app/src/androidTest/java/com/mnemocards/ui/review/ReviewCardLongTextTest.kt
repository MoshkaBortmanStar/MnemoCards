package com.mnemocards.ui.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI-тест длинного текста карточки по сценарию S11 из spec-bdd.md
 * (change `add-review-screen`). Задача T9: `ReviewCard` в изоляции.
 *
 * GIVEN в колоде есть карточка, у которой слово или перевод длиннее ширины
 * области карточки (неразрывное слово — худший случай переноса).
 * WHEN такая карточка отображается (лицевая и перевёрнутая стороны).
 * THEN текст не выходит за границы карточки (перенос по словам либо
 * многоточие), карточка не выходит за границы экрана — вёрстка не ломается.
 *
 * change-002: `ReviewCard` принимает `front`/`back` строками
 * (domain-модель `Card` больше не участвует); `getBoundsInRoot`
 * возвращает `DpRect`.
 */
class ReviewCardLongTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Слово без пробелов, заведомо длиннее ширины карточки. */
    private val longUnbreakableWord: String = LONG_WORD_UNIT.repeat(UNBREAKABLE_WORD_REPEATS)

    /** Фраза из многих длинных слов — перенос по словам на несколько строк. */
    private val longPhrase: String = List(PHRASE_WORD_COUNT) { LONG_WORD_UNIT }.joinToString(" ")

    private fun composeCard(isFlipped: Boolean) {
        composeRule.setContent {
            ReviewCard(
                front = longUnbreakableWord,
                back = longPhrase,
                isFlipped = isFlipped,
                onTap = {},
            )
        }
    }

    /** S11: длинное слово на лицевой стороне не выходит за границы карточки. */
    @Test
    fun s11_longFrontText_staysInsideCardBounds() {
        composeCard(isFlipped = false)

        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("cardSideText", useUnmergedTree = true).assertIsDisplayed()
        assertTextStaysInsideCard()
        assertCardStaysInsideRoot()
    }

    /** S11: длинный перевод на обратной стороне не выходит за границы карточки. */
    @Test
    fun s11_longBackText_staysInsideCardBounds() {
        composeCard(isFlipped = true)

        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("cardSideText", useUnmergedTree = true).assertIsDisplayed()
        assertTextStaysInsideCard()
        assertCardStaysInsideRoot()
    }

    /** Текст карточки целиком лежит внутри области карточки. */
    private fun assertTextStaysInsideCard() {
        val textBounds = composeRule.onNodeWithTag("cardSideText", useUnmergedTree = true).getBoundsInRoot()
        val cardBounds = composeRule.onNodeWithTag("reviewCard").getBoundsInRoot()
        assertRectInside(inner = textBounds, outer = cardBounds, innerName = "cardSideText")
    }

    /** Карточка целиком лежит внутри экрана — вёрстка не ломается. */
    private fun assertCardStaysInsideRoot() {
        val cardBounds = composeRule.onNodeWithTag("reviewCard").getBoundsInRoot()
        val rootBounds = composeRule.onRoot().getBoundsInRoot()
        assertRectInside(inner = cardBounds, outer = rootBounds, innerName = "reviewCard")
    }

    private fun assertRectInside(inner: DpRect, outer: DpRect, innerName: String) {
        assertTrue(
            "$innerName: левая граница внутри родителя (inner.left=${inner.left}, outer.left=${outer.left})",
            inner.left >= outer.left - EPSILON,
        )
        assertTrue(
            "$innerName: верхняя граница внутри родителя (inner.top=${inner.top}, outer.top=${outer.top})",
            inner.top >= outer.top - EPSILON,
        )
        assertTrue(
            "$innerName: правая граница внутри родителя (inner.right=${inner.right}, outer.right=${outer.right})",
            inner.right <= outer.right + EPSILON,
        )
        assertTrue(
            "$innerName: нижняя граница внутри родителя (inner.bottom=${inner.bottom}, outer.bottom=${outer.bottom})",
            inner.bottom <= outer.bottom + EPSILON,
        )
    }

    private companion object {
        val EPSILON = 0.5.dp
        const val LONG_WORD_UNIT = "Достопримечательность"
        const val UNBREAKABLE_WORD_REPEATS = 8
        const val PHRASE_WORD_COUNT = 20
    }
}
