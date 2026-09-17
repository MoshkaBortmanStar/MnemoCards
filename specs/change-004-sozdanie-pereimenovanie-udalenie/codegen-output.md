## Батч 1/6

### FILE: app/src/test/java/com/mnemocards/data/db/DeckDaoTest.kt
```kotlin
package com.mnemocards.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Тесты мутаций DeckDao: вставка, переименование, каскадное удаление,
 * сосуществование дубликатов названий (S1, S4, S8, S12, S20).
 */
@RunWith(RobolectricTestRunner::class)
class DeckDaoTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- Вспомогательные операции над карточками через raw SQL,
    // --- чтобы тест DeckDao не зависел от методов CardDao.

    private fun insertCard(deckId: Long, front: String, back: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO cards (deckId, front, back) VALUES (?, ?, ?)",
            arrayOf(deckId, front, back)
        )
    }

    private fun cardCount(deckId: Long): Int {
        db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM cards WHERE deckId = ?",
            arrayOf(deckId.toString())
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun deckRowCount(): Int {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM decks").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun deckName(id: Long): String? {
        db.openHelper.readableDatabase.query(
            "SELECT name FROM decks WHERE id = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
            return null
        }
    }

    // S1: вставка колоды → появилась строка в таблице, возвращён id
    @Test
    fun insertDeck_createsRowAndReturnsId() = runTest {
        val id = deckDao.insertDeck(DeckEntity(name = "Английские глаголы"))

        assertTrue(id > 0)
        assertEquals(1, deckRowCount())
        assertEquals("Английские глаголы", deckName(id))
    }

    // S8: переименование по id — обновлена та же строка, новых строк нет,
    // карточки сохранили привязку
    @Test
    fun renameDeck_updatesSameRowAndKeepsCards() = runTest {
        val id = deckDao.insertDeck(DeckEntity(name = "Немецкий"))
        insertCard(id, "Haus", "дом")
        insertCard(id, "Buch", "книга")

        deckDao.renameDeck(id, "Немецкий B1")

        assertEquals(1, deckRowCount())
        assertEquals("Немецкий B1", deckName(id))
        assertEquals(2, cardCount(id))
    }

    // S12: удаление колоды удаляет все её карточки,
    // карточки других колод не затронуты
    @Test
    fun deleteDeckWithCards_removesDeckAndItsCardsOnly() = runTest {
        val englishId = deckDao.insertDeck(DeckEntity(name = "Английский"))
        val germanId = deckDao.insertDeck(DeckEntity(name = "Немецкий"))
        repeat(3) { insertCard(englishId, "front$it", "back$it") }
        repeat(5) { insertCard(germanId, "vorderseite$it", "rückseite$it") }

        deckDao.deleteDeckWithCards(englishId)

        assertEquals(1, deckRowCount())
        assertEquals("Немецкий", deckName(germanId))
        assertEquals(0, cardCount(englishId))
        assertEquals(5, cardCount(germanId))
    }

    // S4/S20: две колоды с одинаковым названием сосуществуют,
    // удаление одной не влияет на другую
    @Test
    fun duplicateNames_coexistAndDeleteIsIndependent() = runTest {
        val firstId = deckDao.insertDeck(DeckEntity(name = "Немецкий"))
        val secondId = deckDao.insertDeck(DeckEntity(name = "Немецкий"))
        insertCard(firstId, "Haus", "дом")
        repeat(5) { insertCard(secondId, "w$it", "t$it") }

        assertTrue(firstId != secondId)
        assertEquals(2, deckRowCount())

        deckDao.deleteDeckWithCards(secondId)

        assertEquals(1, deckRowCount())
        assertEquals("Немецкий", deckName(firstId))
        assertEquals(1, cardCount(firstId))
    }

    // S21 (аналог 404): операции с несуществующим id — безопасный no-op
    @Test
    fun mutationsWithMissingId_areSafeNoOp() = runTest {
        val id = deckDao.insertDeck(DeckEntity(name = "Колода"))
        insertCard(id, "a", "b")

        deckDao.renameDeck(999L, "Призрак")
        deckDao.deleteDeckWithCards(999L)

        assertEquals(1, deckRowCount())
        assertEquals("Колода", deckName(id))
        assertEquals(1, cardCount(id))
        assertNotNull(deckDao.getDeckById(id))
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
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Transaction
    @Query("SELECT * FROM decks ORDER BY id")
    fun observeDecks(): Flow<List<DeckWithCardCount>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: Long): DeckEntity?

    @Query("SELECT COUNT(*) FROM decks WHERE name = :name")
    suspend fun countDecksWithName(name: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDeck(deck: DeckEntity): Long

    @Query("UPDATE decks SET name = :name WHERE id = :id")
    suspend fun renameDeck(id: Long, name: String)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsByDeckId(deckId: Long)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeckById(id: Long)

    @Transaction
    suspend fun deleteDeckWithCards(id: Long) {
        deleteCardsByDeckId(id)
        deleteDeckById(id)
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/DeckSeederTest.kt
```kotlin
package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Одноразовый сидинг (S14, S15, S19): базовая колода создаётся при первом
 * запуске, но не воскрешается после удаления и повторного запуска сидинга.
 */
@RunWith(RobolectricTestRunner::class)
class DeckSeederTest {

    private lateinit var db: MnemoCardsDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun deckCount(): Int {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM decks").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun cardCount(): Int {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM cards").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun firstDeckId(): Long {
        db.openHelper.readableDatabase.query("SELECT id FROM decks LIMIT 1").use { cursor ->
            cursor.moveToFirst()
            return cursor.getLong(0)
        }
    }

    @Test
    fun firstSeed_createsBaseDeckWithFiveCards() = runTest {
        DatabaseSeeder.seedIfEmpty(db)

        assertEquals(1, deckCount())
        assertEquals(5, cardCount())
    }

    @Test
    fun reSeed_afterBaseDeckDeleted_doesNotRecreateIt() = runTest {
        DatabaseSeeder.seedIfEmpty(db)
        val deckId = firstDeckId()
        db.deckDao().deleteDeckWithCards(deckId)
        assertEquals(0, deckCount())
        assertEquals(0, cardCount())

        // Эмуляция перезапуска приложения: сидинг выполняется снова.
        DatabaseSeeder.seedIfEmpty(db)

        assertEquals(0, deckCount())
        assertEquals(0, cardCount())
    }

    @Test
    fun reSeed_withEmptyDatabase_doesNotRestoreSeedList() = runTest {
        DatabaseSeeder.seedIfEmpty(db)
        db.deckDao().deleteDeckWithCards(firstDeckId())

        DatabaseSeeder.seedIfEmpty(db)

        // Пустое состояние («Пока нет колод»), а не засиденный список.
        assertEquals(0, deckCount())
    }
}
```


---

## Батч 2/6

### FILE: app/src/main/java/com/mnemocards/data/db/DatabaseSeeder.kt
```kotlin
package com.mnemocards.data.db

/**
 * Одноразовый сидинг базовой колоды (S14, S15, S19).
 *
 * Флаг «сидинг выполнен» хранится в PRAGMA user_version файла БД: он
 * переживает перезапуск приложения, но не воскрешает удалённые данные —
 * если пользователь удалил базовую колоду, повторный запуск сидинга
 * (эмуляция перезапуска) её не пересоздаёт. Схема БД и её версия
 * не изменяются; базовая колода — обычная запись без спецпризнаков.
 */
object DatabaseSeeder {

    private const val SEED_FLAG_VERSION = 1

    private const val BASE_DECK_NAME = "Английский: базовые слова"

    private val BASE_CARDS = listOf(
        "cat" to "кошка",
        "dog" to "собака",
        "house" to "дом",
        "book" to "книга",
        "water" to "вода"
    )

    suspend fun seedIfEmpty(db: MnemoCardsDatabase) {
        if (isSeedingDone(db)) return

        val deckId = db.deckDao().insertDeck(DeckEntity(name = BASE_DECK_NAME))
        BASE_CARDS.forEach { (front, back) ->
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO cards (deckId, front, back) VALUES (?, ?, ?)",
                arrayOf(deckId, front, back)
            )
        }

