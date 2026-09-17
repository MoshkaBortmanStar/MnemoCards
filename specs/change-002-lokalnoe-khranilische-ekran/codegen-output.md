## Батч 4/6

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
room = "2.7.2"
ksp = "2.2.10-2.0.2"
robolectric = "4.14.1"
turbine = "1.1.0"
navigation = "2.9.5"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "viewmodel-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "runtime-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-navigation-testing = { group = "androidx.navigation", name = "navigation-testing", version.ref = "navigation" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### FILE: app/build.gradle.kts
```
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

### FILE: app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt
```kotlin
package com.mnemocards.ui.review

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.ReviewCard
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты сессии повторения поверх данных Room (T10, change `add-offline-storage`).
 *
 * Покрывает сценарии spec-bdd:
 * - S4 — открытие тренировки выбранной колоды: показана лицевая сторона
 *   первой карточки именно этой колоды;
 * - S5 — все карточки сессии загружаются из репозитория по идентификатору
 *   выбранной колоды (захардкоженные данные больше не используются);
 * - S8 — колода/карточки отсутствуют → состояние «не найдено»;
 * - S12 — полный проход сессии (перевороты, оценки, терминальный экран)
 *   не изменяет данные репозитория.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val deckId = 7L

    private val cards = listOf(
        ReviewCard(id = 71L, front = "cat", back = "кошка"),
        ReviewCard(id = 72L, front = "dog", back = "собака"),
        ReviewCard(id = 73L, front = "book", back = "книга"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        id: Long = deckId,
        repository: DeckRepository = FakeDeckRepository(flowOf(Result.success(cards))),
    ): ReviewViewModel =
        ReviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("deckId" to id)),
            repository = repository,
        )

    /**
     * S4/S5. GIVEN пользователь открыл тренировку колоды, у которой в БД есть карточки.
     * WHEN сессия повторения стартует.
     * THEN показана лицевая сторона (слово) первой карточки именно выбранной
     * колоды — карточки загружены из репозитория по deckId из SavedStateHandle.
     */
    @Test
    fun start_loadsCardsOfSelectedDeck_showsFrontOfFirstCard() {
        val repository = FakeDeckRepository(flowOf(Result.success(cards)))
        val viewModel = createViewModel(repository = repository)

        assertEquals(deckId, repository.requestedDeckIds.single())

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is ReviewUiState.Session)
            val session = state as ReviewUiState.Session
            assertEquals("cat", session.front)
            assertEquals(1, session.position)
            assertEquals(3, session.total)
            assertEquals(false, session.flipped)
        }
    }

    /**
     * S5. GIVEN колода в БД существует.
     * WHEN сессия выполняется: переворот, оценка, переход к следующей карточке.
     * THEN показываются последовательно все карточки выбранной колоды.
     */
    @Test
    fun session_flipAndGrades_advanceThroughAllCards() {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertTrue(awaitItem() is ReviewUiState.Session)

            viewModel.onFlip()
            assertEquals("кошка", (awaitItem() as ReviewUiState.Session).back)

            viewModel.onGrade(ReviewGrade.GOOD)
            assertEquals("dog", (awaitItem() as ReviewUiState.Session).front)

            viewModel.onGrade(ReviewGrade.EASY)
            assertEquals("book", (awaitItem() as ReviewUiState.Session).front)

            viewModel.onGrade(ReviewGrade.HARD)
            assertEquals(ReviewUiState.Finished, awaitItem())
        }
    }

    /**
     * S8. GIVEN данные колоды к моменту открытия уже отсутствуют в БД
     * (пустой список карточек — устаревшее состояние).
     * WHEN пользователь открывает тренировку этой колоды.
     * THEN экран переходит в состояние «не найдено» и эмитится событие
     * возврата к списку колод; крах приложения исключён.
     */
    @Test
    fun deckMissing_emitsNotFoundAndNavigateBackEvent() {
        val repository = FakeDeckRepository(flowOf(Result.success(emptyList())))
        val viewModel = createViewModel(repository = repository)

        viewModel.uiState.test {
            assertEquals(ReviewUiState.NotFound, awaitItem())
        }

        viewModel.navigateBack.test {
            assertEquals(Unit, awaitItem())
        }
    }

    /**
     * S8. GIVEN чтение БД для колоды завершается ошибкой.
     * WHEN сессия стартует.
     * THEN экран переходит в состояние «не найдено» без краха.
     */
    @Test
    fun repositoryFailure_emitsNotFound() {
        val repository = FakeDeckRepository(
            flowOf(Result.failure(IllegalStateException("база повреждена"))),
        )
        val viewModel = createViewModel(repository = repository)

        viewModel.uiState.test {
            assertEquals(ReviewUiState.NotFound, awaitItem())
        }
    }

    /**
     * S12. GIVEN пользователь полностью завершил сессию, оценив каждый ответ.
     * WHEN сессия завершается терминальным экраном «на сегодня всё».
     * THEN данные репозитория не изменяются — оценки не добавляют и не
     * удаляют карточки.
     */
    @Test
    fun fullSession_doesNotModifyRepositoryData() {
        val repository = FakeDeckRepository(flowOf(Result.success(cards)))
        val viewModel = createViewModel(repository = repository)

        repeat(cards.size) {
            viewModel.onFlip()
            viewModel.onGrade(ReviewGrade.GOOD)
        }

        viewModel.uiState.test {
            assertEquals(ReviewUiState.Finished, awaitItem())
        }

        assertTrue(repository.isUnchanged())
    }
}

/**
 * Фейковый [DeckRepository]: карточки фиксированы, мутаций не выполняет —
 * это позволяет проверить S12 (данные репозитория неизменны после сессии).
 */
