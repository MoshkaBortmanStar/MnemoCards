package com.mnemocards.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.file.SafCsvFileGateway
import com.mnemocards.data.repository.RoomCardRepository
import com.mnemocards.ui.theme.MnemoCardsTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Навигация (S1, S22): тап по элементу колоды открывает содержимое
 * колоды; кнопка «Тренировать» ведёт на экран тренировки; back-стек
 * список → содержимое → форма (S14) обеспечивается NavController.
 */
@RunWith(AndroidJUnit4::class)
class AppNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: MnemoCardsDatabase

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = java.util.UUID.randomUUID().toString()
        db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(id = deckId, name = "Английский базовый"),
        )
        repository.addCard(deckId, "hello", "привет")
        repository.addCard(deckId, "dog", "собака")

        // Зависимости экрана настроек синхронизации (change-008): тот же
        // вайринг, что в AppContainer, на собственном файле DataStore.
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { java.io.File(context.filesDir, "navhost-test-sync.preferences_pb") },
        )
        val keyValueStore = com.mnemocards.data.syncsettings.DataStoreKeyValueStore(dataStore)
        val tokenStorage = com.mnemocards.data.syncsettings.EncryptedTokenStorage(
            keyValueStore,
            com.mnemocards.data.syncsettings.KeystoreTokenCipher(),
        )
        val syncSettingsStorage = com.mnemocards.data.syncsettings.DefaultSyncSettingsStorage(
            keyValueStore,
            tokenStorage,
        ) { }
        val syncApi = retrofit2.Retrofit.Builder()
            .baseUrl("https://localhost/")
            .addConverterFactory(
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(com.mnemocards.data.network.SyncApi::class.java)
        val syncSettingsRepository = com.mnemocards.data.syncsettings.SyncSettingsRepository(
            api = syncApi,
            settingsStorage = syncSettingsStorage,
            tokenStorage = tokenStorage,
        )

        composeRule.setContent {
            MnemoCardsTheme {
                AppNavHost(
                    db = db,
                    csvFileGateway = SafCsvFileGateway(
                        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
                    ),
                    syncFlagDataStore = com.mnemocards.data.sync.SyncFlagDataStore.fromContext(
                        InstrumentationRegistry.getInstrumentation().targetContext,
                    ),
                    syncSettingsRepository = syncSettingsRepository,
                    syncSettingsStorage = syncSettingsStorage,
                )
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // S1: тап по колоде открывает её содержимое с карточками.
    @Test
    fun tapDeck_opensDeckContent() {
        composeRule.onNodeWithText("Английский базовый").performClick()

        composeRule.onNodeWithText("hello — привет").assertIsDisplayed()
        composeRule.onNodeWithContentDescriptionLabel("Назад").assertIsDisplayed()
    }

    // S22: кнопка «Тренировать» открывает тренировку этой колоды.
    @Test
    fun trainButton_opensReviewSession() {
        composeRule.onNodeWithText("Тренировать").performClick()

        composeRule.waitForIdle()
        // Сессия открыта: показана лицевая сторона какой-то из карточек колоды
        // (все сеяные карточки готовы сразу, порядок равных due не фиксирован).
        composeRule.waitUntil(5_000) {
            listOf("hello", "dog").any {
                composeRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider
        .onNodeWithContentDescriptionLabel(label: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(label))
}
