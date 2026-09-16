## Батч 1/4

### FILE: app/src/androidTest/java/com/mnemocards/ui/review/ReviewScreenTest.kt
```kotlin
package com.mnemocards.ui.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mnemocards.data.HardcodedDeck
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI-тест основного пользовательского флоу экрана тренировки
 * по сценариям S1, S2, S5, S7 из spec-bdd.md (change `add-review-screen`).
 *
 * Задача T1: красное состояние TDD — экран `ReviewScreen` ещё не реализован
 * (задачи T5–T7); тест адресуется testTag-ам `reviewCard`, `cardSideText`,
 * `btnForgot`, `btnHard`, `btnGood`, `btnEasy`, `doneScreen`.
 */
class ReviewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ratingButtonTags = listOf("btnForgot", "btnHard", "btnGood", "btnEasy")

    private fun launchReviewScreen() {
        composeRule.setContent {
            ReviewScreen(viewModel = ReviewViewModel())
        }
    }

    private fun flipCurrentCard() {
        composeRule.onNodeWithTag("reviewCard").performClick()
    }

    private fun rateCurrentCard(buttonTag: String) {
        composeRule.onNodeWithTag(buttonTag).performClick()
    }

    private fun assertRatingButtonsHidden() {
        ratingButtonTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    private fun assertRatingButtonsShownWithExactLabels() {
        composeRule.onNodeWithTag("btnForgot").assertIsDisplayed()
        composeRule.onNodeWithTag("btnHard").assertIsDisplayed()
        composeRule.onNodeWithTag("btnGood").assertIsDisplayed()
        composeRule.onNodeWithTag("btnEasy").assertIsDisplayed()
        composeRule.onNodeWithText("Забыл").assertIsDisplayed()
        composeRule.onNodeWithText("Трудно").assertIsDisplayed()
        composeRule.onNodeWithText("Хорошо").assertIsDisplayed()
        composeRule.onNodeWithText("Легко").assertIsDisplayed()
    }

    /** S1: старт сессии — первая карточка лицевой стороной, перевод и кнопки скрыты. */
    @Test
    fun s1_sessionStarts_withFirstCardFaceUp() {
        launchReviewScreen()

        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards.first().front)
        composeRule.onNodeWithText(HardcodedDeck.cards.first().back)
            .assertDoesNotExist()
        assertRatingButtonsHidden()
    }

    /** S2: одиночный тап по карточке — перевод и четыре активные кнопки оценки. */
    @Test
    fun s2_tapOnCard_showsTranslationAndRatingButtons() {
        launchReviewScreen()

        flipCurrentCard()

        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards.first().back)
        assertRatingButtonsShownWithExactLabels()
    }

    /** S5: кнопка «Хорошо» — карточка №2 в исходном состоянии (лицевая сторона). */
    @Test
    fun s5_rateGood_showsNextCardFaceUp() {
        launchReviewScreen()
        flipCurrentCard()

        rateCurrentCard("btnGood")

        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards[1].front)
        composeRule.onNodeWithText(HardcodedDeck.cards[1].back)
            .assertDoesNotExist()
        assertRatingButtonsHidden()
    }

    /** S7: оценка последней (5-й) карточки — экран «на сегодня всё». */
    @Test
    fun s7_ratingLastCard_showsSessionDoneScreen() {
        launchReviewScreen()

        repeat(HardcodedDeck.cards.size) {
            flipCurrentCard()
            rateCurrentCard("btnGood")
        }

        composeRule.onNodeWithTag("doneScreen").assertIsDisplayed()
        composeRule.onNodeWithTag("reviewCard").assertDoesNotExist()
        composeRule.onNodeWithTag("cardSideText").assertDoesNotExist()
        assertRatingButtonsHidden()
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/HardcodedDeckTest.kt
```kotlin
package com.mnemocards.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты захардкоженной колоды по сценариям S1, S10, S11
 * из spec-bdd.md (change `add-review-screen`). Задача T2.
 */
class HardcodedDeckTest {

    /** S1: колода содержит ровно 5 карточек. */
    @Test
    fun `deck contains exactly 5 cards`() {
        assertEquals(5, HardcodedDeck.cards.size)
    }

