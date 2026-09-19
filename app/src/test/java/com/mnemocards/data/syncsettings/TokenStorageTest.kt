package com.mnemocards.data.syncsettings

import android.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T8: хранение токенов, адреса и пароля.
 * Сценарии S16–S18: токены только в зашифрованном виде, пароль не хранится,
 * logout удаляет токены и состояние «подключено» (адрес остаётся), адрес
 * переживает пересоздание хранилища, вход/выход переключают флаг
 * синхронизации.
 *
 * Крипто-провайдер и персистентность подменяются fakes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenStorageTest {

    /** Фейковый шифратор: Base64 от развёрнутой строки — открытый текст
     * в результате не встречается. */
    private class FakeCipher : TokenCipher {
        override fun encrypt(plain: String): String =
            Base64.encodeToString(plain.reversed().toByteArray(), Base64.NO_WRAP)

        override fun decrypt(cipher: String): String =
            String(Base64.decode(cipher, Base64.NO_WRAP)).reversed()
    }

    /** Фейковое персистентное хранилище: обычная мутабельная карта. */
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

    /** Фейковый приёмник флага синхронизации: фиксирует переключения. */
    private class FakeSyncFlag {
        val values = mutableListOf<Boolean>()
        val setter: suspend (Boolean) -> Unit = { values.add(it) }
    }

    private lateinit var store: FakeKeyValueStore
    private lateinit var cipher: FakeCipher
    private lateinit var flag: FakeSyncFlag
    private lateinit var tokenStorage: TokenStorage
    private lateinit var settingsStorage: SyncSettingsStorage

    private val accessToken = "access-token-plain-1"
    private val refreshToken = "refresh-token-plain-1"
    private val password = "user-secret-password"

    @Before
    fun setUp() {
        store = FakeKeyValueStore()
        cipher = FakeCipher()
        flag = FakeSyncFlag()
        tokenStorage = EncryptedTokenStorage(store, cipher)
        settingsStorage = DefaultSyncSettingsStorage(store, tokenStorage, flag.setter)
    }

    /** Полный цикл входа: сохранение токенов + фиксация подключения. */
    private suspend fun performLogin(address: String = "https://sync.example.com") {
        tokenStorage.saveTokens(TokenPair(accessToken, refreshToken))
        settingsStorage.connect(address)
    }

    // (1) Токены хранятся только зашифрованными (S16).
    @Test
    fun `saved tokens are not stored in plaintext`() = runTest {
        performLogin()

        val stored = store.map.values.joinToString("\n")
        assertFalse(stored.contains(accessToken))
        assertFalse(stored.contains(refreshToken))
        // Расшифровка возвращает исходные токены — шифртекст корректен.
        val tokens = tokenStorage.tokens()
        assertEquals(accessToken, tokens?.accessToken)
        assertEquals(refreshToken, tokens?.refreshToken)
    }

    // (2) Пароль не сохраняется нигде (S16).
    @Test
    fun `password is not stored anywhere`() = runTest {
        performLogin()

        val stored = store.map.values.joinToString("\n")
        assertFalse(stored.contains(password))
        assertTrue(store.map.keys.none { it.contains("password", ignoreCase = true) })
    }

    // (3) Logout удаляет токены и «подключено», адрес сохраняется (S18).
    @Test
    fun `logout clears tokens and connected state but keeps address`() = runTest {
        performLogin()

        settingsStorage.logout()

        assertNull(tokenStorage.tokens())
        assertFalse(settingsStorage.isConnected())
        assertEquals("https://sync.example.com", settingsStorage.serverAddress())
    }

    // (4) Адрес переживает пересоздание хранилища (S17).
    @Test
    fun `server address survives storage recreation`() = runTest {
        performLogin()

        val recreated = DefaultSyncSettingsStorage(store, tokenStorage, flag.setter)
        assertEquals("https://sync.example.com", recreated.serverAddress())
    }

    // (5) Вход включает флаг синхронизации, logout — выключает (S10, S18).
    @Test
    fun `login enables sync flag and logout disables it`() = runTest {
        performLogin()
        settingsStorage.logout()

        assertEquals(listOf(true, false), flag.values)
    }
}