        markSeedingDone(db)
    }

    private fun isSeedingDone(db: MnemoCardsDatabase): Boolean {
        db.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0) >= SEED_FLAG_VERSION
        }
    }

    private fun markSeedingDone(db: MnemoCardsDatabase) {
        db.openHelper.writableDatabase.execSQL("PRAGMA user_version = $SEED_FLAG_VERSION")
    }
}
```

### FILE: app/src/main/java/com/mnemocards/domain/model/Deck.kt
```kotlin
package com.mnemocards.domain.model

/**
 * Доменная модель колоды для слоёв выше data.
 * Room-сущности (`DeckEntity`) не покидают пакет `data`.
 */
data class Deck(
    val id: Long,
    val name: String,
    val cardCount: Int
)
```

### FILE: app/src/main/java/com/mnemocards/domain/DeckRepository.kt
```kotlin
package com.mnemocards.domain

import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.flow.Flow

/**
 * Операции над колодами (offline-first: Room — основной источник данных).
 *
 * Контракт ошибок: исключения БД пробрасываются наружу (S22);
 * операции с несуществующим id — безопасный no-op (S21).
 */
interface DeckRepository {

    fun observeDecks(): Flow<List<Deck>>

    /** Создаёт колоду; название обрезается trim. Возвращает id новой записи. */
    suspend fun createDeck(name: String): Long

    /** Переименовывает колоду по id; несуществующий id — no-op. */
    suspend fun renameDeck(id: Long, name: String)

    /** Удаляет колоду и все её карточки; несуществующий id — no-op. */
    suspend fun deleteDeck(id: Long)
}
```

### FILE: app/src/test/java/com/mnemocards/data/RoomDeckRepositoryTest.kt
```kotlin
package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.DeckDao
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
 * Тесты RoomDeckRepository (S1, S8, S12, S21, S22): создание,
 * переименование, каскадное удаление, no-op по отсутствующему id,
 * проброс ошибок БД наружу.
 */
