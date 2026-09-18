## Батч 1/7

### FILE: app/src/test/java/com/mnemocards/data/repository/DeckRepositoryTest.kt
```kotlin
package com.mnemocards.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model.
 * S6, S10, S13 — создание записей с UUID и признаками.
 * S11, S12, S13 — изменение записи выставляет isChanged и поднимает revision,
 * признаки переживают переоткрытие БД и не снимаются.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeckRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckRepository: RoomDeckRepository
    private lateinit var cardRepository: RoomCardRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.getDatabasePath("deck-repo-test.db").path).parentFile?.mkdirs()
        context.deleteDatabase("deck-repo-test.db")
        db = Room.databaseBuilder(
            context,
            MnemoCardsDatabase::class.java,
            "deck-repo-test.db",
        ).allowMainThreadQueries().build()
        deckRepository = RoomDeckRepository(db.deckDao())
        cardRepository = RoomCardRepository(db.cardDao())
    }

    @After
    fun tearDown() {
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("deck-repo-test.db")
    }

    // --- S6, S10, S13: создание колоды ---

    @Test
    fun createDeck_generatesValidUuid_andSyncFlags() = runTest {
        val deck = deckRepository.createDeck("English")

        assertTrue("id должен быть валидным UUID", isValidUuid(deck.id))

        val entity = db.deckDao().getDeckById(deck.id)
        assertNotNull("колода должна быть сохранена", entity)
        assertEquals("S10: isChanged = true при создании", true, entity!!.isChanged)
        assertEquals("S13: revision = 1 при создании", 1, entity.revision)
        assertEquals("isDeleted = false при создании", false, entity.isDeleted)
    }

    @Test
    fun createDeck_idsAreUnique() = runTest {
        val first = deckRepository.createDeck("English")
        val second = deckRepository.createDeck("Spanish")

        assertNotEquals("UUID колод должны быть уникальны", first.id, second.id)
    }

    // --- S6, S10, S13: создание карточки ---

    @Test
    fun createCard_usesDeckUuid_andSyncFlags() = runTest {
        val deck = deckRepository.createDeck("English")

        val card = cardRepository.addCard(deck.id, "cat", "кот")

        assertTrue("id карточки должен быть валидным UUID", isValidUuid(card.id))
        assertEquals("ссылка карточки на колоду — UUID колоды", deck.id, card.deckId)

        val entity = db.cardDao().getCardById(card.id)
        assertNotNull(entity)
        assertEquals("S10: isChanged = true при создании", true, entity!!.isChanged)
        assertEquals("S13: revision = 1 при создании", 1, entity.revision)
        assertEquals("isDeleted = false при создании", false, entity.isDeleted)
    }

    @Test
    fun createCard_idsAreUniqueWithinDeck() = runTest {
        val deck = deckRepository.createDeck("English")

        val first = cardRepository.addCard(deck.id, "cat", "кот")
        val second = cardRepository.addCard(deck.id, "dog", "пёс")

        assertNotEquals("UUID карточек должны быть уникальны", first.id, second.id)
    }

    // --- S11, S13: изменение записи ---

    @Test
    fun updateCard_setsIsChanged_andIncrementsRevision() = runTest {
        val deckId = UUID.randomUUID().toString()
        db.deckDao().insertDeck(DeckEntity(deckId, "English", isChanged = true, revision = 1, isDeleted = false))
        // GIVEN: карточка с isChanged = false, revision = 3, установлено напрямую
        val cardId = UUID.randomUUID().toString()
        db.cardDao().insertCard(
            CardEntity(
                id = cardId,
                deckId = deckId,
                front = "cat",
                back = "кот",
                intervalDays = 0,
                easeFactor = 2.5,
                dueEpochDay = 0,
                repetitions = 0,
                isChanged = false,
                revision = 3,
                isDeleted = false,
            ),
        )

        // WHEN: первое редактирование
        cardRepository.updateCard(cardRepository.getCard(cardId)!!.copy(front = "cat!", back = "кошка"))

        var entity = db.cardDao().getCardById(cardId)!!
        assertEquals("S11: isChanged = true после редактирования", true, entity.isChanged)
        assertEquals("S13: revision = 4 после первого изменения", 4, entity.revision)

        // WHEN: второе редактирование
        cardRepository.updateCard(entity.toDomainCard().copy(back = "кошак"))

        entity = db.cardDao().getCardById(cardId)!!
        assertEquals("S13: revision = 5 после второго изменения", 5, entity.revision)
        assertEquals(true, entity.isChanged)
        assertEquals("кошак", entity.back)
    }

    @Test
    fun updateDeck_setsIsChanged_andIncrementsRevision() = runTest {
        val deckId = UUID.randomUUID().toString()
        db.deckDao().insertDeck(
            DeckEntity(deckId, "English", isChanged = false, revision = 2, isDeleted = false),
        )

        deckRepository.updateDeck(deckRepository.getDeck(deckId)!!.copy(name = "English B2"))

        val entity = db.deckDao().getDeckById(deckId)!!
        assertEquals("S11: isChanged = true после редактирования", true, entity.isChanged)
        assertEquals("S13: revision = 3", 3, entity.revision)
        assertEquals("English B2", entity.name)
    }

    // --- S12: признаки переживают переоткрытие БД ---

    @Test
    fun syncFlags_surviveDatabaseReopen() = runTest {
        val deckId = UUID.randomUUID().toString()
        val cardId = UUID.randomUUID().toString()
        db.deckDao().insertDeck(DeckEntity(deckId, "English", isChanged = true, revision = 1, isDeleted = false))
        db.cardDao().insertCard(
            CardEntity(
                id = cardId,
                deckId = deckId,
                front = "cat",
                back = "кот",
                intervalDays = 0,
                easeFactor = 2.5,
                dueEpochDay = 0,
                repetitions = 0,
                isChanged = true,
                revision = 2,
                isDeleted = false,
            ),
        )

        // WHEN: закрыть приложение (БД) и открыть заново — новое соединение
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.databaseBuilder(context, MnemoCardsDatabase::class.java, "deck-repo-test.db")
            .allowMainThreadQueries().build()

        // THEN: признаки не сброшены
        val deck = db.deckDao().getDeckById(deckId)!!
        assertEquals(true, deck.isChanged)
        assertEquals(1, deck.revision)
        val card = db.cardDao().getCardById(cardId)!!
        assertEquals(true, card.isChanged)
        assertEquals(2, card.revision)
        assertEquals(false, card.isDeleted)
    }

    private fun isValidUuid(value: String): Boolean = try {
        UUID.fromString(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun CardEntity.toDomainCard() = com.mnemocards.domain.model.Card(
        id = id,
        deckId = deckId,
        front = front,
        back = back,
        intervalDays = intervalDays,
        easeFactor = easeFactor,
        dueEpochDay = dueEpochDay,
        repetitions = repetitions,
    )
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckEntity.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Колоды. Идентификатор — UUID, сгенерированный на клиенте (S6).
 * isChanged/revision/isDeleted — подготовка к фоновой синхронизации
 * (change prepare-sync-data-model).
 */
@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isChanged: Boolean,
    val revision: Int,
    val isDeleted: Boolean,
)
```

### FILE: app/src/main/java/com/mnemocards/data/db/CardEntity.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Карточки. Идентификатор — UUID клиента, ссылка на колоду — UUID колоды (S6).
 */
@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deckId")],
)
data class CardEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val intervalDays: Int,
    val easeFactor: Double,
    val dueEpochDay: Long,
    val repetitions: Int,
    val isChanged: Boolean,
    val revision: Int,
    val isDeleted: Boolean,
)
```

### FILE: app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Версия 2: переход на UUID-идентификаторы и поля
 * isChanged / revision / isDeleted (change prepare-sync-data-model).
 * Миграция 1→2 появится позже; до неё старые данные не запускаются.
 */
@Database(
    entities = [DeckEntity::class, CardEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MnemoCardsDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Insert
    suspend fun insertDeck(deck: DeckEntity)

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: String): DeckEntity?

    @Query("SELECT * FROM decks")
    suspend fun getAllDecks(): List<DeckEntity>

    @Query(
        """
        SELECT decks.id AS deckId, decks.name AS name, COUNT(cards.id) AS cardCount
        FROM decks
        LEFT JOIN cards ON cards.deckId = decks.id
        GROUP BY decks.id
        ORDER BY decks.name
        """,
    )
    fun observeDecksWithCardCounts(): Flow<List<DeckWithCardCount>>

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun countDecks(): Int

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeckById(id: String)
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/CardDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert
    suspend fun insertCard(card: CardEntity)

    @Insert
    suspend fun insertCards(cards: List<CardEntity>)

    @Update
    suspend fun updateCard(card: CardEntity)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getCardById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY dueEpochDay, id")
    fun observeCardsByDeckId(deckId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    suspend fun getCardsByDeckId(deckId: String): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun countCards(): Int

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCardById(id: String)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsByDeckId(deckId: String)
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckWithCardCount.kt
```kotlin
package com.mnemocards.data.db

data class DeckWithCardCount(
    val deckId: String,
    val name: String,
    val cardCount: Int,
)
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckWithCardCounts.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class DeckWithCardCounts(
    @Embedded val deck: DeckEntity,
    @Relation(parentColumn = "id", entityColumn = "deckId")
    val cards: List<CardEntity>,
)
```

### FILE: app/src/main/java/com/mnemocards/domain/model/Deck.kt
```kotlin
package com.mnemocards.domain.model

data class Deck(
    val id: String,
    val name: String,
)
```

### FILE: app/src/main/java/com/mnemocards/domain/model/Card.kt
```kotlin
package com.mnemocards.domain.model

data class Card(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val intervalDays: Int,
    val easeFactor: Double,
    val dueEpochDay: Long,
    val repetitions: Int,
)
```

### FILE: app/src/main/java/com/mnemocards/domain/DeckRepository.kt
```kotlin
package com.mnemocards.domain

import com.mnemocards.domain.model.Deck

interface DeckRepository {
    suspend fun createDeck(name: String): Deck
    suspend fun updateDeck(deck: Deck)
    suspend fun deleteDeck(id: String)
    suspend fun getDeck(id: String): Deck?
}
```

### FILE: app/src/main/java/com/mnemocards/data/RoomDeckRepository.kt
```kotlin
package com.mnemocards.data

import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import java.util.UUID

class RoomDeckRepository(
    private val deckDao: DeckDao,
) : DeckRepository {

    override suspend fun createDeck(name: String): Deck {
        val entity = DeckEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            isChanged = true,
            revision = 1,
            isDeleted = false,
        )
        deckDao.insertDeck(entity)
        return entity.toDomain()
    }

    override suspend fun updateDeck(deck: Deck) {
        val existing = deckDao.getDeckById(deck.id) ?: return
        deckDao.updateDeck(
            existing.copy(name = deck.name),
        )
    }

    override suspend fun deleteDeck(id: String) {
        deckDao.deleteDeckById(id)
    }

    override suspend fun getDeck(id: String): Deck? = deckDao.getDeckById(id)?.toDomain()

    private fun DeckEntity.toDomain() = Deck(id = id, name = name)
}
```

### FILE: app/src/main/java/com/mnemocards/data/repository/CardRepository.kt
```kotlin
package com.mnemocards.data.repository

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.domain.model.Card
import java.util.UUID
import kotlinx.coroutines.flow.Flow

sealed interface CardOperationResult {
    data class Success(val card: Card) : CardOperationResult
    data class Error(val message: String) : CardOperationResult
}

sealed interface CardImportResult {
    data class Success(val imported: Int) : CardImportResult
    data class Error(val message: String) : CardImportResult
}

interface CardRepository {
    suspend fun addCard(deckId: String, front: String, back: String): Card
    suspend fun updateCard(card: Card)
    suspend fun deleteCard(id: String)
    suspend fun getCard(id: String): Card?
    suspend fun getCards(deckId: String): List<Card>
    fun observeCards(deckId: String): Flow<List<Card>>
}

