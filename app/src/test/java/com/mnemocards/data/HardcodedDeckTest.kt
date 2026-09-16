package com.mnemocards.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-тесты захардкоженной колоды по сценариям S1, S10, S11
 * из spec-bdd.md (change `add-review-screen`). Задача T2.
 */
class HardcodedDeckTest {

    /** S1: колода содержит ровно 5 карточек. */
    @Test
    fun `deck contains exactly 5 cards`() {
        assertEquals(5, HardcodedDeck.cards.size)
    }

    /** Каждая карточка — пара непустых строк. */
    @Test
    fun `every card is a pair of non-empty strings`() {
        HardcodedDeck.cards.forEach { card ->
            assertTrue("front must be non-empty", card.front.isNotEmpty())
            assertTrue("back must be non-empty", card.back.isNotEmpty())
        }
    }

    /** Колода — упорядоченный список различных карточек (фиксированный порядок, S6). */
    @Test
    fun `all cards are distinct`() {
        assertEquals(HardcodedDeck.cards.size, HardcodedDeck.cards.toSet().size)
    }

    /** S10: в колоде есть карточки с латиницей. */
    @Test
    fun `deck includes latin script`() {
        assertTrue(
            HardcodedDeck.cards.any { card ->
                hasLatin(card.front) || hasLatin(card.back)
            },
        )
    }

    /** S10: в колоде есть карточки с кириллицей. */
    @Test
    fun `deck includes cyrillic script`() {
        assertTrue(
            HardcodedDeck.cards.any { card ->
                hasCyrillic(card.front) || hasCyrillic(card.back)
            },
        )
    }

    /** S10: в колоде есть карточки с CJK-символами. */
    @Test
    fun `deck includes cjk script`() {
        assertTrue(
            HardcodedDeck.cards.any { card ->
                hasCjk(card.front) || hasCjk(card.back)
            },
        )
    }

    /** S11: есть карточка со словом или переводом длиннее 40 символов. */
    @Test
    fun `deck has a card with word or translation longer than 40 characters`() {
        assertTrue(
            HardcodedDeck.cards.any { card ->
                card.front.length > LONG_TEXT_THRESHOLD || card.back.length > LONG_TEXT_THRESHOLD
            },
        )
    }

    private fun hasLatin(text: String): Boolean =
        text.any { it in 'a'..'z' || it in 'A'..'Z' }

    private fun hasCyrillic(text: String): Boolean =
        text.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

    private fun hasCjk(text: String): Boolean =
        text.any { it.code in CJK_UNIFIED_IDEOGRAPHS_START..CJK_UNIFIED_IDEOGRAPHS_END }

    private companion object {
        const val LONG_TEXT_THRESHOLD = 40
        const val CJK_UNIFIED_IDEOGRAPHS_START = 0x4E00
        const val CJK_UNIFIED_IDEOGRAPHS_END = 0x9FFF
    }
}
