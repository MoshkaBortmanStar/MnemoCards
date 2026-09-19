package com.mnemocards.ui.syncsettings

import com.mnemocards.data.db.sync.LastSyncResultEntity
import com.mnemocards.data.sync.SyncStatus

/**
 * Состояние экрана настроек синхронизации. Владеет им ViewModel через
 * StateFlow — состояние переживает смену конфигурации (S23).
 *
 * change-009: поля фоновой синхронизации — статус текущей/последней
 * сессии и её итог (S2, S13, S23).
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
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val lastSync: LastSyncResultEntity? = null,
)