    /** Каждая карточка — пара непустых строк. */
    @Test
    fun `every card is a pair of non-empty strings`() {
        HardcodedDeck.cards.forEach { card ->
            assertTrue("front must be non-empty", card.front.isNotEmpty())
            assertTrue("back must be non-empty", card.back.isNotEmpty())
        }
    }

    /** Колода — упорядоченный список различных карточек (фиксированный порядок, S6). */
    @Test
    fun `all cards are distinct`() {
        assertEquals(HardcodedDeck.cards.size, HardcodedDeck.cards.toSet().size)
    }

    /** S10: в колоде есть карточки с латиницей. */
    @Test
    fun `deck includes latin script`() {
        assertTrue(
            HardcodedDeck.cards.any { card ->
                hasLatin(card.front) || hasLatin(card.back)
            },
        )
    }

    /** S10: в колоде есть карточки с кириллицей. */
    @Test
    fun `deck includes cyrillic script`() {
        assertTrue(
            HardcodedDeck.cards.any { card ->
                hasCyrillic(card.front) || hasCyrillic(card.back)
            },
        )
    }

    /** S10: в колоде есть карточки с CJK-символами. */
    @Test
    fun `deck includes cjk script`() {
        assertTrue(
            HardcodedDeck.cards.any { card ->
                hasCjk(card.front) || hasCjk(card.back)
            },
        )
    }

    /** S11: есть карточка со словом или переводом длиннее 40 символов. */
    @Test
    fun `deck has a card with word or translation longer than 40 characters`() {
        assertTrue(
            HardcodedDeck.cards.any { card ->
                card.front.length > LONG_TEXT_THRESHOLD || card.back.length > LONG_TEXT_THRESHOLD
            },
        )
    }

    private fun hasLatin(text: String): Boolean =
        text.any { it in 'a'..'z' || it in 'A'..'Z' }

    private fun hasCyrillic(text: String): Boolean =
        text.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

    private fun hasCjk(text: String): Boolean =
        text.any { it.code in CJK_UNIFIED_IDEOGRAPHS_START..CJK_UNIFIED_IDEOGRAPHS_END }

