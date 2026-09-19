package com.mnemocards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mnemocards.ui.navigation.AppNavHost
import com.mnemocards.ui.theme.MnemoCardsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // БД и сидинг принадлежат процессу (Application), экран только
        // подключается к реактивным потокам Room.
        val container = (application as MnemoCardsApplication).container

        setContent {
            // Приложение всегда тёмное: корневой composable оборачивается
            // в MnemoCardsTheme, голая MaterialTheme не используется.
            MnemoCardsTheme {
                AppNavHost(
                    db = container.database,
                    csvFileGateway = container.csvFileGateway,
                    syncFlagDataStore = container.syncFlagDataStore,
                    syncSettingsRepository = container.syncSettingsRepository,
                    syncSettingsStorage = container.syncSettingsStorage,
                    syncManager = container.syncManager,
                )
            }
        }
    }
}