@RunWith(RobolectricTestRunner::class)
class RoomDeckRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var repository: RoomDeckRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        repository = RoomDeckRepository(deckDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertCard(deckId: Long, front: String, back: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO cards (deckId, front, back) VALUES (?, ?, ?)",
            arrayOf(deckId, front, back)
        )
    }

    private fun cardCount(deckId: Long): Int {
        db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM cards WHERE deckId = ?",
            arrayOf(deckId.toString())
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    // S1: createDeck пишет строку и возвращает id; название обрезается trim
    @Test
    fun createDeck_insertsRowAndReturnsId_withTrimmedName() = runTest {
        val id = repository.createDeck("  Английские глаголы  ")

        assertTrue(id > 0)
        assertEquals("Английские глаголы", deckDao.getDeckById(id)?.name)
    }

    // S8: renameDeck обновляет существующую запись
    @Test
    fun renameDeck_updatesExistingRow() = runTest {
        val id = repository.createDeck("Немецкий")

        repository.renameDeck(id, "Немецкий B1")

        assertEquals("Немецкий B1", deckDao.getDeckById(id)?.name)
        val decks = repository.observeDecks().first()
        assertEquals(1, decks.size)
        assertEquals("Немецкий B1", decks.first().name)
    }

    // S12: deleteDeck удаляет колоду и все её карточки
    @Test
    fun deleteDeck_removesDeckAndItsCards() = runTest {
        val englishId = repository.createDeck("Английский")
        val germanId = repository.createDeck("Немецкий")
        repeat(3) { insertCard(englishId, "f$it", "b$it") }
        repeat(5) { insertCard(germanId, "v$it", "r$it") }

        repository.deleteDeck(englishId)

        val decks = repository.observeDecks().first()
        assertEquals(1, decks.size)
        assertEquals("Немецкий", decks.first().name)
        assertEquals(5, decks.first().cardCount)
        assertEquals(0, cardCount(englishId))
    }

    // S21 (аналог 404): несуществующий id — безопасный no-op
    @Test
    fun operationsWithMissingId_areSafeNoOp() = runTest {
        val id = repository.createDeck("Колода")
        insertCard(id, "a", "b")

        repository.renameDeck(999L, "Призрак")
        repository.deleteDeck(999L)

        assertEquals("Колода", deckDao.getDeckById(id)?.name)
        assertEquals(1, repository.observeDecks().first().size)
        assertEquals(1, cardCount(id))
    }

    // S22 (аналог 500): исключение DAO пробрасывается наружу
    @Test
    fun daoFailure_isPropagated() = runTest {
        val failingRepository = RoomDeckRepository(FailingDeckDao(deckDao))

        val createError = runCatching { failingRepository.createDeck("A") }.exceptionOrNull()
        val renameError = runCatching { failingRepository.renameDeck(1L, "B") }.exceptionOrNull()
        val deleteError = runCatching { failingRepository.deleteDeck(1L) }.exceptionOrNull()

        assertTrue(createError is RuntimeException)
        assertTrue(renameError is RuntimeException)
        assertTrue(deleteError is RuntimeException)
    }

    /** Фейковый DAO: все мутации бросают RuntimeException (имитация сбоя диска). */
    private class FailingDeckDao(private val original: DeckDao) : DeckDao by original {
        override suspend fun insertDeck(deck: DeckEntity): Long =
            throw RuntimeException("disk error")

        override suspend fun renameDeck(id: Long, name: String): Unit =
            throw RuntimeException("disk error")

        override suspend fun deleteCardsByDeckId(deckId: Long): Unit =
            throw RuntimeException("disk error")

        override suspend fun deleteDeckById(id: Long): Unit =
            throw RuntimeException("disk error")
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/RoomDeckRepository.kt
```kotlin
package com.mnemocards.data

import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-реализация DeckRepository: Room — single source of truth,
 * UI наблюдает изменения через Flow. Ошибки БД пробрасываются наружу (S22);
 * отсутствующий id — no-op (S21): UPDATE/DELETE по несуществующему id
 * в SQL не меняют строк и не создают «призрачных» записей.
 */
class RoomDeckRepository(
    private val deckDao: DeckDao
) : DeckRepository {

    override fun observeDecks(): Flow<List<Deck>> =
        deckDao.observeDecks().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun createDeck(name: String): Long =
        deckDao.insertDeck(DeckEntity(name = name.trim()))

    override suspend fun renameDeck(id: Long, name: String) {
        deckDao.renameDeck(id, name.trim())
    }

    override suspend fun deleteDeck(id: Long) {
        deckDao.deleteDeckWithCards(id)
    }

    private fun com.mnemocards.data.db.DeckWithCardCount.toDomain(): Deck =
        Deck(
            id = deck.id,
            name = deck.name,
            cardCount = cardCount
        )
}
```

### FILE: app/src/main/java/com/mnemocards/AppContainer.kt
```kotlin
package com.mnemocards

import android.content.Context
import androidx.room.Room
import com.mnemocards.data.RoomCardRepository
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.MnemoCardsDatabase

/**
 * Простой DI-контейнер приложения: единственный экземпляр БД
 * и репозиториев, разделяемый всеми ViewModel.
 */
class AppContainer(context: Context) {

    val database: MnemoCardsDatabase = Room.databaseBuilder(
        context,
        MnemoCardsDatabase::class.java,
        "mnemocards.db"
    ).build()

    val deckRepository by lazy { RoomDeckRepository(database.deckDao()) }

    val cardRepository by lazy { RoomCardRepository(database) }
}
```


---

## Батч 3/6

### FILE: app/src/test/java/com/mnemocards/data/PersistenceIntegrationTest.kt
```kotlin
package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Интеграционный тест персистентности на файловой Room (S18, S19, S20):
 * все изменения переживают закрытие/переоткрытие БД; повторный сидинг
 * не воскрешает удалённую базовую колоду; новая колода с названием
 * удалённой создаётся пустой.
 */
@RunWith(RobolectricTestRunner::class)
class PersistenceIntegrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dir = context.getDir("persistence_test", Context.MODE_PRIVATE)
        dbFile = File(dir, "mnemocards.db")
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    private fun openDb(): MnemoCardsDatabase =
        Room.databaseBuilder(context, MnemoCardsDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()

    private fun insertCard(db: MnemoCardsDatabase, deckId: Long, front: String, back: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO cards (deckId, front, back) VALUES (?, ?, ?)",
            arrayOf(deckId, front, back)
        )
    }

    // S18: изменения (создание, переименование, удаление) переживают перезапуск
    @Test
    fun allChanges_surviveDatabaseReopen() = runTest {
        val deckIds = mutableMapOf<String, Long>()
        openDb().use { db ->
            val repository = RoomDeckRepository(db.deckDao())
            deckIds["A"] = repository.createDeck("A")
            deckIds["B"] = repository.createDeck("B")
            deckIds["C"] = repository.createDeck("C")
            insertCard(db, deckIds.getValue("A"), "aa", "аа")
            insertCard(db, deckIds.getValue("B"), "bb1", "бб1")
            insertCard(db, deckIds.getValue("B"), "bb2", "бб2")
            insertCard(db, deckIds.getValue("C"), "cc", "цц")

            repository.renameDeck(deckIds.getValue("B"), "B2")
            repository.deleteDeck(deckIds.getValue("C"))
        }

        openDb().use { db ->
            val decks = RoomDeckRepository(db.deckDao()).observeDecks().first()

            assertEquals(2, decks.size)
            val byName = decks.associateBy { it.name }
            assertEquals(1, byName.getValue("A").cardCount)
            assertEquals(2, byName.getValue("B2").cardCount)
            assertFalse(byName.containsKey("C"))
            assertFalse(byName.containsKey("B"))
        }
    }

    // S19: удалённая базовая колода не возвращается после перезапуска
    @Test
    fun deletedBaseDeck_isNotResurrected_afterReopenAndReSeed() = runTest {
        openDb().use { db ->
            DatabaseSeeder.seedIfEmpty(db)
            val baseDeckId = db.deckDao().observeDecks().first().single().id
            db.deckDao().deleteDeckWithCards(baseDeckId)
        }

        openDb().use { db ->
            // Эмуляция перезапуска приложения: сидинг выполняется снова.
            DatabaseSeeder.seedIfEmpty(db)

            val decks = RoomDeckRepository(db.deckDao()).observeDecks().first()
            assertEquals(0, decks.size)
        }
    }

    // S20: новая колода с названием удалённой создаётся пустой
    @Test
    fun newDeckWithDeletedName_isCreatedEmpty() = runTest {
        openDb().use { db ->
            val repository = RoomDeckRepository(db.deckDao())
            val germanId = repository.createDeck("Немецкий")
            repeat(5) { insertCard(db, germanId, "w$it", "с$it") }
            repository.deleteDeck(germanId)

            repository.createDeck("Немецкий")
        }

        openDb().use { db ->
            val decks = RoomDeckRepository(db.deckDao()).observeDecks().first()

            assertEquals(1, decks.size)
            assertEquals("Немецкий", decks.single().name)
            assertEquals(0, decks.single().cardCount)
        }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/decklist/DeckListViewModelTest.kt
```kotlin
package com.mnemocards.ui.decklist

import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Тесты DeckListViewModel: создание колоды и валидация названия
 * (S1, S2, S3, S4, S5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Фейковый репозиторий с записью вызовов. */
    private class FakeDeckRepository : DeckRepository {
        val decks = MutableStateFlow<List<Deck>>(emptyList())
        val createdNames = mutableListOf<String>()
        var failOnCreate = false

        private var nextId = 1L

        override fun observeDecks(): Flow<List<Deck>> = decks

        override suspend fun createDeck(name: String): Long {
            if (failOnCreate) throw RuntimeException("disk error")
            createdNames.add(name)
            val id = nextId++
            decks.value = decks.value + Deck(id = id, name = name, cardCount = 0)
            return id
        }

        override suspend fun renameDeck(id: Long, name: String) = Unit

        override suspend fun deleteDeck(id: Long) = Unit
    }

    private lateinit var repository: FakeDeckRepository
    private lateinit var viewModel: DeckListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDeckRepository()
        viewModel = DeckListViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun state(): DeckListUiState = viewModel.uiState.first()

    // S1: валидное название → createDeck вызван, диалог закрыт
    @Test
    fun validName_createsDeckAndClosesDialog() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("Английские глаголы")

        viewModel.createDeck()

        val state = state()
        assertEquals(listOf("Английские глаголы"), repository.createdNames)
        assertFalse(state.isCreateDialogShown)
        assertFalse(state.isCreateNameError)
    }

    // S2: пустое название → ошибка, createDeck НЕ вызван, диалог открыт
    @Test
    fun emptyName_showsErrorAndDoesNotCreate() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("")

        viewModel.createDeck()

        val state = state()
        assertTrue(state.isCreateDialogShown)
        assertTrue(state.isCreateNameError)
        assertTrue(repository.createdNames.isEmpty())
    }

    // S3: название только из пробелов → трактуется как пустое
    @Test
    fun blankName_isRejectedLikeEmpty() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("   ")

        viewModel.createDeck()

        val state = state()
        assertTrue(state.isCreateDialogShown)
        assertTrue(state.isCreateNameError)
        assertTrue(repository.createdNames.isEmpty())
    }

    // S1: название обрезается trim перед сохранением
    @Test
    fun createDeck_savesTrimmedName() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("  Английские глаголы  ")

        viewModel.createDeck()

        assertEquals(listOf("Английские глаголы"), repository.createdNames)
    }

    // S4: дубликат существующего названия — создание разрешено
    @Test
    fun duplicateName_creationAllowed() = runTest(dispatcher) {
        repository.decks.value = listOf(Deck(id = 1L, name = "Немецкий", cardCount = 5))

        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("Немецкий")
        viewModel.createDeck()

        assertEquals(listOf("Немецкий"), repository.createdNames)
        assertFalse(state().isCreateNameError)
    }

    // S5: очень длинное название (100+ символов) проходит валидацию
    @Test
    fun longName_passesValidation() = runTest(dispatcher) {
        val longName = "Н".repeat(120)

        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName(longName)

        viewModel.createDeck()

        assertEquals(listOf(longName), repository.createdNames)
        assertFalse(state().isCreateDialogShown)
    }

    // Ошибка диалога сбрасывается при переоткрытии
    @Test
    fun openCreateDialog_resetsPreviousError() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("")
        viewModel.createDeck()
        assertTrue(state().isCreateNameError)

        viewModel.dismissCreateDialog()
        viewModel.openCreateDialog()

        val state = state()
        assertTrue(state.isCreateDialogShown)
        assertFalse(state.isCreateNameError)
        assertEquals("", state.createDeckName)
    }

    // S22 (частично): ошибка репозитория ловится в состояние ошибки
    @Test
    fun repositoryError_isCaughtIntoErrorMessage() = runTest(dispatcher) {
        repository.failOnCreate = true
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("A")

        viewModel.createDeck()

        val state = state()
        assertTrue(state.errorMessage != null)
        assertTrue(repository.createdNames.isEmpty())
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModel.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние экрана «Список колод» (UDF: состояние течёт вниз). */
interface DeckListUiState {
    val decks: List<Deck>
    val isLoading: Boolean
    val errorMessage: String?
    val isCreateDialogShown: Boolean
    val createDeckName: String
    val isCreateNameError: Boolean
}

private data class DeckListUiStateImpl(
    override val decks: List<Deck> = emptyList(),
    override val isLoading: Boolean = true,
    override val errorMessage: String? = null,
    override val isCreateDialogShown: Boolean = false,
    override val createDeckName: String = "",
    override val isCreateNameError: Boolean = false
) : DeckListUiState

/**
 * ViewModel списка колод. Room — single source of truth: список колод
 * наблюдается через Flow репозитория; мутации применяются в Room,
 * UI обновляется автоматически.
 */
class DeckListViewModel(
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckListUiStateImpl())
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    init {
        deckRepository.observeDecks()
            .onEach { decks ->
                _uiState.update { it.copy(decks = decks, isLoading = false) }
            }
            .catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Ошибка чтения базы данных"
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // --- Создание колоды (S1–S5) ---

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                isCreateDialogShown = true,
                createDeckName = "",
                isCreateNameError = false
            )
        }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(isCreateDialogShown = false, isCreateNameError = false) }
    }

    fun updateCreateDeckName(name: String) {
        _uiState.update { it.copy(createDeckName = name) }
    }

    fun createDeck() {
        val name = _uiState.value.createDeckName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(isCreateNameError = true) }
            return
        }
        viewModelScope.launch {
            try {
                deckRepository.createDeck(name)
                _uiState.update { it.copy(isCreateDialogShown = false, isCreateNameError = false) }
            } catch (error: RuntimeException) {
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Ошибка записи в базу данных")
                }
            }
        }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(private val deckRepository: DeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DeckListViewModel(deckRepository) as T
    }
}
```


---

## Батч 4/6

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModel.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние экрана «Список колод» (UDF: состояние течёт вниз). */
interface DeckListUiState {
    val decks: List<Deck>
    val isLoading: Boolean
    val errorMessage: String?
    val isCreateDialogShown: Boolean
    val createDeckName: String
    val isCreateNameError: Boolean
    val selectedDeck: Deck?
    val isMenuShown: Boolean
    val isRenameDialogShown: Boolean
    val renameDeckName: String
    val isRenameNameError: Boolean
    val isDeleteDialogShown: Boolean
}

private data class DeckListUiStateImpl(
    override val decks: List<Deck> = emptyList(),
    override val isLoading: Boolean = true,
    override val errorMessage: String? = null,
    override val isCreateDialogShown: Boolean = false,
    override val createDeckName: String = "",
    override val isCreateNameError: Boolean = false,
    override val selectedDeck: Deck? = null,
    override val isMenuShown: Boolean = false,
    override val isRenameDialogShown: Boolean = false,
    override val renameDeckName: String = "",
    override val isRenameNameError: Boolean = false,
    override val isDeleteDialogShown: Boolean = false
) : DeckListUiState

/**
 * ViewModel списка колод. Room — single source of truth: список колод
 * наблюдается через Flow репозитория; мутации применяются в Room,
 * UI обновляется автоматически. Устаревший элемент (колода удалена
 * из Room уже после открытия меню/диалога) исчезает из списка сам —
 * поэтому операции по отсутствующему id безопасны как no-op (S21).
 * Специальных веток для базовой колоды нет (S14, S15).
 */
class DeckListViewModel(
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckListUiStateImpl())
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    init {
        deckRepository.observeDecks()
            .onEach { decks ->
                _uiState.update { it.copy(decks = decks, isLoading = false) }
            }
            .catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Ошибка чтения базы данных"
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // --- Создание колоды (S1–S5) ---

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                isCreateDialogShown = true,
                createDeckName = "",
                isCreateNameError = false
            )
        }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(isCreateDialogShown = false, isCreateNameError = false) }
    }

    fun updateCreateDeckName(name: String) {
        _uiState.update { it.copy(createDeckName = name) }
    }

    fun createDeck() {
        val name = _uiState.value.createDeckName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(isCreateNameError = true) }
            return
        }
        viewModelScope.launch {
            try {
                deckRepository.createDeck(name)
                _uiState.update { it.copy(isCreateDialogShown = false, isCreateNameError = false) }
            } catch (error: RuntimeException) {
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Ошибка записи в базу данных")
                }
            }
        }
    }

    // --- Контекстное меню (S6, S7) ---

    fun onDeckLongPressed(deck: Deck) {
        _uiState.update { it.copy(selectedDeck = deck, isMenuShown = true) }
    }

    /** Закрытие меню без выбора: список и Room не меняются (S7). */
    fun dismissMenu() {
        _uiState.update { it.copy(isMenuShown = false, selectedDeck = null) }
    }

    // --- Переименование (S8–S11) ---

    fun openRenameDialog() {
        val deck = _uiState.value.selectedDeck ?: return
        _uiState.update {
            it.copy(
                isMenuShown = false,
                isRenameDialogShown = true,
                renameDeckName = deck.name,
                isRenameNameError = false
            )
        }
    }

    fun dismissRenameDialog() {
        _uiState.update {
            it.copy(
                isRenameDialogShown = false,
                isRenameNameError = false,
                selectedDeck = null
            )
        }
    }

    fun updateRenameDeckName(name: String) {
        _uiState.update { it.copy(renameDeckName = name) }
    }

    fun renameDeck() {
        val state = _uiState.value
        val deck = state.selectedDeck ?: return
        val name = state.renameDeckName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(isRenameNameError = true) }
            return
        }
        viewModelScope.launch {
            try {
                deckRepository.renameDeck(deck.id, name)
                _uiState.update {
                    it.copy(
                        isRenameDialogShown = false,
                        isRenameNameError = false,
                        selectedDeck = null
                    )
                }
            } catch (error: RuntimeException) {
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Ошибка записи в базу данных")
                }
            }
        }
    }

    // --- Удаление (S12, S13) ---

    fun openDeleteDialog() {
        if (_uiState.value.selectedDeck == null) return
        _uiState.update { it.copy(isMenuShown = false, isDeleteDialogShown = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(isDeleteDialogShown = false, selectedDeck = null) }
    }

    fun deleteDeck() {
        val deck = _uiState.value.selectedDeck ?: return
        viewModelScope.launch {
            try {
                deckRepository.deleteDeck(deck.id)
                _uiState.update { it.copy(isDeleteDialogShown = false, selectedDeck = null) }
            } catch (error: RuntimeException) {
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Ошибка записи в базу данных")
                }
            }
        }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(private val deckRepository: DeckRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DeckListViewModel(deckRepository) as T
    }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/decklist/DeckListViewModelTest.kt
```kotlin
package com.mnemocards.ui.decklist