    private companion object {
        const val LONG_TEXT_THRESHOLD = 40
        const val CJK_UNIFIED_IDEOGRAPHS_START = 0x4E00
        const val CJK_UNIFIED_IDEOGRAPHS_END = 0x9FFF
    }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt
```kotlin
package com.mnemocards.ui.review

import com.mnemocards.data.HardcodedDeck
import com.mnemocards.domain.model.Grade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit-тесты ReviewViewModel по сценариям S1, S2, S4–S8, S12
 * из spec-bdd.md (change `add-review-screen`).
 *
 * Задача T3: красное состояние TDD — ReviewViewModel ещё не реализован (задача T4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** ViewModel, продвинутая на `cardIndex` карточек вперёд (каждая оценена «Хорошо»). */
    private fun viewModelAt(cardIndex: Int): ReviewViewModel =
        ReviewViewModel().apply {
            repeat(cardIndex) {
                onCardTap()
                onRate(Grade.GOOD)
            }
        }

    /** S1: начальное состояние — индекс 0, сторона «лицо», кнопки скрыты. */
    @Test
    fun `initial state - index 0, face side, buttons hidden`() {
        val viewModel = ReviewViewModel()

        val state = viewModel.state.value

        assertEquals(0, state.cardIndex)
        assertFalse(state.isFlipped)
        assertFalse(state.isFinished)
        assertEquals(HardcodedDeck.cards.first(), state.currentCard)
    }

    /** S2: onCardTap из состояния «лицо» — переворот, кнопки видимы. */
    @Test
    fun `onCardTap from face side flips the card`() {
        val viewModel = ReviewViewModel()

        viewModel.onCardTap()

        val state = viewModel.state.value
        assertTrue(state.isFlipped)
        assertEquals(0, state.cardIndex)
        assertFalse(state.isFinished)
    }

    /** S4: onCardTap по перевёрнутой карточке — состояние не меняется. */
    @Test
    fun `onCardTap on flipped card does not change state`() {
        val viewModel = ReviewViewModel()
        viewModel.onCardTap()
        val before = viewModel.state.value

        viewModel.onCardTap()

        assertEquals(before, viewModel.state.value)
    }

    /** S5: onRate(GOOD) — индекс +1, сторона «лицо», порядок карточек фиксирован. */
    @Test
    fun `onRate GOOD advances to next card face side`() {
        val viewModel = ReviewViewModel()
        viewModel.onCardTap()

        viewModel.onRate(Grade.GOOD)

        val state = viewModel.state.value
        assertEquals(1, state.cardIndex)
        assertFalse(state.isFlipped)
        assertFalse(state.isFinished)
        assertEquals(HardcodedDeck.cards[1], state.currentCard)
    }

    /** S6: все четыре оценки эквивалентны — одна и та же следующая карточка. */
    @Test
    fun `all four grades lead to the same next card in fixed order`() {
        Grade.entries.forEach { grade ->
            val viewModel = ReviewViewModel()
            viewModel.onCardTap()

            viewModel.onRate(grade)

            val state = viewModel.state.value
            assertEquals(1, state.cardIndex)
            assertFalse(state.isFlipped)
            assertFalse(state.isFinished)
            assertEquals(HardcodedDeck.cards[1], state.currentCard)
        }
    }

    /** Guard-условие (поддержка S12): onRate до переворота состояние не меняет. */
    @Test
    fun `onRate before flip does not change state`() {
        val viewModel = ReviewViewModel()
        val before = viewModel.state.value

        viewModel.onRate(Grade.GOOD)

        assertEquals(before, viewModel.state.value)
    }

    /** S7: onRate на 5-й (последней) карточке — сессия завершена. */
    @Test
    fun `onRate on last card finishes the session`() {
        val viewModel = viewModelAt(HardcodedDeck.cards.size - 1)
        viewModel.onCardTap()
        assertFalse(viewModel.state.value.isFinished)

        viewModel.onRate(Grade.FORGOT)

        assertTrue(viewModel.state.value.isFinished)
    }

    /** S8: в состоянии finished события onCardTap/onRate состояние не меняют. */
    @Test
    fun `events in finished state do not change state`() {
        val viewModel = viewModelAt(HardcodedDeck.cards.size)
        val before = viewModel.state.value
        assertTrue(before.isFinished)

        viewModel.onCardTap()
        viewModel.onRate(Grade.EASY)

        assertEquals(before, viewModel.state.value)
    }

    /** S12: двойной быстрый вызов onRate для одной карточки — индекс растёт ровно на 1. */
    @Test
    fun `double rapid onRate advances exactly one card`() {
        val viewModel = ReviewViewModel()
        viewModel.onCardTap()

        viewModel.onRate(Grade.GOOD)
        viewModel.onRate(Grade.GOOD)

        val state = viewModel.state.value
        assertEquals(1, state.cardIndex)
        assertFalse(state.isFlipped)
        assertFalse(state.isFinished)
        assertEquals(HardcodedDeck.cards[1], state.currentCard)
    }
}
```

### FILE: app/src/main/java/com/mnemocards/domain/model/Card.kt
```kotlin
package com.mnemocards.domain.model

/**
 * Карточка — пара «слово — перевод» (S1).
 *
 * Текст обеих сторон отображается «как есть», без транслитерации
 * и трансформации — письменности произвольные (S10).
 */
data class Card(
    val front: String,
    val back: String,
)
```

### FILE: app/src/main/java/com/mnemocards/domain/model/Grade.kt
```kotlin
package com.mnemocards.domain.model

/**
 * Оценка ответа пользователя: «Забыл», «Трудно», «Хорошо», «Легко».
 *
 * В рамках фичи `add-review-screen` все оценки эквивалентны:
 * пересчёт интервалов повторения (SRS) вне скоупа (S6).
 */
enum class Grade {
    FORGOT,
    HARD,
    GOOD,
    EASY,
}
```

### FILE: app/src/main/java/com/mnemocards/data/HardcodedDeck.kt
```kotlin
package com.mnemocards.data

import com.mnemocards.domain.model.Card

/**
 * Захардкоженная колода для первой версии экрана тренировки (S1).
 *
 * Свойства данных:
 * - ровно 5 карточек, порядок фиксирован этим списком и не меняется (S6);
 * - письменности смешанные: латиница, кириллица, CJK (S10),
 *   конкретная языковая пара не зашита;
 * - одна карточка со словом и переводом длиннее 40 символов (S11).
 */
object HardcodedDeck {

    val cards: List<Card> = listOf(
        Card(front = "cat", back = "кошка"),
        Card(front = "собака", back = "dog"),
        Card(front = "book", back = "本"),
        Card(front = "ありがとう", back = "спасибо"),
        Card(
            front = "The quick brown fox jumps over the lazy dog",
            back = "Быстрая бурая лиса перепрыгивает через ленивого пса",
        ),
    )
}
```

### FILE: gradle/libs.versions.toml
```
[versions]
agp = "9.4.0"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
kotlinxCoroutinesTest = "1.10.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### FILE: app/build.gradle.kts
```
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mnemocards"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mnemocards"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```


---

## Батч 2/4

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewUiState.kt
```kotlin
package com.mnemocards.ui.review

import com.mnemocards.data.HardcodedDeck
import com.mnemocards.domain.model.Card

/**
 * UDF-состояние экрана тренировки (S1).
 *
 * - `cardIndex` — индекс текущей карточки в захардкоженной колоде (S1, S6);
 * - `isFlipped` — сторона карточки: `false` — «лицо» (слово), `true` — «перевод» (S2);
 * - `isFinished` — сессия завершена, показывается экран «на сегодня всё» (S7, S8).
 *
 * Состояние живёт в `ReviewViewModel` (S9), а не в `remember` внутри композиции.
 * `currentCard` — вычисляемое свойство: текущая карточка из `HardcodedDeck.cards`
 * с фиксированным порядком (S6); после завершения сессии индекс выходит за
 * границы списка, поэтому возвращается последняя карточка колоды.
 */
data class ReviewUiState(
    val cardIndex: Int = 0,
    val isFlipped: Boolean = false,
    val isFinished: Boolean = false,
) {
    val currentCard: Card
        get() = HardcodedDeck.cards.getOrElse(cardIndex) { HardcodedDeck.cards.last() }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt
```kotlin
package com.mnemocards.ui.review

import androidx.lifecycle.ViewModel
import com.mnemocards.data.HardcodedDeck
import com.mnemocards.domain.model.Grade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel экрана тренировки (MVVM + UDF): владеет состоянием сессии,
 * принимает события `onCardTap` / `onRate` и эмитит `StateFlow<ReviewUiState>`.
 *
 * Состояние хранится в поле ViewModel — переживает пересоздание Activity
 * при смене конфигурации (S9).
 */
class ReviewViewModel : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())

    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /**
     * Тап по карточке: переворот только из состояния «лицо» (S2).
     *
     * Guard-условия:
     * - повторный тап по перевёрнутой карточке состояние не меняет (S4);
     * - в состоянии finished событие игнорируется (S8).
     */
    fun onCardTap() {
        _state.update { current ->
            if (!current.isFlipped && !current.isFinished) {
                current.copy(isFlipped = true)
            } else {
                current
            }
        }
    }

    /**
     * Оценка ответа: только по перевёрнутой карточке (S2, S5) и только пока
     * сессия не завершена (S8). Индекс +1, сторона сбрасывается на «лицо» (S5),
     * после последней карточки `isFinished = true` (S7).
     *
     * Guard-условие `isFlipped` делает повторный вызов `onRate` для той же
     * карточки (double-tap) no-op'ом — переход выполняется ровно один (S12).
     *
     * S6: все четыре оценки эквивалентны — параметр `grade` намеренно ни на что
     * не влияет, SRS-планирование вне скоупа фичи.
     */
    fun onRate(grade: Grade) {
        _state.update { current ->
            if (current.isFlipped && !current.isFinished) {
                val nextIndex = current.cardIndex + 1
                current.copy(
                    cardIndex = nextIndex,
                    isFlipped = false,
                    isFinished = nextIndex >= HardcodedDeck.cards.size,
                )
            } else {
                current
            }
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewCard.kt
```kotlin
package com.mnemocards.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.mnemocards.domain.model.Card

/**
 * Карточка тренировки: контейнер по центру экрана с текстом текущей стороны.
 *
 * - лицевая сторона — `card.front` (слово), перевёрнутая — `card.back` (перевод),
 *   выбор по флагу [isFlipped] (S1, S2);
 * - `clickable` повешен только на область карточки — тап вне её не переворачивает (S3),
 *   логика повторного тапа по перевёрнутой карточке закрыта в ViewModel (S4);
 * - текст обеих сторон отображается «как есть», без транслитерации и
 *   трансформации (S10); по умолчанию `Text` переносится по словам внутри
 *   фиксированной ширины карточки с внутренними отступами (S11).
 */
@Composable
fun ReviewCard(
    card: Card,
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
                text = if (isFlipped) card.back else card.front,
                modifier = Modifier
                    .testTag("cardSideText")
                    .padding(horizontal = CardContentPadding, vertical = CardContentPadding),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val CardMinHeight = 220.dp
private val CardContentPadding = 24.dp
```

### FILE: app/src/main/java/com/mnemocards/ui/review/RatingBar.kt
```kotlin
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
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt
```kotlin
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
 *   и незавершённой сессии (S2, S12).
 */
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
```

### FILE: gradle/libs.versions.toml
```
[versions]
agp = "9.4.0"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
kotlinxCoroutinesTest = "1.10.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### FILE: app/build.gradle.kts
```
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mnemocards"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mnemocards"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```


---

## Батч 3/4

### FILE: app/src/androidTest/java/com/mnemocards/ui/review/ReviewCardLongTextTest.kt
```kotlin
package com.mnemocards.ui.review

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.mnemocards.domain.model.Card
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
                card = Card(front = longUnbreakableWord, back = longPhrase),
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
        composeRule.onNodeWithTag("cardSideText").assertIsDisplayed()
        assertTextStaysInsideCard()
        assertCardStaysInsideRoot()
    }

    /** S11: длинный перевод на обратной стороне не выходит за границы карточки. */
    @Test
    fun s11_longBackText_staysInsideCardBounds() {
        composeCard(isFlipped = true)

        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("cardSideText").assertIsDisplayed()
        assertTextStaysInsideCard()
        assertCardStaysInsideRoot()
    }

    /** Текст карточки целиком лежит внутри области карточки. */
    private fun assertTextStaysInsideCard() {
        val textBounds = composeRule.onNodeWithTag("cardSideText").getBoundsInRoot()
        val cardBounds = composeRule.onNodeWithTag("reviewCard").getBoundsInRoot()
        assertRectInside(inner = textBounds, outer = cardBounds, innerName = "cardSideText")
    }

    /** Карточка целиком лежит внутри экрана — вёрстка не ломается. */
    private fun assertCardStaysInsideRoot() {
        val cardBounds = composeRule.onNodeWithTag("reviewCard").getBoundsInRoot()
        val rootBounds = composeRule.onRoot().getBoundsInRoot()
        assertRectInside(inner = cardBounds, outer = rootBounds, innerName = "reviewCard")
    }

    private fun assertRectInside(inner: Rect, outer: Rect, innerName: String) {
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
        const val EPSILON = 0.5f
        const val LONG_WORD_UNIT = "Достопримечательность"
        const val UNBREAKABLE_WORD_REPEATS = 8
        const val PHRASE_WORD_COUNT = 20
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/SessionDone.kt
```kotlin
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
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt
```kotlin
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
```

### FILE: app/src/main/java/com/mnemocards/MainActivity.kt
```kotlin
package com.mnemocards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnemocards.ui.review.ReviewScreen
import com.mnemocards.ui.review.ReviewViewModel

/**
 * Точка входа приложения (задача T8): в `setContent` получается
 * `ReviewViewModel` (состояние переживает смену конфигурации, S9)
 * и отрисовывается `ReviewScreen` — полный пользовательский цикл
 * сессии S1→S7 на захардкоженной колоде.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val reviewViewModel: ReviewViewModel = viewModel()
                    ReviewScreen(viewModel = reviewViewModel)
                }
            }
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewCard.kt
```kotlin
package com.mnemocards.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.mnemocards.domain.model.Card

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
    card: Card,
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
                text = if (isFlipped) card.back else card.front,
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
```

### FILE: gradle/libs.versions.toml
```toml
[versions]
agp = "9.4.0"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
kotlinxCoroutinesTest = "1.10.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### FILE: app/build.gradle.kts
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mnemocards"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mnemocards"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```


---

## Батч 4/4

### FILE: app/src/androidTest/java/com/mnemocards/ui/review/ReviewScreenRotationTest.kt
```kotlin
package com.mnemocards.ui.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mnemocards.MainActivity
import com.mnemocards.data.HardcodedDeck
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI-тест сценария S9 из spec-bdd.md (change `add-review-screen`).
 * Задача T10: смена конфигурации (поворот экрана) сохраняет состояние сессии.
 *
 * GIVEN идёт сессия, показана перевёрнутая карточка №3 (перевод и кнопки
 * оценки на экране).
 * WHEN происходит смена конфигурации устройства и Activity пересоздаётся
 * (`ActivityScenario.recreate()`).
 * THEN после пересоздания отображается та же карточка №3 в перевёрнутом
 * состоянии с кнопками оценки; прогресс сессии не сброшен, показ карточки №1
 * с начала не происходит.
 *
 * Гарантия — состояние живёт в `ReviewViewModel` (T4) и получается в
 * `MainActivity` через `viewModel()` (T8), а не в `remember`/`saveable`
 * внутри композиции: ViewModelStore переживает пересоздание Activity.
 */
class ReviewScreenRotationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val ratingButtonTags = listOf("btnForgot", "btnHard", "btnGood", "btnEasy")

    private fun flipCurrentCard() {
        composeRule.onNodeWithTag("reviewCard").performClick()
    }

    private fun rateCurrentCardGood() {
        composeRule.onNodeWithTag("btnGood").performClick()
    }

    private fun assertRatingButtonsDisplayed() {
        ratingButtonTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    /** S9: поворот экрана — та же перевёрнутая карточка №3 с кнопками оценки. */
    @Test
    fun s9_configurationChange_keepsFlippedCardAndProgress() {
        // Пройти оценки карточек №1 и №2 — текущей становится карточка №3.
        repeat(THIRD_CARD_INDEX) {
            flipCurrentCard()
            rateCurrentCardGood()
        }

        // GIVEN: карточка №3 перевёрнута — перевод и кнопки оценки на экране.
        flipCurrentCard()
        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards[THIRD_CARD_INDEX].back)
        composeRule.onNodeWithText(HardcodedDeck.cards[THIRD_CARD_INDEX].front)
            .assertDoesNotExist()
        assertRatingButtonsDisplayed()

        // WHEN: смена конфигурации — Activity пересоздаётся (поворот экрана).
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        // THEN: та же карточка №3 перевёрнута, кнопки оценки на экране,
        // прогресс не сброшен.
        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards[THIRD_CARD_INDEX].back)
        composeRule.onNodeWithText(HardcodedDeck.cards[THIRD_CARD_INDEX].front)
            .assertDoesNotExist()
        assertRatingButtonsDisplayed()
        // Показ карточки №1 с начала не происходит.
        composeRule.onNodeWithText(HardcodedDeck.cards.first().front)
            .assertDoesNotExist()
        composeRule.onNodeWithText(HardcodedDeck.cards.first().back)
            .assertDoesNotExist()
    }

    private companion object {

        /** Индекс карточки №3 (zero-based) в захардкоженной колоде. */
        const val THIRD_CARD_INDEX = 2
    }
}
```

### FILE: app/src/androidTest/java/com/mnemocards/ui/review/ReviewScreenEdgeTest.kt
```kotlin
package com.mnemocards.ui.review

import androidx.compose.ui.test.Position
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.mnemocards.data.HardcodedDeck
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI-тесты краевых взаимодействий экрана тренировки по сценариям
 * S3, S4, S12 из spec-bdd.md (change `add-review-screen`). Задача T11.
 *
 * Границы интеракций: переворот выполняется только тапом по самой карточке (S3),
 * повторный тап по перевёрнутой карточке состояние не меняет (S4), double-tap
 * по кнопке оценки обрабатывается как ровно одно нажатие (S12).
 */
class ReviewScreenEdgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ratingButtonTags = listOf("btnForgot", "btnHard", "btnGood", "btnEasy")

