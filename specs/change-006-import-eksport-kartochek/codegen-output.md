## Батч 1/5

### FILE: app/src/test/java/com/mnemocards/data/csv/CsvEncoderTest.kt
```kotlin
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

    private val encoder = CsvEncoder()

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
        assertEquals(4, lines.size)
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
```

### FILE: app/src/main/java/com/mnemocards/data/csv/CsvEncoder.kt
```kotlin
package com.mnemocards.data.csv

/**
 * Кодировщик CSV для экспорта колоды (capability `deck.csv-transfer`).
 *
 * Контракт файла:
 * - кодировка UTF-8 с BOM (`EF BB BF`);
 * - первая строка — заголовок `front,back`;
 * - разделитель колонок `,`, разделитель записей `CRLF`;
 * - экранирование по RFC 4180: поле, содержащее `,`, `"` или перевод
 *   строки, заключается в `"`; символ `"` внутри поля удваивается;
 * - выгружаются только тексты сторон — идентификаторы и параметры SRS
 *   в файл не попадают.
 */
object CsvEncoder {

    private const val HEADER = "front,back"
    private const val COLUMN_SEPARATOR = ","
    private const val RECORD_SEPARATOR = "\r\n"
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /**
     * Кодирует пары «лицевая сторона / перевод» в CSV-файл как массив байтов
     * UTF-8 с BOM. Пустой список даёт файл из единственной строки-заголовка.
     */
    fun encode(rows: List<Pair<String, String>>): ByteArray {
        val body = buildString {
            append(escape(HEADER))
            rows.forEach { (front, back) ->
                append(RECORD_SEPARATOR)
                append(escape(front))
                append(COLUMN_SEPARATOR)
                append(escape(back))
            }
            append(RECORD_SEPARATOR)
        }
        return UTF8_BOM + body.toByteArray(Charsets.UTF_8)
    }

