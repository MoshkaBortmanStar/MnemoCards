package com.mnemocards.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardDaoTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao
    private lateinit var deckId: String

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        cardDao = db.cardDao()
        deckId = UUID.randomUUID().toString()
        deckDao.insertDeck(DeckEntity(id = deckId, name = "Английский базовый"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insert(front: String, back: String, createdAt: Long): String {
        val id = UUID.randomUUID().toString()
        cardDao.insertCard(
            CardEntity(
                id = id,
                deckId = deckId,
                front = front,
                back = back,
                createdAt = createdAt,
            ),
        )
        return id
    }

    @Test
    fun insertCard_storesRowWithUuid() = runTest {
        val id = insert("hello", "привет", 1000L)
        val card = cardDao.getCardById(id)
        assertNotNull(card)
        assertEquals("hello", card!!.front)
        assertEquals("привет", card.back)
    }

    @Test
    fun observeCardsByDeck_ordersNewestFirst() = runTest {
        insert("old", "старая", 1000L)
        insert("new", "новая", 3000L)
        insert("middle", "средняя", 2000L)

        val cards = cardDao.observeCardsByDeck(deckId).first()
        assertEquals(listOf("new", "middle", "old"), cards.map { it.front })
    }

    @Test
    fun updateCard_changesOnlyTargetRow_andCountStaysSame() = runTest {
        val target = insert("cat", "кошка", 1000L)
        val other = insert("dog", "собака", 2000L)

        val updated = cardDao.updateCard(
            cardDao.getCardById(target)!!.copy(front = "a cat")
        )
        assertEquals(1, updated)

        assertEquals("a cat", cardDao.getCardById(target)!!.front)
        assertEquals("dog", cardDao.getCardById(other)!!.front)

        val count = cardDao.observeCardCountByDeck(deckId).first()
        assertEquals(2, count)
    }

    @Test
    fun updateCard_returnsZeroForMissingRow() = runTest {
        val missing = CardEntity(
            id = UUID.randomUUID().toString(),
            deckId = deckId,
            front = "x",
            back = "y",
            createdAt = 1L,
        )
        assertEquals(0, cardDao.updateCard(missing))
    }

    @Test
    fun deleteCard_removesRow() = runTest {
        val id = insert("cat", "кошка", 1000L)
        assertEquals(1, cardDao.deleteCard(id))
        assertNull(cardDao.getCardById(id))
        assertEquals(0, cardDao.observeCardCountByDeck(deckId).first())
    }

    @Test
    fun duplicateCards_areStoredAsIndependentRows() = runTest {
        insert("cat", "кошка", 1000L)
        insert("cat", "кошка", 2000L)

        val cards = cardDao.observeCardsByDeck(deckId).first()
        assertEquals(2, cards.size)
        assertEquals(listOf("cat", "cat"), cards.map { it.front })
    }

    @Test
    fun longText_isStoredFully() = runTest {
        val longFront = "f".repeat(250)
        val longBack = "б".repeat(250)
        val id = insert(longFront, longBack, 1000L)

        val card = cardDao.getCardById(id)!!
        assertEquals(longFront, card.front)
        assertEquals(longBack, card.back)
    }
}