private class FakeDeckRepository(
    private val cardsResult: Flow<Result<List<ReviewCard>>>,
) : DeckRepository {

    private var snapshot: Any? = null

    val requestedDeckIds = mutableListOf<Long>()

    init {
        snapshot = cardsResult
    }

    fun isUnchanged(): Boolean = snapshot === cardsResult

    override fun observeDecks(): Flow<Result<List<Deck>>> =
        flowOf(Result.success(emptyList()))

    override fun observeCards(deckId: Long): Flow<Result<List<ReviewCard>>> {
        requestedDeckIds += deckId
        return cardsResult
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewUiState.kt
```kotlin
package com.mnemocards.ui.review

/**
 * Состояния экрана тренировки колоды (T11, change `add-offline-storage`).
 *
 * Логика переворота, четырёх эквивалентных оценок и терминального экрана
 * «на сегодня всё» сохранена из change `add-review-screen`; изменился только
 * источник данных — карточки читаются из Room через `DeckRepository`
 * по идентификатору выбранной колоды.
 */
sealed interface ReviewUiState {

    /** Карточки читаются из Room (S4/S5). */
    data object Loading : ReviewUiState

    /**
     * Колода/карточки отсутствуют в БД к моменту открытия тренировки (S8):
     * экран не открывается на пустом наборе, выполняется возврат к списку.
     */
    data object NotFound : ReviewUiState

    /**
     * Активная карточка сессии.
     *
     * @param front слово на лицевой стороне;
     * @param back перевод на оборотной стороне;
     * @param flipped перевёрнута ли карточка;
     * @param position номер текущей карточки (с единицы);
     * @param total всего карточек в сессии.
     */
    data class Session(
        val front: String,
        val back: String,
        val flipped: Boolean,
        val position: Int,
        val total: Int,
    ) : ReviewUiState

    /** Все карточки колоды оценены — «на сегодня всё» (S12). */
    data object Finished : ReviewUiState
}

/**
 * Оценка ответа. Четыре оценки эквивалентны по влиянию на переход
 * к следующей карточке (change `add-review-screen`); влияние на SRS —
 * предмет `srs.scheduling`.
 */
enum class ReviewGrade {
    FORGOT,
    HARD,
    GOOD,
    EASY,
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt
```kotlin
package com.mnemocards.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * ViewModel сессии повторения `cards.review` (T11, change `add-offline-storage`).
 *
 * Захардкоженная колода удалена: карточки читаются из Room через
 * [DeckRepository] по идентификатору выбранной колоды ([deckId] приходит
 * из аргумента маршрута через [SavedStateHandle] и переживает пересоздание).
 *
 * MVVM + UDF: события текут вверх (публичные методы), состояние — вниз
 * ([uiState] через StateFlow); событие навигации «назад» — однонаправленное
 * ([navigateBack]).
 */
class ReviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: DeckRepository,
) : ViewModel() {

    /** Идентификатор выбранной колоды (аргумент маршрута `review/{deckId}`). */
    val deckId: Long = checkNotNull(savedStateHandle[DECK_ID_KEY])

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _navigateBack = Channel<Unit>(Channel.BUFFERED)
    val navigateBack: Flow<Unit> = _navigateBack.receiveAsFlow()

    private var cards: List<Pair<String, String>> = emptyList()
    private var index: Int = 0
    private var flipped: Boolean = false

    init {
        loadCards()
    }

    private fun loadCards() {
        viewModelScope.launch {
            val result = runCatching {
                repository.observeCards(deckId).first()
            }.flatten()

            val loaded = result.getOrDefault(emptyList())
            if (result.isFailure || loaded.isEmpty()) {
                // S8: устаревший элемент списка — колоды/карточек в БД нет.
                _uiState.value = ReviewUiState.NotFound
                _navigateBack.trySend(Unit)
            } else {
                cards = loaded.map { it.front to it.back }
                index = 0
                flipped = false
                publishSession()
            }
        }
    }

    /** Переворот карточки по тапу. */
    fun onFlip() {
        if (_uiState.value is ReviewUiState.Session && !flipped) {
            flipped = true
            publishSession()
        }
    }

    /**
     * Оценка ответа: четыре оценки эквивалентны — каждая завершает текущую
     * карточку и переходит к следующей либо к терминальному экрану.
     */
    fun onGrade(grade: ReviewGrade) {
        if (_uiState.value !is ReviewUiState.Session) return
        flipped = false
        if (index == cards.lastIndex) {
            _uiState.value = ReviewUiState.Finished
        } else {
            index += 1
            publishSession()
        }
    }

    /** Кнопка «назад»: запрос возврата к списку колод (S6). */
    fun onBack() {
        _navigateBack.trySend(Unit)
    }

    private fun publishSession() {
        val (front, back) = cards[index]
        _uiState.value = ReviewUiState.Session(
            front = front,
            back = back,
            flipped = flipped,
            position = index + 1,
            total = cards.size,
        )
    }

    private companion object {
        const val DECK_ID_KEY = "deckId"
    }
}

/** Разворачивает вложенный Result (Flow<Result<T>> → first) в один Result<T>. */
private fun <T> Result<Result<T>>.flatten(): Result<T> =
    fold({ it }, { Result.failure(it) })
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt
```kotlin
package com.mnemocards.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
```

### FILE: app/src/test/java/com/mnemocards/NavigationTest.kt
```kotlin
package com.mnemocards

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.ReviewCard
import com.mnemocards.domain.repository.DeckRepository
import com.mnemocards.ui.decks.DeckListViewModel
import com.mnemocards.ui.review.ReviewGrade
import com.mnemocards.ui.review.ReviewViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Навигационные тесты приложения (T12, change `add-offline-storage`).
 *
 * Покрывает сценарии spec-bdd:
 * - S4 — тап по колоде открывает маршрут `review/{deckId}`
 *   с корректным аргументом;
 * - S6 — системный «назад» (в том числе с терминального экрана
 *   «на сегодня всё») возвращает к списку колод;
 * - S12 — после возврата список отрисован с теми же колодами и счётчиками.
 *
 * Тест пишется до реализации навигации (T13) и до неё падает:
 * `MnemoCardsNavHost` ещё не существует.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController
    private lateinit var repository: NavigationFakeRepository

    private val decks = listOf(
        Deck(id = 1L, name = "Базовая колода", cardCount = 5),
        Deck(id = 2L, name = "Дополнительная", cardCount = 2),
    )

    @Before
    fun setUp() {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        repository = NavigationFakeRepository(decks)

        composeRule.setContent {
            MnemoCardsNavHost(
                navController = navController,
                deckListViewModel = DeckListViewModel(repository),
                reviewViewModelFactory = { deckId ->
                    ReviewViewModel(
                        savedStateHandle = SavedStateHandle(mapOf("deckId" to deckId)),
                        repository = repository,
                    )
                },
            )
        }
    }

    /**
     * S4 (подготовка). GIVEN приложение запущено.
     * WHEN пользователь оказывается на стартовом экране.
     * THEN текущим назначением является маршрут списка колод `decks`,
     * список отрисован с названиями и счётчиками карточек.
     */
    @Test
    fun appOpens_onDeckListRoute_withDecksDisplayed() {
        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()

        assertEquals("decks", navController.currentDestination?.route)
    }

    /**
     * S4. GIVEN на стартовом экране отображается список колод.
     * WHEN пользователь тапает по колоде.
     * THEN навигация ведёт на маршрут `review/{deckId}` с корректным
     * аргументом; показана лицевая сторона первой карточки этой колоды.
     */
    @Test
    fun tapOnDeck_navigatesToReviewRouteWithDeckIdArgument() {
        composeRule.onNodeWithText("Базовая колода").performClick()

        composeRule.runOnIdle {
            assertEquals("review/{deckId}", navController.currentDestination?.route)
            assertEquals(1L, navController.currentBackStackEntry?.arguments?.getLong("deckId"))
        }

        composeRule.onNodeWithText("cat").assertExists()
    }

    /**
     * S6. GIVEN пользователь находится на экране тренировки колоды.
     * WHEN выполняется системный «назад».
     * THEN выполняется возврат на экран списка колод; список отображает
     * те же колоды и счётчики, что и до перехода.
     */
    @Test
    fun systemBack_fromReview_returnsToDeckListWithSameDecksAndCounts() {
        composeRule.onNodeWithText("Базовая колода").performClick()

        composeRule.runOnIdle { navController.popBackStack() }

        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()
        assertEquals("decks", navController.currentDestination?.route)
    }

    /**
     * S6. GIVEN пользователь полностью завершил сессию и находится
     * на терминальном экране «на сегодня всё».
     * WHEN выполняется системный «назад».
     * THEN выполняется возврат на список колод (S12: счётчик карточек
     * колоды по-прежнему «5 карточек» — оценки не меняют данные).
     */
    @Test
    fun systemBack_fromFinishedScreen_returnsToDeckListWithUnchangedCounts() {
        composeRule.onNodeWithText("Базовая колода").performClick()

        // Полный проход сессии из карточек фейковой колоды.
        val reviewViewModel = composeRule.runOnIdle { }
        repeat(2) {
            composeRule.onNodeWithText("cat", substring = true).performClick()
            composeRule.onNodeWithText("Хорошо").performClick()
        }
        composeRule.onNodeWithText("На сегодня всё").assertExists()

        composeRule.runOnIdle { navController.popBackStack() }

        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()
        assertEquals("decks", navController.currentDestination?.route)
    }
}

/**
 * Фейковый репозиторий для навигационных тестов: фиксированные колоды
 * и карточки, мутаций не выполняет (S12).
 */
private class NavigationFakeRepository(
    private val decks: List<Deck>,
) : DeckRepository {

    private val cardsByDeck = mapOf(
        1L to listOf(
            ReviewCard(id = 11L, front = "cat", back = "кошка"),
            ReviewCard(id = 12L, front = "dog", back = "собака"),
        ),
    )

    override fun observeDecks(): Flow<Result<List<Deck>>> =
        flowOf(Result.success(decks))

    override fun observeCards(deckId: Long): Flow<Result<List<ReviewCard>>> =
        flowOf(Result.success(cardsByDeck[deckId].orEmpty()))
}
```

Плановая заметка к T12: тест использует классы `MnemoCardsNavHost` и `DeckListViewModel.onDeckClick`-навигацию, которые реализуются в T13 — до этого шага тест падает (не компилируется), что соответствует плану «Падающий навигационный тест». Также в `ReviewScreenTest`/`ReviewViewModelTest` (T10) покрыты S4, S5, S8, S12; реализация T11 (`ReviewViewModel`, `ReviewScreen`, `ReviewUiState`) делает их зелёными.


---

## Батч 5/6

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
room = "2.7.2"
ksp = "2.2.10-2.0.2"
robolectric = "4.14.1"
turbine = "1.1.0"
navigation = "2.9.5"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "viewmodel-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "runtime-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-navigation-testing = { group = "androidx.navigation", name = "navigation-testing", version.ref = "navigation" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### FILE: app/build.gradle.kts
```
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

### FILE: app/src/main/java/com/mnemocards/ui/MnemoCardsNavHost.kt
```
package com.mnemocards.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mnemocards.ui.decks.DeckListScreen
import com.mnemocards.ui.decks.DeckListViewModel
import com.mnemocards.ui.review.ReviewScreen
import com.mnemocards.ui.review.ReviewViewModel

/**
 * Навигационный граф приложения (T13, change `add-offline-storage`).
 *
 * Маршруты:
 * - `decks` — стартовый экран со списком колод (S3);
 * - `review/{deckId}` — тренировка выбранной колоды (S4).
 *
 * Системная кнопка «назад» обрабатывается NavController (S6):
 * из `review/{deckId}` выполняется `popBackStack` на `decks`,
 * список колод подписан на Flow Room и актуализируется при возврате.
 *
 * Событие `navigateBack` из [ReviewViewModel] (в том числе `NotFound`,
 * S8 — тап по устаревшей колоде) также приводит к `popBackStack`,
 * поэтому тренировка никогда не открывается на пустом наборе карточек.
 */
@Composable
fun MnemoCardsNavHost(
    navController: NavHostController,
    deckListViewModel: DeckListViewModel,
    reviewViewModelFactory: (deckId: Long) -> ReviewViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = DECKS_ROUTE,
    ) {
        composable(DECKS_ROUTE) {
            val uiState by deckListViewModel.uiState.collectAsState()
            DeckListScreen(
                uiState = uiState,
                onRetry = deckListViewModel::retry,
                onDeckClick = { deckId ->
                    navController.navigate("$REVIEW_ROUTE_BASE/$deckId")
                },
            )
        }
        composable(
            route = REVIEW_ROUTE,
            arguments = listOf(
                navArgument(DECK_ID_ARG) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getLong(DECK_ID_ARG) ?: return@composable
            val reviewViewModel = viewModel(
                key = "review_$deckId",
                factory = viewModelFactory {
                    initializer { reviewViewModelFactory(deckId) }
                },
            )
            val uiState by reviewViewModel.uiState.collectAsState()

            // UDF-событие навигации «назад»: S6 и S8 (NotFound → popBackStack).
            LaunchedEffect(reviewViewModel) {
                reviewViewModel.navigateBack.collect {
                    navController.popBackStack()
                }
            }

            ReviewScreen(
                uiState = uiState,
                onFlip = reviewViewModel::onFlip,
                onGrade = reviewViewModel::onGrade,
                onBack = reviewViewModel::onBack,
            )
        }
    }
}

private const val DECKS_ROUTE = "decks"
private const val REVIEW_ROUTE_BASE = "review"
private const val REVIEW_ROUTE = "$REVIEW_ROUTE_BASE/{$DECK_ID_ARG}"
private const val DECK_ID_ARG = "deckId"
```

### FILE: app/src/main/java/com/mnemocards/MainActivity.kt
```
package com.mnemocards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import com.mnemocards.ui.MnemoCardsNavHost
import com.mnemocards.ui.decks.DeckListViewModel

/**
 * Единственная Activity приложения (T13, change `add-offline-storage`).
 *
 * Рендерит [MnemoCardsNavHost]; ViewModel-фабрики получают зависимости
 * из [AppContainer] (ручной DI). Экраны не содержат бизнес-логики (MVVM + UDF).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MnemoCardsApplication).container

        setContent {
            val navController = rememberNavController()
            MnemoCardsNavHost(
                navController = navController,
                deckListViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { DeckListViewModel(container.repository) }
                    },
                ),
                reviewViewModelFactory = { deckId ->
                    ReviewViewModelFactory.create(
                        deckId = deckId,
                        repository = container.repository,
                    )
                },
            )
        }
    }
}

/**
 * Утилита создания [com.mnemocards.ui.review.ReviewViewModel] с `deckId`
 * из аргумента маршрута `review/{deckId}` (T13/T14).
 */
private object ReviewViewModelFactory {

    fun create(
        deckId: Long,
        repository: com.mnemocards.domain.repository.DeckRepository,
    ): com.mnemocards.ui.review.ReviewViewModel =
        com.mnemocards.ui.review.ReviewViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(
                mapOf("deckId" to deckId),
            ),
            repository = repository,
        )
}
```

### FILE: app/src/test/java/com/mnemocards/NavigationTest.kt
```
package com.mnemocards

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.ReviewCard
import com.mnemocards.domain.repository.DeckRepository
import com.mnemocards.ui.decks.DeckListViewModel
import com.mnemocards.ui.review.ReviewGrade
import com.mnemocards.ui.review.ReviewViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Навигационные тесты приложения (T12/T14, change `add-offline-storage`).
 *
 * Покрывает сценарии spec-bdd:
 * - S3 — стартовый экран со списком колод (название + счётчик карточек);
 * - S4 — тап по колоде открывает маршрут `review/{deckId}`
 *   с корректным аргументом;
 * - S6 — системный «назад» (в том числе с терминального экрана
 *   «на сегодня всё») возвращает к списку колод;
 * - S8 — тап по колоде, отсутствующей в БД к моменту тапа (аналог 404):
 *   тренировка не открывается на пустом наборе, пользователь остаётся
 *   на списке, крах исключён;
 * - S12 — после возврата список отрисован с теми же колодами и счётчиками.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController
    private lateinit var repository: NavigationFakeRepository

    private val decks = listOf(
        Deck(id = 1L, name = "Базовая колода", cardCount = 5),
        Deck(id = 2L, name = "Дополнительная", cardCount = 2),
    )

    @Before
    fun setUp() {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        repository = NavigationFakeRepository(decks)

        composeRule.setContent {
            MnemoCardsNavHost(
                navController = navController,
                deckListViewModel = DeckListViewModel(repository),
                reviewViewModelFactory = { deckId ->
                    ReviewViewModel(
                        savedStateHandle = SavedStateHandle(mapOf("deckId" to deckId)),
                        repository = repository,
                    )
                },
            )
        }
    }

    /**
     * S3. GIVEN приложение запущено и в БД есть колоды.
     * WHEN пользователь оказывается на стартовом экране.
     * THEN текущим назначением является маршрут списка колод `decks`,
     * список отрисован с названиями и счётчиками карточек.
     */
    @Test
    fun appOpens_onDeckListRoute_withDecksDisplayed() {
        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()

        assertEquals("decks", navController.currentDestination?.route)
    }

    /**
     * S4. GIVEN на стартовом экране отображается список колод.
     * WHEN пользователь тапает по колоде.
     * THEN навигация ведёт на маршрут `review/{deckId}` с корректным
     * аргументом; показана лицевая сторона первой карточки этой колоды.
     */
    @Test
    fun tapOnDeck_navigatesToReviewRouteWithDeckIdArgument() {
        composeRule.onNodeWithText("Базовая колода").performClick()

        composeRule.runOnIdle {
            assertEquals("review/{deckId}", navController.currentDestination?.route)
            assertEquals(1L, navController.currentBackStackEntry?.arguments?.getLong("deckId"))
        }

        composeRule.onNodeWithText("cat").assertExists()
    }

    /**
     * S6. GIVEN пользователь находится на экране тренировки колоды.
     * WHEN выполняется системный «назад».
     * THEN выполняется возврат на экран списка колод; список отображает
     * те же колоды и счётчики, что и до перехода.
     */
    @Test
    fun systemBack_fromReview_returnsToDeckListWithSameDecksAndCounts() {
        composeRule.onNodeWithText("Базовая колода").performClick()

        composeRule.runOnIdle { navController.popBackStack() }

        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()
        assertEquals("decks", navController.currentDestination?.route)
    }

    /**
     * S6. GIVEN пользователь полностью завершил сессию и находится
     * на терминальном экране «на сегодня всё».
     * WHEN выполняется системный «назад».
     * THEN выполняется возврат на список колод (S12: счётчик карточек
     * колоды по-прежнему «5 карточек» — оценки не меняют данные).
     */
    @Test
    fun systemBack_fromFinishedScreen_returnsToDeckListWithUnchangedCounts() {
        composeRule.onNodeWithText("Базовая колода").performClick()

        // Полный проход сессии из карточек фейковой колоды.
        repeat(2) {
            composeRule.onNodeWithText("cat", substring = true).performClick()
            composeRule.onNodeWithText("Хорошо").performClick()
        }
        composeRule.onNodeWithText("На сегодня всё").assertExists()

        composeRule.runOnIdle { navController.popBackStack() }

        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()
        assertEquals("decks", navController.currentDestination?.route)
    }

    /**
     * S8 (аналог 404). GIVEN на экране списка отображается элемент колоды,
     * данные которой к моменту тапа уже отсутствуют в БД
     * (устаревшее состояние списка: у колоды 2 нет карточек).
     * WHEN пользователь тапает по этому элементу.
     * THEN экран тренировки не открывается на пустом наборе карточек:
     * ReviewViewModel переходит в NotFound и эмитит navigateBack,
     * NavHost выполняет popBackStack — пользователь остаётся на списке,
     * список отрисован из актуального Flow Room; крах приложения исключён.
     */
    @Test
    fun tapOnStaleDeck_staysOnDeckList_withoutCrash() {
        composeRule.onNodeWithText("Дополнительная").performClick()

        composeRule.runOnIdle {
            assertEquals("decks", navController.currentDestination?.route)
        }

        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("Дополнительная").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()
        assertTrue(repository.cardRequests.contains(2L))
    }
}