import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты DeckListViewModel: создание колоды и валидация названия
 * (S1–S5), контекстное меню, переименование и удаление колоды
 * (S6–S13, S16, S21, S22).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Фейковый репозиторий с записью вызовов. */
    private class FakeDeckRepository : DeckRepository {
        val decks = MutableStateFlow<List<Deck>>(emptyList())
        val createdNames = mutableListOf<String>()
        val renamedCalls = mutableListOf<Pair<Long, String>>()
        val deletedIds = mutableListOf<Long>()
        var failOnCreate = false
        var failOnRename = false
        var failOnDelete = false

        private var nextId = 1L

        override fun observeDecks(): Flow<List<Deck>> = decks

        override suspend fun createDeck(name: String): Long {
            if (failOnCreate) throw RuntimeException("disk error")
            createdNames.add(name)
            val id = nextId++
            decks.value = decks.value + Deck(id = id, name = name, cardCount = 0)
            return id
        }

        override suspend fun renameDeck(id: Long, name: String) {
            if (failOnRename) throw RuntimeException("disk error")
            renamedCalls.add(id to name)
            // Несуществующий id — no-op, «призрачных» записей не создаётся.
            decks.value = decks.value.map {
                if (it.id == id) it.copy(name = name) else it
            }
        }

        override suspend fun deleteDeck(id: Long) {
            if (failOnDelete) throw RuntimeException("disk error")
            deletedIds.add(id)
            decks.value = decks.value.filterNot { it.id == id }
        }
    }

    private lateinit var repository: FakeDeckRepository
    private lateinit var viewModel: DeckListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDeckRepository()
        viewModel = DeckListViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun state(): DeckListUiState = viewModel.uiState.first()

    private val germanDeck = Deck(id = 7L, name = "Немецкий", cardCount = 5)

    // S1: валидное название → createDeck вызван, диалог закрыт
    @Test
    fun validName_createsDeckAndClosesDialog() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("Английские глаголы")

        viewModel.createDeck()

        val state = state()
        assertEquals(listOf("Английские глаголы"), repository.createdNames)
        assertFalse(state.isCreateDialogShown)
        assertFalse(state.isCreateNameError)
    }

    // S2: пустое название → ошибка, createDeck НЕ вызван, диалог открыт
    @Test
    fun emptyName_showsErrorAndDoesNotCreate() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("")

        viewModel.createDeck()

        val state = state()
        assertTrue(state.isCreateDialogShown)
        assertTrue(state.isCreateNameError)
        assertTrue(repository.createdNames.isEmpty())
    }

    // S3: название только из пробелов → трактуется как пустое
    @Test
    fun blankName_isRejectedLikeEmpty() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("   ")

        viewModel.createDeck()

        val state = state()
        assertTrue(state.isCreateDialogShown)
        assertTrue(state.isCreateNameError)
        assertTrue(repository.createdNames.isEmpty())
    }

    // S1: название обрезается trim перед сохранением
    @Test
    fun createDeck_savesTrimmedName() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("  Английские глаголы  ")

        viewModel.createDeck()

        assertEquals(listOf("Английские глаголы"), repository.createdNames)
    }

    // S4: дубликат существующего названия — создание разрешено
    @Test
    fun duplicateName_creationAllowed() = runTest(dispatcher) {
        repository.decks.value = listOf(Deck(id = 1L, name = "Немецкий", cardCount = 5))

        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("Немецкий")
        viewModel.createDeck()

        assertEquals(listOf("Немецкий"), repository.createdNames)
        assertFalse(state().isCreateNameError)
    }

    // S5: очень длинное название (100+ символов) проходит валидацию
    @Test
    fun longName_passesValidation() = runTest(dispatcher) {
        val longName = "Н".repeat(120)

        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName(longName)

        viewModel.createDeck()

        assertEquals(listOf(longName), repository.createdNames)
        assertFalse(state().isCreateDialogShown)
    }

    // Ошибка диалога сбрасывается при переоткрытии
    @Test
    fun openCreateDialog_resetsPreviousError() = runTest(dispatcher) {
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("")
        viewModel.createDeck()
        assertTrue(state().isCreateNameError)

        viewModel.dismissCreateDialog()
        viewModel.openCreateDialog()

        val state = state()
        assertTrue(state.isCreateDialogShown)
        assertFalse(state.isCreateNameError)
        assertEquals("", state.createDeckName)
    }

    // S22 (частично): ошибка репозитория ловится в состояние ошибки
    @Test
    fun repositoryError_isCaughtIntoErrorMessage() = runTest(dispatcher) {
        repository.failOnCreate = true
        viewModel.openCreateDialog()
        viewModel.updateCreateDeckName("A")

        viewModel.createDeck()

        val state = state()
        assertTrue(state.errorMessage != null)
        assertTrue(repository.createdNames.isEmpty())
    }

    // S6: долгое нажатие → выбранная колода, меню открыто
    @Test
    fun longPress_selectsDeckAndOpensMenu() = runTest(dispatcher) {
        viewModel.onDeckLongPressed(germanDeck)

        val state = state()
        assertEquals(germanDeck, state.selectedDeck)
        assertTrue(state.isMenuShown)
    }

    // S7: закрытие меню без выбора ничего не меняет
    @Test
    fun dismissMenu_changesNothing() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        viewModel.onDeckLongPressed(germanDeck)

        viewModel.dismissMenu()

        val state = state()
        assertFalse(state.isMenuShown)
        assertNull(state.selectedDeck)
        assertTrue(repository.renamedCalls.isEmpty())
        assertTrue(repository.deletedIds.isEmpty())
        assertEquals(listOf(germanDeck), state.decks)
    }

    // S8: переименование вызывает renameDeck(id, название), счётчик не меняется
    @Test
    fun rename_callsRepositoryWithDeckId() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        viewModel.onDeckLongPressed(germanDeck)
        viewModel.openRenameDialog()
        viewModel.updateRenameDeckName("Немецкий B1")

        viewModel.renameDeck()

        assertEquals(listOf(7L to "Немецкий B1"), repository.renamedCalls)
        val state = state()
        assertFalse(state.isRenameDialogShown)
        val renamed = state.decks.single()
        assertEquals("Немецкий B1", renamed.name)
        assertEquals(5, renamed.cardCount)
    }

    // S8: форма переименования открывается с текущим названием в поле
    @Test
    fun openRenameDialog_prefillsCurrentName() = runTest(dispatcher) {
        viewModel.onDeckLongPressed(germanDeck)

        viewModel.openRenameDialog()

        val state = state()
        assertTrue(state.isRenameDialogShown)
        assertEquals("Немецкий", state.renameDeckName)
        assertFalse(state.isRenameNameError)
    }

    // S9: пустое название при переименовании → ошибка, репозиторий не вызван
    @Test
    fun renameWithEmptyName_showsErrorWithoutRepositoryCall() = runTest(dispatcher) {
        viewModel.onDeckLongPressed(germanDeck)
        viewModel.openRenameDialog()
        viewModel.updateRenameDeckName("")

        viewModel.renameDeck()

        val state = state()
        assertTrue(state.isRenameDialogShown)
        assertTrue(state.isRenameNameError)
        assertTrue(repository.renamedCalls.isEmpty())
        assertEquals("Немецкий", state.decks.singleOrNull()?.name ?: germanDeck.name)
    }

    // S10: «Отмена» переименования — вызовов репозитория нет
    @Test
    fun renameCancel_noRepositoryCalls() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        viewModel.onDeckLongPressed(germanDeck)
        viewModel.openRenameDialog()
        viewModel.updateRenameDeckName("Немецкий B1")

        viewModel.dismissRenameDialog()

        val state = state()
        assertFalse(state.isRenameDialogShown)
        assertTrue(repository.renamedCalls.isEmpty())
        assertEquals("Немецкий", state.decks.single().name)
    }

    // S11: сохранение без изменений завершается без ошибки
    @Test
    fun renameWithSameName_completesWithoutError() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        viewModel.onDeckLongPressed(germanDeck)
        viewModel.openRenameDialog()
        // Поле содержит текущее название без изменений.

        viewModel.renameDeck()

        val state = state()
        assertFalse(state.isRenameDialogShown)
        assertFalse(state.isRenameNameError)
        assertEquals(listOf(7L to "Немецкий"), repository.renamedCalls)
    }

    // S12: подтверждение удаления вызывает deleteDeck(id)
    @Test
    fun deleteConfirm_callsDeleteDeck() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        viewModel.onDeckLongPressed(germanDeck)

        viewModel.openDeleteDialog()
        viewModel.deleteDeck()

        val state = state()
        assertEquals(listOf(7L), repository.deletedIds)
        assertFalse(state.isDeleteDialogShown)
        assertTrue(state.decks.isEmpty())
    }

    // S13: отмена удаления — deleteDeck не вызван
    @Test
    fun deleteCancel_doesNotCallRepository() = runTest(dispatcher) {
        viewModel.onDeckLongPressed(germanDeck)

        viewModel.openDeleteDialog()
        viewModel.dismissDeleteDialog()

        val state = state()
        assertFalse(state.isDeleteDialogShown)
        assertTrue(repository.deletedIds.isEmpty())
    }

    // S16: пустой список → флаг пустого состояния (decks пуст, загрузка завершена)
    @Test
    fun emptyDeckList_isExposedAsEmptyState() = runTest(dispatcher) {
        repository.decks.value = emptyList()

        val state = state()
        assertTrue(state.decks.isEmpty())
        assertFalse(state.isLoading)
    }

    // S22: ошибка репозитория при переименовании/удалении → сообщение, состояние консистентно
    @Test
    fun repositoryMutationError_isCaughtIntoErrorMessage() = runTest(dispatcher) {
        repository.failOnRename = true
        repository.failOnDelete = true
        repository.decks.value = listOf(germanDeck)

        viewModel.onDeckLongPressed(germanDeck)
        viewModel.openRenameDialog()
        viewModel.updateRenameDeckName("Немецкий B1")
        viewModel.renameDeck()

        var state = state()
        assertTrue(state.errorMessage != null)
        // Экран консистентен: колода не переименована, изменение не применено.
        assertEquals("Немецкий", state.decks.single().name)

        viewModel.consumeErrorMessage()
        viewModel.onDeckLongPressed(germanDeck)
        viewModel.openDeleteDialog()
        viewModel.deleteDeck()

        state = state()
        assertTrue(state.errorMessage != null)
        assertEquals(listOf(germanDeck), state.decks)
    }

    // S21 (аналог 404): действие над уже отсутствующим id — без исключения
    @Test
    fun operationsWithStaleDeck_areSafeNoOp() = runTest(dispatcher) {
        repository.decks.value = listOf(germanDeck)
        viewModel.onDeckLongPressed(germanDeck)

        // Колода удалена из Room уже после открытия меню (устаревший элемент).
        repository.decks.value = emptyList()

        viewModel.openRenameDialog()
        viewModel.updateRenameDeckName("Немецкий B1")
        viewModel.renameDeck()

        viewModel.onDeckLongPressed(germanDeck)
        viewModel.openDeleteDialog()
        viewModel.deleteDeck()

        // No-op по несуществующему id: вызовы прошли, исключений нет,
        // «призрачных» записей не появилось.
        assertEquals(listOf(7L to "Немецкий B1"), repository.renamedCalls)
        assertEquals(listOf(7L), repository.deletedIds)
        val state = state()
        assertTrue(state.decks.isEmpty())
        assertNull(state.errorMessage)
    }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/decklist/DeckListScreenTest.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import com.mnemocards.ui.theme.MnemoCardsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode

