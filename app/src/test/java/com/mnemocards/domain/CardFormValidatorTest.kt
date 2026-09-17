package com.mnemocards.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardFormValidatorTest {

    // S6: пустое одно поле — ошибка только у него.
    @Test
    fun blankFront_onlyFrontHasError() {
        val errors = CardFormValidator.validateCardInput(front = "", back = "собака")
        assertEquals(CardFormValidator.REQUIRED_MESSAGE, errors.front)
        assertNull(errors.back)
    }

    @Test
    fun blankBack_onlyBackHasError() {
        val errors = CardFormValidator.validateCardInput(front = "dog", back = "")
        assertEquals(CardFormValidator.REQUIRED_MESSAGE, errors.back)
        assertNull(errors.front)
    }

    // S7: оба поля пусты — две ошибки.
    @Test
    fun bothBlank_bothHaveErrors() {
        val errors = CardFormValidator.validateCardInput(front = "", back = "")
        assertEquals(CardFormValidator.REQUIRED_MESSAGE, errors.front)
        assertEquals(CardFormValidator.REQUIRED_MESSAGE, errors.back)
    }

    // S8: только пробелы и переводы строк — ошибки после trim.
    @Test
    fun whitespaceOnly_treatedAsBlank() {
        val errors = CardFormValidator.validateCardInput(front = "   ", back = " \n\t ")
        assertEquals(CardFormValidator.REQUIRED_MESSAGE, errors.front)
        assertEquals(CardFormValidator.REQUIRED_MESSAGE, errors.back)
    }

    // S13: очистка поля при редактировании — ошибка у очищенного поля.
    @Test
    fun clearedBackField_hasError() {
        val errors = CardFormValidator.validateCardInput(front = "cat", back = "")
        assertEquals(CardFormValidator.REQUIRED_MESSAGE, errors.back)
        assertNull(errors.front)
    }

    // Валидные значения — без ошибок.
    @Test
    fun validValues_noErrors() {
        val errors = CardFormValidator.validateCardInput(front = "cat", back = "кошка")
        assertNull(errors.front)
        assertNull(errors.back)
        assertEquals(true, errors.isEmpty)
    }

    // S10: длинный текст (200+ символов) валиден.
    @Test
    fun longText_isValid() {
        val longFront = "f".repeat(250)
        val longBack = "б".repeat(250)
        val errors = CardFormValidator.validateCardInput(front = longFront, back = longBack)
        assertNull(errors.front)
        assertNull(errors.back)
    }

    // Текст с пробелами по краям валиден (trim не режет содержимое).
    @Test
    fun paddedText_isValid() {
        val errors = CardFormValidator.validateCardInput(front = "  cat  ", back = " кошка ")
        assertNull(errors.front)
        assertNull(errors.back)
    }
}
