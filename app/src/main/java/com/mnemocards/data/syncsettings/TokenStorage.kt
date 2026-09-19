package com.mnemocards.data.syncsettings

/**
 * Пара токенов сервера синхронизации, выданная при входе.
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Хранение токенов сервера синхронизации. Токены записываются и читаются
 * только через [TokenCipher] — открытый текст в персистентном хранилище
 * не появляется нигде (S16). Пароль этим хранилищем не владеет.
 */
interface TokenStorage {

    suspend fun saveTokens(tokens: TokenPair)

    /** Токены или null, если вход не выполнен (или выполнен выход). */
    suspend fun tokens(): TokenPair?

    /** Удаляет токены (выход, S18). */
    suspend fun clear()
}

/** Реализация поверх персистентного хранилища и шифратора. */
class EncryptedTokenStorage(
    private val store: KeyValueStore,
    private val cipher: TokenCipher,
) : TokenStorage {

    override suspend fun saveTokens(tokens: TokenPair) {
        store.put(KEY_ACCESS, cipher.encrypt(tokens.accessToken))
        store.put(KEY_REFRESH, cipher.encrypt(tokens.refreshToken))
    }

    override suspend fun tokens(): TokenPair? {
        val access = store.get(KEY_ACCESS) ?: return null
        val refresh = store.get(KEY_REFRESH) ?: return null
        return TokenPair(
            accessToken = cipher.decrypt(access),
            refreshToken = cipher.decrypt(refresh),
        )
    }

    override suspend fun clear() {
        store.remove(KEY_ACCESS)
        store.remove(KEY_REFRESH)
    }

    private companion object {
        const val KEY_ACCESS = "sync_access_token"
        const val KEY_REFRESH = "sync_refresh_token"
    }
}