    private fun launchReviewScreen() {
        composeRule.setContent {
            ReviewScreen(viewModel = ReviewViewModel())
        }
    }

    private fun flipCurrentCard() {
        composeRule.onNodeWithTag("reviewCard").performClick()
    }

    private fun assertRatingButtonsHidden() {
        ratingButtonTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    private fun assertRatingButtonsShown() {
        ratingButtonTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    /**
     * S3: GIVEN экран тренировки показывает неперевёрнутую карточку
     * (лицевая сторона).
     * WHEN тап по экрану вне границ карточки (верхний левый угол корня).
     * THEN состояние экрана не меняется: карточка остаётся лицевой стороной,
     * перевод не показывается, кнопки оценки не появляются — переворот
     * выполняется только тапом по самой карточке.
     */
    @Test
    fun s3_tapOutsideCard_doesNotFlipIt() {
        launchReviewScreen()

        composeRule.onRoot().performTouchInput { click(Position.topLeft) }

        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards.first().front)
        composeRule.onNodeWithText(HardcodedDeck.cards.first().back)
            .assertDoesNotExist()
        assertRatingButtonsHidden()
    }

    /**
     * S4: GIVEN карточка перевёрнута — показан перевод и четыре кнопки оценки.
     * WHEN повторный тап по карточке.
     * THEN состояние экрана не меняется: карточка остаётся перевёрнутой,
     * кнопки оценки остаются на экране; обратный переворот и переход к
     * следующей карточке не выполняются.
     */
    @Test
    fun s4_tapOnFlippedCard_keepsFlippedStateAndButtons() {
        launchReviewScreen()
        flipCurrentCard()

        flipCurrentCard()

        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards.first().back)
        composeRule.onNodeWithText(HardcodedDeck.cards.first().front)
            .assertDoesNotExist()
        assertRatingButtonsShown()
        // Перехода к следующей карточке нет: показана всё ещё карточка №1.
        composeRule.onNodeWithText(HardcodedDeck.cards[1].front)
            .assertDoesNotExist()
    }

