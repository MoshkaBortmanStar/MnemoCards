package com.mnemocards.data.syncsettings

/**
 * Общие тестовые заглушки для юнит-тестов пакета syncsettings и
 * offline-first регрессий (T19): фейковое key-value хранилище и
 * прозрачный шифратор.
 */
object TestStores {

    fun keyValueStore(): KeyValueStore = object : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override suspend fun put(key: String, value: String) {
            map[key] = value
        }

        override suspend fun get(key: String): String? = map[key]

        override suspend fun remove(key: String) {
            map.remove(key)
        }
    }

    fun plainCipher(): TokenCipher = object : TokenCipher {
        override fun encrypt(plain: String): String = plain
        override fun decrypt(cipher: String): String = cipher
    }
}
