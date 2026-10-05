package com.mnemocards

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.file.CsvFileGateway
import com.mnemocards.data.file.SafCsvFileGateway
import com.mnemocards.data.network.SyncApi
import com.mnemocards.data.sync.SessionRunner
import com.mnemocards.data.sync.SyncEngine
import com.mnemocards.data.sync.SyncFlagDataStore
import com.mnemocards.data.sync.SyncManager
import com.mnemocards.data.syncsettings.DefaultSyncSettingsStorage
import com.mnemocards.data.syncsettings.DataStoreKeyValueStore
import com.mnemocards.data.syncsettings.EncryptedTokenStorage
import com.mnemocards.data.syncsettings.KeystoreTokenCipher
import com.mnemocards.data.syncsettings.SyncSettingsRepository
import com.mnemocards.data.syncsettings.SyncSettingsStorage
import com.mnemocards.data.syncsettings.TokenStorage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Ручной DI-контейнер приложения: singleton-зависимости создаются
 * один раз на процесс приложения и переживают пересоздание Activity
 * (смену конфигурации), что исключает повторный сидинг (S11).
 *
 * Room — единый источник истины: UI-слой читает колоды и карточки
 * через DAO и [com.mnemocards.data.repository.RoomCardRepository].
 *
 * change-007: [syncFlagDataStore] — персистентный флаг «синхронизация
 * включена» (DataStore, по умолчанию выключен), определяет режим
 * удаления: физический или мягкий.
 *
 * change-008: добавлен экран настроек синхронизации — адрес сервера
 * хранится открытым текстом (не секрет), токены — зашифрованными ключом
 * из Android Keystore, пароль не сохраняется (S16). Сетевой слой —
 * DTO, сгенерированные из specs/openapi.yaml (S22).
 */
class AppContainer(context: Context) {

    val database: MnemoCardsDatabase =
        MnemoCardsDatabase.getInstance(context.applicationContext)

    /**
     * Файловый шлюз CSV (change-006): доступ к документам только через
     * ContentResolver/SAF, без разрешений хранилища (S24).
     */
    val csvFileGateway: CsvFileGateway =
        SafCsvFileGateway(context.applicationContext.contentResolver)

    /** Единственный на процесс экземпляр хранилища флага синхронизации (S14–S16). */
    val syncFlagDataStore: SyncFlagDataStore =
        SyncFlagDataStore.fromContext(context.applicationContext)

    /**
     * Key-value хранилище настроек клиента синхронизации. Отдельный файл
     * DataStore (не файл флага): у Preferences DataStore один файл —
     * один экземпляр на процесс.
     */
    private val clientSettingsDataStore: DataStore<Preferences> =
        context.applicationContext.clientSettingsDataStore

    /** Шифратор токенов ключом из Android Keystore (S16). */
    private val tokenCipher = KeystoreTokenCipher()

    /** Токены — только в зашифрованном виде; пароль нигде не хранится (S16). */
    private val tokenStorage: TokenStorage =
        EncryptedTokenStorage(DataStoreKeyValueStore(clientSettingsDataStore), tokenCipher)

    /**
     * Адрес сервера и признак «подключено»; успешный вход включает флаг
     * синхронизации, выход — выключает (T18), режим удаления не
     * дублируется здесь.
     */
    val syncSettingsStorage: SyncSettingsStorage =
        DefaultSyncSettingsStorage(
            store = DataStoreKeyValueStore(clientSettingsDataStore),
            tokenStorage = tokenStorage,
            onSyncFlagChanged = { enabled ->
                syncFlagDataStore.setSyncEnabled(enabled)
            },
        )

    /**
     * Сетевой интерфейс: Retrofit с kotlinx-serialization над DTO из
     * specs/openapi.yaml. Базовый адрес технический — реальный сервер
     * подставляется в каждом вызове из поля «Адрес сервера» (@Url).
     */
    private val syncApi: SyncApi = Retrofit.Builder()
        .client(
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build(),
        )
        .baseUrl("https://localhost/")
        .addConverterFactory(
            Json { ignoreUnknownKeys = true }
                .asConverterFactory("application/json".toMediaType()),
        )
        .build()
        .create(SyncApi::class.java)

    val syncSettingsRepository: SyncSettingsRepository =
        SyncSettingsRepository(
            syncApi,
            syncSettingsStorage,
            tokenStorage,
            onServerChanged = {
                // Подключились к другому серверу: его нумерация ревизий не
                // связана с прежней, и о локальных записях он не знает.
                // Обнуляем ревизии и помечаем всё к отправке, иначе первая
                // же синхронизация сотрёт локальные данные.
                database.syncStateDao().clearRevisions()
                database.deckDao().markAllDecksChanged()
                database.cardDao().markAllCardsChanged()
            },
        )

    /** Application-scope корутины: сессии синхронизации переживают Activity. */
    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Фоновая синхронизация (change-009): движок сессии + single-flight
     * менеджер; токены и адрес сервера — из хранилищ change-008.
     */
    val syncManager: SyncManager = SyncManager(
        runner = SessionRunner {
            SyncEngine(
                db = database,
                api = syncApi,
                tokenStorage = tokenStorage,
                serverAddress = { syncSettingsStorage.serverAddress() },
            ).syncNow()
        },
        db = database,
        isConfigured = { tokenStorage.tokens() != null },
        scope = applicationScope,
    )

    private companion object {
        private val Context.clientSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "sync_settings_client",
        )
    }
}