    /**
     * S12: GIVEN карточка №1 перевёрнута, кнопки оценки активны.
     * WHEN двойное быстрое нажатие одной и той же кнопки («Хорошо», doubleClick).
     * THEN обрабатывается только первое нажатие: показана ровно следующая
     * карточка (№2) лицевой стороной, кнопки скрыты; пропуск до карточки №3
     * не происходит, сессия карточку не пропускает.
     */
    @Test
    fun s12_doubleClickOnRatingButton_advancesExactlyOneCard() {
        launchReviewScreen()
        flipCurrentCard()

        composeRule.onNodeWithTag("btnGood").performTouchInput { doubleClick() }

        // Ровно один переход: карточка №2 лицевой стороной.
        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards[1].front)
        composeRule.onNodeWithText(HardcodedDeck.cards[1].back)
            .assertDoesNotExist()
        assertRatingButtonsHidden()
        // Пропуск до карточки №3 (i+2) не произошёл.
        composeRule.onNodeWithText(HardcodedDeck.cards[2].front)
            .assertDoesNotExist()
        composeRule.onNodeWithText(HardcodedDeck.cards[2].back)
            .assertDoesNotExist()
    }
}
```

### FILE: MAIN.md
```markdown
# MAIN Spec: MnemoCards

> **Артефакт:** мейн-спека проекта (первичный документ)
> **Дата:** 2026-09-16
> **Источники:** промпт пользователя + `.agent/config.yaml`

