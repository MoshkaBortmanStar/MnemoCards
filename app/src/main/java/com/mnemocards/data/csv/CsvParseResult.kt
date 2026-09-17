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
