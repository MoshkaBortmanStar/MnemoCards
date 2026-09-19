package com.mnemocards.data.syncsettings

/**
 * Крипто-провайдер для шифрования токенов. Тонкая абстракция над Android
 * Keystore: позволяет подменить шифратор фейком в юнит-тестах.
 */
interface TokenCipher {

    /** Шифрует открытый текст токена; возвращает шифртекст для хранения. */
    fun encrypt(plain: String): String

    /** Расшифровывает шифртекст, ранее возвращённый [encrypt]. */
    fun decrypt(cipher: String): String
}
