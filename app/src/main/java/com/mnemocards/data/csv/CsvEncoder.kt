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
            // Заголовок фиксированный и не требует экранирования.
            append(HEADER)
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
