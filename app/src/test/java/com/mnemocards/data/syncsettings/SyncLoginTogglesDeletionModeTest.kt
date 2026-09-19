package com.mnemocards.data.syncsettings

import android.util.Base64
import com.mnemocards.data.network.SyncApi
import com.mnemocards.domain.model.LoginOutcome
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * T17: флаг синхронизации переключает режим удаления.
 *
 * Сценарии S10, S18–S20: успешный вход включает существующий флаг
 * синхронизации → удаления переходят в мягкий режим (S19); выход
 * выключает флаг → удаления физические (S20). Связь «вход/выход → флаг»
 * реализуется в SyncSettingsRepository (T18); сам выбор мягкого/
 * физического удаления по флагу уже покрыт существующими тестами
 * репозиториев колод/карточек — здесь проверяется, что вход и выход
 * переключают именно тот флаг, которым пользуются репозитории удаления.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncLoginTogglesDeletionModeTest {

    /** Фейковый шифратор (см. TokenStorageTest). */
    private class FakeCipher : TokenCipher {
        override fun encrypt(plain: String): String =
            Base64.encodeToString(plain.reversed().toByteArray(), Base64.NO_WRAP)

        override fun decrypt(cipher: String): String =
            String(Base64.decode(cipher, Base64.NO_WRAP)).reversed()
    }

    /** Фейковое персистентное хранилище. */
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

    /** Фиксатор значения флага синхронизации (тот же сеттер, что в AppContainer). */
    private class FakeSyncFlag {
        var value: Boolean = false
        val setter: suspend (Boolean) -> Unit = { value = it }
    }

    private lateinit var server: MockWebServer
    private lateinit var store: FakeKeyValueStore
    private lateinit var flag: FakeSyncFlag
    private lateinit var tokenStorage: TokenStorage
    private lateinit var settingsStorage: SyncSettingsStorage
    private lateinit var repository: com.mnemocards.data.syncsettings.SyncSettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeKeyValueStore()
        flag = FakeSyncFlag()
        tokenStorage = EncryptedTokenStorage(store, FakeCipher())
        settingsStorage = DefaultSyncSettingsStorage(store, tokenStorage, flag.setter)
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
        repository = com.mnemocards.data.syncsettings.SyncSettingsRepository(
            api = api,
            settingsStorage = settingsStorage,
            tokenStorage = tokenStorage,
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

    // (1) S10, S19: успешный вход включает флаг синхронизации —
    // удаления записей переходят в мягкий режим.
    @Test
    fun `successful login enables sync flag for soft deletion`() = runTest {
        enqueueLoginSuccess()

        val outcome = repository.login(server.url("/").toString(), "user", "pass")

        assertEquals(LoginOutcome.Success, outcome)
        assertTrue("Флаг синхронизации должен быть включён после входа (S19)", flag.value)
        assertTrue(repository.isConnected())
    }

    // (2) S18, S20: выход выключает флаг — удаления возвращаются
    // к физическому режиму; токены удалены, адрес сохраняется.
    @Test
    fun `logout disables sync flag for physical deletion`() = runTest {
        enqueueLoginSuccess()
        repository.login(server.url("/").toString(), "user", "pass")

        repository.logout()

        assertFalse("Флаг синхронизации должен быть выключен после выхода (S20)", flag.value)
        assertFalse(repository.isConnected())
        assertNull(tokenStorage.tokens())
        assertEquals(server.url("/").toString().trimEnd('/'), repository.serverAddress())
    }

    // (3) S11: неуспешный вход не включает флаг — удаления остаются
    // физическими.
    @Test
    fun `failed login leaves sync flag disabled`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        val outcome = repository.login(server.url("/").toString(), "user", "wrong")

        assertEquals(LoginOutcome.InvalidCredentials, outcome)
        assertFalse(flag.value)
        assertFalse(repository.isConnected())
        assertNull(tokenStorage.tokens())
    }

    // (4) Сетевой сбой входа не включает флаг (S14, S20).
    @Test
    fun `network error during login leaves sync flag disabled`() = runTest {
        server.enqueue(
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START),
        )

        val outcome = repository.login(server.url("/").toString(), "user", "pass")

        assertEquals(LoginOutcome.NetworkError, outcome)
        assertFalse(flag.value)
        assertNull(tokenStorage.tokens())
    }
}