class RoomCardRepository(
    private val cardDao: CardDao,
) : CardRepository {

    override suspend fun addCard(deckId: String, front: String, back: String): Card {
        val entity = CardEntity(
            id = UUID.randomUUID().toString(),
            deckId = deckId,
            front = front,
            back = back,
            intervalDays = 0,
            easeFactor = 2.5,
            dueEpochDay = 0,
            repetitions = 0,
            isChanged = true,
            revision = 1,
            isDeleted = false,
        )
        cardDao.insertCard(entity)
        return entity.toDomain()
    }

    override suspend fun updateCard(card: Card) {
        val existing = cardDao.getCardById(card.id) ?: return
        cardDao.updateCard(
            existing.copy(
                front = card.front,
                back = card.back,
                intervalDays = card.intervalDays,
                easeFactor = card.easeFactor,
                dueEpochDay = card.dueEpochDay,
                repetitions = card.repetitions,
            ),
        )
    }

    override suspend fun deleteCard(id: String) {
        cardDao.deleteCardById(id)
    }

    override suspend fun getCard(id: String): Card? = cardDao.getCardById(id)?.toDomain()

    override suspend fun getCards(deckId: String): List<Card> =
        cardDao.getCardsByDeckId(deckId).map { it.toDomain() }

    override fun observeCards(deckId: String): Flow<List<Card>> =
        cardDao.observeCardsByDeckId(deckId).toCardFlows()

    private fun Flow<List<CardEntity>>.toCardFlows(): Flow<List<Card>> =
        kotlinx.coroutines.flow.map { list -> list.map { it.toDomain() } }

    private fun CardEntity.toDomain() = Card(
        id = id,
        deckId = deckId,
        front = front,
        back = back,
        intervalDays = intervalDays,
        easeFactor = easeFactor,
        dueEpochDay = dueEpochDay,
        repetitions = repetitions,
    )
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DatabaseSeeder.kt
```kotlin
package com.mnemocards.data.db

import java.util.UUID

/**
 * Идемпотентный сидинг базовой колоды из 5 карточек при первом запуске.
 * Каждой записи генерируется UUID (S6), признак isChanged = true,
 * revision = 1 — записи считаются локально созданными (S5).
 */
object DatabaseSeeder {

    private val seedCards = listOf(
        "cat" to "кот",
        "dog" to "пёс",
        "house" to "дом",
        "tree" to "дерево",
        "book" to "книга",
    )

    suspend fun seedIfNeeded(deckDao: DeckDao, cardDao: CardDao) {
        if (deckDao.countDecks() > 0) return

        val deckId = UUID.randomUUID().toString()
        deckDao.insertDeck(
            DeckEntity(
                id = deckId,
                name = "Базовая колода",
                isChanged = true,
                revision = 1,
                isDeleted = false,
            ),
        )
        cardDao.insertCards(
            seedCards.map { (front, back) ->
                CardEntity(
                    id = UUID.randomUUID().toString(),
                    deckId = deckId,
                    front = front,
                    back = back,
                    intervalDays = 0,
                    easeFactor = 2.5,
                    dueEpochDay = 0,
                    repetitions = 0,
                    isChanged = true,
                    revision = 1,
                    isDeleted = false,
                )
            },
        )
    }
}
```


---

## Батч 2/7

### FILE: app/src/main/java/com/mnemocards/data/db/DeckDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Insert
    suspend fun insertDeck(deck: DeckEntity)

    /**
     * Единственная точка изменения колоды: всегда поднимает isChanged
     * и увеличивает revision ровно на 1 (S11, S13). Операции снятия
     * isChanged нет — это будет делать только будущая синхронизация (S12).
     */
    @Query(
        """
        UPDATE decks
        SET name = :name, isChanged = 1, revision = revision + 1
        WHERE id = :id
        """,
    )
    suspend fun updateDeckName(id: String, name: String)

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: String): DeckEntity?

    @Query("SELECT * FROM decks")
    suspend fun getAllDecks(): List<DeckEntity>

    @Query(
        """
        SELECT decks.id AS deckId, decks.name AS name, COUNT(cards.id) AS cardCount
        FROM decks
        LEFT JOIN cards ON cards.deckId = decks.id
        GROUP BY decks.id
        ORDER BY decks.name
        """,
    )
    fun observeDecksWithCardCounts(): Flow<List<DeckWithCardCount>>

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun countDecks(): Int

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeckById(id: String)
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/CardDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert
    suspend fun insertCard(card: CardEntity)

    @Insert
    suspend fun insertCards(cards: List<CardEntity>)

    /**
     * Единственная точка изменения карточки: всегда поднимает isChanged
     * и увеличивает revision ровно на 1 (S11, S13). Операции снятия
     * isChanged нет — это будет делать только будущая синхронизация (S12).
     */
    @Query(
        """
        UPDATE cards
        SET front = :front,
            back = :back,
            intervalDays = :intervalDays,
            easeFactor = :easeFactor,
            dueEpochDay = :dueEpochDay,
            repetitions = :repetitions,
            isChanged = 1,
            revision = revision + 1
        WHERE id = :id
        """,
    )
    suspend fun updateCardContent(
        id: String,
        front: String,
        back: String,
        intervalDays: Int,
        easeFactor: Double,
        dueEpochDay: Long,
        repetitions: Int,
    )

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getCardById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY dueEpochDay, id")
    fun observeCardsByDeckId(deckId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    suspend fun getCardsByDeckId(deckId: String): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun countCards(): Int

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCardById(id: String)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsByDeckId(deckId: String)
}
```

### FILE: app/src/main/java/com/mnemocards/data/RoomDeckRepository.kt
```kotlin
package com.mnemocards.data

import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import java.util.UUID

class RoomDeckRepository(
    private val deckDao: DeckDao,
) : DeckRepository {

    override suspend fun createDeck(name: String): Deck {
        val entity = DeckEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            isChanged = true,
            revision = 1,
            isDeleted = false,
        )
        deckDao.insertDeck(entity)
        return entity.toDomain()
    }

    override suspend fun updateDeck(deck: Deck) {
        // Редактирование идёт только через DAO-запрос, который всегда
        // выставляет isChanged = 1 и делает revision + 1 (S11, S13).
        deckDao.updateDeckName(deck.id, deck.name)
    }

    override suspend fun deleteDeck(id: String) {
        deckDao.deleteDeckById(id)
    }

    override suspend fun getDeck(id: String): Deck? = deckDao.getDeckById(id)?.toDomain()

    private fun DeckEntity.toDomain() = Deck(id = id, name = name)
}
```

### FILE: app/src/main/java/com/mnemocards/data/repository/CardRepository.kt
```kotlin
package com.mnemocards.data.repository

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.domain.model.Card
import java.util.UUID
import kotlinx.coroutines.flow.Flow

sealed interface CardOperationResult {
    data class Success(val card: Card) : CardOperationResult
    data class Error(val message: String) : CardOperationResult
}

sealed interface CardImportResult {
    data class Success(val imported: Int) : CardImportResult
    data class Error(val message: String) : CardImportResult
}

interface CardRepository {
    suspend fun addCard(deckId: String, front: String, back: String): Card
    suspend fun updateCard(card: Card)
    suspend fun deleteCard(id: String)
    suspend fun getCard(id: String): Card?
    suspend fun getCards(deckId: String): List<Card>
    fun observeCards(deckId: String): Flow<List<Card>>
}

class RoomCardRepository(
    private val cardDao: CardDao,
) : CardRepository {

    override suspend fun addCard(deckId: String, front: String, back: String): Card {
        val entity = CardEntity(
            id = UUID.randomUUID().toString(),
            deckId = deckId,
            front = front,
            back = back,
            intervalDays = 0,
            easeFactor = 2.5,
            dueEpochDay = 0,
            repetitions = 0,
            isChanged = true,
            revision = 1,
            isDeleted = false,
        )
        cardDao.insertCard(entity)
        return entity.toDomain()
    }

    override suspend fun updateCard(card: Card) {
        // Редактирование идёт только через DAO-запрос, который всегда
        // выставляет isChanged = 1 и делает revision + 1 (S11, S13).
        cardDao.updateCardContent(
            id = card.id,
            front = card.front,
            back = card.back,
            intervalDays = card.intervalDays,
            easeFactor = card.easeFactor,
            dueEpochDay = card.dueEpochDay,
            repetitions = card.repetitions,
        )
    }

    override suspend fun deleteCard(id: String) {
        cardDao.deleteCardById(id)
    }

    override suspend fun getCard(id: String): Card? = cardDao.getCardById(id)?.toDomain()

    override suspend fun getCards(deckId: String): List<Card> =
        cardDao.getCardsByDeckId(deckId).map { it.toDomain() }

    override fun observeCards(deckId: String): Flow<List<Card>> =
        cardDao.observeCardsByDeckId(deckId).toCardFlows()

    private fun Flow<List<CardEntity>>.toCardFlows(): Flow<List<Card>> =
        kotlinx.coroutines.flow.map { list -> list.map { it.toDomain() } }

    private fun CardEntity.toDomain() = Card(
        id = id,
        deckId = deckId,
        front = front,
        back = back,
        intervalDays = intervalDays,
        easeFactor = easeFactor,
        dueEpochDay = dueEpochDay,
        repetitions = repetitions,
    )
}
```

### FILE: app/src/test/java/com/mnemocards/data/db/MigrationFrom1To2Test.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model.
 * S1, S2, S3 — миграция выдаёт UUID, сохраняет данные и статистику,
 * пересчитывает ссылки, помечает записи isChanged = true / revision = 1.
 * S4 — пустая БД мигрирует без ошибок и остаётся пустой.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationFrom1To2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MnemoCardsDatabase::class.java,
    )

    // --- S1, S2, S3: миграция непустой БД ---

    @Test
    fun migrate_assignsUniqueUuids_preservesDataAndStats() {
        val dbName = "migration-test-full.db"

        // GIVEN: v1-БД с 2 колодами и 7 карточками, числовые PK, накопленная статистика
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO decks (name) VALUES ('English')")
            execSQL("INSERT INTO decks (name) VALUES ('Spanish')")
            execSQL(
                "INSERT INTO cards (deckId, front, back, intervalDays, easeFactor, dueEpochDay, repetitions) VALUES " +
                    "(1, 'cat', 'кот', 12, 2.6, 20000, 5)," +
                    "(1, 'dog', 'пёс', 3, 2.5, 19998, 2)," +
                    "(1, 'house', 'дом', 0, 2.5, 19990, 0)," +
                    "(1, 'tree', 'дерево', 21, 2.7, 20005, 7)," +
                    "(2, 'hola', 'привет', 6, 2.5, 19999, 3)," +
                    "(2, 'gracias', 'спасибо', 1, 2.4, 19995, 1)," +
                    "(2, 'adios', 'пока', 8, 2.55, 20002, 4)",
            )
            close()
        }

        // WHEN: выполняется миграция 1→2
        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        // THEN: приложение (миграция) завершилось без сбоев
        db.query("SELECT id, name, isChanged, revision, isDeleted FROM decks ORDER BY name").use { cursor ->
            assertEquals("S1: колоды не продублированы и не потеряны", 2, cursor.count)
            val deckUuids = mutableSetOf<String>()
            val englishUuid: String
            val spanishUuid: String
            assertTrue(cursor.moveToFirst())
            assertEquals("English", cursor.getString(1))
            englishUuid = cursor.getString(0)
            assertTrue(cursor.moveToNext())
            assertEquals("Spanish", cursor.getString(1))
            spanishUuid = cursor.getString(0)
            assertNotEquals("S1: UUID колод уникальны", englishUuid, spanishUuid)
            deckUuids.add(englishUuid)
            deckUuids.add(spanishUuid)
            deckUuids.forEach {
                assertTrue("S1: id — валидный UUID", isValidUuid(it))
            }
            // S3: записи помечены как локально изменённые
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                assertEquals("S3: isChanged = true", 1, cursor.getInt(2))
                assertEquals("S3: revision = 1", 1, cursor.getInt(3))
                assertEquals("S3: isDeleted = false", 0, cursor.getInt(4))
                cursor.moveToNext()
            }

            // S1, S2: карточки — ссылки пересчитаны, данные и статистика сохранены
            db.query(
                "SELECT deckId, front, back, intervalDays, easeFactor, dueEpochDay, repetitions " +
                    "FROM cards ORDER BY front",
            ).use { cards ->
                assertEquals("S1: карточки не продублированы и не потеряны", 7, cards.count)
                val cardUuids = mutableSetOf<String>()
                db.query("SELECT id FROM cards").use { ids ->
                    while (ids.moveToNext()) {
                        val uuid = ids.getString(0)
                        assertTrue("S6-совместимость: id карточки — валидный UUID", isValidUuid(uuid))
                        cardUuids.add(uuid)
                    }
                }
                assertEquals("S1: UUID карточек уникальны", 7, cardUuids.size)

                val expected = mapOf(
                    "cat" to listOf(englishUuid, 12, 2.6, 20000, 5),
                    "dog" to listOf(englishUuid, 3, 2.5, 19998, 2),
                    "house" to listOf(englishUuid, 0, 2.5, 19990, 0),
                    "tree" to listOf(englishUuid, 21, 2.7, 20005, 7),
                    "hola" to listOf(spanishUuid, 6, 2.5, 19999, 3),
                    "gracias" to listOf(spanishUuid, 1, 2.4, 19995, 1),
                    "adios" to listOf(spanishUuid, 8, 2.55, 20002, 4),
                )
                while (cards.moveToNext()) {
                    val front = cards.getString(1)
                    val exp = expected.getValue(front)
                    assertEquals("S2: deckId карточки — UUID её колоды", exp[0], cards.getString(0))
                    assertEquals("S1: intervalDays сохранён", exp[1], cards.getInt(3))
                    assertEquals("S1: easeFactor сохранён", (exp[2] as Double), cards.getDouble(4), 0.0001)
                    assertEquals("S1: dueEpochDay сохранён", (exp[3] as Int).toLong(), cards.getLong(5))
                    assertEquals("S1: repetitions (статистика) сохранены", exp[4], cards.getInt(6))
                }
            }
        }

        db.close()
    }

    @Test
    fun migrate_marksSyncFields_onAllRows() {
        val dbName = "migration-test-flags.db"
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO decks (name) VALUES ('Solo')")
            execSQL(
                "INSERT INTO cards (deckId, front, back, intervalDays, easeFactor, dueEpochDay, repetitions) " +
                    "VALUES (1, 'a', 'а', 0, 2.5, 0, 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)
        db.query("SELECT isChanged, revision, isDeleted FROM cards").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("S3: isChanged = true", 1, c.getInt(0))
            assertEquals("S3: revision = 1", 1, c.getInt(1))
            assertEquals("S3: isDeleted = false", 0, c.getInt(2))
        }
        db.close()
    }

    // --- S4: миграция пустой БД ---

    @Test
    fun migrate_emptyDatabase_staysEmptyAndValid() {
        val dbName = "migration-test-empty.db"
        helper.createDatabase(dbName, 1).close()

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        db.query("SELECT COUNT(*) FROM decks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("S4: колоды не создаются", 0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM cards").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("S4: карточки не создаются", 0, c.getInt(0))
        }
        db.close()
    }

    private fun isValidUuid(value: String): Boolean = try {
        UUID.fromString(value)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/MigrationFrom1To2.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * Миграция 1→2: переход с числовых автоинкрементных PK на UUID (String)
 * и добавление полей isChanged / revision / isDeleted (S1–S4).
 *
 * Шаги: создать новые таблицы со String PK → прочитать старые строки →
 * сгенерировать UUID (карта oldId→UUID для колод) → вставить с
 * isChanged = true, revision = 1, isDeleted = false, пересчитанным deckId
 * и сохранёнными остальными колонками (статистика) → удалить старые
 * таблицы, переименовать новые, пересоздать индекс и FK.
 */
class MigrationFrom1To2 : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `decks_new` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`isChanged` INTEGER NOT NULL, " +
                "`revision` INTEGER NOT NULL, " +
                "`isDeleted` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cards_new` (" +
                "`id` TEXT NOT NULL, " +
                "`deckId` TEXT NOT NULL, " +
                "`front` TEXT NOT NULL, " +
                "`back` TEXT NOT NULL, " +
                "`intervalDays` INTEGER NOT NULL, " +
                "`easeFactor` REAL NOT NULL, " +
                "`dueEpochDay` INTEGER NOT NULL, " +
                "`repetitions` INTEGER NOT NULL, " +
                "`isChanged` INTEGER NOT NULL, " +
                "`revision` INTEGER NOT NULL, " +
                "`isDeleted` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )

        // Карта старых числовых id колод → новые UUID (S2).
        val deckIdMap = mutableMapOf<Long, String>()
        db.query("SELECT id FROM decks").use { cursor ->
            while (cursor.moveToNext()) {
                deckIdMap[cursor.getLong(0)] = UUID.randomUUID().toString()
            }
        }

        // Колоды: имя сохраняется, записи считаются локально созданными (S3).
        for ((oldId, uuid) in deckIdMap) {
            db.execSQL(
                "INSERT INTO decks_new (id, name, isChanged, revision, isDeleted) " +
                    "SELECT ?, name, 1, 1, 0 FROM decks WHERE id = ?",
                arrayOf(uuid, oldId),
            )
        }

        // Карточки: ссылка на колоду пересчитывается в UUID колоды (S2),
        // данные и статистика сохраняются, признаки — как у локальной записи (S3).
        db.query(
            "SELECT deckId, front, back, intervalDays, easeFactor, dueEpochDay, repetitions FROM cards",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                db.execSQL(
                    "INSERT INTO cards_new (id, deckId, front, back, intervalDays, easeFactor, " +
                        "dueEpochDay, repetitions, isChanged, revision, isDeleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0)",
                    arrayOf(
                        UUID.randomUUID().toString(),
                        deckIdMap.getValue(cursor.getLong(0)),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getInt(3),
                        cursor.getDouble(4),
                        cursor.getLong(5),
                        cursor.getInt(6),
                    ),
                )
            }
        }

        // Сначала карточки (FK), затем колоды; переименование и индекс.
        db.execSQL("DROP TABLE cards")
        db.execSQL("DROP TABLE decks")
        db.execSQL("ALTER TABLE decks_new RENAME TO decks")
        db.execSQL("ALTER TABLE cards_new RENAME TO cards")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_deckId` ON `cards` (`deckId`)")
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Версия 2: переход на UUID-идентификаторы и поля
 * isChanged / revision / isDeleted (change prepare-sync-data-model).
 * Миграция 1→2 сохраняет данные и статистику, выдаёт UUID и помечает
 * записи isChanged = true, revision = 1, isDeleted = false (S1–S4).
 */
@Database(
    entities = [DeckEntity::class, CardEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MnemoCardsDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao

    companion object {
        val MIGRATION_1_2: androidx.room.migration.Migration = MigrationFrom1To2()
    }
}
```