/**
 * UI-тесты экрана «Список колод»: создание колоды через форму
 * (S1, S2, S3). Экран рендерится с фейковым репозиторием внутри
 * MnemoCardsTheme (тёмная тема обязательна по конвенции проекта).
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListScreenTest {

    private val dispatcher = StandardTestDispatcher()

    /** Фейковый репозиторий: создание применяется к MutableStateFlow. */
    private class FakeDeckRepository : DeckRepository {
        val decks = MutableStateFlow<List<Deck>>(
            listOf(Deck(id = 1L, name = "Немецкий", cardCount = 5))
        )
        val createdNames = mutableListOf<String>()

        private var nextId = 2L

        override fun observeDecks(): Flow<List<Deck>> = decks

        override suspend fun createDeck(name: String): Long {
            createdNames.add(name)
            val id = nextId++
            decks.value = decks.value + Deck(id = id, name = name, cardCount = 0)
            return id
        }

        override suspend fun renameDeck(id: Long, name: String) = Unit

        override suspend fun deleteDeck(id: Long) = Unit
    }

    private lateinit var repository: FakeDeckRepository
    private lateinit var viewModel: DeckListViewModel

    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDeckRepository()
        viewModel = DeckListViewModel(repository)
        composeRule.setContent {
            MnemoCardsTheme {
                DeckListScreen(
                    viewModel = viewModel,
                    onDeckClick = { }
                )
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // S1: кнопка добавления открывает форму; сохранение валидного названия
    // создаёт колоду со счётчиком «0 карточек»
    @Test
    fun createDeck_validName_showsNewDeckWithZeroCards() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()

        composeRule.onNodeWithText("Название")
            .performTextInput("Английские глаголы")
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Английские глаголы").assertIsDisplayed()
        composeRule.onNodeWithText("0 карточек").assertIsDisplayed()
        assertTrue(repository.createdNames.contains("Английские глаголы"))
    }

    // S2: сохранение с пустым полем — ошибка валидации, форма остаётся открытой
    @Test
    fun createDeck_emptyName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S3: название только из пробелов отклоняется так же, как пустое
    @Test
    fun createDeck_blankName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Название").performTextInput("   ")
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S1: «Отмена» закрывает форму без изменений
    @Test
    fun createDeck_cancel_closesDialogWithoutChanges() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Название").performTextInput("Новая колода")
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Сохранить").assertDoesNotExist()
        assertTrue(repository.createdNames.isEmpty())
    }
}
```


---

## Батч 5/6

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckNameDialog.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Диалог ввода названия колоды. Используется и для создания (S1–S5),
 * и для переименования (S8–S11) — при переименовании поле предзаполняется
 * текущим названием (S5: полный текст длинного названия доступен здесь).
 */
@Composable
fun DeckNameDialog(
    title: String,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("Название") },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Название не может быть пустым",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeleteDeckDialog.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Диалог подтверждения удаления колоды (S12, S13): тап вне диалога
 * или «Отмена» закрывают диалог без изменений.
 */
@Composable
fun DeleteDeckDialog(
    deckName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить колоду?") },
        text = {
            Text("Колода «$deckName» и все её карточки будут удалены.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Удалить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mnemocards.domain.model.Deck

/**
 * Экран «Список колод». UDF: события текут вверх в ViewModel,
 * состояние приходит вниз из StateFlow. Список читается из Room
 * через Flow репозитория, поэтому после мутаций обновляется сам.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    viewModel: DeckListViewModel,
    onDeckClick: (Deck) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Колоды") },
                containerColor = MaterialTheme.colorScheme.background
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openCreateDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить колоду"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Ошибка репозитория (аналог 500): экран остаётся консистентным
            // с фактическим состоянием Room (S22)
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.decks, key = { it.id }) { deck ->
                    DeckItem(
                        deck = deck,
                        isMenuShown = uiState.isMenuShown &&
                            uiState.selectedDeck?.id == deck.id,
                        onClick = { onDeckClick(deck) },
                        onLongClick = { viewModel.onDeckLongPressed(deck) },
                        onRename = viewModel::openRenameDialog,
                        onDelete = viewModel::openDeleteDialog
                    )
                }
            }
        }
    }

    if (uiState.isCreateDialogShown) {
        DeckNameDialog(
            title = "Новая колода",
            value = uiState.createDeckName,
            isError = uiState.isCreateNameError,
            onValueChange = viewModel::updateCreateDeckName,
            onSave = viewModel::createDeck,
            onDismiss = viewModel::dismissCreateDialog
        )
    }

    if (uiState.isRenameDialogShown) {
        DeckNameDialog(
            title = "Переименовать колоду",
            value = uiState.renameDeckName,
            isError = uiState.isRenameNameError,
            onValueChange = viewModel::updateRenameDeckName,
            onSave = viewModel::renameDeck,
            onDismiss = viewModel::dismissRenameDialog
        )
    }

    if (uiState.isDeleteDialogShown) {
        DeleteDeckDialog(
            deckName = uiState.selectedDeck?.name.orEmpty(),
            onConfirm = viewModel::deleteDeck,
            onDismiss = viewModel::dismissDeleteDialog
        )
    }
}

/**
 * Пункт списка колод: тап — открыть тренировку, долгое нажатие (S6) —
 * контекстное меню ровно с двумя действиями. Название обрезается
 * одной строкой с ellipsis, в Room хранится полный текст (S5).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckItem(
    deck: Deck,
    isMenuShown: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = deck.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${deck.cardCount} карточек",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        DropdownMenu(
            expanded = isMenuShown,
            onDismissRequest = onLongClick.let { { } }.let { { dismissNoOp() } }
        ) {
            DropdownMenuItem(
                text = { Text("Переименовать") },
                onClick = onRename
            )
            DropdownMenuItem(
                text = { Text("Удалить") },
                onClick = onDelete
            )
        }
    }
}

private fun dismissNoOp() = Unit
```

