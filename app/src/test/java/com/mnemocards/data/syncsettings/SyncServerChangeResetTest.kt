package com.mnemocards.data.syncsettings

import android.util.Base64
import com.mnemocards.data.network.SyncApi
import com.mnemocards.domain.model.LoginOutcome
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Сброс состояния синхронизации при подключении к ДРУГОМУ серверу.
 *
 * Инцидент, из-за которого появились эти тесты: приложение было
 * подключено к одному серверу, колоды синхронизировались и получили
 * isChanged=false, ревизии потоков легли в sync_states. Затем адрес
 * сменили на новый сервер с пустой базой. Клиент отправил since из
 * ЧУЖОЙ нумерации и пустой changes (отправлять нечего — всё подтверждено
 * прежним сервером), получил fullSync с пустым срезом и удалил все
 * локальные колоды как «отсутствующие на сервере».
 *
 * Ревизии выдаёт сервер, и для другого сервера они не значат ничего.
 * Поэтому при смене адреса состояние синхронизации сбрасывается:
 * ревизии обнуляются, локальные записи помечаются к отправке.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncServerChangeResetTest {

    private class FakeCipher : TokenCipher {
        override fun encrypt(plain: String): String =
            Base64.encodeToString(plain.reversed().toByteArray(), Base64.NO_WRAP)

        override fun decrypt(cipher: String): String =
            String(Base64.decode(cipher, Base64.NO_WRAP)).reversed()
    }

    private class FakeKeyValueStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override suspend fun put(key: String, value: String) {
            map[key] = value
        }
        override suspend fun get(key: String): String? = map[key]
        override suspend fun remove(key: String) {
            map.remove(key)
        }
    }

    /** Считает вызовы сброса — в бою это очистка ревизий и пометка записей. */
    private class ResetSpy {
        var calls: Int = 0
        val action: suspend () -> Unit = { calls++ }
    }

    private lateinit var server: MockWebServer
    private lateinit var store: FakeKeyValueStore
    private lateinit var reset: ResetSpy
    private lateinit var repository: SyncSettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeKeyValueStore()
        reset = ResetSpy()
        val tokenStorage = EncryptedTokenStorage(store, FakeCipher())
        val settingsStorage = DefaultSyncSettingsStorage(store, tokenStorage) {}
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build(),
            )
            .build()
            .create(SyncApi::class.java)
        repository = SyncSettingsRepository(
            api = api,
            settingsStorage = settingsStorage,
            tokenStorage = tokenStorage,
            onServerChanged = reset.action,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueLoginSuccess() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"accessToken":"access-1","accessTokenExpiresAt":1,""" +
                        """"refreshToken":"refresh-1","refreshTokenExpiresAt":2}""",
                ),
        )
    }

    // Первое подключение: прежнего адреса нет, значит сервер «сменился»
    // с пустого на реальный — состояние сбрасывается. Локальные записи
    // при этом попадут в changes и уедут наверх, а не будут стёрты.
    @Test
    fun `first login resets sync state`() = runTest {
        enqueueLoginSuccess()

        val outcome = repository.login(server.url("/").toString(), "user", "pass")

        assertEquals(LoginOutcome.Success, outcome)
        assertEquals("Первый вход должен сбросить состояние синхронизации", 1, reset.calls)
    }

    // Повторный вход на ТОТ ЖЕ сервер: ревизии и подтверждения остаются
    // в силе, сбрасывать нечего. Иначе каждая переавторизация гнала бы
    // всю базу наверх заново.
    @Test
    fun `login to the same server does not reset sync state`() = runTest {
        enqueueLoginSuccess()
        repository.login(server.url("/").toString(), "user", "pass")
        val afterFirst = reset.calls

        enqueueLoginSuccess()
        repository.login(server.url("/").toString(), "user", "pass")

        assertEquals("Тот же адрес — сброса быть не должно", afterFirst, reset.calls)
    }

    // Хвостовой слэш не делает адрес другим: SyncSettingsRepository
    // нормализует адрес перед сравнением и сохранением.
    @Test
    fun `trailing slash does not count as server change`() = runTest {
        enqueueLoginSuccess()
        repository.login(server.url("/").toString().trimEnd('/'), "user", "pass")
        val afterFirst = reset.calls

        enqueueLoginSuccess()
        repository.login(server.url("/").toString(), "user", "pass")

        assertEquals("Адрес со слэшем и без — один и тот же сервер", afterFirst, reset.calls)
    }

    // Главный сценарий инцидента: адрес сменился на другой сервер.
    @Test
    fun `login to a different server resets sync state`() = runTest {
        enqueueLoginSuccess()
        repository.login(server.url("/").toString(), "user", "pass")
        val afterFirst = reset.calls

        val another = MockWebServer()
        another.start()
        try {
            another.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """{"accessToken":"access-2","accessTokenExpiresAt":1,""" +
                            """"refreshToken":"refresh-2","refreshTokenExpiresAt":2}""",
                    ),
            )

            val outcome = repository.login(another.url("/").toString(), "user", "pass")

            assertEquals(LoginOutcome.Success, outcome)
            assertEquals(
                "Смена сервера обязана сбросить состояние синхронизации",
                afterFirst + 1,
                reset.calls,
            )
        } finally {
            another.shutdown()
        }
    }

    // Неудачный вход ничего не трогает: состояние сбрасывать не за что,
    // подключения не произошло.
    @Test
    fun `failed login does not reset sync state`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        val outcome = repository.login(server.url("/").toString(), "user", "bad")

        assertEquals(LoginOutcome.InvalidCredentials, outcome)
        assertEquals("Неудачный вход не должен сбрасывать состояние", 0, reset.calls)
        assertFalse(repository.isConnected())
    }
}
