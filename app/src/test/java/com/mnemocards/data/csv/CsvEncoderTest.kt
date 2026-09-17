package com.mnemocards.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Спецификация: add-csv-import-export, сценарии S2, S3, S4, S5, S6.
 *
 * Формат CSV-экспорта: UTF-8 с BOM, заголовок `front,back`,
 * разделитель колонок `,`, разделитель записей CRLF,
 * экранирование по RFC 4180, только текст карточек (без id и SRS-полей).
 */
class CsvEncoderTest {

    private val encoder = CsvEncoder

    private fun ByteArray.asUtf8Text(): String =
        toString(Charsets.UTF_8).removePrefix("\uFEFF")

    @Test
    fun `S2 happy path - 3 карточки, заголовок, BOM, CRLF`() {
        val rows = listOf(
            "cat" to "кошка",
            "dog" to "собака",
            "sun" to "солнце",
        )

        val bytes = encoder.encode(rows)

        // UTF-8 с BOM (S3): первые байты EF BB BF.
        assertEquals(0xEF, bytes[0].toInt() and 0xFF)
        assertEquals(0xBB, bytes[1].toInt() and 0xFF)
        assertEquals(0xBF, bytes[2].toInt() and 0xFF)

        val text = bytes.asUtf8Text()
        val lines = text.split("\r\n")
        assertEquals("front,back", lines[0])
        assertEquals("cat,кошка", lines[1])
        assertEquals("dog,собака", lines[2])
        assertEquals("sun,солнце", lines[3])
        // Последняя строка тоже завершается CRLF, хвостовых пустых строк нет.
        // 4 строки данных+заголовок и пустой хвост от завершающего CRLF.
        assertEquals(5, lines.size)
        assertTrue(text.endsWith("sun,солнце\r\n"))
    }

    @Test
    fun `S3 кириллица кодируется в UTF-8`() {
        val bytes = encoder.encode(listOf("привет" to "hello"))

        val expected = buildString {
            append("front,back\r\n")
            append("привет,hello\r\n")
        }.toByteArray(Charsets.UTF_8)

        assertEquals(
            expected.toList(),
            bytes.copyOfRange(3, bytes.size).toList(),
        )
    }

    @Test
    fun `S4 экранирование спецсимволов по RFC 4180`() {
        val rows = listOf(
            "hello, world" to "мир",
            "say \"hi\"" to "привет",
            "line1\nline2" to "перенос",
        )

        val text = encoder.encode(rows).asUtf8Text()
        val lines = text.split("\r\n")

        assertEquals("front,back", lines[0])
        // Запятая внутри значения — поле в кавычках.
        assertEquals("\"hello, world\",мир", lines[1])
        // Внутренние кавычки удвоены, поле обёрнуто.
        assertEquals("\"say \"\"hi\"\"\",привет", lines[2])
        // Перенос строки внутри значения — поле в кавычках; значение с \n
        // проверяем по полному тексту, а не по разбиению на строки.
        assertTrue(text.contains("\"line1\nline2\",перенос"))
    }

    @Test
    fun `S5 только текст карточек - ровно 2 колонки в каждой строке данных`() {
        val rows = listOf(
            "cat" to "кошка",
            "dog" to "собака",
        )

        val text = encoder.encode(rows).asUtf8Text()

        text.split("\r\n").dropLast(1).forEach { line ->
            // Числo колонок считаем с учётом кавычек: у этих данных их нет,
            // поэтому простое разбиение по запятой даёт честный подсчёт.
            assertEquals(2, line.split(",").size)
        }
        // Идентификаторы и SRS-параметры в файл не попадают.
        assertTrue(!text.contains("id"))
        assertTrue(!text.contains("interval"))
        assertTrue(!text.contains("ease"))
        assertTrue(!text.contains("due"))
    }

    @Test
    fun `S6 пустая колода - файл из одного заголовка с BOM`() {
        val bytes = encoder.encode(emptyList())

        assertEquals(0xEF, bytes[0].toInt() and 0xFF)
        assertEquals(0xBB, bytes[1].toInt() and 0xFF)
        assertEquals(0xBF, bytes[2].toInt() and 0xFF)
        assertEquals("front,back\r\n", bytes.asUtf8Text())
    }
}