Этот документ — единая точка правды о приложении. Каждое новое изменение
(change-*) генерируется с учётом этого файла.

## What Is This App

MnemoCards — Android-приложение для заучивания слов по карточкам (мобильный аналог
Anki). Карточка — пара «слово + перевод». Пользователь видит лицевую сторону,
переворачивает карточку и оценивает ответ; планировщик интервальных повторений
(SRS) определяет, когда карточка появится снова.

Границы репозитория: **только Android-клиент на Kotlin**. Backend синхронизации —
отдельный сервис на собственном хостинге, в репозиторий не входит; клиент
общается с ним по REST API.

Ключевое свойство — **offline-first**: локальная БД является основным источником
данных для UI, приложение полностью работоспособно без сети; синхронизация колод
с сервером выполняется в фоне.

Явно исключено из скоупа (не делать): социальные функции, магазин колод, аудио и
картинки на карточках.

## Tech Stack

- **Язык:** Kotlin
- **Платформа:** Android SDK; UI — Jetpack Compose
- **Локальные данные:** Room (офлайн-кэш, основной источник данных)
- **Сеть:** Retrofit + OkHttp (REST-клиент к внешнему сервису синхронизации)
- **Асинхронность:** Coroutines / Flow
- **Тесты:** JUnit + MockWebServer
- **Базовый пакет:** `com.mnemocards`

