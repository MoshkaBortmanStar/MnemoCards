package com.mnemocards.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Спецификация: add-csv-import-export, S25, S5, S13.
 *
 * Раунд-трип: encode → parse возвращает исходные пары текстов без
 * потерь (кириллица, запятые, кавычки, переносы строк, `;` внутри
 * значений); то же для файла с LF вместо CRLF; кодек переносит
 * только тексты сторон — идентификаторы и SRS-данные не участвуют.
 */
class CsvRoundTripTest {

    private val encoder = CsvEncoder
    private val parser = CsvParser()

    private val trickyRows = listOf(
        "привет" to "hello",
        "hello, world" to "мир, вселенная",
        "say \"hi\"" to "скажи \"привет\"",
        "line1\nline2" to "перенос",
        "a;b" to "точка;с запятой",
        "cat" to "кошка",
    )

    @Test
    fun `S25 раунд-трип CRLF возвращает исходные пары`() {
        val bytes = encoder.encode(trickyRows)

        val result = parser.parse(bytes)

        result as CsvParseResult.Parsed
        assertEquals(trickyRows, result.cards)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `S25 раунд-трип с LF вместо CRLF возвращает исходные пары`() {
        val bytes = encoder.encode(trickyRows)
        // Имитируем файл, пересохранённый с LF: значения содержат только \n,
        // поэтому простая замена записи CRLF → LF безопасна.
        val text = bytes.toString(Charsets.UTF_8)
            .removePrefix("﻿")
            .replace("\r\n", "\n")
        val lfBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(Charsets.UTF_8)

        val result = parser.parse(lfBytes)

        result as CsvParseResult.Parsed
        assertEquals(trickyRows, result.cards)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `S25 раунд-трип пустой колоды даёт ноль карточек`() {
        val result = parser.parse(encoder.encode(emptyList()))

        result as CsvParseResult.Parsed
        assertTrue(result.cards.isEmpty())
        assertEquals(0, result.skipped)
    }

    // S5: в файле нет ничего, кроме текстов сторон — идентификаторы
    // и параметры SRS не переносятся, все карточки колоды-приёмника новые.
    @Test
    fun `S5 раунд-трип переносит только пары текстов`() {
        val bytes = encoder.encode(trickyRows)
        val text = bytes.toString(Charsets.UTF_8).removePrefix("﻿")

        assertTrue(!text.contains("id"))
        assertTrue(!text.contains("interval"))
        assertTrue(!text.contains("ease"))
        assertTrue(!text.contains("due"))

        val result = parser.parse(bytes)
        result as CsvParseResult.Parsed
        // На выходе — ровно пары строк, без служебных данных.
        assertEquals(
            trickyRows.map { it.first to it.second },
            result.cards,
        )
    }
}
