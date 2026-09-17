package com.mnemocards.data.csv

import java.nio.ByteBuffer
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

    fun parse(bytes: ByteArray): CsvParseResult {
        val text = decodeStrictUtf8(bytes) ?: return CsvParseResult.NotCsv

        // S10: BOM пропускается.
        val body = if (text.startsWith(BOM)) text.removePrefix(BOM) else text

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

    /**
     * Строгая UTF-8-декодация одним вызовом: некорректный байт —
     * REPORT → исключение → null (S18).
     */
    private fun decodeStrictUtf8(bytes: ByteArray): String? = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

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
        /** UTF-8 BOM как строка: removePrefix принимает CharSequence. */
        const val BOM = "\uFEFF"
        val HEADER_COLUMNS = listOf("front", "back")
    }
}