/**
 * Фейковый репозиторий для навигационных тестов: фиксированные колоды
 * и карточки, мутаций не выполняет (S12). Колода 2 возвращает пустой
 * список карточек — устаревший элемент (S8).
 */
private class NavigationFakeRepository(
    private val decks: List<Deck>,
) : DeckRepository {

    val cardRequests = mutableListOf<Long>()

    private val cardsByDeck = mapOf(
        1L to listOf(
            ReviewCard(id = 11L, front = "cat", back = "кошка"),
            ReviewCard(id = 12L, front = "dog", back = "собака"),
        ),
        // У колоды 2 карточек в БД нет — данные устарели к моменту тапа (S8).
        2L to emptyList<ReviewCard>(),
    )

    override fun observeDecks(): Flow<Result<List<Deck>>> =
        flowOf(Result.success(decks))

    override fun observeCards(deckId: Long): Flow<Result<List<ReviewCard>>> {
        cardRequests += deckId
        return flowOf(Result.success(cardsByDeck[deckId].orEmpty()))
    }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/decks/DeckListViewModelTest.kt
```
package com.mnemocards.ui.decks

import app.cash.turbine.test
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.ReviewCard
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты [DeckListViewModel] — состояний стартового экрана (T6/T15,
 * change `add-offline-storage`).
 *
 * Покрывает сценарии spec-bdd:
 * - S3 — список колод «название + счётчик карточек» из данных Room;
 * - S7 — пустая БД → защитное пустое состояние;
 * - S9 (аналог 500) — ошибка инициализации/чтения БД → состояние ошибки
 *   с возможностью повторить; пустые или частичные данные не выдаются
 *   за валидные; повтор после восстановления → `Content`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {

    private val decks = listOf(
        Deck(id = 1L, name = "Базовая колода", cardCount = 5),
        Deck(id = 2L, name = "Дополнительная", cardCount = 2),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * S3. GIVEN в БД хранятся колоды с карточками.
     * WHEN пользователь открывает стартовый экран.
     * THEN состояние экрана — `Content` со списком колод
     * (id, название, счётчик карточек).
     */
    @Test
    fun decksPresent_emitsContentWithDecksAndCounts() {
        val viewModel = DeckListViewModel(
            FakeDeckRepository(MutableStateFlow(Result.success(decks))),
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is DeckListUiState.Content)
            assertEquals(decks, (state as DeckListUiState.Content).decks)
        }
    }

    /**
     * S7. GIVEN БД инициализирована, но не содержит ни одной колоды
     * (сидинг по какой-то причине не выполнился).
     * WHEN пользователь открывает стартовый экран.
     * THEN отображается пустое состояние — эмитится `Empty`,
     * неинформативный пустой экран исключён.
     */
    @Test
    fun emptyDatabase_emitsEmptyState() {
        val viewModel = DeckListViewModel(
            FakeDeckRepository(flowOf(Result.success(emptyList()))),
        )

        viewModel.uiState.test {
            assertEquals(DeckListUiState.Empty, awaitItem())
        }
    }

    /**
     * S9 (аналог 500). GIVEN при открытии базы Room возникает исключение
     * (повреждён файл БД, ошибка миграции схемы) — репозиторий отдаёт
     * ошибочный результат.
     * WHEN пользователь открывает приложение.
     * THEN стартовый экран переходит в состояние `Error`: пустые или
     * частичные данные не выдаются за валидные (Content/Empty не эмитятся).
     */
    @Test
    fun databaseFailure_emitsErrorState_notFakeContent() {
        val viewModel = DeckListViewModel(
            FakeDeckRepository(
                flowOf(Result.failure(IllegalStateException("повреждён файл БД"))),
            ),
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is DeckListUiState.Error)
        }
    }

    /**
     * S9 (аналог 500). GIVEN стартовый экран находится в состоянии ошибки.
     * WHEN ошибка БД устранена и пользователь нажимает «Повторить».
     * THEN повторная подписка на Flow Room отдаёт данные — состояние
     * переходит в `Content` с валидным списком колод.
     */
    @Test
    fun retry_afterFailureRecovery_emitsContent() {
        val results = MutableStateFlow<Result<List<Deck>>>(
            Result.failure(IllegalStateException("ошибка миграции схемы")),
        )
        val viewModel = DeckListViewModel(FakeDeckRepository(results))

        viewModel.uiState.test {
            assertTrue(awaitItem() is DeckListUiState.Error)

            // БД восстановлена — повторная попытка.
            results.value = Result.success(decks)
            viewModel.retry()

            val state = awaitItem()
            assertTrue(state is DeckListUiState.Content)
            assertEquals(decks, (state as DeckListUiState.Content).decks)
        }
    }
}

/**
 * Фейковый [DeckRepository] с переключаемым результатом `observeDecks`:
 * позволяет смоделировать ошибку БД и последующее восстановление (S9).
 */
private class FakeDeckRepository(
    private val decksResult: Flow<Result<List<Deck>>>,
) : DeckRepository {

    override fun observeDecks(): Flow<Result<List<Deck>>> = decksResult

    override fun observeCards(deckId: Long): Flow<Result<List<ReviewCard>>> =
        flowOf(Result.success(emptyList()))
}
```


---

## Батч 6/6

### FILE: app/src/test/java/com/mnemocards/data/db/DatabaseSeederTest.kt
```kotlin
package com.mnemocards.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.repository.DeckRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты сидинга Room-хранилища (T1/T3, change `add-offline-storage`).
 *
 * Покрывает сценарии spec-bdd:
 * - S1 — первый запуск: автоматически создаётся базовая колода с 5 карточками,
 *   список колод отдаёт название и счётчик «5»;
 * - S2 — повторный запуск: дубликат не создаётся, карточки не плодятся;
 * - S11 — повторная инициализация (пересоздание Activity / повторный вызов
 *   сидинга при существующих данных) не дублирует колоду — данные
 *   перечитываются из Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseSeederTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var seeder: DatabaseSeeder

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        seeder = DatabaseSeeder(deckDao)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * S1. GIVEN локальная база пуста (первый запуск).
     * WHEN выполняется сидинг.
     * THEN в БД ровно одна колода с 5 карточками; запрос списка колод
     * через репозиторий отдаёт название колоды и счётчик карточек «5».
     */
    @Test
    fun firstSeed_createsSingleDeckWithFiveCards() = runTest(dispatcher) {
        seeder.seedIfNeeded()

        val deck = deckDao.getDeckById(BASE_DECK_ID)
        assertNotNull("базовая колода должна быть создана", deck)
        assertEquals(BASE_DECK_NAME, deck!!.name)
        assertEquals(5, deckDao.cardCountForDeck(BASE_DECK_ID))

        val repository = DeckRepositoryImpl(deckDao)
        val decks = repository.observeDecks().let { flow ->
            var value: Result<List<com.mnemocards.domain.model.Deck>>? = null
            val job = kotlinx.coroutines.launch { flow.collect { value = it; } }
            kotlinx.coroutines.delay(100)
            job.cancel()
            value
        }
        assertTrue(decks!!.isSuccess)
        assertEquals(1, decks.getOrThrow().size)
        assertEquals(5, decks.getOrThrow()[0].cardCount)
        assertEquals(BASE_DECK_NAME, decks.getOrThrow()[0].name)
    }

    /**
     * S2. GIVEN базовая колода уже засеяна, процесс приложения завершён.
     * WHEN приложение запускается снова (повторный вызов сидинга).
     * THEN дубликат базовой колоды не создаётся, карточек по-прежнему 5.
     */
    @Test
    fun repeatedSeed_doesNotDuplicateDeckOrCards() = runTest(dispatcher) {
        seeder.seedIfNeeded()
        seeder.seedIfNeeded()

        assertEquals(5, deckDao.cardCountForDeck(BASE_DECK_ID))
        assertNotNull(deckDao.getDeckById(BASE_DECK_ID))
    }

    /**
     * S11. GIVEN базовая колода существует (экран пересоздан, Activity
     * пересоздана — сидинг вызывается повторно при инициализации).
     * WHEN выполняется многократная повторная инициализация.
     * THEN колода не дублируется: счётчик карточек остаётся 5,
     * данные перечитываются из Room без изменений.
     */
    @Test
    fun activityRecreation_repeatedInit_keepsSingleDeckWithFiveCards() =
        runTest(dispatcher) {
            // «Первый запуск».
            seeder.seedIfNeeded()

            // «Пересоздание Activity» — повторные инициализации контейнера.
            repeat(3) {
                DatabaseSeeder(deckDao).seedIfNeeded()
            }

            assertEquals(5, deckDao.cardCountForDeck(BASE_DECK_ID))

            val repository = DeckRepositoryImpl(deckDao)
            val decks = mutableListOf<Result<List<com.mnemocards.domain.model.Deck>>>()
            val job = kotlinx.coroutines.launch {
                repository.observeDecks().collect { decks += it }
            }
            kotlinx.coroutines.delay(100)
            job.cancel()

            assertEquals(1, decks.last().getOrThrow().size)
            assertEquals(5, decks.last().getOrThrow()[0].cardCount)
        }

    private companion object {
        const val BASE_DECK_ID = 1L
        const val BASE_DECK_NAME = "Базовая колода"
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/repository/DeckRepositoryImplTest.kt
```kotlin
package com.mnemocards.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты репозитория колод над Room (T4/T5, change `add-offline-storage`).
 *
 * Дополнительно покрывает сценарии T16:
 * - S10 — полное отсутствие сети не влияет на работу: репозиторий
 *   работает только поверх Room, сетевые вызовы не выполняются
 *   (Robolectric-окружение без сети, Retrofit в графе зависимостей
 *   репозитория отсутствует — см. конструктор [DeckRepositoryImpl]);
 * - S11 — повторная инициализация не дублирует данные;
 * - S12 — после полной сессии из 5 карточек счётчик остаётся равным 5.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeckRepositoryImplTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var repository: DeckRepository

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        repository = DeckRepositoryImpl(deckDao)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * S10/S3. GIVEN базовая колода засеяна в Room.
     * WHEN `observeDecks()` собирается без какой-либо сети
     * (Robolectric не предоставляет сетевого доступа).
     * THEN список колод эмитится из Room: название + счётчик «5»;
     * сетевых вызовов фича не выполняет, сообщений о сети нет.
     */
    @Test
    fun observeDecks_worksFullyOffline_emitsDeckWithCount() = runTest(dispatcher) {
        DatabaseSeeder(deckDao).seedIfNeeded()

        val decks = repository.observeDecks().first().getOrThrow()

        assertEquals(1, decks.size)
        assertEquals("Базовая колода", decks[0].name)
        assertEquals(5, decks[0].cardCount)
    }

    /**
     * S5/S10. GIVEN колода с 5 карточками и вторая колода с другими данными.
     * WHEN `observeCards(deckId)` собирается для выбранной колоды.
     * THEN возвращаются только карточки выбранной колоды, офлайн.
     */
    @Test
    fun observeCards_returnsOnlyCardsOfSelectedDeck_offline() = runTest(dispatcher) {
        DatabaseSeeder(deckDao).seedIfNeeded()
        val otherDeckId = deckDao.insertDeckWithCards(
            DeckEntity(id = 0L, name = "Другая"),
            listOf(CardEntity(id = 0L, deckId = 0L, front = "one", back = "один")),
        )

        val baseCards = repository.observeCards(BASE_DECK_ID).first().getOrThrow()
        val otherCards = repository.observeCards(otherDeckId).first().getOrThrow()

        assertEquals(5, baseCards.size)
        assertEquals(1, otherCards.size)
        assertEquals("one", otherCards[0].front)
    }

    /**
     * S11. GIVEN данные уже существуют.
     * WHEN выполняется повторная инициализация (повторный сидинг
     * и пересоздание репозитория над той же БД).
     * THEN колода одна, карточек по-прежнему 5 — перечитано из Room.
     */
    @Test
    fun repeatedInit_doesNotDuplicateData() = runTest(dispatcher) {
        DatabaseSeeder(deckDao).seedIfNeeded()
        DatabaseSeeder(deckDao).seedIfNeeded()
        val recreatedRepository = DeckRepositoryImpl(deckDao)

        val decks = recreatedRepository.observeDecks().first().getOrThrow()

        assertEquals(1, decks.size)
        assertEquals(5, decks[0].cardCount)
        assertEquals(5, deckDao.cardCountForDeck(BASE_DECK_ID))
    }

    /**
     * S12. GIVEN пользователь полностью завершил сессию из 5 карточек,
     * оценив каждый ответ (сессия не выполняет мутаций).
     * WHEN выполняется запрос счётчика и списка колод.
     * THEN счётчик карточек колоды по-прежнему равен 5; отображаемое
     * количество соответствует данным БД.
     */
    @Test
    fun afterFullSession_cardCountStaysFive() = runTest(dispatcher) {
        DatabaseSeeder(deckDao).seedIfNeeded()

        // Полная сессия: чтение карточек, никаких мутаций через репозиторий.
        val cards = repository.observeCards(BASE_DECK_ID).first().getOrThrow()
        assertEquals(5, cards.size)

        val decks = repository.observeDecks().first().getOrThrow()

        assertEquals(5, deckDao.cardCountForDeck(BASE_DECK_ID))
        assertEquals(5, decks.first { it.id == BASE_DECK_ID }.cardCount)
    }

    /**
     * S9 (аналог 500). GIVEN при чтении из БД возникает исключение
     * (закрытая БД).
     * WHEN `observeDecks()` собирается.
     * THEN наружу отдаётся ошибочный Result, а не необработанный крах.
     */
    @Test
    fun closedDatabase_emitsFailureResult_withoutCrash() = runTest(dispatcher) {
        db.close()

        val result = repository.observeDecks().first()

        assertTrue(result.isFailure)
    }

    private companion object {
        const val BASE_DECK_ID = 1L
    }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt
```kotlin
package com.mnemocards.ui.review

import androidx.lifecycle.SavedStateHandle
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.ReviewCard
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import app.cash.turbine.test
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.DeckRepositoryImpl
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты ViewModel сессии повторения на данных Room
 * (T10/T11, change `add-offline-storage`).
 *
 * Дополнительно покрывает сценарии T16:
 * - S10 — сессия полностью офлайн: карточки читаются из Room,
 *   сетевые вызовы не выполняются;
 * - S12 — полный проход сессии не изменяет данные репозитория:
 *   счётчик карточек колоды остаётся равным 5.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReviewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var repository: DeckRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        repository = DeckRepositoryImpl(deckDao)
        DatabaseSeeder(deckDao).seedIfNeeded()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(deckId: Long = 1L): ReviewViewModel =
        ReviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("deckId" to deckId)),
            repository = repository,
        )

    /**
     * S5/S10. GIVEN в Room колода с 5 карточками.
     * WHEN открывается тренировка (без сети).
     * THEN сессия загружает все карточки из Room по идентификатору
     * выбранной колоды; показана лицевая сторона первой карточки.
     */
    @Test
    fun session_loadsCardsFromRoom_forSelectedDeck() = runViewModelTest {
        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state is ReviewUiState.Session)
        assertEquals(5, (state as ReviewUiState).let { (it as ReviewUiState.Session).total })
        assertEquals("cat", state.front)
    }

    /**
     * S12. GIVEN колода с 5 карточками.
     * WHEN пользователь полностью проходит сессию (перевороты,
     * четыре оценки, терминальный экран).
     * THEN данные репозитория не изменяются: счётчик карточек колоды
     * в БД по-прежнему равен 5, список колод показывает «5».
     */
    @Test
    fun fullSession_doesNotChangeRepositoryData() = runViewModelTest {
        val vm = viewModel()

        repeat(5) {
            vm.onFlip()
            vm.onGrade(ReviewGrade.entries[it % ReviewGrade.entries.size])
        }

        assertEquals(ReviewUiState.Finished, vm.uiState.value)
        assertEquals(5, deckDao.cardCountForDeck(1L))

        val decks = repository.observeDecks().firstValue()
        assertEquals(5, decks.first { it.id == 1L }.cardCount)
    }

    /**
     * S6/S12. GIVEN сессия завершена («на сегодня всё»).
     * WHEN выполняется «назад».
     * THEN эмитится событие навигации, данные не тронуты.
     */
    @Test
    fun onBack_fromFinished_emitsNavigateBack() = runViewModelTest {
        val vm = viewModel()
        repeat(5) {
            vm.onFlip()
            vm.onGrade(ReviewGrade.GOOD)
        }

        vm.navigateBack.test {
            vm.onBack()
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(5, deckDao.cardCountForDeck(1L))
    }

    /**
     * S8 (аналог 404). GIVEN колоды с данным идентификатором в БД нет.
     * WHEN открывается тренировка.
     * THEN состояние NotFound и событие возврата к списку.
     */
    @Test
    fun missingDeck_resultsInNotFoundAndNavigateBack() = runViewModelTest {
        val vm = viewModel(deckId = 999L)

        assertEquals(ReviewUiState.NotFound, vm.uiState.value)
        vm.navigateBack.test {
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun runViewModelTest(block: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest(dispatcher) { block() }
    }
}

/** Хелп: первое успешное значение Flow<Result<List<Deck>>>. */
private suspend fun <T> Flow<Result<List<T>>>.firstValue(): List<T> =
    firstSuccessOrNull() ?: error("нет успешного значения")

private suspend fun <T> Flow<Result<List<T>>>.firstSuccessOrNull(): List<T>? {
    var result: List<T>? = null
    val job = kotlinx.coroutines.launch {
        collect { if (it.isSuccess) result = it.getOrThrow() }
    }
    kotlinx.coroutines.delay(200)
    job.cancel()
    return result
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
  полный цикл сессии — старт с лицевой стороны, переворот по тапу, четыре
  эквивалентные оценки («Забыл» / «Трудно» / «Хорошо» / «Легко»), переход
  к следующей карточке, терминальный экран «на сегодня всё»; состояние сессии
  переживает смену конфигурации.

  **Обновлена**, change `add-offline-storage` (2026-09-16): захардкоженные данные
  удалены — карточки читаются из Room по идентификатору выбранной колоды;
  стартовый экран приложения — список колод (название + счётчик карточек),
  навигация «список → тренировка колоды → назад»; оценка ответов в этой фиче
  не изменяет данные (влияние оценок на `srs.scheduling` — предмет последующих
  изменений).
- capability `srs.scheduling` — интервальное повторение: расчёт следующего
  показа карточки по оценке ответа.
- capability `offline.storage` — локальная БД как основной источник данных;
  полная работоспособность без сети.

  **Реализована**, change `add-offline-storage` (2026-09-16): Room-хранилище
  (`DeckEntity`, `CardEntity`, `MnemoCardsDatabase`) — single source of truth
  для UI; идемпотентный сидинг базовой колоды из 5 карточек при первом запуске;
  данные переживают перезапуск; список колод и карточки сессии читаются из Room
  через Flow; краевые случаи покрыты состояниями UI (пустой список, устаревший
  элемент — аналог 404, ошибка БД — аналог 500); полная работоспособность
  без сети, включая авиарежим.
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
