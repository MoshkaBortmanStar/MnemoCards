package com.mnemocards.ui.syncsettings

import com.mnemocards.data.network.SyncApi
import com.mnemocards.data.syncsettings.SyncSettingsRepository
import com.mnemocards.data.syncsettings.SyncSettingsStorage
import com.mnemocards.data.syncsettings.TokenPair
import com.mnemocards.data.syncsettings.TokenStorage
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException
import javax.net.ssl.SSLException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T10: валидация полей — сетевые вызовы не выполняются при некорректном
 * адресе (пустой, не-URL, http) и пустых логине/пароле; статус подключения
 * не меняется. Сценарии S3, S4, S25.
 *
 * T12: проверка соединения, вход, выход, загрузка и восстановление
 * состояния. Сценарии S5–S15, S17, S18.
 *
 * Репозиторий и хранилища подменяются fakes, фиксирующими вызовы.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncSettingsViewModelTest {

    /**
     * Фейковый сетевой интерфейс: фиксирует вызовы, программируется
     * доменным результатом (как его понимает репозиторий), поддерживает
     * gate для незавершённых запросов (S15).
     */
    private class FakeSyncApi : SyncApi {
        val checkCalls = mutableListOf<String>()
        val loginCalls = mutableListOf<Pair<String, com.mnemocards.data.network.dto.models.LoginRequest>>()
        var checkResult: ConnectionCheckResult = ConnectionCheckResult.ServerResponds
        var loginResult: LoginOutcome = LoginOutcome.InvalidCredentials
        var gate: CompletableDeferred<Unit>? = null

        private suspend fun awaitGate() {
            gate?.await()
        }

        private fun errorBody(code: Int, message: String): Response<Unit> = Response.error(
            code,
            """{"error":{"code":"INTERNAL_ERROR","message":"$message"}}"""
                .toResponseBody("application/json".toMediaType()),
        )

        override suspend fun health(url: String): Response<Unit> {
            checkCalls.add(url)
            awaitGate()
            return when (checkResult) {
                ConnectionCheckResult.ServerResponds -> Response.success(Unit)
                ConnectionCheckResult.NoSyncServer -> errorBody(404, "not found")
                ConnectionCheckResult.ServerError -> errorBody(500, "boom")
                ConnectionCheckResult.Unreachable -> throw IOException("unreachable")
                ConnectionCheckResult.CertificateRejected -> throw SSLException("bad cert")
            }
        }

        override suspend fun login(
            url: String,
            body: com.mnemocards.data.network.dto.models.LoginRequest,
        ): Response<com.mnemocards.data.network.dto.models.TokenPair> {
            loginCalls.add(url to body)
            awaitGate()
            return when (val result = loginResult) {
                LoginOutcome.Success -> Response.success(
                    com.mnemocards.data.network.dto.models.TokenPair(
                        accessToken = "access-${loginCalls.size}",
                        accessTokenExpiresAt = 1,
                        refreshToken = "refresh-${loginCalls.size}",
                        refreshTokenExpiresAt = 2,
                    ),
                )
                LoginOutcome.InvalidCredentials -> errorTokenBody(401)
                is LoginOutcome.BadRequest -> errorTokenBody(400, result.reason)
                LoginOutcome.ServerError -> errorTokenBody(500)
                LoginOutcome.NetworkError -> throw IOException("network down")
            }
        }

        private fun errorTokenBody(code: Int, message: String = "error") =
            Response.error<com.mnemocards.data.network.dto.models.TokenPair>(
            code,
            """{"error":{"code":"INTERNAL_ERROR","message":"$message"}}"""
                .toResponseBody("application/json".toMediaType()),
        )
    }

    /** Фейковое хранилище токенов: фиксирует сохранения и удаления. */
    private class FakeTokenStorage : TokenStorage {
        val saved = mutableListOf<TokenPair>()
        var clearCount = 0
        override suspend fun saveTokens(tokens: TokenPair) {
            saved.add(tokens)
        }
        override suspend fun tokens(): TokenPair? = saved.lastOrNull()
        override suspend fun clear() {
            clearCount++
        }
    }

    /** Фейковое хранилище настроек: адрес, признак подключения, вызовы. */
    private class FakeSettingsStorage(
        private val tokenStorage: TokenStorage,
    ) : SyncSettingsStorage {
        var address: String? = null
        var connected: Boolean = false
        val connectCalls = mutableListOf<String>()
        var logoutCount = 0
        override suspend fun serverAddress(): String? = address
        override suspend fun isConnected(): Boolean = connected
        override suspend fun connect(address: String) {
            connectCalls.add(address)
            this.address = address
            connected = true
        }
        override suspend fun logout() {
            logoutCount++
            connected = false
            tokenStorage.clear()
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: FakeSyncApi
    private lateinit var tokenStorage: FakeTokenStorage
    private lateinit var settingsStorage: FakeSettingsStorage
    private lateinit var repository: SyncSettingsRepository
    private lateinit var viewModel: SyncSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        api = FakeSyncApi()
        tokenStorage = FakeTokenStorage()
        settingsStorage = FakeSettingsStorage(tokenStorage)
        repository = SyncSettingsRepository(api, settingsStorage, tokenStorage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SyncSettingsViewModel {
        viewModel = SyncSettingsViewModel(repository, settingsStorage)
        return viewModel
    }

    // --- T10: валидация полей (S3, S4, S25) ---

    // (1) Пустой адрес: ошибка «Введите адрес сервера», сеть не вызывается.
    @Test
    fun `blank address shows error and network is not called`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("   ")
        vm.checkConnection()

        assertEquals("Введите адрес сервера", vm.uiState.value.addressError)
        assertTrue(api.checkCalls.isEmpty())
        assertTrue(api.loginCalls.isEmpty())
        assertFalse(vm.uiState.value.isConnected)
    }

    // (2) Не-URL: «Некорректный адрес», сеть не вызывается.
    @Test
    fun `non url address shows error and network is not called`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("sync example")
        vm.checkConnection()

        assertEquals("Некорректный адрес", vm.uiState.value.addressError)
        assertTrue(api.checkCalls.isEmpty())
        assertFalse(vm.uiState.value.isConnected)
    }

    // (3) http-адрес: cleartext-ошибка до отправки запроса, флаг в манифесте
    // не включается, состояние подключения не меняется (S25).
    @Test
    fun `http address is rejected before any request`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("http://sync.example.com")
        vm.checkConnection()
        vm.login()

        assertEquals(
            "Незашифрованное соединение (http) заблокировано Android — используйте https-адрес",
            vm.uiState.value.addressError,
        )
        assertTrue(api.checkCalls.isEmpty())
        assertTrue(api.loginCalls.isEmpty())
        assertFalse(vm.uiState.value.isConnected)
    }

    // (4) Пустой логин или пароль: запрос входа не отправляется.
    @Test
    fun `blank credentials show error and login is not called`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.onPasswordChanged("secret")
        vm.login()
        assertEquals("Заполните логин и пароль", vm.uiState.value.credentialsError)

        vm.onUsernameChanged("user")
        vm.onPasswordChanged("")
        vm.login()
        assertEquals("Заполните логин и пароль", vm.uiState.value.credentialsError)

        assertTrue(api.loginCalls.isEmpty())
        assertFalse(vm.uiState.value.isConnected)
    }

    // --- T12: результаты проверки соединения (S5–S9) ---

    @Test
    fun `check results are shown and connection status unchanged`() = runTest(dispatcher) {
        val results = mapOf(
            ConnectionCheckResult.ServerResponds to "Сервер отвечает",
            ConnectionCheckResult.Unreachable to "Адрес недоступен",
            ConnectionCheckResult.CertificateRejected to "Сертификат не принят",
            ConnectionCheckResult.NoSyncServer to "По этому адресу нет сервера синхронизации",
            ConnectionCheckResult.ServerError to "Ошибка сервера",
        )
        for ((result, text) in results) {
            setUp()
            api.checkResult = result
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onAddressChanged("https://sync.example.com")
            vm.checkConnection()
            advanceUntilIdle()

            assertEquals(text, vm.uiState.value.resultText)
            assertFalse(vm.uiState.value.isChecking)
            assertFalse(vm.uiState.value.isConnected)
            assertEquals(1, api.checkCalls.size)
        }
    }

    // --- T12: вход (S10–S14) ---

    // (2) Успех: статус «Подключено», пароль очищен, токены и адрес
    // сохранены, флаг синхронизации включён (через connect).
    @Test
    fun `login success connects and stores tokens`() = runTest(dispatcher) {
        api.loginResult = LoginOutcome.Success
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("secret")
        vm.login()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isConnected)
        assertEquals("", vm.uiState.value.password)
        assertEquals(listOf(TokenPair("access-1", "refresh-1")), tokenStorage.saved)
        assertEquals(listOf("https://sync.example.com"), settingsStorage.connectCalls)
    }

    // (3) 401: «Неверный логин или пароль», статус «Не подключено».
    @Test
    fun `login 401 shows message and stays disconnected`() = runTest(dispatcher) {
        api.loginResult = LoginOutcome.InvalidCredentials
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("wrong")
        vm.login()
        advanceUntilIdle()

        assertEquals("Неверный логин или пароль", vm.uiState.value.resultText)
        assertFalse(vm.uiState.value.isConnected)
        assertTrue(tokenStorage.saved.isEmpty())
    }

    // (4) Сетевой сбой: «Адрес недоступен», флаги загрузки сняты,
    // повторная попытка доступна.
    @Test
    fun `login network failure allows retry`() = runTest(dispatcher) {
        api.loginResult = LoginOutcome.NetworkError
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("secret")
        vm.login()
        advanceUntilIdle()

        assertEquals("Адрес недоступен", vm.uiState.value.resultText)
        assertFalse(vm.uiState.value.isLoggingIn)
        assertFalse(vm.uiState.value.isConnected)
        assertTrue(tokenStorage.saved.isEmpty())

        api.loginResult = LoginOutcome.Success
        vm.login()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isConnected)
        assertEquals(listOf(TokenPair("access-2", "refresh-2")), tokenStorage.saved)
    }

    // (5) Пока выполняется запрос, повторные вызовы не проходят.
    @Test
    fun `duplicate calls are ignored while request in flight`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.checkConnection()
        runCurrent()
        assertTrue(vm.uiState.value.isChecking)

        vm.checkConnection()
        vm.login()
        runCurrent()
        assertEquals(1, api.checkCalls.size)
        assertTrue(api.loginCalls.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isChecking)
        assertEquals("Сервер отвечает", vm.uiState.value.resultText)
    }

    // --- T12: выход (S18) ---

    // (6) Выход: токены удалены, флаг выключен, статус «Не подключено»,
    // адрес сохранён.
    @Test
    fun `logout disconnects and keeps address`() = runTest(dispatcher) {
        api.loginResult = LoginOutcome.Success
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onAddressChanged("https://sync.example.com")
        vm.onUsernameChanged("user")
        vm.onPasswordChanged("secret")
        vm.login()
        advanceUntilIdle()
        vm.logout()
        advanceUntilIdle()

        assertEquals(1, tokenStorage.clearCount)
        assertEquals(1, settingsStorage.logoutCount)
        assertFalse(vm.uiState.value.isConnected)
        assertEquals("https://sync.example.com", settingsStorage.address)
    }

    // --- T12: восстановление состояния (S17) ---

    // (7) init восстанавливает адрес и статус «Подключено», пароль пуст.
    @Test
    fun `init restores address and connected state`() = runTest(dispatcher) {
        settingsStorage.address = "https://sync.example.com"
        settingsStorage.connected = true

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals("https://sync.example.com", vm.uiState.value.address)
        assertTrue(vm.uiState.value.isConnected)
        assertEquals("", vm.uiState.value.password)
        assertNull(vm.uiState.value.addressError)
        assertTrue(api.checkCalls.isEmpty())
        assertTrue(api.loginCalls.isEmpty())
    }
}
