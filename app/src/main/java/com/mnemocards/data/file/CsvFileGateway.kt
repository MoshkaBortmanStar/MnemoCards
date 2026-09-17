package com.mnemocards.data.file

import android.net.Uri

/**
 * Файловый шлюз CSV (capability `deck.csv-transfer`).
 *
 * Доступ к файлам — только через SAF-Uri, разрешения хранилища
 * не запрашиваются, прямые пути не используются (S24).
 */
interface CsvFileGateway {

    /** Записывает байты CSV-файла по выбранному в CreateDocument Uri (S2). */
    suspend fun write(uri: Uri, bytes: ByteArray)

    /** Читает байты выбранного в OpenDocument файла (S9). */
    suspend fun read(uri: Uri): ByteArray
}
