package com.mnemocards.ui.review

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Русская плюрализация подписей интервалов (S13).
 */
class IntervalLabelTest {

    @Test
    fun `S13 russian pluralization for days`() {
        assertEquals("1 день", formatInterval(1.days))
        assertEquals("3 дня", formatInterval(3.days))
        assertEquals("5 дней", formatInterval(5.days))
        assertEquals("11 дней", formatInterval(11.days))
        assertEquals("21 день", formatInterval(21.days))
    }

    @Test
    fun `S13 minutes are abbreviated`() {
        assertEquals("10 мин", formatInterval(10.minutes))
    }
}
