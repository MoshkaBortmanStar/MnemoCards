package com.mnemocards.ui.syncsettings

/**
 * Состояние экрана настроек синхронизации. Владеет им ViewModel через
 * StateFlow — состояние переживает смену конфигурации (S23).
 */
data class SyncSettingsUiState(
    val address: String = "",
    val username: String = "",
    val password: String = "",
    val isChecking: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isConnected: Boolean = false,
    val resultText: String? = null,
    val addressError: String? = null,
    val credentialsError: String? = null,
)
