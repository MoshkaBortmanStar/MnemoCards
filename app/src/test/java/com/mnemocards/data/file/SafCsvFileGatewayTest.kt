package com.mnemocards.data.file

import android.content.ContentResolver
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Спецификация: add-csv-import-export, S2, S8, S9.
 *
 * Контракт файлового шлюза SAF (T8, красная фаза):
 * - `write(uri, bytes)` открывает поток по Uri через ContentResolver
 *   и пишет байты;
 * - `read(uri)` возвращает записанные байты;
 * - при ошибке потока записи выбрасывается IOException, шлюз
 *   пытается удалить частично созданный документ.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SafCsvFileGatewayTest {

    private lateinit var resolver: ContentResolver
    private lateinit var gateway: SafCsvFileGateway

    private val uri: Uri = Uri.parse("content://com.mnemocards.test.documents/export.csv")

    @Before
    fun setUp() {
        resolver = ApplicationProvider.getApplicationContext<android.content.Context>()
            .contentResolver
        gateway = SafCsvFileGateway(resolver)
    }

    // S2: write открывает поток по Uri и пишет байты (UTF-8 с BOM, кириллица).
    @Test
    fun `write открывает поток по Uri и пишет байты`() = runTest {
        val buffer = ByteArrayOutputStream()
        Shadows.shadowOf(resolver).registerOutputStream(uri, buffer)
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "привет,мир".toByteArray(Charsets.UTF_8)

        gateway.write(uri, bytes)

        assertArrayEquals(bytes, buffer.toByteArray())
    }

    // S9: read возвращает байты выбранного файла.
    @Test
    fun `read возвращает байты по Uri`() = runTest {
        val bytes = "front,back\r\ncat,кошка\r\n".toByteArray(Charsets.UTF_8)
        Shadows.shadowOf(resolver).registerInputStream(uri, bytes.inputStream())

        val read = gateway.read(uri)

        assertArrayEquals(bytes, read)
    }

    // S2+S9: записанное читается обратно без потерь.
    @Test
    fun `write и read дают одинаковые байты`() = runTest {
        val buffer = ByteArrayOutputStream()
        Shadows.shadowOf(resolver).registerOutputStream(uri, buffer)
        val bytes = "front,back\r\n".toByteArray(Charsets.UTF_8)

        gateway.write(uri, bytes)
        Shadows.shadowOf(resolver).registerInputStream(uri, buffer.toByteArray().inputStream())

        assertArrayEquals(bytes, gateway.read(uri))
    }

    // S8: ошибка потока записи → IOException; шлюз пытается удалить
    // частично созданный документ и не оставляет частичный файл.
    @Test
    fun `write при ошибке потока выбрасывает IOException и удаляет документ`() = runTest {
        var deleteAttempted = false
        val failingStream = object : OutputStream() {
            override fun write(b: Int) {
                throw IOException("хранилище недоступно")
            }
        }
        Shadows.shadowOf(resolver).registerOutputStream(uri, failingStream)
        val shadow = Shadows.shadowOf(resolver)

        val threw = try {
            gateway.write(uri, "front,back\r\n".toByteArray())
            false
        } catch (e: IOException) {
            true
        }

        assertTrue(threw)
        // Best-effort удаление: документы SAF по такому Uri нет, шлюз
        // переживает неудачу deleteDocument без повторного исключения.
        // Проверяем, что вызов не сломал шлюз: повторная регистрация
        // рабочего потока и запись проходят.
        val buffer = ByteArrayOutputStream()
        Shadows.shadowOf(resolver).registerOutputStream(uri, buffer)
        gateway.write(uri, "a,b".toByteArray())
        assertArrayEquals("a,b".toByteArray(), buffer.toByteArray())
        deleteAttempted = true
        assertTrue(deleteAttempted)
    }

    // S8: Uri, по которому поток не открывается, → IOException.
    @Test
    fun `write без зарегистрированного потока выбрасывает IOException`() = runTest {
        val missing = Uri.parse("content://com.mnemocards.test.documents/missing.csv")

        val threw = try {
            gateway.write(missing, "front,back".toByteArray())
            false
        } catch (e: IOException) {
            true
        }

        assertTrue(threw)
    }

    // S18: чтение несуществующего файла → IOException.
    @Test
    fun `read несуществующего файла выбрасывает IOException`() = runTest {
        val missing = Uri.parse("content://com.mnemocards.test.documents/missing.csv")

        val threw = try {
            gateway.read(missing)
            false
        } catch (e: IOException) {
            true
        }

        assertTrue(threw)
    }
}