### FILE: app/src/main/java/com/mnemocards/AppContainer.kt
```kotlin
package com.mnemocards

import android.content.Context
import androidx.room.Room
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.data.repository.RoomCardRepository
import com.mnemocards.domain.DeckRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Единственный контейнер зависимостей приложения (инвариант MAIN.md).
 * БД собирается здесь же; миграция 1→2 зарегистрирована в билдере,
 * чтобы обновление с числовых ID не теряло данные пользователя.
 */
class AppContainer(context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: MnemoCardsDatabase = Room.databaseBuilder(
        context.applicationContext,
        MnemoCardsDatabase::class.java,
        "mnemocards.db",
    )
        .addMigrations(MnemoCardsDatabase.MIGRATION_1_2)
        .build()

    val deckRepository: DeckRepository = RoomDeckRepository(database.deckDao())
    val cardRepository: CardRepository = RoomCardRepository(database.cardDao())

    init {
        // Идемпотентный сидинг базовой колоды при первом запуске (S5).
        applicationScope.launch {
            DatabaseSeeder.seedIfNeeded(database.deckDao(), database.cardDao())
        }
    }
}
```

### FILE: app/schemas/com.mnemocards.data.db.MnemoCardsDatabase/1.json
```json
{
  "formatVersion": 1,
  "database": {
    "version": 1,
    "identityHash": "8c1f2a9d4b7e60513c2f8a9b0d4e7c51",
    "entities": [
      {
        "tableName": "decks",
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)",
        "fields": [
          {
            "fieldPath": "id",
            "columnName": "id",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "name",
            "columnName": "name",
            "affinity": "TEXT",
            "notNull": true
          }
        ],
        "primaryKey": {
          "autoGenerate": true,
          "columnNames": [
            "id"
          ]
        },
        "indices": [],
        "foreignKeys": []
      },
      {
        "tableName": "cards",
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deckId` INTEGER NOT NULL, `front` TEXT NOT NULL, `back` TEXT NOT NULL, `intervalDays` INTEGER NOT NULL, `easeFactor` REAL NOT NULL, `dueEpochDay` INTEGER NOT NULL, `repetitions` INTEGER NOT NULL, FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "fields": [
          {
            "fieldPath": "id",
            "columnName": "id",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "deckId",
            "columnName": "deckId",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "front",
            "columnName": "front",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "back",
            "columnName": "back",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "intervalDays",
            "columnName": "intervalDays",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "easeFactor",
            "columnName": "easeFactor",
            "affinity": "REAL",
            "notNull": true
          },
          {
            "fieldPath": "dueEpochDay",
            "columnName": "dueEpochDay",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "repetitions",
            "columnName": "repetitions",
            "affinity": "INTEGER",
            "notNull": true
          }
        ],
        "primaryKey": {
          "autoGenerate": true,
          "columnNames": [
            "id"
          ]
        },
        "indices": [
          {
            "name": "index_cards_deckId",
            "unique": false,
            "columnNames": [
              "deckId"
            ],
            "orders": [],
            "createSql": "CREATE INDEX IF NOT EXISTS `index_cards_deckId` ON `${TABLE_NAME}` (`deckId`)"
          }
        ],
        "foreignKeys": [
          {
            "table": "decks",
            "onDelete": "CASCADE",
            "onUpdate": "NO ACTION",
            "columns": [
              "deckId"
            ],
            "referencedColumns": [
              "id"
            ]
          }
        ]
      }
    ],
    "views": [],
    "setupQueries": [
      "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
      "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8c1f2a9d4b7e60513c2f8a9b0d4e7c51')"
    ]
  }
}
```


---

## Батч 3/7

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
datastorePreferences = "1.1.1"
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
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
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
    implementation(libs.androidx.datastore.preferences)
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

### FILE: app/src/main/java/com/mnemocards/data/sync/SyncFlagDataStore.kt
```
package com.mnemocards.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Персистентный флаг синхронизации (S14–S16).
 * По умолчанию выключен (false) → режим удаления физический.
 * UI-переключателей нет — флагом будет управлять будущий экран настроек сервера.
 */
private val Context.syncPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_settings",
)

class SyncFlagDataStore(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * Текущее значение флага. Ошибки чтения (IOException, включая
     * CorruptionException от повреждённого файла) транслируются в false —
     * приложение трактует флаг как выключенный и не падает (S16).
     */
    val syncEnabled: Flow<Boolean> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { preferences -> preferences[SYNC_ENABLED] ?: false }

    suspend fun setSyncEnabled(value: Boolean) {
        dataStore.edit { preferences -> preferences[SYNC_ENABLED] = value }
    }

    companion object {
        private val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")

        /**
         * Единственная точка создания DataStore для AppContainer:
         * делегат гарантирует один экземпляр на процесс.
         */
        fun fromContext(context: Context): SyncFlagDataStore =
            SyncFlagDataStore(context.applicationContext.syncPreferencesDataStore)
    }
}
```

### FILE: app/src/main/java/com/mnemocards/AppContainer.kt
```
package com.mnemocards

import android.content.Context
import androidx.room.Room
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.data.repository.RoomCardRepository
import com.mnemocards.data.sync.SyncFlagDataStore
import com.mnemocards.domain.DeckRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Единственный контейнер зависимостей приложения (инвариант MAIN.md).
 * БД собирается здесь же; миграция 1→2 зарегистрирована в билдере,
 * чтобы обновление с числовых ID не теряло данные пользователя.
 */
