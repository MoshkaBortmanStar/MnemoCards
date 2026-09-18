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
import org.robolectric.annotation.Config

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
@Config(sdk = [35])
class DeckContentRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var repository: CardRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCardRepository(db.deckDao(), db.cardDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun createDeck(name: String = "Английский"): String {
        val id = java.util.UUID.randomUUID().toString()
        db.deckDao().insertDeck(DeckEntity(id = id, name = name))
        return id
    }

    private suspend fun cardsOf(deckId: String): List<CardEntity> =
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
        // карточек одинаковые (дефолтные) SRS-параметры, срок — «сразу».
        val manual = cards.first { it.front == "cat" }
        val imported = cards.first { it.front == "dog" }
        assertEquals(manual.srsStateSignature(), imported.srsStateSignature())
        val now = System.currentTimeMillis()
        assertTrue(cards.all { it.dueDate in 0..now })
    }

    private fun CardEntity.srsStateSignature() = listOf(
        intervalDays, easinessFactor, repetitionNumber,
    )

    // S21: устаревшая колода → DeckNotFound, ничего не вставлено.
    @Test
    fun `S21 несуществующая колода даёт DeckNotFound`() = runTest {
        val ghost = java.util.UUID.randomUUID().toString()
        val result = repository.importCards(ghost, listOf("cat" to "кошка"))

        assertEquals(CardImportResult.DeckNotFound, result)
        assertTrue(db.cardDao().observeCardsByDeck(ghost).first().isEmpty())
    }

    // S22: ошибка DAO пробрасывается, вставка атомарно откачена.
    @Test
    fun `S22 ошибка DAO пробрасывается и вставка откачена`() = runTest {
        val deckId = createDeck()
        repository.importCards(deckId, listOf("cat" to "кошка"))
        // Room генерирует реализацию @Transaction-метода в CardDao_Impl,
        // поэтому перехватывать нужно сам insertImportedCards, а не insertAll.
        val failing = RoomCardRepository(
            db.deckDao(),
            object : CardDao by db.cardDao() {
                override suspend fun insertImportedCards(
                    deckId: String,
                    cards: List<CardEntity>,
                ): Boolean = throw RuntimeException("disk I/O error")
            },
        )

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

        // Порядок как на экране: DAO отдаёт новые сверху (createdAt DESC).
        assertEquals(
            listOf("hello, world" to "мир", "cat" to "кошка"),
            texts,
        )
    }
}