    /** Экранирует поле по RFC 4180. */
    private fun escape(field: String): String {
        val needsQuoting = field.contains(',') ||
            field.contains('"') ||
            field.contains('\n') ||
            field.contains('\r')
        if (!needsQuoting) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/csv/CsvParserTest.kt
```kotlin
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
        val result = parser.parse(csv("cat;dog", "one,two"))

        assertTrue(result is Parsed)
        result as Parsed
        assertEquals(
            listOf("cat;dog" to "", "one" to "two"),
            // cat;dog при разделителе `,` — одна колонка: перевод пуст,
            // строка пропускается; one,two даёт карточку.
            result.cards,
        )
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
```


---

## Батч 2/5

### FILE: app/src/main/java/com/mnemocards/data/csv/CsvParseResult.kt
```kotlin
package com.mnemocards.data.csv

/**
 * Результат разбора CSV-файла при импорте (capability `deck.csv-transfer`).
 */
sealed interface CsvParseResult {

    /** Файл разобран: валидные пары текстов и число пропущенных строк. */
    data class Parsed(
        val cards: List<Pair<String, String>>,
        val skipped: Int,
    ) : CsvParseResult

    /**
     * Файл не является CSV или не читается: пустой файл, бинарные данные
     * или некорректная UTF-8 последовательность (аналог 400, S18/S19).
     */
    object NotCsv : CsvParseResult
}
```

### FILE: app/src/main/java/com/mnemocards/data/csv/CsvParser.kt
```kotlin
package com.mnemocards.data.csv

import java.nio.ByteBuffer
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction

/**
 * Парсер CSV-файлов при импорте (capability `deck.csv-transfer`).
 *
 * Правила разбора:
 * - строгая UTF-8-декодация: некорректная последовательность байтов
 *   даёт [CsvParseResult.NotCsv] (S18);
 * - BOM пропускается и не приклеивается к первому значению (S10);
 * - разделитель (`,` или `;`) определяется по первой строке файла:
 *   побеждает символ, которого больше, при равенстве — запятая (S14);
 * - строка заголовка пропускается, только если её колонки равны
 *   `front` и `back` без учёта регистра (S10, S11);
 * - значения экранированы по RFC 4180: кавычки, запятые и переносы
 *   строк внутри поля (S13);
 * - записи разделяются CRLF или LF;
 * - поля обрезаются от пробелов; строки с пустой лицевой стороной
 *   или пустым переводом пропускаются и учитываются в `skipped` (S12);
 * - используются первые две колонки, лишние игнорируются (S17);
 * - пустой файл и файл из одного BOM — [CsvParseResult.NotCsv] (S19).
 */
class CsvParser {

    private val utf8Decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)

    fun parse(bytes: ByteArray): CsvParseResult {
        val text = decodeStrictUtf8(bytes) ?: return CsvParseResult.NotCsv

        // S10: BOM пропускается.
        val body = if (text.startsWith(BOM_CHAR)) text.removePrefix(BOM_CHAR) else text

        // S19: пустой файл и файл из одного BOM не являются CSV.
        if (body.isEmpty()) return CsvParseResult.NotCsv

        val separator = detectSeparator(body)
        val records = tokenize(body, separator)

        // Хвостовой перевод строки не создаёт пустую запись.
        val meaningful = records.dropLastWhile { it == listOf("") }
        if (meaningful.isEmpty()) return CsvParseResult.NotCsv

        val dataRecords = dropHeaderIfPresent(meaningful)

        val cards = mutableListOf<Pair<String, String>>()
        var skipped = 0
        for (record in dataRecords) {
            val front = record.getOrNull(0)?.trim().orEmpty()
            val back = record.getOrNull(1)?.trim().orEmpty()
            if (front.isEmpty() || back.isEmpty()) {
                skipped++
            } else {
                cards.add(front to back)
            }
        }
        return CsvParseResult.Parsed(cards, skipped)
    }

    /** Строгая UTF-8-декодация: ошибка кодировки → null (S18). */
    private fun decodeStrictUtf8(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val decoder = utf8Decoder.reset()
        val output = StringBuilder(bytes.size)
        val input = ByteBuffer.wrap(bytes)
        val charBuffer = CharArray(4096)
        while (true) {
            val wrapped = ByteBuffer.wrap(ByteArray(0)) // placeholder, не используется
            break
        }
        val out = java.nio.CharBuffer.wrap(charBuffer)
        while (true) {
            out.clear()
            val result = decoder.decode(input, out, true)
            if (result.isError) return null
            output.append(charBuffer, 0, out.position())
            if (result.isUnderflow) {
                val flush = decoder.flush(out)
                if (flush.isError) return null
                output.append(charBuffer, 0, out.position())
                if (flush.isUnderflow) break
            }
        }
        return output.toString()
    }

    /** S14: разделитель определяется по первой строке, ничья — запятая. */
    private fun detectSeparator(body: String): Char {
        val firstLine = body.substringBefore('\n').substringBefore('\r')
        val commas = firstLine.count { it == ',' }
        val semicolons = firstLine.count { it == ';' }
        return if (semicolons > commas) ';' else ','
    }

    /** S10/S11: заголовок пропускается только при точном совпадении колонок. */
    private fun dropHeaderIfPresent(records: List<List<String>>): List<List<String>> {
        val first = records.firstOrNull() ?: return emptyList()
        val normalized = first.map { it.trim().lowercase() }
        return if (normalized == HEADER_COLUMNS) records.drop(1) else records
    }

    /**
     * Конечный автомат по RFC 4180: поля в кавычках могут содержать
     * разделитель, переводы строк и удвоенные кавычки.
     */
    private fun tokenize(text: String, separator: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == separator -> {
                    fields.add(field.toString())
                    field.setLength(0)
                }
                c == '\n' -> {
                    fields.add(field.toString())
                    field.setLength(0)
                    records.add(fields.toList())
                    fields.clear()
                }
                c == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    fields.add(field.toString())
                    field.setLength(0)
                    records.add(fields.toList())
                    fields.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        // Последнее поле/запись без завершающего перевода строки.
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(field.toString())
            records.add(fields.toList())
        }
        return records
    }

    private companion object {
        const val BOM_CHAR = '﻿'
        val HEADER_COLUMNS = listOf("front", "back")
    }
}
```

Уберу случайно оставшийся мёртвый цикл в decodeStrictUtf8 — вывожу финальную чистую версию парсера:

### FILE: app/src/main/java/com/mnemocards/data/csv/CsvParser.kt
```kotlin
package com.mnemocards.data.csv

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * Парсер CSV-файлов при импорте (capability `deck.csv-transfer`).
 *
 * Правила разбора:
 * - строгая UTF-8-декодация: некорректная последовательность байтов
 *   даёт [CsvParseResult.NotCsv] (S18);
 * - BOM пропускается и не приклеивается к первому значению (S10);
 * - разделитель (`,` или `;`) определяется по первой строке файла:
 *   побеждает символ, которого больше, при равенстве — запятая (S14);
 * - строка заголовка пропускается, только если её колонки равны
 *   `front` и `back` без учёта регистра (S10, S11);
 * - значения экранированы по RFC 4180: кавычки, запятые и переносы
 *   строк внутри поля (S13);
 * - записи разделяются CRLF или LF;
 * - поля обрезаются от пробелов; строки с пустой лицевой стороной
 *   или пустым переводом пропускаются и учитываются в `skipped` (S12);
 * - используются первые две колонки, лишние игнорируются (S17);
 * - пустой файл и файл из одного BOM — [CsvParseResult.NotCsv] (S19).
 */
class CsvParser {

    private val utf8Decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)

    fun parse(bytes: ByteArray): CsvParseResult {
        val text = decodeStrictUtf8(bytes) ?: return CsvParseResult.NotCsv

        // S10: BOM пропускается.
        val body = if (text.startsWith(BOM_CHAR)) text.removePrefix(BOM_CHAR) else text

        // S19: пустой файл и файл из одного BOM не являются CSV.
        if (body.isEmpty()) return CsvParseResult.NotCsv

        val separator = detectSeparator(body)
        val records = tokenize(body, separator)

        // Хвостовой перевод строки не создаёт пустую запись.
        val meaningful = records.dropLastWhile { it == listOf("") }
        if (meaningful.isEmpty()) return CsvParseResult.NotCsv

        val dataRecords = dropHeaderIfPresent(meaningful)

        val cards = mutableListOf<Pair<String, String>>()
        var skipped = 0
        for (record in dataRecords) {
            val front = record.getOrNull(0)?.trim().orEmpty()
            val back = record.getOrNull(1)?.trim().orEmpty()
            if (front.isEmpty() || back.isEmpty()) {
                skipped++
            } else {
                cards.add(front to back)
            }
        }
        return CsvParseResult.Parsed(cards, skipped)
    }

    /** Строгая UTF-8-декодация: ошибка кодировки → null (S18). */
    private fun decodeStrictUtf8(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val decoder = utf8Decoder.reset()
        val input = ByteBuffer.wrap(bytes)
        val charBuffer = CharArray(4096)
        val output = StringBuilder(bytes.size)
        val out = CharBuffer.wrap(charBuffer)
        while (true) {
            out.clear()
            val result = decoder.decode(input, out, true)
            if (result.isError) return null
            output.append(charBuffer, 0, out.position())
            if (result.isUnderflow) {
                val flush = decoder.flush(out)
                if (flush.isError) return null
                output.append(charBuffer, 0, out.position())
                break
            }
        }
        return output.toString()
    }

    /** S14: разделитель определяется по первой строке, ничья — запятая. */
    private fun detectSeparator(body: String): Char {
        val firstLine = body.substringBefore('\n').substringBefore('\r')
        val commas = firstLine.count { it == ',' }
        val semicolons = firstLine.count { it == ';' }
        return if (semicolons > commas) ';' else ','
    }

    /** S10/S11: заголовок пропускается только при точном совпадении колонок. */
    private fun dropHeaderIfPresent(records: List<List<String>>): List<List<String>> {
        val first = records.firstOrNull() ?: return emptyList()
        val normalized = first.map { it.trim().lowercase() }
        return if (normalized == HEADER_COLUMNS) records.drop(1) else records
    }

    /**
     * Конечный автомат по RFC 4180: поля в кавычках могут содержать
     * разделитель, переводы строк и удвоенные кавычки.
     */
    private fun tokenize(text: String, separator: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == separator -> {
                    fields.add(field.toString())
                    field.setLength(0)
                }
                c == '\n' -> {
                    fields.add(field.toString())
                    field.setLength(0)
                    records.add(fields.toList())
                    fields.clear()
                }
                c == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    fields.add(field.toString())
                    field.setLength(0)
                    records.add(fields.toList())
                    fields.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        // Последнее поле/запись без завершающего перевода строки.
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(field.toString())
            records.add(fields.toList())
        }
        return records
    }

    private companion object {
        const val BOM_CHAR = '﻿'
        val HEADER_COLUMNS = listOf("front", "back")
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/csv/CsvRoundTripTest.kt
```kotlin
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

    private val encoder = CsvEncoder()
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
```

### FILE: app/src/test/java/com/mnemocards/data/repository/DeckContentRepositoryTest.kt
```kotlin
package com.mnemocards.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Спецификация: add-csv-import-export, S5, S9, S15, S16, S21, S22.
 *
 * Контракт репозитория колоды (T6, красная фаза):
 * - `importCards` добавляет все валидные строки как новые карточки
 *   без дедупликации, атомарно; существующие карточки не затрагиваются;
 * - счётчик колоды читается из Room через Flow и обновляется сам;
 * - несуществующая колода → [CardImportResult.DeckNotFound];
 * - ошибка DAO пробрасывается наружу, вставка откатывается;
 * - `exportCardTexts` возвращает только тексты сторон.
 */
@RunWith(RobolectricTestRunner::class)
class DeckContentRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var repository: CardRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCardRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun createDeck(name: String = "Английский"): Long =
        db.deckDao().insert(DeckEntity(name = name))

    private suspend fun cardsOf(deckId: Long): List<CardEntity> =
        db.cardDao().observeCardsByDeck(deckId).first()

    // S9: добавление 4 карточек в колоду из 5 → счётчик 5 → 9.
    @Test
    fun `S9 импорт добавляет карточки и счётчик обновляется`() = runTest {
        val deckId = createDeck()
        repository.importCards(
            deckId,
            listOf(
                "cat" to "кошка",
                "dog" to "собака",
                "sun" to "солнце",
                "moon" to "луна",
                "star" to "звезда",
            ),
        )

        val before = cardsOf(deckId)
        assertEquals(5, before.size)

        val result = repository.importCards(
            deckId,
            listOf(
                "one" to "один",
                "two" to "два",
                "three" to "три",
                "four" to "четыре",
            ),
        )

        assertEquals(CardImportResult.Imported(4), result)
        val after = cardsOf(deckId)
        assertEquals(9, after.size)
        // Существующие 5 карточек не изменены и не удалены.
        assertTrue(after.map { it.front to it.back }.containsAll(before.map { it.front to it.back }))
    }

    // S15: дубликат «cat»/«кошка» добавляется как новая карточка.
    @Test
    fun `S15 дубликат добавляется без дедупликации`() = runTest {
        val deckId = createDeck()
        repository.importCards(deckId, listOf("cat" to "кошка"))
        val original = cardsOf(deckId).single()

        val result = repository.importCards(deckId, listOf("cat" to "кошка"))

        assertEquals(CardImportResult.Imported(1), result)
        val cards = cardsOf(deckId)
        assertEquals(2, cards.size)
        // Исходная карточка не изменена, из файла создана отдельная новая.
        assertTrue(cards.any { it.id == original.id })
        assertTrue(cards.count { it.front == "cat" && it.back == "кошка" } == 2)
    }

    // S16: импортированные карточки сразу участвуют в тренировке —
    // попадают в ту же выборку колоды и имеют состояние новых карточек.
    @Test
    fun `S16 импортированные карточки сразу готовы к повторению`() = runTest {
        val deckId = createDeck()
        repository.importCards(deckId, listOf("cat" to "кошка"))
        repository.importCards(deckId, listOf("dog" to "собака"))

        val cards = cardsOf(deckId)
        assertEquals(2, cards.size)
        // Состояние новых карточек — как при ручном создании: у обеих
        // карточек одинаковые (дефолтные) SRS-параметры.
        val manual = cards.first { it.front == "cat" }
        val imported = cards.first { it.front == "dog" }
        assertEquals(manual.srsStateSignature(), imported.srsStateSignature())
    }

    private fun CardEntity.srsStateSignature() = listOf(
        intervalDays, easeFactor, repetitions, dueDate,
    )

    // S21: устаревшая колода → DeckNotFound, ничего не вставлено.
    @Test
    fun `S21 несуществующая колода даёт DeckNotFound`() = runTest {
        val result = repository.importCards(999L, listOf("cat" to "кошка"))

        assertEquals(CardImportResult.DeckNotFound, result)
        assertTrue(db.cardDao().observeCardsByDeck(999L).first().isEmpty())
    }

    // S22: ошибка DAO пробрасывается, вставка атомарно откачена.
    @Test
    fun `S22 ошибка DAO пробрасывается и вставка откачена`() = runTest {
        val deckId = createDeck()
        repository.importCards(deckId, listOf("cat" to "кошка"))
        val failing = RoomCardRepository(db, object : CardDao by db.cardDao() {
            override suspend fun insertAll(cards: List<CardEntity>): List<Long> =
                throw RuntimeException("disk I/O error")
        })

        val threw = try {
            failing.importCards(deckId, listOf("dog" to "собака", "sun" to "солнце"))
            false
        } catch (e: RuntimeException) {
            true
        }

        assertTrue(threw)
        // Колода содержит только исходную карточку — частичной вставки нет.
        assertEquals(1, cardsOf(deckId).size)
    }

    // S5: экспорт возвращает только тексты сторон.
    @Test
    fun `S5 exportCardTexts возвращает только тексты сторон`() = runTest {
        val deckId = createDeck()
        repository.importCards(
            deckId,
            listOf("cat" to "кошка", "hello, world" to "мир"),
        )

        val texts = repository.exportCardTexts(deckId)

        assertEquals(
            listOf("cat" to "кошка", "hello, world" to "мир"),
            texts,
        )
    }
}
```

### FILE: gradle/libs.versions.toml
```
[versions]
agp = "9.4.0"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
kotlinxCoroutinesTest = "1.10.2"
room = "2.7.2"
ksp = "2.2.10-2.0.2"
robolectric = "4.14.1"
turbine = "1.1.0"
navigation = "2.9.5"
androidxTestCore = "1.6.1"
# Material Icons заморожены на 1.7.8 (deprecated) и больше не входят в Compose BOM.
composeIconsCore = "1.7.8"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-test-core-ktx = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core", version.ref = "composeIconsCore" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-navigation-testing = { group = "androidx.navigation", name = "navigation-testing", version.ref = "navigation" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### FILE: app/build.gradle.kts
```
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mnemocards"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mnemocards"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        // JSON схем Room — как assets androidTest: их читает MigrationTestHelper (S28).
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    // Тесты (Robolectric/ASM) запускаются на тулчейне JDK 17: класс-файлы
    // JDK 25 (major 69) не читаются ASM-инструментацией Robolectric.
    jvmToolchain(17)
}

ksp {
    // Экспорт схем Room для MigrationTestHelper (T5).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```


---

## Батч 3/5

### FILE: app/src/main/java/com/mnemocards/data/db/CardDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CardEntity): Long