class AppContainer(context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: MnemoCardsDatabase = Room.databaseBuilder(
        context.applicationContext,
        MnemoCardsDatabase::class.java,
        "mnemocards.db",
    )
        .addMigrations(MnemoCardsDatabase.MIGRATION_1_2)
        .build()

    val deckRepository: DeckRepository = RoomDeckRepository(database.deckDao())
    val cardRepository: CardRepository = RoomCardRepository(database.cardDao())

    /**
     * Единственный экземпляр хранилища флага синхронизации (S14–S16).
     * UI-переключателей нет — флагом управляет будущий экран настроек сервера.
     */
    val syncFlagDataStore: SyncFlagDataStore = SyncFlagDataStore.fromContext(context)

    init {
        // Идемпотентный сидинг базовой колоды при первом запуске (S5).
        applicationScope.launch {
            DatabaseSeeder.seedIfNeeded(database.deckDao(), database.cardDao())
        }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/sync/SyncFlagDataStoreTest.kt
```
package com.mnemocards.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model.
 * S14 — чистый DataStore: флаг по умолчанию выключен (false).
 * S15 — значение флага персистентно, переживает создание нового
 *        экземпляра хранилища (аналог перезапуска приложения).
 * S16 — повреждённый файл хранилища: чтение возвращает false без исключения.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncFlagDataStoreTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private fun newStore(file: File): Pair<SyncFlagDataStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return SyncFlagDataStore(dataStore) to scope
    }

    // --- S14: флаг по умолчанию выключен ---

    @Test
    fun cleanDataStore_flagDefaultsToFalse() = runBlocking {
        val file = File(tmpFolder.newFolder(), "sync_settings.preferences_pb")
        val (store, scope) = newStore(file)

        val value = store.syncEnabled.first()

        assertFalse("S14: чистая установка — флаг выключен", value)

        scope.cancel()
    }

    // --- S15: значение персистентно ---

    @Test
    fun writtenFlagTrue_persistedForNewStoreInstance() = runBlocking {
        val file = File(tmpFolder.newFolder(), "sync_settings.preferences_pb")
        val (storeA, scopeA) = newStore(file)

        storeA.setSyncEnabled(true)
        // Закрываем первый экземпляр — аналог перезапуска приложения.
        scopeA.cancel()

        val (storeB, scopeB) = newStore(file)
        val value = storeB.syncEnabled.first()

        assertTrue("S15: флаг true переживает новый экземпляр хранилища", value)

        scopeB.cancel()
    }

    @Test
    fun writtenFlagFalse_persistedForNewStoreInstance() = runBlocking {
        val file = File(tmpFolder.newFolder(), "sync_settings.preferences_pb")
        val (storeA, scopeA) = newStore(file)

        storeA.setSyncEnabled(false)
        scopeA.cancel()

        val (storeB, scopeB) = newStore(file)
        val value = storeB.syncEnabled.first()

        assertFalse("S15: явный false тоже персистентен", value)

        scopeB.cancel()
    }

    // --- S16: повреждённое хранилище (аналог 500) ---

    @Test
    fun corruptedFile_readReturnsFalseWithoutCrash() = runBlocking {
        val dir = tmpFolder.newFolder()
        val file = File(dir, "sync_settings.preferences_pb")
        // Мусорные байты: файл существует, но не парсится как preferences_pb.
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x7F, -0x80, 0x33, 0x33, 0x33))

        val (store, scope) = newStore(file)

        val value = store.syncEnabled.first()

        assertFalse("S16: ошибка чтения трактуется как выключенный флаг", value)

        scope.cancel()
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/repository/DeletionRepositoryTest.kt
```
package com.mnemocards.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model.
 * S17 — физическое удаление карточки при выключенном флаге.
 * S18 — физическое удаление колоды каскадно удаляет все её карточки.
 * S16 — ошибка чтения DataStore трактуется как выключенный флаг,
 *        удаление выполняется физическое.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeletionRepositoryTest {

    private lateinit var database: MnemoCardsDatabase
    private lateinit var deckRepository: RoomDeckRepository
    private lateinit var cardRepository: RoomCardRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        deckRepository = RoomDeckRepository(database.deckDao())
        cardRepository = RoomCardRepository(database.cardDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newDeckId(): String = UUID.randomUUID().toString()

    private suspend fun insertDeck(id: String, name: String = "Deck") {
        database.deckDao().insertDeck(
            DeckEntity(
                id = id,
                name = name,
                isChanged = false,
                revision = 1,
                isDeleted = false,
            ),
        )
    }

    private suspend fun insertCard(id: String, deckId: String, front: String = "front") {
        database.cardDao().insertCard(
            CardEntity(
                id = id,
                deckId = deckId,
                front = front,
                back = "back",
                intervalDays = 0,
                easeFactor = 2.5,
                dueEpochDay = 0,
                repetitions = 0,
                isChanged = false,
                revision = 1,
                isDeleted = false,
            ),
        )
    }

    // --- S17: физическое удаление карточки ---

    @Test
    fun deleteCard_flagOff_rowPhysicallyRemoved() = runBlocking {
        val deckId = newDeckId()
        insertDeck(deckId)
        val cardA = UUID.randomUUID().toString()
        val cardB = UUID.randomUUID().toString()
        insertCard(cardA, deckId, "a")
        insertCard(cardB, deckId, "b")
        assertEquals(2, database.cardDao().getCardsByDeckId(deckId).size)

        // Флаг синхронизации выключен (S14: значение по умолчанию).
        cardRepository.deleteCard(cardA)

        assertNull("S17: прямой DAO-запрос не находит карточку", database.cardDao().getCardById(cardA))
        assertEquals(
            "S17: счётчик карточек колоды уменьшился",
            listOf(cardB),
            database.cardDao().getCardsByDeckId(deckId).map { it.id },
        )
    }

    // --- S18: физическое удаление колоды каскадом ---

    @Test
    fun deleteDeck_flagOff_cascadeRemovesAllCards() = runBlocking {
        val deckId = newDeckId()
        insertDeck(deckId, "D")
        val cardIds = (1..5).map { index ->
            UUID.randomUUID().toString().also { insertCard(it, deckId, "card$index") }
        }
        assertEquals(5, database.cardDao().getCardsByDeckId(deckId).size)

        deckRepository.deleteDeck(deckId)

        assertNull("S18: строка колоды физически удалена", database.deckDao().getDeckById(deckId))
        cardIds.forEach { cardId ->
            assertNull("S18: карточка $cardId физически удалена каскадом", database.cardDao().getCardById(cardId))
        }
        assertTrue(
            "S18: у колоды не осталось карточек",
            database.cardDao().getCardsByDeckId(deckId).isEmpty(),
        )
    }

    // --- S16: ошибка чтения DataStore → физическое удаление ---

    @Test
    fun deleteCard_dataStoreReadError_deletesPhysically() = runBlocking {
        // GIVEN: чтение флага из DataStore завершается ошибкой (повреждённый файл),
        // флаг трактуется как выключенный (см. SyncFlagDataStoreTest, S16).
        // Для удаления это означает физический режим.
        val store = corruptedFlagStore()
        val flagValue = store.syncEnabled.first()
        assertFalse("S16: ошибка чтения DataStore → флаг трактуется как false", flagValue)

        val deckId = newDeckId()
        insertDeck(deckId)
        val cardId = UUID.randomUUID().toString()
        insertCard(cardId, deckId)

        if (!flagValue) {
            cardRepository.deleteCard(cardId)
        }

        assertNull(
            "S16: удаление выполнено физически, строки нет в БД",
            database.cardDao().getCardById(cardId),
        )

        store.close()
    }

    /** Повреждённый DataStore: мусорные байты в файле preferences_pb. */
    private fun corruptedFlagStore(): com.mnemocards.data.sync.SyncFlagDataStore {
        val dir = createTempDir(prefix = "sync_corrupted_")
        val file = java.io.File(dir, "sync_settings.preferences_pb")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x7F, 0x33, 0x33, 0x33))
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.Job())
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return object : com.mnemocards.data.sync.SyncFlagDataStore(dataStore) {
            // не закрываем DataStore напрямую: гасим scope
        }.also { created ->
            createdStoreScopes.add(scope)
        }
    }

    private val createdStoreScopes = mutableListOf<kotlinx.coroutines.CoroutineScope>()

    private fun com.mnemocards.data.sync.SyncFlagDataStore.close() {
        createdStoreScopes.forEach { it.cancel() }
        createdStoreScopes.clear()
    }
}
```


---

## Батч 4/7

### FILE: app/src/main/java/com/mnemocards/data/db/CardDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert
    suspend fun insertCard(card: CardEntity)

    @Insert
    suspend fun insertCards(cards: List<CardEntity>)

    @Update
    suspend fun updateCard(card: CardEntity)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getCardById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    suspend fun getCardsByDeckId(deckId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    fun observeCardsByDeckId(deckId: String): Flow<List<CardEntity>>

    // --- Физическое удаление (S17, S18): строка немедленно уходит из БД ---

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCardById(id: String)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsByDeckId(deckId: String)

    // --- Мягкое удаление (S19, S20, S23): строка остаётся до будущей
    //     синхронизации с isDeleted = 1, isChanged = 1, revision + 1 ---

    @Query(
        "UPDATE cards SET isDeleted = 1, isChanged = 1, " +
            "revision = revision + 1 WHERE id = :id"
    )
    suspend fun softDeleteCardById(id: String)

    @Query(
        "UPDATE cards SET isDeleted = 1, isChanged = 1, " +
            "revision = revision + 1 WHERE deckId = :deckId"
    )
    suspend fun softDeleteCardsByDeckId(deckId: String)
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Insert
    suspend fun insertDeck(deck: DeckEntity)

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun countDecks(): Int

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: String): DeckEntity?

    @Query("SELECT * FROM decks")
    fun observeDecks(): Flow<List<DeckEntity>>

    // --- Физическое удаление (S18): строка колоды уходит из БД,
    //     карточки снимает FK CASCADE (продублировано явным запросом
    //     на уровне repository) ---

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeckById(id: String)

    // --- Мягкое удаление (S20): колода остаётся в БД до будущей
    //     синхронизации с isDeleted = 1, isChanged = 1, revision + 1 ---

    @Query(
        "UPDATE decks SET isDeleted = 1, isChanged = 1, " +
            "revision = revision + 1 WHERE id = :id"
    )
    suspend fun softDeleteDeckById(id: String)
}
```

### FILE: app/src/main/java/com/mnemocards/data/RoomDeckRepository.kt
```kotlin
package com.mnemocards.data

import androidx.room.withTransaction
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.sync.SyncFlagDataStore
import com.mnemocards.domain.DeckRepository
import kotlinx.coroutines.flow.first

/**
 * Удаление колоды ветвится по флагу синхронизации (S16–S20):
 *  - флаг выключен (или ошибка чтения DataStore) — физическое удаление;
 *  - флаг включён — мягкое: колода и её карточки помечаются isDeleted = 1,
 *    isChanged = 1, revision + 1 и остаются в БД до будущей синхронизации.
 */
class RoomDeckRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao? = null,
    private val database: MnemoCardsDatabase? = null,
    private val flagStore: SyncFlagDataStore? = null,
) : DeckRepository {

    /** Флаг читается один раз на операцию; при ошибке трактуется как false (S16). */
    private suspend fun syncEnabled(): Boolean =
        flagStore?.syncEnabled?.first() ?: false

    override suspend fun deleteDeck(id: String) {
        if (syncEnabled()) {
            val softDelete: suspend () -> Unit = {
                deckDao.softDeleteDeckById(id)
                cardDao?.softDeleteCardsByDeckId(id)
            }
            val db = database
            if (db != null) {
                db.withTransaction { softDelete() }
            } else {
                softDelete()
            }
        } else {
            deckDao.deleteDeckById(id)
            // FK CASCADE уже снимает карточки; явный запрос — страховка
            // на случай отсутствия cardDao в этом экземпляре репозитория.
            cardDao?.deleteCardsByDeckId(id)
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/repository/CardRepository.kt
```kotlin
package com.mnemocards.data.repository

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.sync.SyncFlagDataStore
import kotlinx.coroutines.flow.first

interface CardRepository {

    /**
     * Удаление карточки: физическое или мягкое в зависимости от флага
     * синхронизации (S16–S19, S23).
     */
    suspend fun deleteCard(id: String)
}

/**
 * Удаление ветвится по флагу синхронизации:
 *  - флаг выключен (или ошибка чтения DataStore) — физическое удаление (S17);
 *  - флаг включён — мягкое: isDeleted = 1, isChanged = 1, revision + 1,
 *    строка остаётся в БД и доступна по прямому DAO-запросу (S19, S23).
 */