## Architecture

- **Паттерн:** MVVM + UDF. События текут вверх (Compose → ViewModel), состояние —
  вниз (StateFlow из ViewModel → Compose). Compose-экраны не содержат
  бизнес-логики.
- **Слои клиента:** UI (Compose) → ViewModel → Repository → источники данных:
  Room (локальный, основной) и Retrofit-сервис (сеть).
- **Offline-first данные:** Room — single source of truth для UI. Сетевые ответы
  применяются к БД; UI наблюдает БД через Flow и обновляется автоматически.
  Отсутствие сети не блокирует ни один пользовательский сценарий.
- **Синхронизация:** фоновый обмен изменениями колод с REST-сервером; результат
  применяется в Room по тем же принципам (детали механизма — в спеках
  соответствующих change-*).
- **Внешняя система:** REST backend синхронизации (вне репозитория, собственный
  хостинг). Контракт API фиксируется в спеках изменений.
- **SRS:** планировщик интервальных повторений рассчитывает следующий показ
  карточки на основе оценки ответа (алгоритм уточняется в change-*).

## Capabilities

Стартовый набор (заложен при создании проекта; детали и пополнение — через change-*):

- capability `cards.review` — сессия повторения: показ лицевой стороны (слово),
  переворот (перевод), оценка ответа.
  **Реализована** — первая версия, change `add-review-screen` (2026-09-16):
  статические данные (захардкоженная колода из 5 карточек), полный цикл сессии —
  старт с лицевой стороны, переворот по тапу, четыре эквивалентные оценки
  («Забыл» / «Трудно» / «Хорошо» / «Легко»), переход к следующей карточке,
  терминальный экран «на сегодня всё»; состояние сессии переживает смену
  конфигурации. Интеграция с `offline.storage` (Room) и влияние оценок на
  `srs.scheduling` — предмет последующих изменений.