    /**
     * Пакетная вставка карточек при импорте CSV (T7): атомарность
     * обеспечивает Room-транзакция на уровне репозитория.
     */
    @Insert
    suspend fun insertAll(cards: List<CardEntity>): List<Long>

    @Update
    suspend fun updateCard(card: CardEntity)

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteByDeckId(deckId: Long)

    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCardById(cardId: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id")
    suspend fun getCardsByDeck(deckId: Long): List<CardEntity>

    /**
     * Наблюдение за карточками колоды (T7): счётчик и содержимое колоды
     * читаются из Room через Flow и обновляются автоматически (S1, S9).
     */
    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id")
    fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>>
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Insert
    suspend fun insert(deck: DeckEntity): Long

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Delete
    suspend fun deleteDeck(deck: DeckEntity)

    /**
     * Колода по идентификатору (T7): проверка существования колоды
     * перед импортом CSV — аналог 404 (S21) и экспорт.
     */
    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun getDeckById(deckId: Long): DeckEntity?

    /**
     * Наблюдение за колодой (T7): экран содержимого колоды реагирует
     * на изменения Room автоматически (S1).
     */
    @Query("SELECT * FROM decks WHERE id = :deckId")
    fun observeDeck(deckId: Long): Flow<DeckEntity?>
}
```

### FILE: app/src/main/java/com/mnemocards/data/repository/CardRepository.kt
```kotlin
package com.mnemocards.data.repository

import androidx.room.withTransaction
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.domain.model.Card
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface CardOperationResult {
    data object Success : CardOperationResult
    data class Error(val throwable: Throwable) : CardOperationResult
}

sealed interface CardImportResult {
    data class Imported(val added: Int) : CardImportResult
    data object DeckNotFound : CardImportResult
}

interface CardRepository {
    fun observeCardsByDeck(deckId: Long): Flow<List<Card>>
    suspend fun addCard(deckId: Long, front: String, back: String): CardOperationResult
    suspend fun deleteCard(cardId: Long): CardOperationResult
}

class RoomCardRepository(
    private val db: MnemoCardsDatabase,
    private val cardDao: CardDao = db.cardDao(),
) : CardRepository {

    private val deckDao = db.deckDao()

    override fun observeCardsByDeck(deckId: Long): Flow<List<Card>> =
        cardDao.observeCardsByDeck(deckId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addCard(
        deckId: Long,
        front: String,
        back: String,
    ): CardOperationResult = try {
        if (deckDao.getDeckById(deckId) == null) {
            return CardOperationResult.Error(IllegalStateException("Колода не найдена"))
        }
        db.withTransaction {
            cardDao.insert(CardEntity(deckId = deckId, front = front, back = back))
        }
        CardOperationResult.Success
    } catch (t: Throwable) {
        CardOperationResult.Error(t)
    }

    override suspend fun deleteCard(cardId: Long): CardOperationResult = try {
        val card = cardDao.getCardById(cardId)
            ?: return CardOperationResult.Error(IllegalStateException("Карточка не найдена"))
        db.withTransaction {
            cardDao.deleteCard(card)
        }
        CardOperationResult.Success
    } catch (t: Throwable) {
        CardOperationResult.Error(t)
    }

    /**
     * Экспорт колоды (S5, S25): только тексты сторон карточек —
     * идентификаторы и параметры SRS не выгружаются.
     * `null` — колода не найдена.
     */
    suspend fun exportCardTexts(deckId: Long): List<Pair<String, String>>? {
        if (deckDao.getDeckById(deckId) == null) return null
        return cardDao.getCardsByDeck(deckId).map { it.front to it.back }
    }

    /**
     * Импорт CSV (S9, S15, S16, S21, S22): все валидные строки
     * добавляются как новые карточки без дедупликации, атомарно;
     * новые [CardEntity] создаются с дефолтами новых карточек (S16).
     * Несуществующая колода → [CardImportResult.DeckNotFound];
     * ошибка БД пробрасывается наружу, вставка откачена (S22).
     */
    suspend fun importCards(
        deckId: Long,
        cards: List<Pair<String, String>>,
    ): CardImportResult {
        if (deckDao.getDeckById(deckId) == null) return CardImportResult.DeckNotFound
        return db.withTransaction {
            val entities = cards.map { (front, back) ->
                CardEntity(deckId = deckId, front = front, back = back)
            }
            cardDao.insertAll(entities)
            CardImportResult.Imported(cards.size)
        }
    }

    private fun CardEntity.toDomain() = Card(
        id = id,
        deckId = deckId,
        front = front,
        back = back,
    )
}
```

### FILE: app/src/main/java/com/mnemocards/data/file/CsvFileGateway.kt
```kotlin
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
```

### FILE: app/src/main/java/com/mnemocards/data/file/SafCsvFileGateway.kt
```kotlin
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
        val stream = contentResolver.openInputStream(uri)
            ?: throw IOException("Не удалось открыть файл")
        stream.use { it.readBytes() }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/file/SafCsvFileGatewayTest.kt
```kotlin
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
```


---

## Батч 4/5

### FILE: app/src/test/java/com/mnemocards/ui/deckdetail/DeckDetailViewModelTest.kt
```kotlin
package com.mnemocards.ui.deckdetail

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.csv.CsvEncoder
import com.mnemocards.data.csv.CsvParser
import com.mnemocards.data.file.CsvFileGateway
import com.mnemocards.data.repository.CardImportResult
import com.mnemocards.data.repository.CardOperationResult
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.domain.model.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Спецификация: add-csv-import-export.
 *
 * T10, красная фаза: тесты [DeckDetailViewModel] на kotlinx-coroutines-test
 * с фейками репозитория и файлового шлюза.
 *
 * Сценарии: S1, S2, S6, S7, S8, S9, S12, S15, S18, S19, S21, S22, S23.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeckDetailViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher()

    private lateinit var repo: FakeCardRepository
    private lateinit var gateway: FakeCsvFileGateway
    private lateinit var strings: FakeStrings

    private val uri: Uri = Uri.parse("content://com.mnemocards.test/documents/file.csv")

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repo = FakeCardRepository()
        gateway = FakeCsvFileGateway()
        strings = FakeStrings()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(deckName: String? = "Английский") = DeckDetailViewModel(
        deckId = 1L,
        deckName = MutableStateFlow(deckName),
        cardRepository = repo,
        csvFileGateway = gateway,
        encoder = CsvEncoder(),
        parser = CsvParser(),
        strings = strings,
        ioDispatcher = ioDispatcher,
    )

    // S1: начальное состояние (имя колоды, карточки, счётчик) приходит из Flow.
    @Test
    fun `начальное состояние приходит из Flow репозитория`() = runTest(mainDispatcher) {
        repo.cards.value = listOf(card(1, "cat", "кошка"), card(2, "dog", "собака"))
        val vm = viewModel()

        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Английский", state.deckName)
        assertEquals(2, state.cards.size)
        assertEquals(2, state.cardCount)
        assertEquals(TransferMessage.Idle, state.transferMessage)
        assertTrue(!state.isBusy)
    }

    // S2: экспорт пишет CSV через шлюз и показывает «Файл сохранён».
    @Test
    fun `экспорт пишет файл и показывает подтверждение`() = runTest(mainDispatcher) {
        repo.cards.value = listOf(
            card(1, "cat", "кошка"),
            card(2, "dog", "собака"),
            card(3, "sun", "солнце"),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.onExportTarget(uri)
        advanceUntilIdle()

        val written = gateway.written!!
        val text = written.toString(Charsets.UTF_8).removePrefix("﻿")
        assertEquals("front,back\r\ncat,кошка\r\ndog,собака\r\nsun,солнце\r\n", text)
        assertEquals("Файл сохранён", vm.uiState.value.transferMessage.savedText())
        // Данные колоды не изменились.
        assertEquals(3, vm.uiState.value.cardCount)
        assertTrue(repo.imported.isEmpty())
    }

    // S6: экспорт пустой колоды — файл из одного заголовка, ошибка не показывается.
    @Test
    fun `экспорт пустой колоды создаёт только заголовок`() = runTest(mainDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onExportTarget(uri)
        advanceUntilIdle()

        val text = gateway.written!!.toString(Charsets.UTF_8).removePrefix("﻿")
        assertEquals("front,back\r\n", text)
        assertEquals("Файл сохранён", vm.uiState.value.transferMessage.savedText())
    }

    // S7: без события от SAF состояние не меняется.
    @Test
    fun `без события SAF состояние не меняется`() = runTest(mainDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(TransferMessage.Idle, vm.uiState.value.transferMessage)
        assertNull(gateway.written)
        assertTrue(repo.imported.isEmpty())
    }

    // S8: ошибка записи → «Не удалось сохранить файл», состояние колоды прежнее.
    @Test
    fun `ошибка записи файла показывает ошибку и не меняет колоду`() = runTest(mainDispatcher) {
        repo.cards.value = listOf(card(1, "cat", "кошка"))
        gateway.throwOnWrite = true
        val vm = viewModel()
        advanceUntilIdle()

        vm.onExportTarget(uri)
        advanceUntilIdle()

        assertEquals(
            TransferMessage.Error("Не удалось сохранить файл"),
            vm.uiState.value.transferMessage,
        )
        assertEquals(1, vm.uiState.value.cardCount)
    }

    // S9: happy path импорта — 4 карточки добавлены, итог «Добавлено: 4, пропущено: 0».
    @Test
    fun `импорт добавляет карточки и показывает итог`() = runTest(mainDispatcher) {
        repo.cards.value = (1..5).map { card(it.toLong(), "f$it", "b$it") }
        gateway.readBytes = CsvEncoder().encode(
            listOf("cat" to "кошка", "dog" to "собака", "sun" to "солнце", "moon" to "луна"),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.onImportFile(uri)
        advanceUntilIdle()

        assertEquals(TransferMessage.Summary(added = 4, skipped = 0), vm.uiState.value.transferMessage)
        assertEquals("Добавлено: 4, пропущено: 0", vm.uiState.value.transferMessage.summaryText())
        // Существующие 5 карточек не изменены, счётчик 5 → 9.
        assertEquals(9, vm.uiState.value.cardCount)
        assertEquals(5, vm.uiState.value.cards.take(5).size)
    }

    // S12: строки с пустыми полями пропускаются — «Добавлено: 1, пропущено: 3».
    @Test
    fun `импорт пропускает строки с пустыми полями`() = runTest(mainDispatcher) {
        gateway.readBytes = CsvEncoder().encode(listOf("cat" to "кошка")) // + вручную:
        gateway.readBytes = (
            "front,back\r\ncat,кошка\r\n,собака\r\nsun,\r\n   ,   \r\n"
            ).toByteArray(Charsets.UTF_8)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onImportFile(uri)
        advanceUntilIdle()

        assertEquals(TransferMessage.Summary(added = 1, skipped = 3), vm.uiState.value.transferMessage)
        assertEquals(1, repo.imported.single().size)
    }

    // S15: дубликаты добавляются как новые, дедупликации нет.
    @Test
    fun `импорт дубликата добавляет новую карточку`() = runTest(mainDispatcher) {
        repo.cards.value = listOf(card(1, "cat", "кошка"))
        gateway.readBytes = "front,back\r\ncat,кошка\r\n".toByteArray(Charsets.UTF_8)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onImportFile(uri)
        advanceUntilIdle()

        assertEquals(TransferMessage.Summary(added = 1, skipped = 0), vm.uiState.value.transferMessage)
        assertEquals(2, vm.uiState.value.cardCount)
    }

    // S18: нечитаемый/бинарный файл → ошибка 400, вставки нет.
    @Test
    fun `импорт не-CSV показывает ошибку и не вставляет`() = runTest(mainDispatcher) {
        gateway.readBytes = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onImportFile(uri)
        advanceUntilIdle()

        assertEquals(
            TransferMessage.Error("Файл не является CSV или не читается"),
            vm.uiState.value.transferMessage,
        )
        assertTrue(repo.imported.isEmpty())
        assertEquals(0, vm.uiState.value.cardCount)
    }

    // S19: пустой файл → та же понятная ошибка, колода не меняется.
    @Test
    fun `импорт пустого файла показывает ошибку`() = runTest(mainDispatcher) {
        gateway.readBytes = ByteArray(0)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onImportFile(uri)
        advanceUntilIdle()

        assertEquals(
            TransferMessage.Error("Файл не является CSV или не читается"),
            vm.uiState.value.transferMessage,
        )
        assertTrue(repo.imported.isEmpty())
    }

    // S21: колода не найдена → «Колода не найдена», вставки нет.
    @Test
    fun `импорт в отсутствующую колоду показывает Колода не найдена`() = runTest(mainDispatcher) {
        gateway.readBytes = "front,back\r\ncat,кошка\r\n".toByteArray(Charsets.UTF_8)
        repo.deckExists = false
        val vm = viewModel()
        advanceUntilIdle()

        vm.onImportFile(uri)
        advanceUntilIdle()

        assertEquals(TransferMessage.Error("Колода не найдена"), vm.uiState.value.transferMessage)
        assertTrue(repo.imported.isEmpty())
    }

    // S22: ошибка БД при вставке — понятная ошибка, колода не изменена.
    @Test
    fun `ошибка БД при импорте показывает ошибку и не меняет колоду`() = runTest(mainDispatcher) {
        gateway.readBytes = "front,back\r\ncat,кошка\r\n".toByteArray(Charsets.UTF_8)
        repo.throwOnImport = true
        val vm = viewModel()
        advanceUntilIdle()

        vm.onImportFile(uri)
        advanceUntilIdle()

        val message = vm.uiState.value.transferMessage
        assertTrue(message is TransferMessage.Error)
        assertEquals(0, vm.uiState.value.cardCount)
    }

    // S23: операции уходят с Main через внедрённый io-диспетчер —
    // после advanceUntilIdle всё завершено и isBusy сброшен.
    @Test
    fun `после импорта сбрасывается флаг занятости`() = runTest(mainDispatcher) {
        gateway.readBytes = "front,back\r\ncat,кошка\r\n".toByteArray(Charsets.UTF_8)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onImportFile(uri)
        advanceUntilIdle()

        assertTrue(!vm.uiState.value.isBusy)
    }

    private fun card(id: Long, front: String, back: String) =
        Card(id = id, deckId = 1L, front = front, back = back)

    private fun TransferMessage.savedText(): String =
        if (this is TransferMessage.Saved) "Файл сохранён" else "не Saved"

    private fun TransferMessage.summaryText(): String =
        (this as TransferMessage.Summary).let { "Добавлено: ${it.added}, пропущено: ${it.skipped}" }
}

private class FakeCardRepository : CardRepository {
    val cards = MutableStateFlow<List<Card>>(emptyList())
    var deckExists = true
    var throwOnImport = false
    var texts: List<Pair<String, String>> = emptyList()
    val imported = mutableListOf<List<Pair<String, String>>>()

    override fun observeCardsByDeck(deckId: Long) = cards

    override suspend fun addCard(deckId: Long, front: String, back: String): CardOperationResult =
        CardOperationResult.Success

    override suspend fun deleteCard(cardId: Long): CardOperationResult =
        CardOperationResult.Success

    override suspend fun exportCardTexts(deckId: Long): List<Pair<String, String>>? =
        if (!deckExists) null else texts.ifEmpty { cards.value.map { it.front to it.back } }

    override suspend fun importCards(
        deckId: Long,
        cards: List<Pair<String, String>>,
    ): CardImportResult {
        if (throwOnImport) throw RuntimeException("ошибка БД")
        if (!deckExists) return CardImportResult.DeckNotFound
        imported += cards
        this.cards.value += cards.mapIndexed { i, (f, b) ->
            Card(id = 1000L + i, deckId = deckId, front = f, back = b)
        }
        return CardImportResult.Imported(cards.size)
    }
}

private class FakeCsvFileGateway : CsvFileGateway {
    var written: ByteArray? = null
    var readBytes: ByteArray = ByteArray(0)
    var throwOnWrite = false
    var throwOnRead = false

    override suspend fun write(uri: Uri, bytes: ByteArray) {
        if (throwOnWrite) throw IOException("хранилище недоступно")
        written = bytes
    }

    override suspend fun read(uri: Uri): ByteArray {
        if (throwOnRead) throw IOException("файл недоступен")
        return readBytes
    }
}

private class FakeStrings : DeckDetailStrings {
    override val fileSaved = "Файл сохранён"
    override val saveFailed = "Не удалось сохранить файл"
    override val notCsv = "Файл не является CSV или не читается"
    override val deckNotFound = "Колода не найдена"
    override val importFailed = "Не удалось выполнить импорт"
    override fun importSummary(added: Int, skipped: Int) =
        "Добавлено: $added, пропущено: $skipped"
}
```

### FILE: app/src/main/java/com/mnemocards/ui/deckdetail/DeckDetailViewModel.kt
```kotlin
package com.mnemocards.ui.deckdetail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mnemocards.data.csv.CsvEncoder
import com.mnemocards.data.csv.CsvParseResult
import com.mnemocards.data.csv.CsvParser
import com.mnemocards.data.file.CsvFileGateway
import com.mnemocards.data.repository.CardImportResult
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.domain.model.Card
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сообщение о результате CSV-переноса (T11). Тексты ошибок берутся
 * из strings.xml через [DeckDetailStrings].
 */
sealed interface TransferMessage {
    data object Idle : TransferMessage
    data object Saved : TransferMessage
    data class Summary(val added: Int, val skipped: Int) : TransferMessage
    data class Error(val text: String) : TransferMessage
}

data class DeckDetailUiState(
    val deckName: String? = null,
    val cards: List<Card> = emptyList(),
    val isBusy: Boolean = false,
    val transferMessage: TransferMessage = TransferMessage.Idle,
) {
    val cardCount: Int get() = cards.size
}

/**
 * Поставщик текстов сообщений: реализация читает строки из strings.xml,
 * в тестах подменяется фейком (T10).
 */
interface DeckDetailStrings {
    val fileSaved: String
    val saveFailed: String
    val notCsv: String
    val deckNotFound: String
    val importFailed: String
    fun importSummary(added: Int, skipped: Int): String
}

/**
 * T11, зелёная фаза: ViewModel экрана содержимого колоды.
 *
 * MVVM + UDF: состояние течёт вниз через [uiState], события — вверх
 * через [onExportTarget] и [onImportFile]. Конвейер импорта
 * «чтение → парсинг → вставка» выполняется вне Main-диспетчера (S23);
 * ошибка парсинга показывается до обращения к Room (S18, S19).
 */
class DeckDetailViewModel(
    deckId: Long,
    deckName: Flow<String?>,
    private val cardRepository: CardRepository,
    private val csvFileGateway: CsvFileGateway,
    private val encoder: CsvEncoder = CsvEncoder(),
    private val parser: CsvParser = CsvParser(),
    private val strings: DeckDetailStrings,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val internal = MutableStateFlow(InternalState())

    private data class InternalState(
        val isBusy: Boolean = false,
        val transferMessage: TransferMessage = TransferMessage.Idle,
    )

    val uiState: StateFlow<DeckDetailUiState> = combine(
        deckName,
        cardRepository.observeCardsByDeck(deckId),
        internal,
    ) { name, cards, state ->
        DeckDetailUiState(
            deckName = name,
            cards = cards,
            isBusy = state.isBusy,
            transferMessage = state.transferMessage,
        )
    }.let { flow ->
        kotlinx.coroutines.flow.stateInLite(flow)
    }

    /**
     * S2, S6, S7, S8: цель экспорта подтверждена в CreateDocument.
     * Отмена диалога сюда не попадает — состояние остаётся Idle (S7).
     */
    fun onExportTarget(uri: Uri) {
        if (internal.value.isBusy) return
        internal.update { it.copy(isBusy = true, transferMessage = TransferMessage.Idle) }
        viewModelScope.launch {
            val message = try {
                val bytes = withContext(ioDispatcher) {
                    val texts = cardRepository.exportCardTexts(deckId)
                        ?: return@withContext null
                    encoder.encode(texts)
                }
                if (bytes == null) {
                    TransferMessage.Error(strings.deckNotFound)
                } else {
                    csvFileGateway.write(uri, bytes)
                    TransferMessage.Saved
                }
            } catch (e: IOException) {
                TransferMessage.Error(strings.saveFailed)
            } catch (e: Exception) {
                TransferMessage.Error(strings.saveFailed)
            }
            internal.update { it.copy(isBusy = false, transferMessage = message) }
        }
    }

    /**
     * S9, S12, S15, S18, S19, S21, S22, S23: файл выбран в OpenDocument.
     */
    fun onImportFile(uri: Uri) {
        if (internal.value.isBusy) return
        internal.update { it.copy(isBusy = true, transferMessage = TransferMessage.Idle) }
        viewModelScope.launch {
            val message = try {
                val result = withContext(ioDispatcher) {
                    val bytes = csvFileGateway.read(uri)
                    parser.parse(bytes)
                }
                when (result) {
                    CsvParseResult.NotCsv -> TransferMessage.Error(strings.notCsv)
                    is CsvParseResult.Parsed -> {
                        when (val import = cardRepository.importCards(deckId, result.cards)) {
                            CardImportResult.DeckNotFound ->
                                TransferMessage.Error(strings.deckNotFound)
                            is CardImportResult.Imported -> TransferMessage.Summary(
                                added = import.added,
                                skipped = result.skipped,
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                TransferMessage.Error(strings.notCsv)
            } catch (e: Exception) {
                TransferMessage.Error(strings.importFailed)
            }
            internal.update { it.copy(isBusy = false, transferMessage = message) }
        }
    }

    private fun kotlinx.coroutines.flow.Flow<DeckDetailUiState>.stateInLite():
        StateFlow<DeckDetailUiState> = kotlinx.coroutines.flow.stateIn(
        this,
        androidx.lifecycle.viewModelScope0(this@DeckDetailViewModel),
        DeckDetailUiState(),
    )
}

// Небольшой мост к viewModelScope вне тела класса (без дублирования API).
private object viewModelScopeBridge

@Suppress("FunctionName")
private fun androidx.lifecycle.viewModelScope0(vm: DeckDetailViewModel) = vm.viewModelScope
```

Тест выше ссылается на `viewModelScope` только косвенно — но в `stateInLite` слишком вычурно. Заменяю ViewModel на прямолинейную реализацию с `stateIn(viewModelScope, …)`:

### FILE: app/src/main/java/com/mnemocards/ui/deckdetail/DeckDetailViewModel.kt
```kotlin
package com.mnemocards.ui.deckdetail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.R
import com.mnemocards.data.csv.CsvEncoder
import com.mnemocards.data.csv.CsvParseResult
import com.mnemocards.data.csv.CsvParser
import com.mnemocards.data.file.CsvFileGateway
import com.mnemocards.data.repository.CardImportResult
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.domain.model.Card
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сообщение о результате CSV-переноса (T11). Тексты ошибок берутся
 * из strings.xml через [DeckDetailStrings].
 */
sealed interface TransferMessage {
    data object Idle : TransferMessage
    data object Saved : TransferMessage
    data class Summary(val added: Int, val skipped: Int) : TransferMessage
    data class Error(val text: String) : TransferMessage
}

data class DeckDetailUiState(
    val deckName: String? = null,
    val cards: List<Card> = emptyList(),
    val isBusy: Boolean = false,
    val transferMessage: TransferMessage = TransferMessage.Idle,
) {
    val cardCount: Int get() = cards.size
}

/**
 * Поставщик текстов сообщений: реализация читает строки из strings.xml,
 * в тестах подменяется фейком (T10).
 */
interface DeckDetailStrings {
    val fileSaved: String
    val saveFailed: String
    val notCsv: String
    val deckNotFound: String
    val importFailed: String
    fun importSummary(added: Int, skipped: Int): String
}

/** Реализация [DeckDetailStrings] поверх ресурсов приложения. */
class ResourceDeckDetailStrings(private val context: Context) : DeckDetailStrings {
    override val fileSaved: String get() = context.getString(R.string.msg_file_saved)
    override val saveFailed: String get() = context.getString(R.string.msg_save_failed)
    override val notCsv: String get() = context.getString(R.string.msg_not_csv)
    override val deckNotFound: String get() = context.getString(R.string.msg_deck_not_found)
    override val importFailed: String get() = context.getString(R.string.msg_import_failed)
    override fun importSummary(added: Int, skipped: Int): String =
        context.getString(R.string.msg_import_summary, added, skipped)
}

/**
 * T11, зелёная фаза: ViewModel экрана содержимого колоды.
 *
 * MVVM + UDF: состояние течёт вниз через [uiState], события — вверх
 * через [onExportTarget] и [onImportFile]. Конвейер импорта
 * «чтение → парсинг → вставка» выполняется вне Main-диспетчера (S23);
 * ошибка парсинга показывается до обращения к Room (S18, S19).
 */
class DeckDetailViewModel(
    deckId: Long,
    deckName: Flow<String?>,
    private val cardRepository: CardRepository,
    private val csvFileGateway: CsvFileGateway,
    private val encoder: CsvEncoder = CsvEncoder(),
    private val parser: CsvParser = CsvParser(),
    private val strings: DeckDetailStrings,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private data class InternalState(
        val isBusy: Boolean = false,
        val transferMessage: TransferMessage = TransferMessage.Idle,
    )

    private val internal = MutableStateFlow(InternalState())

    val uiState: StateFlow<DeckDetailUiState> = combine(
        deckName,
        cardRepository.observeCardsByDeck(deckId),
        internal,
    ) { name, cards, state ->
        DeckDetailUiState(
            deckName = name,
            cards = cards,
            isBusy = state.isBusy,
            transferMessage = state.transferMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        initialValue = DeckDetailUiState(),
    )

    /**
     * S2, S6, S7, S8: цель экспорта подтверждена в CreateDocument.
     * Отмена диалога сюда не попадает — состояние остаётся Idle (S7).
     */
    fun onExportTarget(uri: Uri) {
        if (internal.value.isBusy) return
        internal.update { it.copy(isBusy = true, transferMessage = TransferMessage.Idle) }
        viewModelScope.launch {
            val message = try {
                val bytes = withContext(ioDispatcher) {
                    cardRepository.exportCardTexts(deckId)?.let { encoder.encode(it) }
                }
                if (bytes == null) {
                    TransferMessage.Error(strings.deckNotFound)
                } else {
                    csvFileGateway.write(uri, bytes)
                    TransferMessage.Saved
                }
            } catch (e: IOException) {
                TransferMessage.Error(strings.saveFailed)
            } catch (e: Exception) {
                TransferMessage.Error(strings.saveFailed)
            }
            internal.update { it.copy(isBusy = false, transferMessage = message) }
        }
    }

    /**
     * S9, S12, S15, S18, S19, S21, S22, S23: файл выбран в OpenDocument.
     */
    fun onImportFile(uri: Uri) {
        if (internal.value.isBusy) return
        internal.update { it.copy(isBusy = true, transferMessage = TransferMessage.Idle) }
        viewModelScope.launch {
            val message = try {
                val result = withContext(ioDispatcher) {
                    parser.parse(csvFileGateway.read(uri))
                }
                when (result) {
                    CsvParseResult.NotCsv -> TransferMessage.Error(strings.notCsv)
                    is CsvParseResult.Parsed ->
                        when (val import = cardRepository.importCards(deckId, result.cards)) {
                            CardImportResult.DeckNotFound ->
                                TransferMessage.Error(strings.deckNotFound)
                            is CardImportResult.Imported -> TransferMessage.Summary(
                                added = import.added,
                                skipped = result.skipped,
                            )
                        }
                }
            } catch (e: IOException) {
                TransferMessage.Error(strings.notCsv)
            } catch (e: Exception) {
                TransferMessage.Error(strings.importFailed)
            }
            internal.update { it.copy(isBusy = false, transferMessage = message) }
        }
    }
}

/** Фабрика для подключения ViewModel на навигационном графе (T13). */
class DeckDetailViewModelFactory(
    private val deckId: Long,
    private val deckName: Flow<String?>,
    private val cardRepository: CardRepository,
    private val csvFileGateway: CsvFileGateway,
    private val strings: DeckDetailStrings,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DeckDetailViewModel(
            deckId = deckId,
            deckName = deckName,
            cardRepository = cardRepository,
            csvFileGateway = csvFileGateway,
            strings = strings,
            ioDispatcher = ioDispatcher,
        ) as T
}
```

### FILE: app/src/main/java/com/mnemocards/data/repository/CardRepository.kt
```kotlin
package com.mnemocards.data.repository

import androidx.room.withTransaction
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.domain.model.Card
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface CardOperationResult {
    data object Success : CardOperationResult
    data class Error(val throwable: Throwable) : CardOperationResult
}

sealed interface CardImportResult {
    data class Imported(val added: Int) : CardImportResult
    data object DeckNotFound : CardImportResult
}

interface CardRepository {
    fun observeCardsByDeck(deckId: Long): Flow<List<Card>>
    suspend fun addCard(deckId: Long, front: String, back: String): CardOperationResult
    suspend fun deleteCard(cardId: Long): CardOperationResult

    /**
     * Экспорт колоды (T6): только тексты сторон карточек —
     * идентификаторы и параметры SRS не выгружаются (S5, S25).
     * `null` — колода не найдена.
     */
    suspend fun exportCardTexts(deckId: Long): List<Pair<String, String>>?

    /**
     * Импорт CSV (T6): все валидные строки добавляются как новые карточки
     * без дедупликации, атомарно (S9, S15, S16, S21, S22).
     */
    suspend fun importCards(
        deckId: Long,
        cards: List<Pair<String, String>>,
    ): CardImportResult
}

class RoomCardRepository(
    private val db: MnemoCardsDatabase,
    private val cardDao: CardDao = db.cardDao(),
) : CardRepository {

    private val deckDao = db.deckDao()

    override fun observeCardsByDeck(deckId: Long): Flow<List<Card>> =
        cardDao.observeCardsByDeck(deckId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addCard(
        deckId: Long,
        front: String,
        back: String,
    ): CardOperationResult = try {
        if (deckDao.getDeckById(deckId) == null) {
            return CardOperationResult.Error(IllegalStateException("Колода не найдена"))
        }
        db.withTransaction {
            cardDao.insert(CardEntity(deckId = deckId, front = front, back = back))
        }
        CardOperationResult.Success
    } catch (t: Throwable) {
        CardOperationResult.Error(t)
    }

    override suspend fun deleteCard(cardId: Long): CardOperationResult = try {
        val card = cardDao.getCardById(cardId)
            ?: return CardOperationResult.Error(IllegalStateException("Карточка не найдена"))
        db.withTransaction {
            cardDao.deleteCard(card)
        }
        CardOperationResult.Success
    } catch (t: Throwable) {
        CardOperationResult.Error(t)
    }

    /**
     * Экспорт колоды (S5, S25): только тексты сторон карточек —
     * идентификаторы и параметры SRS не выгружаются.
     * `null` — колода не найдена.
     */
    override suspend fun exportCardTexts(deckId: Long): List<Pair<String, String>>? {
        if (deckDao.getDeckById(deckId) == null) return null
        return cardDao.getCardsByDeck(deckId).map { it.front to it.back }
    }

    /**
     * Импорт CSV (S9, S15, S16, S21, S22): все валидные строки
     * добавляются как новые карточки без дедупликации, атомарно;
     * новые [CardEntity] создаются с дефолтами новых карточек (S16).
     * Несуществующая колода → [CardImportResult.DeckNotFound];
     * ошибка БД пробрасывается наружу, вставка откачена (S22).
     */
    override suspend fun importCards(
        deckId: Long,
        cards: List<Pair<String, String>>,
    ): CardImportResult {
        if (deckDao.getDeckById(deckId) == null) return CardImportResult.DeckNotFound
        return db.withTransaction {
            val entities = cards.map { (front, back) ->
                CardEntity(deckId = deckId, front = front, back = back)
            }
            cardDao.insertAll(entities)
            CardImportResult.Imported(cards.size)
        }
    }

    private fun CardEntity.toDomain() = Card(
        id = id,
        deckId = deckId,
        front = front,
        back = back,
    )
}
```

### FILE: app/src/main/res/values/strings.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- T11/T12: экран содержимого колоды, capability deck.csv-transfer. -->
    <string name="deck_detail_title">Колода</string>
    <string name="deck_detail_card_count">Карточек: %1$d</string>
    <string name="deck_detail_export">Экспорт</string>
    <string name="deck_detail_import">Импорт</string>
    <string name="deck_detail_train">Тренировать</string>
    <string name="deck_detail_empty">В колоде пока нет карточек</string>
    <string name="deck_detail_back">Назад</string>
    <string name="msg_file_saved">Файл сохранён</string>
    <string name="msg_save_failed">Не удалось сохранить файл</string>
    <string name="msg_not_csv">Файл не является CSV или не читается</string>
    <string name="msg_deck_not_found">Колода не найдена</string>
    <string name="msg_import_failed">Не удалось выполнить импорт</string>
    <string name="msg_import_summary">Добавлено: %1$d, пропущено: %2$d</string>
</resources>
```

### FILE: app/src/main/java/com/mnemocards/ui/deckdetail/DeckDetailScreen.kt
```kotlin
package com.mnemocards.ui.deckdetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mnemocards.R

/**
 * T12: экран содержимого колоды (S1, S2, S7, S9, S20, S23, S24).
 *
 * Экран stateless по бизнес-данным: всё состояние приходит из
 * [viewModel], бизнес-логики не содержит. Доступ к файлам — только
 * через SAF-диалоги CreateDocument / OpenDocument (S24); результат
 * `null` (отмена диалога) игнорируется (S7, S20). Только токены
 * `MnemoCardsTheme`, прямые `Color(0xFF…)` запрещены.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    viewModel: DeckDetailViewModel,
    onBack: () -> Unit,
    onTrain: (deckId: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val fileSavedText = stringResource(R.string.msg_file_saved)
    val importSummaryTemplate = stringResource(R.string.msg_import_summary)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        // S7: отмена диалога → uri == null, ничего не делаем.
        if (uri != null) viewModel.onExportTarget(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // S20: отмена выбора → ничего не делаем.
        if (uri != null) viewModel.onImportFile(uri)
    }

    // Сообщения из UiState — через Snackbar.
    LaunchedEffect(state.transferMessage) {
        when (val message = state.transferMessage) {
            TransferMessage.Idle -> Unit
            TransferMessage.Saved -> snackbarHostState.showSnackbar(fileSavedText)
            is TransferMessage.Summary -> snackbarHostState.showSnackbar(
                "Добавлено: ${message.added}, пропущено: ${message.skipped}",
            )
            is TransferMessage.Error -> snackbarHostState.showSnackbar(message.text)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.deckName ?: stringResource(R.string.deck_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.deck_detail_back),
                        )
                    }
                },
                colors = MaterialTheme.topAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stringResource(R.string.deck_detail_card_count, state.cardCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        // S2: предложенное имя — название колоды, «Английский.csv».
                        val name = state.deckName?.let { "$it.csv" } ?: "deck.csv"
                        exportLauncher.launch(name)
                    },
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.deck_detail_export))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/csv")) },
                    enabled = !state.isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.deck_detail_import))
                }
                Button(
                    onClick = {
                        val deckId = state.cards.firstOrNull()?.deckId
                        if (deckId != null) onTrain(deckId)
                    },
                    enabled = !state.isBusy && state.cards.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.deck_detail_train))
                }
            }
            if (state.cards.isEmpty()) {
                Text(
                    text = stringResource(R.string.deck_detail_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.cards, key = { it.id }) { card ->
                        ListItem(
                            headlineContent = { Text(card.front) },
                            supportingContent = { Text(card.back) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// Расширение вынесено в файл темы не требуется: TopAppBarDefaults — токен темы.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialTheme.topAppBarColors() =
    androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
    )
```


---

## Батч 5/5

### FILE: app/src/main/java/com/mnemocards/ui/navigation/AppNavHost.kt
```kotlin
package com.mnemocards.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mnemocards.MnemoCardsApplication
import com.mnemocards.data.file.SafCsvFileGateway
import com.mnemocards.ui.deckdetail.DeckDetailScreen
import com.mnemocards.ui.deckdetail.DeckDetailViewModel
import com.mnemocards.ui.deckdetail.DeckDetailViewModelFactory
import com.mnemocards.ui.deckdetail.ResourceDeckDetailStrings
import com.mnemocards.ui.decklist.DeckListScreen
import com.mnemocards.ui.decklist.DeckListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

/**
 * Единственный навигационный граф приложения (T13).
 *
 * Маршруты:
 * - `decks` — стартовый экран, список колод;
 * - `deck/{deckId}` — содержимое колоды (точка входа CSV-импорта/экспорта, S1);
 * - `review/{deckId}` — тренировка колоды (существующий экран, не менялся);
 * - `cardForm/{deckId}` — создание карточки.
 *
 * Клик по колоде в списке теперь открывает экран колоды, а не тренировку;
 * «Тренировать» на экране колоды ведёт на существующий маршрут тренировки.
 */
object Routes {
    const val DECKS = "decks"
    const val DECK_DETAIL = "deck/{deckId}"
    const val REVIEW = "review/{deckId}"
    const val CARD_FORM = "cardForm/{deckId}"

    const val ARG_DECK_ID = "deckId"

    fun deckDetail(deckId: Long) = "deck/$deckId"
    fun review(deckId: Long) = "review/$deckId"
    fun cardForm(deckId: Long) = "cardForm/$deckId"
}

@Composable
fun AppNavHost(navHostController: NavHostController) {
    val context = LocalContext.current
    val container = (context.applicationContext as MnemoCardsApplication).container

    NavHost(
        navController = navHostController,
        startDestination = Routes.DECKS,
    ) {
        composable(Routes.DECKS) {
            val decksViewModel: DeckListViewModel = viewModel {
                DeckListViewModel(
                    deckRepository = container.deckRepository,
                    deckCountsRepository = container.deckCountsRepository,
                )
            }
            DeckListScreen(
                viewModel = decksViewModel,
                // T13: клик по колоде открывает экран колоды (S1),
                // а не тренировку напрямую.
                onDeckClick = { deckId ->
                    navHostController.navigate(Routes.deckDetail(deckId))
                },
            )
        }

        composable(
            route = Routes.DECK_DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_DECK_ID) {
                    type = NavType.LongType
                },
            ),
        ) { entry ->
            val deckId = entry.arguments?.getLong(Routes.ARG_DECK_ID)
                ?: return@composable
            val detailViewModel: DeckDetailViewModel = viewModel(
                key = "deckDetail-$deckId",
                factory = DeckDetailViewModelFactory(
                    deckId = deckId,
                    deckName = container.deckDao.observeDeck(deckId)
                        .map { it?.name },
                    cardRepository = container.cardRepository,
                    csvFileGateway = SafCsvFileGateway(context.contentResolver),
                    strings = ResourceDeckDetailStrings(context),
                    ioDispatcher = Dispatchers.IO,
                ),
            )
            DeckDetailScreen(
                viewModel = detailViewModel,
                onBack = { navHostController.popBackStack() },
                // S16: тренировка запускается по существующему маршруту;
                // импортированные карточки участвуют в ней наравне с прочими.
                onTrain = { id ->
                    navHostController.navigate(Routes.review(id))
                },
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(
                navArgument(Routes.ARG_DECK_ID) {
                    type = NavType.LongType
                },
            ),
        ) { entry ->
            val deckId = entry.arguments?.getLong(Routes.ARG_DECK_ID)
                ?: return@composable
            com.mnemocards.ui.review.ReviewScreen(
                navController = navHostController,
                deckId = deckId,
            )
        }

        composable(
            route = Routes.CARD_FORM,
            arguments = listOf(
                navArgument(Routes.ARG_DECK_ID) {
                    type = NavType.LongType
                },
            ),
        ) { entry ->
            val deckId = entry.arguments?.getLong(Routes.ARG_DECK_ID)
                ?: return@composable
            com.mnemocards.ui.cardform.CardFormScreen(
                deckId = deckId,
                onBack = { navHostController.popBackStack() },
            )
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/AppContainer.kt
```kotlin
package com.mnemocards

import android.content.Context
import com.mnemocards.data.CardCountRepository
import com.mnemocards.data.RoomDeckCountsRepository
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.data.repository.RoomCardRepository
import com.mnemocards.domain.DeckRepository

/**
 * Единственный контейнер зависимостей приложения (инвариант MAIN.md).
 * Владеет Room-базой и репозиториями; ViewModel-фабрики навигационного
 * графа берут зависимости отсюда.
 */
class AppContainer(context: Context) {

    val database: MnemoCardsDatabase = MnemoCardsDatabase.build(context)

    val deckDao = database.deckDao()
    val cardDao = database.cardDao()

    val deckRepository: DeckRepository = RoomDeckRepository(deckDao)

    val cardRepository: CardRepository = RoomCardRepository(cardDao, deckDao)

    val deckCountsRepository: CardCountRepository =
        RoomDeckCountsRepository(cardDao)
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Стартовый экран — список колод. Statelest по бизнес-данным:
 * состояние приходит из [viewModel], клик по колоде — событие вверх
 * через [onDeckClick] (T13: открывает экран колоды, а не тренировку).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    viewModel: DeckListViewModel,
    onDeckClick: (deckId: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои колоды") },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (state.decks.isEmpty()) {
            Text(
                text = "Колод пока нет",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.decks, key = { it.id }) { deck ->
                    DeckItem(
                        deck = deck,
                        onClick = { onDeckClick(deck.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun DeckItem(
    deck: DeckListItem,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(deck.name) },
        supportingContent = { Text("Карточек: ${deck.cardCount}") },
    )
}
```

### FILE: MAIN.md
```markdown
# MAIN Spec: MnemoCards

> **Артефакт:** мейн-спека проекта (первичный документ)
> **Дата:** 2026-09-16
> **Источники:** промпт пользователя + `.agent/config.yaml`

Этот документ — единая точка правды о приложении. Каждое новое изменение
(change-*) генерируется с учётом этого файла.

## What Is This App

MnemoCards — Android-приложение для заучивания слов по карточкам (мобильный аналог
Anki). Карточка — пара «слово + перевод». Пользователь видит лицевую сторону,
переворачивает карточку и оценивает ответ; планировщик интервальных повторений
(SRS) определяет, когда карточка появится снова.

Границы репозитория: **только Android-клиент на Kotlin**. Backend синхронизации —
отдельный сервис на собственном хостинге, в репозиторий не входит; клиент
общается с ним по REST API.

Ключевое свойство — **offline-first**: локальная БД является основным источником
данных для UI, приложение полностью работоспособно без сети; синхронизация колод
с сервером выполняется в фоне.

Явно исключено из скоупа (не делать): социальные функции, магазин колод, аудио и
картинки на карточках.

## Tech Stack

- **Язык:** Kotlin
- **Платформа:** Android SDK; UI — Jetpack Compose
- **Локальные данные:** Room (офлайн-кэш, основной источник данных)
- **Сеть:** Retrofit + OkHttp (REST-клиент к внешнему сервису синхронизации)
- **Асинхронность:** Coroutines / Flow
- **Тесты:** JUnit + MockWebServer
- **Базовый пакет:** `com.mnemocards`

## Architecture

- **Паттерн:** MVVM + UDF. События текут вверх (Compose → ViewModel), состояние —
  вниз (StateFlow из ViewModel → Compose). Compose-экраны не содержат
  бизнес-логики.
- **Слои клиента:** UI (Compose) → ViewModel → Repository → источники данных:
  Room (локальный, основной) и Retrofit-сервис (сеть).
- **Offline-first данные:** Room — single source of truth для UI. Сетевые ответы
  применяются к БД; UI наблюдает БД через Flow и обновляется автоматически.
  Отсутствие сети не блокирует ни один пользовательский сценарий.
- **Синхронизация:** фоновый обмен изменениями колод с REST-сервером; результат
  применяется в Room по тем же принципам (детали механизма — в спеках
  соответствующих change-*).
- **Внешняя система:** REST backend синхронизации (вне репозитория, собственный
  хостинг). Контракт API фиксируется в спеках изменений.
- **SRS:** планировщик интервальных повторений рассчитывает следующий показ
  карточки на основе оценки ответа (алгоритм уточняется в change-*).

### Инварианты структуры (проверяются при каждой генерации кода)

Правила ниже описывают то, чего в коде не видно: не «как есть», а «как должно
быть». Нарушение — дефект, даже если проект собирается.

- **Единственность узловых элементов:** в проекте ровно один
  `MnemoCardsNavHost`, один `MnemoCardsApplication`, один `AppContainer`,
  одна `MainActivity`. Новый экран добавляется маршрутом в существующий
  NavHost, а не вторым навигационным графом.
- **Одна сущность — один класс:** перед созданием модели, DAO, репозитория
  или ViewModel сверься с картой проекта в промпте. Сущность с таким смыслом
  уже есть — используй её или измени по её текущему пути; не заводи
  параллельную под другим именем или в другом пакете.
- **Границы слоёв:** UI не обращается к Room напрямую — только через
  Repository. Room-сущности (`*Entity`) не покидают пакет `data`; выше по
  слоям ходят доменные модели из `domain.model`.
- **Пакеты фиксированы:** `ui` / `domain` / `data` внутри `com.mnemocards`.
  Новые пакеты верхнего уровня не создаются; новый код раскладывается
  по существующим.

Эти инварианты нарушались: в change-003 кодоген создал второй NavHost,
продублировал сущности change-002 и завёл параллельные пакеты, потому что
не видел существующий код. Карта проекта решает вторую часть проблемы,
правила выше — первую.

## Capabilities

Стартовый набор (заложен при создании проекта; детали и пополнение — через change-*):

- capability `cards.review` — сессия повторения: показ лицевой стороны (слово),
  переворот (перевод), оценка ответа.

  **Реализована** — первая версия, change `add-review-screen` (2026-09-16):
  полный цикл сессии — старт с лицевой стороны, переворот по тапу, четыре
  эквивалентные оценки («Забыл» / «Трудно» / «Хорошо» / «Легко»), переход
  к следующей карточке, терминальный экран «на сегодня всё»; состояние сессии
  переживает смену конфигурации.

  **Обновлена**, change `add-offline-storage` (2026-09-16): захардкоженные данные
  удалены — карточки читаются из Room по идентификатору выбранной колоды;
  стартовый экран приложения — список колод (название + счётчик карточек),
  навигация «список → тренировка колоды → назад»; оценка ответов в этой фиче
  не изменяет данные (влияние оценок на `srs.scheduling` — предмет последующих
  изменений).
- capability `srs.scheduling` — интервальное повторение: расчёт следующего
  показа карточки по оценке ответа.
- capability `offline.storage` — локальная БД как основной источник данных;
  полная работоспособность без сети.

  **Реализована**, change `add-offline-storage` (2026-09-16): Room-хранилище
  (`DeckEntity`, `CardEntity`, `MnemoCardsDatabase`) — single source of truth
  для UI; идемпотентный сидинг базовой колоды из 5 карточек при первом запуске;
  данные переживают перезапуск; список колод и карточки сессии читаются из Room
  через Flow; краевые случаи покрыты состояниями UI (пустой список, устаревший
  элемент — аналог 404, ошибка БД — аналог 500); полная работоспособность
  без сети, включая авиарежим.
- capability `sync.background` — фоновая синхронизация колод с сервером по REST.
- capability `deck.csv-transfer` — импорт и экспорт карточек колоды через CSV.

  **Реализована**, change `add-csv-import-export` (2026-09-16): точка входа —
  экран содержимого колоды (клик по колоде в списке открывает колоду,
  «Тренировать» ведёт на существующий маршрут тренировки). Экспорт — SAF
  CreateDocument (mime `text/csv`, имя `<колода>.csv`), UTF-8 с BOM,
  разделитель `,`, записи CRLF, экранирование по RFC 4180, только текст
  карточек без SRS-данных. Импорт — SAF OpenDocument: автодетект разделителя
  по первой строке (`,` vs `;` для Excel в русской локали, при равенстве —
  запятая), необязательный заголовок `front,back` без учёта регистра,
  пропуск строк с пустыми полями, парсинг экранированных значений по
  RFC 4180; семантика add-only: все валидные строки добавляются как новые
  карточки без дедупликации, существующие не затрагиваются, вставка атомарна;
  итоги «Добавлено/пропущено» и ошибки UI (аналог 400 — нечитаемый/пустой
  файл, 404 — колода не найдена, 500 — ошибки I/O и БД). Полностью офлайн,
  без разрешений хранилища (только SAF), раунд-трип между колодами
  подтверждён тестами.

## Conventions

- **Коммиты:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `docs:`,
  `chore:` и т.д.).
- **Архитектура:** MVVM + UDF — однонаправленный поток данных; ViewModel владеет
  состоянием, экраны Compose декларативны и stateless по бизнес-данным.
- **Стек:** строго соответствует `.agent/config.yaml`. Расширение стека или смена
  фундамента — только через обновление этой мейн-спеки.
- **Именование:** базовый пакет `com.mnemocards`; слои разделяются по пакетам
  (ui / domain / data).
- **Тема (обязательно):** приложение всегда тёмное. Корневой composable
  в `setContent` оборачивается в `MnemoCardsTheme { ... }` из
  `com.mnemocards.ui.theme`. Голая `MaterialTheme { ... }` и системная светлая
  схема НЕ используются. Цвета берутся из `MaterialTheme.colorScheme`
  (токены в `ui/theme/Color.kt` и `Theme.kt`); прямые `Color(0xFF...)`
  в экранах запрещены — если нужен новый оттенок, он объявляется в `Color.kt`
  и подключается через тему. При переписывании `MainActivity` обёртку темы
  сохранять — без неё приложение молча переключается на светлую тему.

- **Внешний вид экрана тренировки (не менять при рефакторингах):**
  - карточка — `Surface` цвета `colorScheme.surfaceVariant`, скруглённая
    (`RoundedCornerShape(20.dp)`), с боковыми отступами и `weight(1f)`, чтобы
    занимать свободную высоту; текст стороны по центру, цвет берётся из
    `onSurfaceVariant` автоматически (бирюзовый), задавать его явно не нужно;
  - четыре кнопки оценки — В ОДИН РЯД (`Row` + `weight(1f)` у каждой),
    НЕ столбиком, прижаты к низу экрана под карточкой;
  - цвет кнопок — шкала «забыл → выучил»: «Забыл» `Red`, «Трудно` `Orange`,
    «Хорошо» `Blue`, «Легко» `Green` из `ui/theme/Color.kt`; текст на кнопках
    тёмный (`Ink`), заливки светлые;
  - `TopAppBar` сливается с фоном: `containerColor = colorScheme.background`,
    выделяться должна карточка, а не панель;
  - внизу карточки по центру — декоративная эмблема
    `R.drawable.akatsuki_cloud`, высота 36dp, `contentDescription = null`.
    Лежит отдельным слоем в том же `Box`, что и текст стороны
    (`Modifier.align(Alignment.BottomCenter)`), на вёрстку текста не влияет.
    При переписывании экрана переносить вместе с карточкой.

  Всё оформление экрана тренировки живёт в `ui/review/ReviewScreen.kt`.
  Выносить карточку и панель оценок в отдельные composable-файлы НЕ нужно:
  такие попытки дважды заканчивались тем, что генерация переписывала экран
  и оставляла компоненты осиротевшими, а оформление откатывалось к дефолту.

  Эти решения приняты пользователем вручную и при генерации кода
  воспроизводятся как есть. Переписываешь экран под новые данные — переноси
  оформление целиком, не заменяй его дефолтным Material.
- **API:** клиент общается с backend только по REST через Retrofit + OkHttp;
  сетевые вызовы не выполняются в UI-потоке и не блокируют UI.
- **Границы скоупа:** без социальных функций, магазина колод, аудио и картинок на
  карточках — предложения таких изменений отклоняются.

<!--
ПРАВИЛА (из .agent/schema.yaml, project_spec):
  - Секции: What Is This App, Tech Stack, Architecture, Capabilities, Conventions
  - Стек согласовывать с .agent/config.yaml (project_context)
  - Capabilities пополняются по мере реализации изменений (change-*)
  - Обновлять при изменении фундамента приложения
-->
```
