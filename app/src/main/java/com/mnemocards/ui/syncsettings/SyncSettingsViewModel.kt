package com.mnemocards.ui.syncsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.syncsettings.SyncSettingsRepository
import com.mnemocards.data.syncsettings.SyncSettingsStorage
import com.mnemocards.domain.model.ConnectionCheckResult
import com.mnemocards.domain.model.LoginOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URISyntaxException

/**
 * ViewModel экрана настроек синхронизации (S23: состояние переживает
 * смену конфигурации, так как владеет им ViewModel через StateFlow).
 *
 * Валидация выполняется до любых сетевых вызовов (S3, S4, S25);
 * guard-флаги isChecking/isLoggingIn защищают от повторных нажатий (S15).
 */
class SyncSettingsViewModel(
    private val repository: SyncSettingsRepository,
    private val storage: SyncSettingsStorage,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncSettingsUiState())
    val uiState: StateFlow<SyncSettingsUiState> = _uiState.asStateFlow()

    init {
        // Восстановление состояния после перезапуска (S17): адрес и статус
        // «Подключено» — из хранилища, пароль пуст.
        viewModelScope.launch {
            val address = storage.serverAddress().orEmpty()
            val connected = storage.isConnected()
            _uiState.update {
                it.copy(address = address, isConnected = connected)
            }
        }
    }

    fun onAddressChanged(value: String) {
        _uiState.update { it.copy(address = value, addressError = null) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, credentialsError = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, credentialsError = null) }
    }

    /**
     * Валидация адреса (S3, S25). null — адрес корректен.
     */
    private fun validateAddress(address: String): String? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return "Введите адрес сервера"
        val uri = try {
            URI(trimmed)
        } catch (e: URISyntaxException) {
            null
        }
        if (uri == null || uri.scheme == null || uri.host == null) {
            return "Некорректный адрес"
        }
        if (uri.scheme.equals("http", ignoreCase = true)) {
            return "Незашифрованное соединение (http) заблокировано Android — используйте https-адрес"
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return "Некорректный адрес"
        }
        return null
    }

    /** Проверка соединения (S5–S9). Статус подключения не меняется. */
    fun checkConnection() {
        val state = _uiState.value
        if (state.isChecking || state.isLoggingIn) return

        val error = validateAddress(state.address)
        if (error != null) {
            _uiState.update { it.copy(addressError = error) }
            return
        }

        _uiState.update { it.copy(isChecking = true, resultText = null, addressError = null) }
        viewModelScope.launch {
            val result = repository.checkConnection(_uiState.value.address)
            _uiState.update {
                it.copy(
                    isChecking = false,
                    resultText = when (result) {
                        ConnectionCheckResult.ServerResponds -> "Сервер отвечает"
                        ConnectionCheckResult.NoSyncServer ->
                            "По этому адресу нет сервера синхронизации"
                        ConnectionCheckResult.ServerError -> "Ошибка сервера"
                        ConnectionCheckResult.Unreachable -> "Адрес недоступен"
                        ConnectionCheckResult.CertificateRejected -> "Сертификат не принят"
                    },
                )
            }
        }
    }

    /** Вход (S10–S14). */
    fun login() {
        val state = _uiState.value
        if (state.isChecking || state.isLoggingIn) return

        val addressError = validateAddress(state.address)
        if (addressError != null) {
            _uiState.update { it.copy(addressError = addressError) }
            return
        }
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(credentialsError = "Заполните логин и пароль") }
            return
        }

        _uiState.update {
            it.copy(
                isLoggingIn = true,
                resultText = null,
                addressError = null,
                credentialsError = null,
            )
        }
        viewModelScope.launch {
            val outcome = repository.login(
                address = _uiState.value.address,
                username = _uiState.value.username,
                password = _uiState.value.password,
            )
            _uiState.update {
                when (outcome) {
                    LoginOutcome.Success -> it.copy(
                        isLoggingIn = false,
                        isConnected = true,
                        // Пароль не сохраняется и очищается из UI (S10, S16).
                        password = "",
                        resultText = null,
                    )

                    LoginOutcome.InvalidCredentials -> it.copy(
                        isLoggingIn = false,
                        resultText = "Неверный логин или пароль",
                    )

                    is LoginOutcome.BadRequest -> it.copy(
                        isLoggingIn = false,
                        resultText = outcome.reason,
                    )

                    LoginOutcome.ServerError -> it.copy(
                        isLoggingIn = false,
                        resultText = "Ошибка сервера",
                    )

                    LoginOutcome.NetworkError -> it.copy(
                        isLoggingIn = false,
                        resultText = "Адрес недоступен",
                    )
                }
            }
        }
    }

    /**
     * Выход (S18): токены удаляются, статус «Не подключено», адрес
     * сохраняется, флаг синхронизации выключается (физические удаления).
     */
    fun logout() {
        val state = _uiState.value
        if (state.isChecking || state.isLoggingIn) return
        viewModelScope.launch {
            repository.logout()
            _uiState.update {
                it.copy(isConnected = false, resultText = null, password = "")
            }
        }
    }
}

/**
 * Фабрика ViewModel настроек синхронизации — существующий механизм:
 * зависимости приходят из AppContainer (T16).
 */
class SyncSettingsViewModelFactory(
    private val repository: SyncSettingsRepository,
    private val storage: SyncSettingsStorage,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == SyncSettingsViewModel::class.java)
        return SyncSettingsViewModel(repository, storage) as T
    }
}