- capability `srs.scheduling` — интервальное повторение: расчёт следующего
  показа карточки по оценке ответа.
- capability `offline.storage` — локальная БД как основной источник данных;
  полная работоспособность без сети.
- capability `sync.background` — фоновая синхронизация колод с сервером по REST.

## Conventions

- **Коммиты:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `docs:`,
  `chore:` и т.д.).
- **Архитектура:** MVVM + UDF — однонаправленный поток данных; ViewModel владеет
  состоянием, экраны Compose декларативны и stateless по бизнес-данным.
- **Стек:** строго соответствует `.agent/config.yaml`. Расширение стека или смена
  фундамента — только через обновление этой мейн-спеки.
- **Именование:** базовый пакет `com.mnemocards`; слои разделяются по пакетам
  (ui / domain / data).
- **API:** клиент общается с backend только по REST через Retrofit + OkHttp;
  сетевые вызовы не выполняются в UI-потоке и не блокируют UI.
- **Границы скоупа:** без социальных функций, магазина колод, аудио и картинок на
  карточках — предложения таких изменений отклоняются.

<!--
ПРАВИЛА (из .agent/schema.yaml, project_spec):
  - Секции: What Is This App, Tech Stack, Architecture, Capabilities, Conventions
  - Стек согласовывать с .agent/config.yaml (project_context)
  - Capabilities пополняются по мере реализации изменений (change-*)
  - Обновлять при изменении фундамента приложения
-->
```