class RoomCardRepository(
    private val cardDao: CardDao,
    private val flagStore: SyncFlagDataStore? = null,
) : CardRepository {

    /** Флаг читается один раз на операцию; при ошибке трактуется как false (S16). */
    private suspend fun syncEnabled(): Boolean =
        flagStore?.syncEnabled?.first() ?: false

    override suspend fun deleteCard(id: String) {
        if (syncEnabled()) {
            cardDao.softDeleteCardById(id)
        } else {
            cardDao.deleteCardById(id)
        }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/repository/DeletionRepositoryTest.kt
```kotlin
package com.mnemocards.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.sync.SyncFlagDataStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model.
 * S17 — физическое удаление карточки при выключенном флаге.
 * S18 — физическое удаление колоды каскадно удаляет все её карточки.
 * S16 — ошибка чтения DataStore трактуется как выключенный флаг,
 *        удаление выполняется физическое.
 * S19 — мягкое удаление карточки при включённом флаге.
 * S20 — мягкое удаление колоды каскадно помечает карточки.
 * S23 — мягко удалённая запись доступна по прямому запросу к DAO.
 * S24 — переключение флага не трогает мягко удалённые записи.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeletionRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var database: MnemoCardsDatabase
    private lateinit var deckRepository: RoomDeckRepository
    private lateinit var cardRepository: RoomCardRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        deckRepository = RoomDeckRepository(database.deckDao())
        cardRepository = RoomCardRepository(database.cardDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newDeckId(): String = UUID.randomUUID().toString()

    private suspend fun insertDeck(id: String, name: String = "Deck") {
        database.deckDao().insertDeck(
            DeckEntity(
                id = id,
                name = name,
                isChanged = false,
                revision = 1,
                isDeleted = false,
            ),
        )
    }

    private suspend fun insertCard(
        id: String,
        deckId: String,
        front: String = "front",
        revision: Int = 1,
    ) {
        database.cardDao().insertCard(
            CardEntity(
                id = id,
                deckId = deckId,
                front = front,
                back = "back",
                intervalDays = 0,
                easeFactor = 2.5,
                dueEpochDay = 0,
                repetitions = 0,
                isChanged = false,
                revision = revision,
                isDeleted = false,
            ),
        )
    }

    /** DataStore флага синхронизации во временной папке + scope для гашения. */
    private fun newFlagStore(initial: Boolean): Pair<SyncFlagDataStore, CoroutineScope> =
        runBlocking {
            val file = File(tmpFolder.newFolder(), "sync_settings.preferences_pb")
            val scope = CoroutineScope(Dispatchers.IO + Job())
            val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
            val store = SyncFlagDataStore(dataStore)
            store.setSyncEnabled(initial)
            store to scope
        }

    // --- S17: физическое удаление карточки ---

    @Test
    fun deleteCard_flagOff_rowPhysicallyRemoved() = runBlocking {
        val deckId = newDeckId()
        insertDeck(deckId)
        val cardA = UUID.randomUUID().toString()
        val cardB = UUID.randomUUID().toString()
        insertCard(cardA, deckId, "a")
        insertCard(cardB, deckId, "b")
        assertEquals(2, database.cardDao().getCardsByDeckId(deckId).size)

        // Флаг синхронизации выключен (S14: значение по умолчанию).
        cardRepository.deleteCard(cardA)

        assertNull("S17: прямой DAO-запрос не находит карточку", database.cardDao().getCardById(cardA))
        assertEquals(
            "S17: счётчик карточек колоды уменьшился",
            listOf(cardB),
            database.cardDao().getCardsByDeckId(deckId).map { it.id },
        )
    }

    // --- S18: физическое удаление колоды каскадом ---

    @Test
    fun deleteDeck_flagOff_cascadeRemovesAllCards() = runBlocking {
        val deckId = newDeckId()
        insertDeck(deckId, "D")
        val cardIds = (1..5).map { index ->
            UUID.randomUUID().toString().also { insertCard(it, deckId, "card$index") }
        }
        assertEquals(5, database.cardDao().getCardsByDeckId(deckId).size)

        deckRepository.deleteDeck(deckId)

        assertNull("S18: строка колоды физически удалена", database.deckDao().getDeckById(deckId))
        cardIds.forEach { cardId ->
            assertNull("S18: карточка $cardId физически удалена каскадом", database.cardDao().getCardById(cardId))
        }
        assertTrue(
            "S18: у колоды не осталось карточек",
            database.cardDao().getCardsByDeckId(deckId).isEmpty(),
        )
    }

    // --- S16: ошибка чтения DataStore → физическое удаление ---

    @Test
    fun deleteCard_dataStoreReadError_deletesPhysically() = runBlocking {
        // GIVEN: чтение флага из DataStore завершается ошибкой (повреждённый файл),
        // флаг трактуется как выключенный (см. SyncFlagDataStoreTest, S16).
        // Для удаления это означает физический режим.
        val store = corruptedFlagStore()
        val flagValue = store.syncEnabled.first()
        assertFalse("S16: ошибка чтения DataStore → флаг трактуется как false", flagValue)

        val repositoryWithBrokenStore = RoomCardRepository(database.cardDao(), store)

        val deckId = newDeckId()
        insertDeck(deckId)
        val cardId = UUID.randomUUID().toString()
        insertCard(cardId, deckId)

        if (!flagValue) {
            repositoryWithBrokenStore.deleteCard(cardId)
        }

        assertNull(
            "S16: удаление выполнено физически, строки нет в БД",
            database.cardDao().getCardById(cardId),
        )

        store.close()
    }

    /** Повреждённый DataStore: мусорные байты в файле preferences_pb. */
    private fun corruptedFlagStore(): com.mnemocards.data.sync.SyncFlagDataStore {
        val dir = createTempDir(prefix = "sync_corrupted_")
        val file = java.io.File(dir, "sync_settings.preferences_pb")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x7F, 0x33, 0x33, 0x33))
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.Job())
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return object : com.mnemocards.data.sync.SyncFlagDataStore(dataStore) {
            // не закрываем DataStore напрямую: гасим scope
        }.also { created ->
            createdStoreScopes.add(scope)
        }
    }

    private val createdStoreScopes = mutableListOf<kotlinx.coroutines.CoroutineScope>()

    private fun com.mnemocards.data.sync.SyncFlagDataStore.close() {
        createdStoreScopes.forEach { it.cancel() }
        createdStoreScopes.clear()
    }

    // --- S19: мягкое удаление карточки ---

    @Test
    fun deleteCard_flagOn_rowSoftDeletedWithFlagsBumped() = runBlocking {
        val (store, scope) = newFlagStore(initial = true)
        val repository = RoomCardRepository(database.cardDao(), store)

        val deckId = newDeckId()
        insertDeck(deckId)
        val cardId = UUID.randomUUID().toString()
        // GIVEN: isChanged = true, revision = 2.
        database.cardDao().insertCard(
            CardEntity(
                id = cardId,
                deckId = deckId,
                front = "front",
                back = "back",
                intervalDays = 0,
                easeFactor = 2.5,
                dueEpochDay = 0,
                repetitions = 0,
                isChanged = true,
                revision = 2,
                isDeleted = false,
            ),
        )

        repository.deleteCard(cardId)

        val row = database.cardDao().getCardById(cardId)
        assertTrue("S19: строка осталась в БД", row != null)
        row ?: return@runBlocking
        assertTrue("S19: isDeleted = true", row.isDeleted)
        assertTrue("S19: isChanged = true", row.isChanged)
        assertEquals("S19: revision увеличился на 1", 3, row.revision)
        assertEquals("S19: данные карточки сохранены", "front", row.front)
        assertEquals("S19: данные карточки сохранены", "back", row.back)

        scope.cancel()
    }

    // --- S20: мягкое удаление колоды каскадно помечает карточки ---

    @Test
    fun deleteDeck_flagOn_deckAndCardsSoftDeleted() = runBlocking {
        val (store, scope) = newFlagStore(initial = true)
        val repository = RoomDeckRepository(
            database.deckDao(),
            database.cardDao(),
            database,
            store,
        )

        val deckId = newDeckId()
        insertDeck(deckId, "D")
        val cardIds = (1..4).map { index ->
            UUID.randomUUID().toString().also { insertCard(it, deckId, "card$index") }
        }

        repository.deleteDeck(deckId)

        val deckRow = database.deckDao().getDeckById(deckId)
        assertTrue("S20: строка колоды осталась в БД", deckRow != null)
        deckRow ?: return@runBlocking
        assertTrue("S20: колода isDeleted = true", deckRow.isDeleted)
        assertTrue("S20: колода isChanged = true", deckRow.isChanged)
        assertEquals("S20: revision колоды +1", 2, deckRow.revision)

        cardIds.forEach { cardId ->
            val card = database.cardDao().getCardById(cardId)
            assertTrue("S20: карточка $cardId осталась в БД", card != null)
            card ?: return@runBlocking
            assertTrue("S20: карточка isDeleted = true", card.isDeleted)
            assertTrue("S20: карточка isChanged = true", card.isChanged)
            assertEquals("S20: revision карточки +1", 2, card.revision)
        }
        assertEquals(
            "S20: все 5 строк физически присутствуют в БД",
            5,
            1 + database.cardDao().getCardsByDeckId(deckId).size,
        )

        scope.cancel()
    }

    // --- S23: мягко удалённая запись доступна по прямому запросу к DAO ---

    @Test
    fun softDeletedCard_directDaoQueryWithoutFilter_findsRow() = runBlocking {
        val (store, scope) = newFlagStore(initial = true)
        val repository = RoomCardRepository(database.cardDao(), store)

        val deckId = newDeckId()
        insertDeck(deckId)
        val cardId = UUID.randomUUID().toString()
        insertCard(cardId, deckId, "front")

        repository.deleteCard(cardId)

        val row = database.cardDao().getCardById(cardId)
        assertTrue("S23: прямой DAO-запрос без фильтра находит запись", row != null)
        row ?: return@runBlocking
        assertTrue("S23: isDeleted = true у найденной записи", row.isDeleted)
        assertEquals("S23: исходные данные сохранены", "front", row.front)

        scope.cancel()
    }

    // --- S24: переключение флага не трогает мягко удалённые записи ---

    @Test
    fun flagToggledOff_softDeletedRowsUntouched() = runBlocking {
        val (storeA, scopeA) = newFlagStore(initial = true)
        val softRepository = RoomCardRepository(database.cardDao(), storeA)

        val deckId = newDeckId()
        insertDeck(deckId)
        val cardId = UUID.randomUUID().toString()
        insertCard(cardId, deckId, "front", revision = 7)

        softRepository.deleteCard(cardId)
        scopeA.cancel()

        // WHEN: флаг переключается на выключенный напрямую в DataStore,
        // приложение «перезапускается» (новые экземпляры хранилища и репозитория).
        val (storeB, scopeB) = newFlagStore(initial = false)
        assertFalse("S24: флаг теперь выключен", storeB.syncEnabled.first())
        val hardRepository = RoomCardRepository(database.cardDao(), storeB)

        // THEN: мягко удалённые записи не изменяются и не восстанавливаются:
        // никаких операций над ними флаг не запускает.
        val row = database.cardDao().getCardById(cardId)
        assertTrue("S24: строка осталась в БД", row != null)
        row ?: return@runBlocking
        assertTrue("S24: isDeleted по-прежнему true", row.isDeleted)
        assertEquals("S24: revision не изменился", 8, row.revision)

        hardRepository.deleteCard(cardId)
        assertNull(
            "S24: физическое удаление новой записи флагом не управляет " +
                "уже мягко удалённой строкой — она удаляется физически только " +
                "явной операцией",
            database.cardDao().getCardById(cardId),
        )

        scopeB.cancel()
    }
}
```


---

## Батч 5/7

### FILE: app/src/test/java/com/mnemocards/data/db/DaoFilterTest.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model.
 * S21 — мягко удалённые записи скрыты со всех экранов, счётчиков и экспорта.
 * S22 — мягко удалённые карточки не участвуют в тренировке.
 * S23 — мягко удалённая запись доступна по прямому запросу к DAO.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaoFilterTest {

    private lateinit var database: MnemoCardsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertDeck(id: String, name: String = "Deck") {
        database.deckDao().insertDeck(
            DeckEntity(
                id = id,
                name = name,
                isChanged = true,
                revision = 1,
                isDeleted = false,
            ),
        )
    }

    private suspend fun insertCard(id: String, deckId: String, front: String) {
        database.cardDao().insertCard(
            CardEntity(
                id = id,
                deckId = deckId,
                front = front,
                back = "back",
                intervalDays = 0,
                easeFactor = 2.5,
                dueEpochDay = 0,
                repetitions = 0,
                isChanged = true,
                revision = 1,
                isDeleted = false,
            ),
        )
    }

    // --- S21: счётчик колоды не учитывает мягко удалённые карточки ---

    @Test
    fun deckListCounts_excludeSoftDeletedCardsAndDecks() = runBlocking {
        val deckA = java.util.UUID.randomUUID().toString()
        val deckDeleted = java.util.UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        insertDeck(deckDeleted, "Deleted")

        // 5 карточек в колоде A, 2 из них мягко удалены.
        val active = (1..3).map { java.util.UUID.randomUUID().toString() }
        val softDeleted = (1..2).map { java.util.UUID.randomUUID().toString() }
        (active + softDeleted).forEachIndexed { index, id ->
            insertCard(id, deckA, "card$index")
        }
        database.cardDao().softDeleteCardById(softDeleted[0])
        database.cardDao().softDeleteCardById(softDeleted[1])

        // Мягко удаляем вторую колоду — её не должно быть в списке.
        database.deckDao().softDeleteDeckById(deckDeleted)

        val decks = database.deckDao().observeDecksWithCardCounts().first()

        assertEquals("S21: в списке только неудалённая колода", listOf(deckA), decks.map { it.deck.id })
        val row = decks.first()
        assertEquals("S21: счётчик колоды A — 3, а не 5", 3, row.cardCount)
    }

    // --- S21: агрегирующий счётчик без мягко удалённых ---

    @Test
    fun aggregateCardCounts_excludeSoftDeleted() = runBlocking {
        val deckA = java.util.UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        val cardA = java.util.UUID.randomUUID().toString()
        val cardB = java.util.UUID.randomUUID().toString()
        insertCard(cardA, deckA, "a")
        insertCard(cardB, deckA, "b")
        database.cardDao().softDeleteCardById(cardB)

        val counts = database.deckDao().getCardCounts()

        assertEquals(
            "S21: агрегирующий запрос считает только активные карточки",
            1,
            counts.first { it.deckId == deckA }.cardCount,
        )
    }

    // --- S21: экспортный запрос без мягко удалённых ---

    @Test
    fun exportQuery_excludeSoftDeletedCards() = runBlocking {
        val deckA = java.util.UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        val keep = java.util.UUID.randomUUID().toString()
        val remove = java.util.UUID.randomUUID().toString()
        insertCard(keep, deckA, "keep")
        insertCard(remove, deckA, "remove")
        database.cardDao().softDeleteCardById(remove)

        val exported = database.cardDao().getActiveCardsByDeckId(deckA)

        assertEquals("S21: экспорт не содержит мягко удалённые карточки", listOf(keep), exported.map { it.id })
    }

    // --- S22: карточки для сессии тренировки без мягко удалённых ---

    @Test
    fun sessionCardsQuery_excludesSoftDeleted() = runBlocking {
        val deckA = java.util.UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        val cardIds = (1..6).map { java.util.UUID.randomUUID().toString() }
        cardIds.forEachIndexed { index, id -> insertCard(id, deckA, "card$index") }
        database.cardDao().softDeleteCardById(cardIds[0])

        val sessionCards = database.cardDao().observeActiveCardsByDeckId(deckA).first()

        assertEquals("S22: сессия состоит из 5 карточек", 5, sessionCards.size)
        assertTrue(
            "S22: мягко удалённая карточка не показывается",
            sessionCards.none { it.id == cardIds[0] },
        )
    }

    // --- S23: прямой запрос по-прежнему находит мягко удалённую запись ---

    @Test
    fun getById_withoutFilter_findsSoftDeletedRow() = runBlocking {
        val deckA = java.util.UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        val cardId = java.util.UUID.randomUUID().toString()
        insertCard(cardId, deckA, "front")
        database.cardDao().softDeleteCardById(cardId)
        database.deckDao().softDeleteDeckById(deckA)

        val card = database.cardDao().getCardById(cardId)
        assertNotNull("S23: карточка найдена прямым запросом", card)
        assertTrue("S23: isDeleted = true у найденной карточки", card!!.isDeleted)
        assertEquals("S23: исходные данные сохранены", "front", card.front)

        val deck = database.deckDao().getDeckById(deckA)
        assertNotNull("S23: колода найдена прямым запросом", deck)
        assertTrue("S23: isDeleted = true у найденной колоды", deck!!.isDeleted)
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/db/SeedingTest.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model.
 * S5 — чистая установка: БД создаётся сразу в новой схеме; идемпотентный
 *        сидинг создаёт базовую колоду из 5 карточек с клиентскими UUID,
 *        isChanged = true, revision = 1; колода видна в списке со счётчиком 5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedingTest {

    private lateinit var database: MnemoCardsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cleanInstall_seedingCreatesDeckWithFiveCardsAndClientUuids() = runBlocking {
        DatabaseSeeder.seedIfNeeded(database.deckDao(), database.cardDao())

        val decks = database.deckDao().observeDecksWithCardCounts().first()
        assertEquals("S5: сидинг создаёт ровно одну колоду", 1, decks.size)

        val deckRow = decks.first()
        assertEquals("S5: колода видна в списке со счётчиком 5", 5, deckRow.cardCount)

        // id записи — корректный UUID, сгенерированный на клиенте.
        val deckUuid = runCatching { java.util.UUID.fromString(deckRow.deck.id) }
        assertTrue("S5: id колоды — валидный UUID", deckUuid.isSuccess)

        val cards = database.cardDao().getCardsByDeckId(deckRow.deck.id)
        assertEquals("S5: в колоде 5 карточек", 5, cards.size)
        assertTrue(
            "S5: id всех карточек — валидные UUID",
            cards.all { runCatching { java.util.UUID.fromString(it.id) }.isSuccess },
        )
        assertEquals(
            "S5: UUID карточек уникальны",
            5,
            cards.map { it.id }.toSet().size,
        )
        assertEquals(
            "S5: ссылка карточки на колоду — UUID колоды",
            deckRow.deck.id,
            cards.first().deckId,
        )
        assertTrue("S5: карточки isChanged = true", cards.all { it.isChanged })
        assertEquals("S5: карточки revision = 1", listOf(1, 1, 1, 1, 1), cards.map { it.revision })
        assertFalse("S5: карточки isDeleted = false", cards.any { it.isDeleted })
        assertTrue("S5: колода isChanged = true", deckRow.deck.isChanged)
        assertEquals("S5: колода revision = 1", 1, deckRow.deck.revision)
        assertFalse("S5: колода isDeleted = false", deckRow.deck.isDeleted)
    }

    @Test
    fun repeatedSeeding_doesNotCreateDuplicates() = runBlocking {
        DatabaseSeeder.seedIfNeeded(database.deckDao(), database.cardDao())
        DatabaseSeeder.seedIfNeeded(database.deckDao(), database.cardDao())
        DatabaseSeeder.seedIfNeeded(database.deckDao(), database.cardDao())

        val decks = database.deckDao().observeDecksWithCardCounts().first()
        assertEquals("S5: повторный сидинг не дублирует колоду", 1, decks.size)
        assertEquals("S5: счётчик карточек после повторов — 5", 5, decks.first().cardCount)
        assertEquals(
            "S5: физических строк карточек не больше 5",
            5,
            database.cardDao().getCardsByDeckId(decks.first().deck.id).size,
        )
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity)

    /** Редактирование всегда поднимает isChanged и инкрементирует revision (S11, S13). */
    @Query("UPDATE decks SET name = :name, isChanged = 1, revision = revision + 1 WHERE id = :id")
    suspend fun updateDeckName(id: String, name: String)

    /** Прямой запрос без фильтра isDeleted: мягко удалённая колода находится (S23). */
    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: String): DeckEntity?

    @Query("SELECT * FROM decks WHERE id = :id")
    fun observeDeckById(id: String): Flow<DeckEntity?>

    /** UI: список колод — без мягко удалённых (S21). */
    @Query("SELECT * FROM decks WHERE isDeleted = 0 ORDER BY name")
    fun observeDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE isDeleted = 0 ORDER BY name")
    suspend fun getActiveDecks(): List<DeckEntity>

    /**
     * UI: список колод со счётчиками. Фильтр isDeleted = 0 и в колодах,
     * и в подзапросе счётчика — мягко удалённые не отображаются и не
     * участвуют в счётчиках (S21).
     */
    @Query(
        """
        SELECT decks.*, (
            SELECT COUNT(*) FROM cards
            WHERE cards.deckId = decks.id AND cards.isDeleted = 0
        ) AS cardCount
        FROM decks
        WHERE decks.isDeleted = 0
        ORDER BY decks.name
        """,
    )
    fun observeDecksWithCardCounts(): Flow<List<DeckWithCardCount>>

    /** Агрегирующий запрос счётчиков — без мягко удалённых карточек (S21). */
    @Query(
        """
        SELECT cards.deckId AS deckId, COUNT(*) AS cardCount
        FROM cards
        WHERE cards.isDeleted = 0
        GROUP BY cards.deckId
        """,
    )
    suspend fun getCardCounts(): List<DeckWithCardCounts>

    /** Физическое удаление (каскад карточек — на уровне БД, S18). */
    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeckById(id: String)

    /** Мягкое удаление колоды: isDeleted = 1, isChanged = 1, revision +1 (S20). */
    @Query("UPDATE decks SET isDeleted = 1, isChanged = 1, revision = revision + 1 WHERE id = :id")
    suspend fun softDeleteDeckById(id: String)

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun countDecks(): Int
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/CardDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>)

    /** Редактирование всегда поднимает isChanged и инкрементирует revision (S11, S13). */
    @Query("UPDATE cards SET front = :front, back = :back, isChanged = 1, revision = revision + 1 WHERE id = :id")
    suspend fun updateCard(id: String, front: String, back: String)

    /** Прямой запрос без фильтра isDeleted: мягко удалённая карточка находится (S23). */
    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getCardById(id: String): CardEntity?

    /** Прямой запрос без фильтра isDeleted — используется в тестах/синхронизации. */
    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY front")
    suspend fun getCardsByDeckId(deckId: String): List<CardEntity>

    /** UI: карточки колоды для сессии тренировки — без мягко удалённых (S22). */
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND isDeleted = 0 ORDER BY front")
    fun observeActiveCardsByDeckId(deckId: String): Flow<List<CardEntity>>

    /** Экспорт и другие агрегирующие выборки — без мягко удалённых (S21). */
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND isDeleted = 0 ORDER BY front")
    suspend fun getActiveCardsByDeckId(deckId: String): List<CardEntity>

    /** Физическое удаление строки карточки (S17). */
    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCardById(id: String)

    /** Мягкое удаление карточки: isDeleted = 1, isChanged = 1, revision +1 (S19). */
    @Query("UPDATE cards SET isDeleted = 1, isChanged = 1, revision = revision + 1 WHERE id = :id")
    suspend fun softDeleteCardById(id: String)

    /** Каскадное мягкое удаление карточек колоды (S20). */
    @Query(
        "UPDATE cards SET isDeleted = 1, isChanged = 1, revision = revision + 1 " +
            "WHERE deckId = :deckId AND isDeleted = 0",
    )
    suspend fun softDeleteCardsByDeckId(deckId: String)

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun countCards(): Int
}
```


---

## Батч 6/7

### FILE: app/src/main/java/com/mnemocards/data/db/DeckEntity.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isChanged: Boolean,
    val revision: Int,
    val isDeleted: Boolean,
)
```

### FILE: app/src/main/java/com/mnemocards/data/db/CardEntity.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deckId")],
)
data class CardEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val intervalDays: Int,
    val easeFactor: Double,
    val dueEpochDay: Long,
    val repetitions: Int,
    val isChanged: Boolean,
    val revision: Int,
    val isDeleted: Boolean,
)
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckWithCardCount.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Embedded

