package com.mnemocards.data.csv

import com.mnemocards.data.csv.CsvParseResult.NotCsv
import com.mnemocards.data.csv.CsvParseResult.Parsed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Спецификация: add-csv-import-export, сценарии S10–S14, S17–S19.
 *
 * Правила импорта: BOM пропускается; разделитель (`,` или `;`)
 * определяется по первой строке — побеждает символ, которого больше,
 * при равенстве — запятая; строка заголовка `front`/`back` в любом
 * регистре пропускается; строки с пустыми полями пропускаются и
 * считаются; используются первые две колонки; значения экранированы
 * по RFC 4180; бинарные данные и пустой файл — [NotCsv].
 */
class CsvParserTest {

    private val parser = CsvParser()

    private fun csv(vararg lines: String, separator: String = "\r\n"): ByteArray {
        return lines.joinToString(separator).toByteArray(Charsets.UTF_8)
    }

    private fun csvWithBom(vararg lines: String, separator: String = "\r\n"): ByteArray {
        val text = lines.joinToString(separator)
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(Charsets.UTF_8)
    }

    // S10: заголовок (в любом регистре) и BOM пропускаются.
    @Test
    fun `S10 заголовок в верхнем регистре и BOM пропускаются`() {
        val result = parser.parse(
            csvWithBom(
                "FRONT,BACK",
                "cat,кошка",
            ),
        )

        assertTrue(result is Parsed)
        result as Parsed
        assertEquals(listOf("cat" to "кошка"), result.cards)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `S10 BOM не приклеивается к тексту первой карточки`() {
        val result = parser.parse(csvWithBom("front,back", "cat,кошка"))

        result as Parsed
        assertEquals("cat", result.cards.first().first)
        assertEquals("кошка", result.cards.first().second)
    }

    // S11: файл без заголовка — первая строка становится карточкой.
    @Test
    fun `S11 файл без заголовка - первая строка обрабатывается как карточка`() {
        val result = parser.parse(csv("cat,кошка", "dog,собака"))

        assertTrue(result is Parsed)
        result as Parsed
        assertEquals(
            listOf("cat" to "кошка", "dog" to "собака"),
            result.cards,
        )
        assertEquals(0, result.skipped)
    }

    // S12: строки с пустыми полями и из пробелов пропускаются и считаются.
    @Test
    fun `S12 пропуск строк с пустыми полями`() {
        val result = parser.parse(
            csv(
                "front,back",
                "cat,кошка",
                ",собака",
                "sun,",
                "   ",
            ),
        )

        assertTrue(result is Parsed)
        result as Parsed
        assertEquals(listOf("cat" to "кошка"), result.cards)
        assertEquals(3, result.skipped)
    }

    // S13: экранированные значения по RFC 4180.
    @Test
    fun `S13 парсинг экранированных значений`() {
        val result = parser.parse(
            csv(
                "front,back",
                "\"hello, world\",\"скажи \"\"привет\"\"\"",
                "\"line1\nline2\",перенос",
            ),
        )

        assertTrue(result is Parsed)
        result as Parsed
        assertEquals(
            listOf(
                "hello, world" to "скажи \"привет\"",
                "line1\nline2" to "перенос",
            ),
            result.cards,
        )
        assertEquals(0, result.skipped)
    }

    // S14: разделитель `;` (Excel, русская локаль) определяется автоматически.
    @Test
    fun `S14 разделитель точка с запятой определяется по первой строке`() {
        val result = parser.parse(
            csv(
                "front;back",
                "cat;кошка",
                "\"hello, world\";привет",
            ),
        )

        assertTrue(result is Parsed)
        result as Parsed
        assertEquals(
            listOf("cat" to "кошка", "hello, world" to "привет"),
            result.cards,
        )
        assertEquals(0, result.skipped)
    }

    @Test
    fun `S14 при равенстве счёта запятых и точек с запятой побеждает запятая`() {
        // В первой строке одна запятая и одна точка с запятой — выбирается `,`.
        val result = parser.parse(csv("a,b;c", "one,two"))

        assertTrue(result is Parsed)
        result as Parsed
        // При `,` строка «a,b;c» даёт колонки «a» и «b;c» — ничего не пропущено.
        assertEquals(
            listOf("a" to "b;c", "one" to "two"),
            result.cards,
        )
        assertEquals(0, result.skipped)
    }

    // S17: недостаточное и избыточное число колонок.
    @Test
    fun `S17 строка из одной колонки пропущена, из трёх - взяты первые две`() {
        val result = parser.parse(csv("front,back", "solo", "a,b,c"))

        assertTrue(result is Parsed)
        result as Parsed
        assertEquals(listOf("a" to "b"), result.cards)
        assertEquals(1, result.skipped)
    }

    // S18: бинарные данные и битая UTF-8 → NotCsv.
    @Test
    fun `S18 бинарные данные дают NotCsv`() {
        val binary = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x01, 0x02, 0x03,
        )

        assertTrue(parser.parse(binary) is NotCsv)
    }

    @Test
    fun `S18 битая UTF-8 последовательность даёт NotCsv`() {
        // 0xC3 без продолжающего байта — некорректная UTF-8 последовательность.
        val broken = byteArrayOf(0xC3.toByte(), 0x2C, 0x61)

        assertTrue(parser.parse(broken) is NotCsv)
    }

    // S19: пустой файл и файл из одного BOM → NotCsv.
    @Test
    fun `S19 файл размером 0 байт даёт NotCsv`() {
        assertTrue(parser.parse(ByteArray(0)) is NotCsv)
    }

    @Test
    fun `S19 файл из одного BOM даёт NotCsv`() {
        val bomOnly = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertTrue(parser.parse(bomOnly) is NotCsv)
    }

    // Дополнение к контракту: записи с LF тоже принимаются.
    @Test
    fun `строки с LF принимаются`() {
        val result = parser.parse(csv("front,back", "cat,кошка", separator = "\n"))

        assertTrue(result is Parsed)
        result as Parsed
        assertEquals(listOf("cat" to "кошка"), result.cards)
        assertEquals(0, result.skipped)
    }
}
