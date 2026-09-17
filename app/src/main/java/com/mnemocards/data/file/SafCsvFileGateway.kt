package com.mnemocards.data.file

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Реализация [CsvFileGateway] через [ContentResolver] и SAF-документы.
 *
 * - запись: `openOutputStream(uri, "wt")` — перезапись содержимого документа;
 * - чтение: `openInputStream(uri)`;
 * - при сбое записи — best-effort удаление Uri через
 *   [DocumentsContract.deleteDocument], чтобы частично записанный файл
 *   не оставался (S8), затем исключение пробрасывается;
 * - все операции ввода-вывода выполняются на [Dispatchers.IO] (S23);
 * - разрешения на хранилище не запрашиваются (S24).
 */
class SafCsvFileGateway(
    private val contentResolver: ContentResolver,
) : CsvFileGateway {

    override suspend fun write(uri: Uri, bytes: ByteArray): Unit =
        withContext(Dispatchers.IO) {
            try {
                val stream = contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Не удалось открыть поток записи")
                stream.use {
                    it.write(bytes)
                    it.flush()
                }
            } catch (e: Exception) {
                // Частично созданный документ удаляем по возможности (S8).
                runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
                throw if (e is IOException) e else IOException(e)
            }
        }

    override suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        try {
            val stream = contentResolver.openInputStream(uri)
                ?: throw IOException("Не удалось открыть файл")
            stream.use { it.readBytes() }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            // Любой сбой чтения (в т.ч. особенности провайдера) — IOException,
            // наверху он превращается в понятную ошибку формата файла (S18).
            throw IOException(e)
        }
    }
}