data class DeckWithCardCount(
    @Embedded val deck: DeckEntity,
    val cardCount: Int,
)
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckWithCardCounts.kt
```kotlin
package com.mnemocards.data.db

data class DeckWithCardCounts(
    val deckId: String,
    val cardCount: Int,
)
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity)

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: String): DeckEntity?

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeckById(id: String)

    @Query(
        "UPDATE decks SET isDeleted = 1, isChanged = 1, revision = revision + 1 " +
            "WHERE id = :id",
    )
    suspend fun softDeleteDeckById(id: String)

    @Query(
        "SELECT d.*, COUNT(c.id) AS cardCount FROM decks d " +
            "LEFT JOIN cards c ON c.deckId = d.id AND c.isDeleted = 0 " +
            "WHERE d.isDeleted = 0 GROUP BY d.id",
    )
    fun observeDecksWithCardCounts(): Flow<List<DeckWithCardCount>>

    @Query(
        "SELECT c.deckId AS deckId, COUNT(c.id) AS cardCount FROM cards c " +
            "WHERE c.isDeleted = 0 GROUP BY c.deckId",
    )
    suspend fun getCardCounts(): List<DeckWithCardCounts>
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/CardDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity)

    @Update
    suspend fun updateCard(card: CardEntity)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getCardById(id: String): CardEntity?

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCardById(id: String)

    @Query(
        "UPDATE cards SET isDeleted = 1, isChanged = 1, revision = revision + 1 " +
            "WHERE id = :id",
    )
    suspend fun softDeleteCardById(id: String)

    @Query(
        "UPDATE cards SET isDeleted = 1, isChanged = 1, revision = revision + 1 " +
            "WHERE deckId = :deckId AND isDeleted = 0",
    )
    suspend fun softDeleteCardsByDeckId(deckId: String)

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    suspend fun getCardsByDeckId(deckId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deckId = :deckId AND isDeleted = 0")
    suspend fun getActiveCardsByDeckId(deckId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deckId = :deckId AND isDeleted = 0")
    fun observeActiveCardsByDeckId(deckId: String): Flow<List<CardEntity>>
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DeckEntity::class, CardEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class MnemoCardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao

    companion object {
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(
            MigrationFrom1To2(),
        )
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DatabaseSeeder.kt
```kotlin
package com.mnemocards.data.db

import java.time.LocalDate
import java.util.UUID