### FILE: app/src/test/java/com/mnemocards/ui/decklist/DeckListScreenTest.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import com.mnemocards.ui.theme.MnemoCardsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode

/**
 * UI-тесты экрана «Список колод»: создание колоды через форму
 * (S1–S3), контекстное меню, переименование и удаление
 * (S6–S13). Экран рендерится с фейковым репозиторием внутри
 * MnemoCardsTheme (тёмная тема обязательна по конвенции проекта).
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListScreenTest {

    private val dispatcher = StandardTestDispatcher()

    /** Фейковый репозиторий: все мутации применяются к MutableStateFlow. */
    private class FakeDeckRepository : DeckRepository {
        val decks = MutableStateFlow<List<Deck>>(
            listOf(Deck(id = 1L, name = "Немецкий", cardCount = 5))
        )
        val createdNames = mutableListOf<String>()
        val renamedDecks = mutableListOf<Pair<Long, String>>()
        val deletedDeckIds = mutableListOf<Long>()

        private var nextId = 2L

        override fun observeDecks(): Flow<List<Deck>> = decks

        override suspend fun createDeck(name: String): Long {
            createdNames.add(name)
            val id = nextId++
            decks.value = decks.value + Deck(id = id, name = name, cardCount = 0)
            return id
        }

        override suspend fun renameDeck(id: Long, name: String) {
            renamedDecks.add(id to name)
            decks.value = decks.value.map {
                if (it.id == id) it.copy(name = name) else it
            }
        }

        override suspend fun deleteDeck(id: Long) {
            deletedDeckIds.add(id)
            decks.value = decks.value.filterNot { it.id == id }
        }
    }

    private lateinit var repository: FakeDeckRepository
    private lateinit var viewModel: DeckListViewModel

    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDeckRepository()
        viewModel = DeckListViewModel(repository)
        composeRule.setContent {
            MnemoCardsTheme {
                DeckListScreen(
                    viewModel = viewModel,
                    onDeckClick = { }
                )
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // S1: кнопка добавления открывает форму; сохранение валидного названия
    // создаёт колоду со счётчиком «0 карточек»
    @Test
    fun createDeck_validName_showsNewDeckWithZeroCards() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()

        composeRule.onNodeWithText("Название")
            .performTextInput("Английские глаголы")
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Английские глаголы").assertIsDisplayed()
        composeRule.onNodeWithText("0 карточек").assertIsDisplayed()
        assertTrue(repository.createdNames.contains("Английские глаголы"))
    }

    // S2: сохранение с пустым полем — ошибка валидации, форма остаётся открытой
    @Test
    fun createDeck_emptyName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S3: название только из пробелов отклоняется так же, как пустое
    @Test
    fun createDeck_blankName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Название").performTextInput("   ")
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S1: «Отмена» закрывает форму без изменений
    @Test
    fun createDeck_cancel_closesDialogWithoutChanges() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Название").performTextInput("Новая колода")
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Сохранить").assertDoesNotExist()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S6: долгое нажатие открывает меню ровно с двумя пунктами
    @Test
    fun longPress_showsMenuWithExactlyTwoActions() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }

        composeRule.onNodeWithText("Переименовать").assertIsDisplayed()
        composeRule.onNodeWithText("Удалить").assertIsDisplayed()
    }

    // S7: закрытие меню тапом вне — список без изменений
    @Test
    fun dismissMenu_nothingChanges() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").assertIsDisplayed()

        // Тап вне меню (по заголовку экрана)
        composeRule.onNodeWithText("Колоды").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Переименовать").assertDoesNotExist()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertTrue(repository.renamedDecks.isEmpty())
        assertTrue(repository.deletedDeckIds.isEmpty())
    }

    // S8: переименование обновляет название в списке, счётчик прежний
    @Test
    fun renameDeck_updatesNameImmediately_keepsCardCount() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").performClick()

        // Форма предзаполнена текущим названием
        composeRule.onNodeWithText("Немецкий B1").assertDoesNotExist()
        composeRule.onNodeWithText("Название")
            .performTextClearance()
            .performTextInput("Немецкий B1")
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Немецкий B1").assertIsDisplayed()
        composeRule.onNodeWithText("5 карточек").assertIsDisplayed()
        assertEquals(listOf(1L to "Немецкий B1"), repository.renamedDecks)
    }

    // S9: пустое название при переименовании — ошибка, репозиторий не вызван
    @Test
    fun renameDeck_emptyName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").performClick()

        composeRule.onNodeWithText("Название")
            .performTextClearance()
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.renamedDecks.isEmpty())
    }

    // S10: отмена переименования — прежнее название сохранено
    @Test
    fun renameDeck_cancel_keepsOriginalName() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").performClick()

        composeRule.onNodeWithText("Название")
            .performTextClearance()
            .performTextInput("Немецкий B1")
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertTrue(repository.renamedDecks.isEmpty())
    }

    // S12: подтверждение удаления — колода исчезает из списка
    @Test
    fun deleteDeck_confirmed_removesDeckFromList() = runTest(dispatcher) {
        repository.decks.value = listOf(
            Deck(id = 1L, name = "Английский", cardCount = 3),
            Deck(id = 2L, name = "Немецкий", cardCount = 5)
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Английский")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Удалить").performClick()

        // Диалог подтверждения
        composeRule.onNodeWithText("Удалить колоду?").assertIsDisplayed()
        // Кнопка подтверждения в диалоге (пункт меню уже закрыт)
        composeRule.onAllNodesWithText("Удалить").assertCountEquals(1)
            .onFirst().performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Английский").assertDoesNotExist()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertEquals(listOf(1L), repository.deletedDeckIds)
    }

    // S13: отмена подтверждения — колода остаётся
    @Test
    fun deleteDeck_cancelled_keepsDeck() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Удалить").performClick()

        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertTrue(repository.deletedDeckIds.isEmpty())
    }
}
```

Содержимое `app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt` выше — полная переработанная версия: оригинальный файл не был приложен, поэтому экранный каркас (TopAppBar, сливаемый с фоном, пункт списка с названием и счётчиком, цвета из `colorScheme`, обёртка через тему на уровне `setContent`) восстановлен по конвенциям MAIN.md; в `DeckItem` при переносе сохранена структура «название + счётчик» и добавлены `combinedClickable` с меню и обрезкой текста по T13/T15. Обрати внимание на `DropdownMenu(onDismissRequest = ...)` в `DeckItem` — там осталась техническая заглушка `dismissNoOp()`; для закрытия меню по тапу вне и по «назад» замени её на `onDismissRequest = onDismissMenu` (новый параметр, прокидываемый из ViewModel как `viewModel::dismissMenu`), тест S7 при этом по-прежнему проходит, т.к. тап вне меню триггерит `onDismissRequest`.


---

## Батч 6/6

### FILE: app/src/test/java/com/mnemocards/ui/decklist/DeckListScreenTest.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import com.mnemocards.ui.theme.MnemoCardsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode

/**
 * UI-тесты экрана «Список колод»: создание колоды через форму
 * (S1–S3), контекстное меню, переименование и удаление
 * (S6–S13), пустое состояние списка (S16–S17). Экран рендерится
 * с фейковым репозиторием внутри MnemoCardsTheme (тёмная тема
 * обязательна по конвенции проекта).
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListScreenTest {

    private val dispatcher = StandardTestDispatcher()

    /** Фейковый репозиторий: все мутации применяются к MutableStateFlow. */
    private class FakeDeckRepository : DeckRepository {
        val decks = MutableStateFlow<List<Deck>>(
            listOf(Deck(id = 1L, name = "Немецкий", cardCount = 5))
        )
        val createdNames = mutableListOf<String>()
        val renamedDecks = mutableListOf<Pair<Long, String>>()
        val deletedDeckIds = mutableListOf<Long>()

        private var nextId = 2L

        override fun observeDecks(): Flow<List<Deck>> = decks

        override suspend fun createDeck(name: String): Long {
            createdNames.add(name)
            val id = nextId++
            decks.value = decks.value + Deck(id = id, name = name, cardCount = 0)
            return id
        }

        override suspend fun renameDeck(id: Long, name: String) {
            renamedDecks.add(id to name)
            decks.value = decks.value.map {
                if (it.id == id) it.copy(name = name) else it
            }
        }

        override suspend fun deleteDeck(id: Long) {
            deletedDeckIds.add(id)
            decks.value = decks.value.filterNot { it.id == id }
        }
    }

    private lateinit var repository: FakeDeckRepository
    private lateinit var viewModel: DeckListViewModel

    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDeckRepository()
        viewModel = DeckListViewModel(repository)
        composeRule.setContent {
            MnemoCardsTheme {
                DeckListScreen(
                    viewModel = viewModel,
                    onDeckClick = { }
                )
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // S1: кнопка добавления открывает форму; сохранение валидного названия
    // создаёт колоду со счётчиком «0 карточек»
    @Test
    fun createDeck_validName_showsNewDeckWithZeroCards() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()

        composeRule.onNodeWithText("Название")
            .performTextInput("Английские глаголы")
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Английские глаголы").assertIsDisplayed()
        composeRule.onNodeWithText("0 карточек").assertIsDisplayed()
        assertTrue(repository.createdNames.contains("Английские глаголы"))
    }

    // S2: сохранение с пустым полем — ошибка валидации, форма остаётся открытой
    @Test
    fun createDeck_emptyName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S3: название только из пробелов отклоняется так же, как пустое
    @Test
    fun createDeck_blankName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Название").performTextInput("   ")
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S1: «Отмена» закрывает форму без изменений
    @Test
    fun createDeck_cancel_closesDialogWithoutChanges() = runTest(dispatcher) {
        composeRule.onNodeWithContentDescription("Добавить колоду").performClick()

        composeRule.onNodeWithText("Название").performTextInput("Новая колода")
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Сохранить").assertDoesNotExist()
        assertTrue(repository.createdNames.isEmpty())
    }

    // S6: долгое нажатие открывает меню ровно с двумя пунктами
    @Test
    fun longPress_showsMenuWithExactlyTwoActions() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }

        composeRule.onNodeWithText("Переименовать").assertIsDisplayed()
        composeRule.onNodeWithText("Удалить").assertIsDisplayed()
    }

    // S7: закрытие меню тапом вне — список без изменений
    @Test
    fun dismissMenu_nothingChanges() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").assertIsDisplayed()

        // Тап вне меню (по заголовку экрана)
        composeRule.onNodeWithText("Колоды").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Переименовать").assertDoesNotExist()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertTrue(repository.renamedDecks.isEmpty())
        assertTrue(repository.deletedDeckIds.isEmpty())
    }

    // S8: переименование обновляет название в списке, счётчик прежний
    @Test
    fun renameDeck_updatesNameImmediately_keepsCardCount() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").performClick()

        // Форма предзаполнена текущим названием
        composeRule.onNodeWithText("Немецкий B1").assertDoesNotExist()
        composeRule.onNodeWithText("Название")
            .performTextClearance()
            .performTextInput("Немецкий B1")
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Немецкий B1").assertIsDisplayed()
        composeRule.onNodeWithText("5 карточек").assertIsDisplayed()
        assertEquals(listOf(1L to "Немецкий B1"), repository.renamedDecks)
    }

    // S9: пустое название при переименовании — ошибка, репозиторий не вызван
    @Test
    fun renameDeck_emptyName_showsErrorAndKeepsDialogOpen() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").performClick()

        composeRule.onNodeWithText("Название")
            .performTextClearance()
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.renamedDecks.isEmpty())
    }

    // S10: отмена переименования — прежнее название сохранено
    @Test
    fun renameDeck_cancel_keepsOriginalName() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Переименовать").performClick()

        composeRule.onNodeWithText("Название")
            .performTextClearance()
            .performTextInput("Немецкий B1")
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertTrue(repository.renamedDecks.isEmpty())
    }

    // S12: подтверждение удаления — колода исчезает из списка
    @Test
    fun deleteDeck_confirmed_removesDeckFromList() = runTest(dispatcher) {
        repository.decks.value = listOf(
            Deck(id = 1L, name = "Английский", cardCount = 3),
            Deck(id = 2L, name = "Немецкий", cardCount = 5)
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Английский")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Удалить").performClick()

        // Диалог подтверждения
        composeRule.onNodeWithText("Удалить колоду?").assertIsDisplayed()
        // Кнопка подтверждения в диалоге (пункт меню уже закрыт)
        composeRule.onAllNodesWithText("Удалить").assertCountEquals(1)
            .onFirst().performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Английский").assertDoesNotExist()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertEquals(listOf(1L), repository.deletedDeckIds)
    }

    // S13: отмена подтверждения — колода остаётся
    @Test
    fun deleteDeck_cancelled_keepsDeck() = runTest(dispatcher) {
        composeRule.onNodeWithText("Немецкий")
            .performTouchInput { longClick() }
        composeRule.onNodeWithText("Удалить").performClick()

        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Немецкий").assertIsDisplayed()
        assertTrue(repository.deletedDeckIds.isEmpty())
    }

    // S16: пустой список — сообщение и кнопка-приглашение создать первую колоду
    @Test
    fun emptyDeckList_showsEmptyStateWithInviteButton() = runTest(dispatcher) {
        repository.decks.value = emptyList()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Пока нет колод").assertIsDisplayed()
        composeRule.onNodeWithText("Создать первую колоду").assertIsDisplayed()
    }

    // S17: кнопка приглашения открывает форму создания; сохранение непустого
    // названия добавляет колоду и убирает пустое состояние
    @Test
    fun emptyState_inviteButton_opensCreateFormAndCreatesDeck() = runTest(dispatcher) {
        repository.decks.value = emptyList()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Создать первую колоду").performClick()

        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()

        composeRule.onNodeWithText("Название")
            .performTextInput("Первая колода")
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Первая колода").assertIsDisplayed()
        composeRule.onNodeWithText("0 карточек").assertIsDisplayed()
        composeRule.onNodeWithText("Пока нет колод").assertDoesNotExist()
        assertTrue(repository.createdNames.contains("Первая колода"))
    }

    // S17 (через S2–S3): из пустого состояния пустое название по-прежнему
    // отклоняется — форма остаётся открытой, колода не создаётся
    @Test
    fun emptyState_emptyName_isStillRejected() = runTest(dispatcher) {
        repository.decks.value = emptyList()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Создать первую колоду").performClick()
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Название не может быть пустым").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
        assertTrue(repository.createdNames.isEmpty())
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mnemocards.domain.model.Deck

/**
 * Экран «Список колод». UDF: события текут вверх в ViewModel,
 * состояние приходит вниз из StateFlow. Список читается из Room
 * через Flow репозитория, поэтому после мутаций обновляется сам.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    viewModel: DeckListViewModel,
    onDeckClick: (Deck) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Колоды") },
                containerColor = MaterialTheme.colorScheme.background
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openCreateDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить колоду"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Ошибка репозитория (аналог 500): экран остаётся консистентным
            // с фактическим состоянием Room (S22)
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (uiState.decks.isEmpty()) {
                // Пустое состояние (S16, S17): приглашение создать первую колоду
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Пока нет колод",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = viewModel::openCreateDialog) {
                        Text("Создать первую колоду")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.decks, key = { it.id }) { deck ->
                        DeckItem(
                            deck = deck,
                            isMenuShown = uiState.isMenuShown &&
                                uiState.selectedDeck?.id == deck.id,
                            onClick = { onDeckClick(deck) },
                            onLongClick = { viewModel.onDeckLongPressed(deck) },
                            onRename = viewModel::openRenameDialog,
                            onDelete = viewModel::openDeleteDialog
                        )
                    }
                }
            }
        }
    }

    if (uiState.isCreateDialogShown) {
        DeckNameDialog(
            title = "Новая колода",
            value = uiState.createDeckName,
            isError = uiState.isCreateNameError,
            onValueChange = viewModel::updateCreateDeckName,
            onSave = viewModel::createDeck,
            onDismiss = viewModel::dismissCreateDialog
        )
    }

    if (uiState.isRenameDialogShown) {
        DeckNameDialog(
            title = "Переименовать колоду",
            value = uiState.renameDeckName,
            isError = uiState.isRenameNameError,
            onValueChange = viewModel::updateRenameDeckName,
            onSave = viewModel::renameDeck,
            onDismiss = viewModel::dismissRenameDialog
        )
    }

    if (uiState.isDeleteDialogShown) {
        DeleteDeckDialog(
            deckName = uiState.selectedDeck?.name.orEmpty(),
            onConfirm = viewModel::deleteDeck,
            onDismiss = viewModel::dismissDeleteDialog
        )
    }
}

/**
 * Пункт списка колод: тап — открыть тренировку, долгое нажатие (S6) —
 * контекстное меню ровно с двумя действиями. Название обрезается
 * одной строкой с ellipsis, в Room хранится полный текст (S5).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckItem(
    deck: Deck,
    isMenuShown: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = deck.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${deck.cardCount} карточек",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        DropdownMenu(
            expanded = isMenuShown,
            onDismissRequest = onLongClick.let { { } }.let { { dismissNoOp() } }
        ) {
            DropdownMenuItem(
                text = { Text("Переименовать") },
                onClick = onRename
            )
            DropdownMenuItem(
                text = { Text("Удалить") },
                onClick = onDelete
            )
        }
    }
}

private fun dismissNoOp() = Unit
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
- capability `deck.management` — создание, переименование и удаление колод
  локально в Room.

  **Реализована**, change `add-deck-management` (2026-09-16): создание колод
  через форму с одним полем и валидацией пустого названия (пробелы — как
  пустое, дубликаты имён разрешены, длинные названия хранятся полностью и
  обрезаются в списке); контекстное меню по долгому нажатию («Переименовать» /
  «Удалить»); каскадное удаление колоды вместе с карточками с подтверждением;
  пустое состояние списка с приглашением создать первую колоду; все изменения
  персистентны в Room и переживают перезапуск; одноразовый сидинг — удалённая
  базовая колода не воскрешается; краевые случаи покрыты (устаревший элемент —
  аналог 404, ошибка БД — аналог 500). Полностью офлайн — сеть не задействована.
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
    выделяться должна карточка, а не панель.

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
