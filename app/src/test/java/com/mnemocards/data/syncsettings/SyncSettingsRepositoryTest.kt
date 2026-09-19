package com.mnemocards.data.syncsettings

import com.mnemocards.data.network.SyncApi
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * T1/T3: проверка соединения через MockWebServer.
 * Сценарии S5 (200), S8 (404), S9 (5xx); метод и путь — GET /api/v1/health
 * согласно specs/API.md.
 *
 * T4: сетевые ошибки проверки соединения — S6 (нерезолвящийся домен, таймаут
 * → Unreachable), S7 (недоверенный TLS-сертификат → CertificateRejected).
 *
 * T6: вход POST /api/v1/auth/login — S10–S14 (успех, 400, 401, 500, обрыв
 * соединения); тела запросов и ответов — по контракту specs/openapi.yaml.
 */
class SyncSettingsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var tokenStorage: TokenStorage
    private lateinit var settingsStorage: SyncSettingsStorage
    private lateinit var repository: SyncSettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(250, TimeUnit.MILLISECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val store = TestStores.keyValueStore()
        tokenStorage = EncryptedTokenStorage(store, TestStores.plainCipher())
        settingsStorage = DefaultSyncSettingsStorage(store, tokenStorage) { }
        repository = SyncSettingsRepository(
            api = retrofit.create(SyncApi::class.java),
            settingsStorage = settingsStorage,
            tokenStorage = tokenStorage,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun address(): String = server.url("").toString().trimEnd('/')

    @Test
    fun `health 200 returns ServerResponds and requests GET api v1 health`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.ServerResponds, result)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v1/health", recorded.path)
    }

    @Test
    fun `health 404 returns NoSyncServer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.NoSyncServer, result)
        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    @Test
    fun `health 500 returns ServerError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.ServerError, result)
        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    // --- T4: сетевые ошибки проверки соединения (S6, S7) ---

    @Test
    fun `health on unresolvable host returns Unreachable`() = runTest {
        val result = repository.checkConnection("https://nonexistent-host.mnemocards.invalid")

        assertEquals(ConnectionCheckResult.Unreachable, result)
    }

    @Test
    fun `health connection timeout returns Unreachable`() = runTest {
        // 10.255.255.1 — заведомо не маршрутизируемый адрес: соединение
        // не устанавливается, OkHttp бросает таймаут соединения (IOException).
        val result = repository.checkConnection("https://10.255.255.1")

        assertEquals(ConnectionCheckResult.Unreachable, result)
    }

    @Test
    fun `health on untrusted TLS certificate returns CertificateRejected`() = runTest {
        // Самоподписанный сертификат сервера, которому клиентский OkHttp
        // не доверяет → SSLHandshakeException (S7).
        val selfSigned = HeldCertificate.Builder()
            .commonName("sync.example.com")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(selfSigned)
            .build()
        server.useHttps(serverCertificates.sslSocketFactory(), false)

        val result = repository.checkConnection(address())

        assertEquals(ConnectionCheckResult.CertificateRejected, result)
    }

    // --- T6: вход POST /api/v1/auth/login (S10–S14) ---

    @Test
    fun `login 200 returns Success with tokens and posts credentials`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"access-token-1","accessTokenExpiresAt":1,""" +
                    """"refreshToken":"refresh-token-1","refreshTokenExpiresAt":2}""",
            ),
        )

        val result = repository.login(address(), "user", "secret")

        assertEquals(LoginOutcome.Success, result)
        val saved = tokenStorage.tokens()
        assertNotNull("токены сохранены зашифрованным хранилищем", saved)
        assertEquals("access-token-1", saved!!.accessToken)
        assertEquals("refresh-token-1", saved.refreshToken)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/auth/login", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"username\":\"user\""))
        assertTrue(body.contains("\"password\":\"secret\""))
    }

    @Test
    fun `login 401 returns InvalidCredentials and no tokens`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid credentials"}"""))

        val result = repository.login(address(), "user", "wrong")

        assertEquals(LoginOutcome.InvalidCredentials, result)
        assertNull("токены не сохраняются при ошибке входа", tokenStorage.tokens())
    }

    @Test
    fun `login 400 returns BadRequest with reason from body`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":{"code":"VALIDATION_FAILED","message":"invalid request body"}}""",
            ),
        )

        val result = repository.login(address(), "user", "secret")

        assertEquals(LoginOutcome.BadRequest("invalid request body"), result)
    }

    @Test
    fun `login 500 returns ServerError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val result = repository.login(address(), "user", "secret")

        assertEquals(LoginOutcome.ServerError, result)
    }

    @Test
    fun `login network failure returns NetworkError and retry available`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val first = repository.login(address(), "user", "secret")

        assertTrue(first is LoginOutcome.NetworkError)

        // Повторная попытка доступна: сервер отвечает успехом.
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"access-token-2","accessTokenExpiresAt":1,""" +
                    """"refreshToken":"refresh-token-2","refreshTokenExpiresAt":2}""",
            ),
        )
        val retry = repository.login(address(), "user", "secret")
        assertEquals(LoginOutcome.Success, retry)
        assertEquals("access-token-2", tokenStorage.tokens()!!.accessToken)
    }
}