/**
 * Идемпотентный сидинг базовой колоды при первом запуске.
 *
 * Идемпотентность обеспечивается фиксированным UUID базовой колоды:
 * повторные вызовы не создают дубликатов. Карточки получают клиентские
 * UUID (S5: каждой записи приложение само генерирует UUID).
 */
object DatabaseSeeder {

    /** Фиксированный идентификатор базовой колоды — основа идемпотентности. */
    const val BASE_DECK_ID: String = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

    private val BASE_CARDS: List<Pair<String, String>> = listOf(
        "cat" to "кот",
        "dog" to "собака",
        "house" to "дом",
        "tree" to "дерево",
        "book" to "книга",
    )

    suspend fun seedIfNeeded(deckDao: DeckDao, cardDao: CardDao) {
        // S5: колода уже засеяна — ничего не делаем.
        if (deckDao.getDeckById(BASE_DECK_ID) != null) return

        deckDao.insertDeck(
            DeckEntity(
                id = BASE_DECK_ID,
                name = "Базовая колода",
                isChanged = true,
                revision = 1,
                isDeleted = false,
            ),
        )

        val todayEpochDay = LocalDate.now().toEpochDay()
        BASE_CARDS.forEach { (front, back) ->
            cardDao.insertCard(
                CardEntity(
                    id = UUID.randomUUID().toString(),
                    deckId = BASE_DECK_ID,
                    front = front,
                    back = back,
                    intervalDays = 0,
                    easeFactor = 2.5,
                    dueEpochDay = todayEpochDay,
                    repetitions = 0,
                    isChanged = true,
                    revision = 1,
                    isDeleted = false,
                ),
            )
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/domain/model/Card.kt
```kotlin
package com.mnemocards.domain.model

data class Card(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val intervalDays: Int,
    val easeFactor: Double,
    val dueEpochDay: Long,
    val repetitions: Int,
)
```

### FILE: app/src/main/java/com/mnemocards/domain/model/DeckCounts.kt
```kotlin
package com.mnemocards.domain.model

data class DeckCounts(
    val deckId: String,
    val deckName: String,
    val cardCount: Int,
)
```

### FILE: app/src/main/java/com/mnemocards/data/repository/CardRepository.kt
```kotlin
package com.mnemocards.data.repository

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.sync.SyncFlagDataStore
import com.mnemocards.domain.model.Card
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

sealed interface CardOperationResult {
    data class Success(val card: Card) : CardOperationResult
    data class Failure(val error: Throwable) : CardOperationResult
}

sealed interface CardImportResult {
    data class Success(val imported: Int) : CardImportResult
    data class Failure(val error: Throwable) : CardImportResult
}

interface CardRepository {
    fun observeCardsByDeck(deckId: String): Flow<List<Card>>
    suspend fun getCardsByDeck(deckId: String): List<Card>
    suspend fun createCard(deckId: String, front: String, back: String): CardOperationResult
    suspend fun updateCard(card: Card): CardOperationResult
    suspend fun deleteCard(cardId: String)
    suspend fun importCsv(deckId: String, csv: String): CardImportResult
    suspend fun exportCsv(deckId: String): String
}

class RoomCardRepository(
    private val cardDao: CardDao,
    private val syncFlagDataStore: SyncFlagDataStore? = null,
) : CardRepository {

    override fun observeCardsByDeck(deckId: String): Flow<List<Card>> =
        cardDao.observeActiveCardsByDeckId(deckId).map { rows -> rows.map(::toDomain) }

    override suspend fun getCardsByDeck(deckId: String): List<Card> =
        cardDao.getActiveCardsByDeckId(deckId).map(::toDomain)

    override suspend fun createCard(
        deckId: String,
        front: String,
        back: String,
    ): CardOperationResult = runCatching {
        // S6/S10: идентификатор — клиентский UUID; isChanged = true всегда.
        val entity = CardEntity(
            id = UUID.randomUUID().toString(),
            deckId = deckId,
            front = front,
            back = back,
            intervalDays = 0,
            easeFactor = 2.5,
            dueEpochDay = java.time.LocalDate.now().toEpochDay(),
            repetitions = 0,
            isChanged = true,
            revision = 1,
            isDeleted = false,
        )
        cardDao.insertCard(entity)
        toDomain(entity)
    }.fold(
        onSuccess = { CardOperationResult.Success(it) },
        onFailure = { CardOperationResult.Failure(it) },
    )

    override suspend fun updateCard(card: Card): CardOperationResult = runCatching {
        val row = cardDao.getCardById(card.id)
            ?: error("Card ${card.id} not found")
        // S11/S13: изменение всегда ставит isChanged = true и revision + 1.
        val updated = row.copy(
            front = card.front,
            back = card.back,
            intervalDays = card.intervalDays,
            easeFactor = card.easeFactor,
            dueEpochDay = card.dueEpochDay,
            repetitions = card.repetitions,
            isChanged = true,
            revision = row.revision + 1,
        )
        cardDao.updateCard(updated)
        toDomain(updated)
    }.fold(
        onSuccess = { CardOperationResult.Success(it) },
        onFailure = { CardOperationResult.Failure(it) },
    )

    override suspend fun deleteCard(cardId: String) {
        val soft = isSyncEnabled()
        if (soft) {
            // S19: мягкое удаление — строка остаётся с пометками.
            cardDao.softDeleteCardById(cardId)
        } else {
            // S17: физическое удаление.
            cardDao.deleteCardById(cardId)
        }
    }

    override suspend fun importCsv(deckId: String, csv: String): CardImportResult = runCatching {
        csv.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val idx = line.indexOf(',')
                if (idx <= 0 || idx == line.length - 1) null
                else line.substring(0, idx) to line.substring(idx + 1)
            }
            .map { (front, back) ->
                cardDao.insertCard(
                    CardEntity(
                        id = UUID.randomUUID().toString(),
                        deckId = deckId,
                        front = front,
                        back = back,
                        intervalDays = 0,
                        easeFactor = 2.5,
                        dueEpochDay = java.time.LocalDate.now().toEpochDay(),
                        repetitions = 0,
                        isChanged = true,
                        revision = 1,
                        isDeleted = false,
                    ),
                )
            }
            .count()
    }.fold(
        onSuccess = { CardImportResult.Success(it) },
        onFailure = { CardImportResult.Failure(it) },
    )

    override suspend fun exportCsv(deckId: String): String =
        cardDao.getActiveCardsByDeckId(deckId).joinToString("\n") { "${it.front},${it.back}" }

    /**
     * S16: любая ошибка чтения DataStore трактуется как выключенный флаг —
     * удаление выполняется в дефолтном (физическом) режиме.
     */
    private suspend fun isSyncEnabled(): Boolean =
        syncFlagDataStore?.let { store ->
            runCatching { store.syncEnabled.first() }.getOrDefault(false)
        } ?: false

    private fun toDomain(entity: CardEntity) = Card(
        id = entity.id,
        deckId = entity.deckId,
        front = entity.front,
        back = entity.back,
        intervalDays = entity.intervalDays,
        easeFactor = entity.easeFactor,
        dueEpochDay = entity.dueEpochDay,
        repetitions = entity.repetitions,
    )
}
```

### FILE: app/src/main/java/com/mnemocards/data/RoomDeckRepository.kt
```kotlin
package com.mnemocards.data

import androidx.room.withTransaction
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.sync.SyncFlagDataStore
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import java.util.UUID
import kotlinx.coroutines.flow.first

class RoomDeckRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao? = null,
    private val database: MnemoCardsDatabase? = null,
    private val syncFlagDataStore: SyncFlagDataStore? = null,
) : DeckRepository {

    override suspend fun createDeck(name: String): Deck {
        // S6/S10: клиентский UUID, isChanged = true, revision = 1.
        val entity = DeckEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            isChanged = true,
            revision = 1,
            isDeleted = false,
        )
        deckDao.insertDeck(entity)
        return Deck(entity.id, entity.name)
    }

    override suspend fun updateDeck(deck: Deck) {
        val row = deckDao.getDeckById(deck.id) ?: return
        // S11/S13: isChanged = true, revision + 1.
        deckDao.updateDeck(
            row.copy(
                name = deck.name,
                isChanged = true,
                revision = row.revision + 1,
            ),
        )
    }

    override suspend fun deleteDeck(id: String) {
        val soft = isSyncEnabled()
        if (soft) {
            // S20: мягкое удаление колоды каскадно помечает карточки.
            val dao = cardDao
            if (dao != null && database != null) {
                database.withTransaction {
                    deckDao.softDeleteDeckById(id)
                    dao.softDeleteCardsByDeckId(id)
                }
            } else {
                deckDao.softDeleteDeckById(id)
                dao?.softDeleteCardsByDeckId(id)
            }
        } else {
            // S18: физическое удаление, карточки уходят каскадом по FK.
            deckDao.deleteDeckById(id)
        }
    }

    override suspend fun getDeck(id: String): Deck? =
        deckDao.getDeckById(id)?.let { Deck(it.id, it.name) }

    /**
     * S16: ошибка чтения DataStore трактуется как выключенный флаг —
     * физический режим удаления.
     */
    private suspend fun isSyncEnabled(): Boolean =
        syncFlagDataStore?.let { store ->
            runCatching { store.syncEnabled.first() }.getOrDefault(false)
        } ?: false
}
```

### FILE: app/src/main/java/com/mnemocards/data/CardCountRepository.kt
```kotlin
package com.mnemocards.data

import com.mnemocards.data.db.DeckDao
import com.mnemocards.domain.model.DeckCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DeckCountsRepository {
    fun observeDecks(): Flow<List<DeckCounts>>
}

class RoomDeckCountsRepository(
    private val deckDao: DeckDao,
) : DeckCountsRepository {

    override fun observeDecks(): Flow<List<DeckCounts>> =
        deckDao.observeDecksWithCardCounts().map { rows ->
            rows.map { row ->
                DeckCounts(
                    deckId = row.deck.id,
                    deckName = row.deck.name,
                    cardCount = row.cardCount,
                )
            }
        }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewUiState.kt
```kotlin
package com.mnemocards.ui.review

sealed interface ReviewUiState {
    object Loading : ReviewUiState

    data class Question(
        val front: String,
        val back: String,
        val flipped: Boolean,
        val position: Int,
        val total: Int,
    ) : ReviewUiState

    /** Терминальное состояние «на сегодня всё». */
    object Done : ReviewUiState
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt
```kotlin
package com.mnemocards.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Сессия тренировки колоды. Состояние переживает смену конфигурации
 * (позиция и переворот живут в ViewModel).
 *
 * S7: идентификатор колоды приходит строкой (UUID).
 * S8: невалидная строка ведёт к терминальному состоянию, не к исключению.
 * S9: несуществующая колода ведёт себя как отсутствующая.
 * S25: если Flow эмитит пустой список (колоду мягко удалили),
 *      сессия корректно завершается терминальным состоянием.
 */
class ReviewViewModel(
    cardRepository: CardRepository,
    deckId: String,
) : ViewModel() {

    private val isUuid: Boolean = runCatching { UUID.fromString(deckId) }.isSuccess

    private val cards: Flow<List<Card>> =
        if (isUuid) cardRepository.observeCardsByDeck(deckId) else flowOf(emptyList())

    private val position = MutableStateFlow(0)
    private val flipped = MutableStateFlow(false)

    val uiState: StateFlow<ReviewUiState> = combine(
        cards,
        position,
        flipped,
    ) { list, pos, isFlipped ->
        if (pos >= list.size) {
            ReviewUiState.Done
        } else {
            ReviewUiState.Question(
                front = list[pos].front,
                back = list[pos].back,
                flipped = isFlipped,
                position = pos,
                total = list.size,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ReviewUiState.Loading,
    )

    fun flip() {
        flipped.value = true
    }

    /**
     * Оценка ответа: продвигает сессию к следующей карточке.
     * В этой фиче оценка не изменяет данные (см. MAIN.md, capability cards.review).
     */
    fun grade(grade: ReviewGrade) {
        flipped.value = false
        position.value += 1
    }
}

class ReviewViewModelFactory(
    private val cardRepository: CardRepository,
    private val deckId: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReviewViewModel(cardRepository, deckId) as T
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModel.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.DeckCountsRepository
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.DeckCounts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeckListItem(
    val id: String,
    val name: String,
    val cardCount: Int,
)

sealed interface DeckListUiState {
    object Loading : DeckListUiState

    interface Content : DeckListUiState {
        val decks: List<DeckListItem>
    }

    /** S26: ошибка БД (аналог 500) — состояние ошибки с возможностью повтора. */
    data class Error(val message: String? = null) : DeckListUiState
}

class DeckListUiStateImpl(
    override val decks: List<DeckListItem>,
) : DeckListUiState.Content

@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModel(
    private val deckCountsRepository: DeckCountsRepository,
    private val deckRepository: DeckRepository,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<DeckListUiState> = retryTrigger
        .flatMapLatest {
            deckCountsRepository.observeDecks()
                .map<List<DeckCounts>, DeckListUiState> { decks ->
                    DeckListUiStateImpl(
                        decks.map { DeckListItem(it.deckId, it.deckName, it.cardCount) },
                    )
                }
                .catch { emit(DeckListUiState.Error(it.message)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckListUiState.Loading,
        )

    /** S26: повторная попытка загрузки после ошибки. */
    fun retry() {
        retryTrigger.value += 1
    }

    fun createDeck(name: String) {
        viewModelScope.launch { deckRepository.createDeck(name) }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch { deckRepository.deleteDeck(deckId) }
    }
}
```

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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mnemocards.MnemoCardsApplication
import com.mnemocards.ui.decklist.DeckListScreen
import com.mnemocards.ui.decklist.DeckListViewModel
import com.mnemocards.ui.review.ReviewScreen
import com.mnemocards.ui.review.ReviewViewModel
import com.mnemocards.ui.review.ReviewViewModelFactory

object Routes {
    const val DECKS = "decks"
    const val REVIEW = "review/{deckId}"

    /** S7: идентификатор колоды передаётся строкой (UUID). */
    fun review(deckId: String): String = "review/$deckId"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val container = (context.applicationContext as MnemoCardsApplication).container

    NavHost(
        navController = navController,
        startDestination = Routes.DECKS,
    ) {
        composable(Routes.DECKS) {
            val deckListViewModel: DeckListViewModel = viewModel {
                DeckListViewModel(
                    deckCountsRepository = container.deckCountsRepository,
                    deckRepository = container.deckRepository,
                )
            }
            DeckListScreen(
                viewModel = deckListViewModel,
                onDeckClick = { deckId ->
                    navController.navigate(Routes.review(deckId))
                },
            )
        }
        composable(
            route = Routes.REVIEW,
            arguments = listOf(
                navArgument("deckId") { type = NavType.StringType },
            ),
        ) { entry ->
            val deckId = entry.arguments?.getString("deckId").orEmpty()
            val reviewViewModel: ReviewViewModel = viewModel(
                key = deckId,
                factory = ReviewViewModelFactory(
                    cardRepository = container.cardRepository,
                    deckId = deckId,
                ),
            )
            ReviewScreen(
                viewModel = reviewViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt
```kotlin
package com.mnemocards.ui.review

import com.mnemocards.data.repository.CardImportResult
import com.mnemocards.data.repository.CardOperationResult
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Спека: prepare-sync-data-model.
 * S7  — навигация передаёт deckId строкой, сессия работает как раньше.
 * S8  — невалидный аргумент (аналог 400) не роняет экран, терминальное состояние.
 * S9  — несуществующая колода (аналог 404) ведёт себя как отсутствующая.
 * S25 — колоду мягко удалили во время сессии: Flow пустеет,
 *       сессия завершается терминально без краха.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeCardRepository(
        val cards: MutableStateFlow<List<Card>> = MutableStateFlow(emptyList()),
    ) : CardRepository {
        override fun observeCardsByDeck(deckId: String): Flow<List<Card>> = cards
        override suspend fun getCardsByDeck(deckId: String): List<Card> = cards.value
        override suspend fun createCard(
            deckId: String,
            front: String,
            back: String,
        ): CardOperationResult = CardOperationResult.Failure(UnsupportedOperationException())
        override suspend fun updateCard(card: Card): CardOperationResult =
            CardOperationResult.Failure(UnsupportedOperationException())
        override suspend fun deleteCard(cardId: String) = Unit
        override suspend fun importCsv(deckId: String, csv: String): CardImportResult =
            CardImportResult.Success(0)
        override suspend fun exportCsv(deckId: String): String = ""
    }

    private fun card(front: String, back: String = "back"): Card = Card(
        id = UUID.randomUUID().toString(),
        deckId = UUID.randomUUID().toString(),
        front = front,
        back = back,
        intervalDays = 0,
        easeFactor = 2.5,
        dueEpochDay = 0,
        repetitions = 0,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- S7: валидный UUID — колода загружается, сессия работает ---

    @Test
    fun validUuid_deckLoaded_sessionWorksAsBefore() = runTest(dispatcher) {
        val repository = FakeCardRepository(
            MutableStateFlow(listOf(card("cat", "кот"), card("dog", "собака"))),
        )
        val viewModel = ReviewViewModel(repository, UUID.randomUUID().toString())
        advanceUntilIdle()

        val first = viewModel.uiState.value as ReviewUiState.Question
        assertEquals("S7: показана лицевая сторона первой карточки", "cat", first.front)
        assertEquals("S7: счётчик карточек сессии", 2, first.total)
        assertEquals("S7: позиция 0", 0, first.position)
        assertFalse("S7: карточка не перевёрнута на старте", first.flipped)

        viewModel.flip()
        val flipped = viewModel.uiState.value as ReviewUiState.Question
        assertTrue("S7: переворот по тапу", flipped.flipped)
        assertEquals("S7: обратная сторона", "кот", flipped.back)

        viewModel.grade(ReviewGrade.entries.first())
        val second = viewModel.uiState.value as ReviewUiState.Question
        assertEquals("S7: переход к следующей карточке", 1, second.position)
        assertEquals("S7: следующая карточка не перевёрнута", false, second.flipped)

        viewModel.grade(ReviewGrade.entries.first())
        assertTrue(
            "S7: после последней карточки — терминальное состояние",
            viewModel.uiState.value is ReviewUiState.Done,
        )
    }

    // --- S8: невалидный аргумент навигации ---

    @Test
    fun invalidDeckId_numericString_noExceptionTerminalState() = runTest(dispatcher) {
        val repository = FakeCardRepository()
        val viewModel = ReviewViewModel(repository, "1")
        advanceUntilIdle()

        assertTrue(
            "S8: невалидный «1» — терминальное состояние, не исключение",
            viewModel.uiState.value is ReviewUiState.Done,
        )
    }

    @Test
    fun invalidDeckId_garbageString_noExceptionTerminalState() = runTest(dispatcher) {
        val repository = FakeCardRepository()
        val viewModel = ReviewViewModel(repository, "abc")
        advanceUntilIdle()

        assertTrue(
            "S8: невалидный «abc» — терминальное состояние, не исключение",
            viewModel.uiState.value is ReviewUiState.Done,
        )
    }

    // --- S9: несуществующая колода ведёт себя как отсутствующая ---

    @Test
    fun nonexistentDeckId_behavesAsMissingDeck() = runTest(dispatcher) {
        val repository = FakeCardRepository(MutableStateFlow(emptyList()))
        val viewModel = ReviewViewModel(repository, UUID.randomUUID().toString())
        advanceUntilIdle()

        assertTrue(
            "S9: несуществующая колода — терминальное состояние без краха",
            viewModel.uiState.value is ReviewUiState.Done,
        )
    }

    // --- S25: удаление колоды во время открытой сессии ---

    @Test
    fun deckSoftDeletedMidSession_flowEmitsEmpty_sessionTerminatesGracefully() =
        runTest(dispatcher) {
            val repository = FakeCardRepository(
                MutableStateFlow(listOf(card("a"), card("b"), card("c"))),
            )
            val viewModel = ReviewViewModel(repository, UUID.randomUUID().toString())
            advanceUntilIdle()

            assertTrue(
                "S25: сессия открыта",
                viewModel.uiState.value is ReviewUiState.Question,
            )

            // Колоду мягко удалили: Flow эмитит список без карточек.
            repository.cards.value = emptyList()
            advanceUntilIdle()

            assertTrue(
                "S25: сессия корректно завершается терминальным состоянием",
                viewModel.uiState.value is ReviewUiState.Done,
            )
        }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/decklist/DeckListViewModelTest.kt
```kotlin
package com.mnemocards.ui.decklist

import com.mnemocards.data.DeckCountsRepository
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.DeckCounts
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Спека: prepare-sync-data-model.
 * S26 — ошибка БД при загрузке списка колод (аналог 500):
 *       состояние ошибки, приложение не падает, повтор попытки доступен.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeDeckRepository : DeckRepository {
        override suspend fun createDeck(name: String): Deck =
            Deck(UUID.randomUUID().toString(), name)

        override suspend fun updateDeck(deck: Deck) = Unit
        override suspend fun deleteDeck(id: String) = Unit
        override suspend fun getDeck(id: String): Deck? = null
    }

    // --- S26: ошибка БД → состояние ошибки, повтор доступен ---

    @Test
    fun databaseError_showsErrorStateAndRetryRecovers() = runTest(dispatcher) {
        var attempts = 0
        val repository = object : DeckCountsRepository {
            override fun observeDecks(): Flow<List<DeckCounts>> = flow {
                attempts++
                if (attempts == 1) {
                    throw RuntimeException("database disk I/O error")
                } else {
                    emit(
                        listOf(
                            DeckCounts(
                                deckId = UUID.randomUUID().toString(),
                                deckName = "A",
                                cardCount = 3,
                            ),
                        ),
                    )
                }
            }
        }
        val viewModel = DeckListViewModel(repository, FakeDeckRepository())

        assertTrue(
            "S26: ошибка БД переводит экран в состояние ошибки",
            viewModel.uiState.value is DeckListUiState.Error,
        )

        // Пользователь повторяет попытку — загрузка успешна.
        viewModel.retry()
        advanceUntilIdle()

        val content = viewModel.uiState.value as DeckListUiState.Content
        assertEquals("S26: после повтора список загружен", 1, content.decks.size)
        assertEquals("S26: счётчик карточек на месте", 3, content.decks.first().cardCount)
        assertEquals(2, attempts)
    }

    // --- Регрессия: обычная загрузка списка (S21: только активные колоды) ---

    @Test
    fun decksLoaded_contentStateShown() = runTest(dispatcher) {
        val deckId = UUID.randomUUID().toString()
        val repository = object : DeckCountsRepository {
            override fun observeDecks(): Flow<List<DeckCounts>> = flow {
                emit(listOf(DeckCounts(deckId, "A", 5)))
            }
        }
        val viewModel = DeckListViewModel(repository, FakeDeckRepository())
        advanceUntilIdle()

        val content = viewModel.uiState.value as DeckListUiState.Content
        assertEquals(listOf(DeckListItem(deckId, "A", 5)), content.decks)
    }
}
```


---

## Батч 7/7

### FILE: MAIN.md
```
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

  **Обновлена**, change `prepare-sync-data-model` (2026-09-16): идентификатор
  колоды передаётся в навигации строкой (UUID); невалидный аргумент (аналог 400)
  и отсутствующая колода (аналог 404) завершают сессию терминальным состоянием
  без краха; мягкое удаление колоды во время открытой сессии корректно
  завершает её через Flow; мягко удалённые карточки не показываются в сессии;
  вёрстка и поведение экранов не изменены.
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

  **Обновлена**, change `prepare-sync-data-model` (2026-09-16): колоды и карточки
  переведены на клиентские UUID (миграция БД 1→2 сохраняет записи, статистику
  и пересчитывает ссылки карточек на колоды; чистая установка создаёт схему
  сразу в новой версии, сидинг генерирует UUID и ставит isChanged = true,
  revision = 1); записи несут признаки `isChanged` / `revision` / `isDeleted`
  (создание и изменение всегда ставят isChanged = true и инкрементируют
  revision; признак переживает перезапуск и ничем не снимается); мягко
  удалённые записи скрыты со всех экранов, счётчиков и экспорта, доступны
  только прямым запросом к DAO; мигрированные записи помечаются как локально
  изменённые.
- capability `sync.background` — фоновая синхронизация колод с сервером по REST.

  **Подготовлена (groundwork)**, change `prepare-sync-data-model` (2026-09-16),
  сетевого слоя ещё нет: персистентный флаг синхронизации в Preferences
  DataStore (по умолчанию выключен, переживает перезапуск, UI для переключения
  отсутствует — им будет управлять будущий экран настроек сервера); ветвление
  удаления по флагу — при выключенном флаге физическое (каскадное для колод),
  при включённом мягкое (isDeleted = true, isChanged = true, revision +1,
  каскадная пометка карточек колоды; при ошибке чтения DataStore — аналог 500 —
  удаление выполняется физически); переключение флага не трогает мягко
  удалённые записи. Сама синхронизация, отправка признака isChanged и очистка
  мягко удалённых записей — предмет будущих изменений.

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
