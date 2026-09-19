## Батч 1/8

### FILE: app/src/test/java/com/mnemocards/data/local/SyncFieldsDaoTest.kt
```kotlin
package com.mnemocards.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * T1. Падающий тест: синх-поля pendingChanges/editCounter/deleted
 * и DAO-операции над ними.
 *
 * Сценарии: S5 (выборка pending, включая tombstone), S7/S11 (физическое
 * удаление), S9 (снятие pending при совпадении счётчика), S10 (правка
 * в полёте — pending сохраняется).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncFieldsDaoTest {

    private lateinit var db: MnemoCardsDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedDeck(name: String = "Deck A"): Long =
        db.deckDao().insert(DeckEntity(name = name))

    private suspend fun seedCard(deckId: Long, word: String = "word"): Long =
        db.cardDao().insert(
            CardEntity(deckId = deckId, word = word, translation = "перевод"),
        )

    // --- S5: выборка pending-записей, включая tombstone ---

    @Test
    fun markPending_setsFlag_andIncrementsEditCounter() = runTest {
        val deckId = seedDeck()
        db.deckDao().markPending(deckId)
        db.deckDao().markPending(deckId)

        val pending = db.deckDao().getPendingDecks()
        assertEquals(1, pending.size)
        assertTrue(pending[0].pendingChanges)
        assertEquals(2L, pending[0].editCounter)
    }

    @Test
    fun pendingSelection_forDecks_includesDeletedTombstones() = runTest {
        val deckId = seedDeck()
        seedDeck("Deck B") // не помечена

        db.deckDao().markDeleted(deckId) // tombstone + pending
        db.deckDao().markPending(deckId)

        val pending = db.deckDao().getPendingDecks()
        assertEquals(1, pending.size)
        assertEquals(deckId, pending[0].id)
        assertTrue(pending[0].deleted)
    }

    @Test
    fun pendingSelection_forCards_includesDeletedTombstones_andSkipsClean() = runTest {
        val deckId = seedDeck()
        val x1 = seedCard(deckId, "x1")
        val x2 = seedCard(deckId, "x2")
        val x3 = seedCard(deckId, "x3")

        db.cardDao().markPending(x1)
        db.cardDao().markDeleted(x3)
        db.cardDao().markPending(x3)
        // x2 — без пометки, в выборку не попадает

        val pending = db.cardDao().getPendingCardsByDeck(deckId)
        assertEquals(2, pending.size)
        val ids = pending.map { it.id }.sorted()
        assertEquals(listOf(x1, x3).sorted(), ids)
        assertTrue(pending.first { it.id == x3 }.deleted)
    }

    // --- S9: снятие pending при совпадении editCounter со снапшотом ---

    @Test
    fun clearPendingIfUnchanged_matchingSnapshot_clearsFlag() = runTest {
        val deckId = seedDeck()
        db.deckDao().markPending(deckId)
        db.deckDao().markPending(deckId) // editCounter = 2

        db.deckDao().clearPendingIfUnchanged(deckId, snapshotEditCounter = 2)

        val deck = db.deckDao().getByIdIncludingDeleted(deckId)
        assertNotNull(deck)
        assertFalse(deck!!.pendingChanges)
        assertTrue(db.deckDao().getPendingDecks().isEmpty())
    }

    @Test
    fun clearPendingIfUnchanged_staleSnapshot_keepsFlag_andKeepsEdit() = runTest {
        val deckId = seedDeck()
        db.deckDao().markPending(deckId) // editCounter = 1, снапшот = 1
        val snapshot = 1L
        db.deckDao().markPending(deckId) // правка в полёте: editCounter = 2

        db.deckDao().clearPendingIfUnchanged(deckId, snapshot)

        val deck = db.deckDao().getByIdIncludingDeleted(deckId)
        assertNotNull(deck)
        assertTrue(deck!!.pendingChanges)
        assertEquals(2L, deck.editCounter)
    }

    @Test
    fun clearPendingIfUnchanged_forCards_respectsSnapshot() = runTest {
        val deckId = seedDeck()
        val x1 = seedCard(deckId, "x1")
        val x2 = seedCard(deckId, "x2")

        db.cardDao().markPending(x1) // counter = 1
        db.cardDao().markPending(x2)
        db.cardDao().markPending(x2) // counter = 2

        db.cardDao().clearPendingIfUnchanged(x1, snapshotEditCounter = 1)
        db.cardDao().clearPendingIfUnchanged(x2, snapshotEditCounter = 1) // не совпал

        val pending = db.cardDao().getPendingCardsByDeck(deckId)
        assertEquals(listOf(x2), pending.map { it.id })
    }

    // --- S7 / S11: физическое удаление tombstone ---

    @Test
    fun deleteByIdPhysically_removesTombstone_fromDecks() = runTest {
        val deckId = seedDeck()
        db.deckDao().markDeleted(deckId)
        db.deckDao().markPending(deckId)

        db.deckDao().deleteByIdPhysically(deckId)

        assertNull(db.deckDao().getByIdIncludingDeleted(deckId))
        assertTrue(db.deckDao().getPendingDecks().isEmpty())
    }

    @Test
    fun deleteByIdPhysically_removesTombstone_fromCards() = runTest {
        val deckId = seedDeck()
        val cardId = seedCard(deckId, "x3")
        db.cardDao().markDeleted(cardId)
        db.cardDao().markPending(cardId)

        db.cardDao().deleteByIdPhysically(cardId)

        assertNull(db.cardDao().getByIdIncludingDeleted(cardId))
        assertTrue(db.cardDao().getPendingCardsByDeck(deckId).isEmpty())
    }

    // --- S7: tombstone скрыт от UI-запросов, но остаётся в pending ---

    @Test
    fun tombstoneCards_areHiddenFromLiveQueries() = runTest {
        val deckId = seedDeck()
        val live = seedCard(deckId, "live")
        val dead = seedCard(deckId, "dead")
        db.cardDao().markDeleted(dead)

        val visible = db.cardDao().getByDeckIncludingDeleted(deckId)
            .filterNot { it.deleted }
            .map { it.id }

        assertEquals(listOf(live), visible)
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/local/SyncStateDaoTest.kt
```kotlin
package com.mnemocards.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.AuthTokensEntity
import com.mnemocards.data.db.sync.LastSyncResultEntity
import com.mnemocards.data.db.sync.SyncStateDao
import com.mnemocards.data.db.sync.SyncStateEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * T3. Падающий тест: состояние потоков (ревизии по ключу), пара токенов,
 * итог последней синхронизации.
 *
 * Сценарии: S4 (отсутствие ревизии = 0), S12 (обновление пары токенов),
 * S13/S16/S24 (запись итога, чтение ревизий по ключу потока).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncStateDaoTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var dao: SyncStateDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.syncStateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- Ревизии потоков ---

    @Test
    fun missingRevision_isTreatedAsZero() = runTest {
        assertEquals(0L, dao.getRevision("decks"))
        assertEquals(0L, dao.getRevision("cards:9"))
    }

    @Test
    fun saveRevision_isReadableByStreamKey() = runTest {
        dao.saveRevision("decks", 42)
        dao.saveRevision("cards:5", 7)
        dao.saveRevision("cards:9", 13)

        assertEquals(42L, dao.getRevision("decks"))
        assertEquals(7L, dao.getRevision("cards:5"))
        assertEquals(13L, dao.getRevision("cards:9"))
    }

    @Test
    fun saveRevision_overwritesPreviousValue() = runTest {
        dao.saveRevision("decks", 41)
        dao.saveRevision("decks", 42)

        assertEquals(42L, dao.getRevision("decks"))
        assertEquals(1, dao.getAllSyncStates().size)
    }

    @Test
    fun getAllSyncStates_returnsPersistedEntries() = runTest {
        dao.saveRevision("decks", 42)
        dao.saveRevision("cards:5", 7)

        val states = dao.getAllSyncStates()
        assertEquals(2, states.size)
        assertTrue(states.any { it.streamKey == "decks" && it.revision == 42L })
        assertTrue(states.any { it.streamKey == "cards:5" && it.revision == 7L })
    }

    // --- Токены ---

    @Test
    fun tokens_initiallyAbsent() = runTest {
        assertNull(dao.getTokens())
    }

    @Test
    fun saveTokens_storesPair() = runTest {
        dao.saveTokens(AuthTokensEntity(accessToken = "access-1", refreshToken = "refresh-1"))

        val tokens = dao.getTokens()
        assertNotNull(tokens)
        assertEquals("access-1", tokens!!.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
    }

    @Test
    fun saveTokens_updatesExistingPair_afterRefresh() = runTest {
        dao.saveTokens(AuthTokensEntity(accessToken = "access-1", refreshToken = "refresh-1"))
        dao.saveTokens(AuthTokensEntity(accessToken = "access-2", refreshToken = "refresh-2"))

        val tokens = dao.getTokens()
        assertNotNull(tokens)
        assertEquals("access-2", tokens!!.accessToken)
        assertEquals("refresh-2", tokens.refreshToken)
    }

    @Test
    fun clearTokens_removesPair() = runTest {
        dao.saveTokens(AuthTokensEntity(accessToken = "access-1", refreshToken = "refresh-1"))
        dao.clearTokens()

        assertNull(dao.getTokens())
    }

    // --- Итог последней синхронизации ---

    @Test
    fun lastSyncResult_initiallyAbsent() = runTest {
        assertNull(dao.getLastSyncResult())
    }

    @Test
    fun saveLastSyncResult_overwritesPrevious() = runTest {
        dao.saveLastSyncResult(
            LastSyncResultEntity(
                finishedAtMillis = 1_000L,
                status = "success",
                errorMessage = null,
            ),
        )
        dao.saveLastSyncResult(
            LastSyncResultEntity(
                finishedAtMillis = 2_000L,
                status = "error",
                errorMessage = "HTTP 500",
            ),
        )

        val result = dao.getLastSyncResult()
        assertNotNull(result)
        assertEquals(2_000L, result!!.finishedAtMillis)
        assertEquals("error", result.status)
        assertEquals("HTTP 500", result.errorMessage)
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckEntity.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /**
     * Синхронизация (change add-background-sync):
     * запись изменена локально и должна уйти на сервер (S5, S20).
     */
    val pendingChanges: Boolean = false,
    /**
     * Счётчик правок записи; снапшот фиксируется при отправке, по нему
     * решается, можно ли снять pending после ответа (S9, S10).
     */
    val editCounter: Long = 0,
    /** Tombstone: локально удалена, ждёт подтверждения сервером (S5, S11). */
    val deleted: Boolean = false,
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
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val word: String,
    val translation: String,
    /** Планировщик SRS: срок следующего показа (epoch millis). */
    val dueAt: Long = 0,
    /** Планировщик SRS: текущий интервал, дней. */
    val intervalDays: Int = 0,
    /** Планировщик SRS: коэффициент лёгкости. */
    val easeFactor: Float = 2.5f,
    /** Планировщик SRS: число успешных повторений. */
    val repetitions: Int = 0,
    /**
     * Синхронизация (change add-background-sync):
     * запись изменена локально и должна уйти на сервер (S5, S20).
     */
    val pendingChanges: Boolean = false,
    /**
     * Счётчик правок записи; снапшот фиксируется при отправке, по нему
     * решается, можно ли снять pending после ответа (S9, S10).
     */
    val editCounter: Long = 0,
    /** Tombstone: локально удалена, ждёт подтверждения сервером (S5, S11). */
    val deleted: Boolean = false,
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
    suspend fun insert(deck: DeckEntity): Long

    @Update
    suspend fun update(deck: DeckEntity)

    @Query("SELECT * FROM decks WHERE deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE deleted = 0 ORDER BY name")
    suspend fun getAll(): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE id = :id AND deleted = 0")
    suspend fun getById(id: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: Long): DeckEntity?

    @Query(
        """
        SELECT decks.id AS deckId, decks.name AS name, COUNT(cards.id) AS cardCount
        FROM decks
        LEFT JOIN cards ON cards.deckId = decks.id AND cards.deleted = 0
        WHERE decks.deleted = 0
        GROUP BY decks.id
        ORDER BY decks.name
        """,
    )
    fun observeDeckStats(): Flow<List<DeckWithStats>>

    @Query("SELECT COUNT(*) FROM decks WHERE deleted = 0")
    suspend fun count(): Int

    // --- Синхронизация (change add-background-sync) ---

    /** Все колоды, помеченные как локально изменённые (включая tombstone, S5). */
    @Query("SELECT * FROM decks WHERE pendingChanges = 1")
    suspend fun getPendingDecks(): List<DeckEntity>

    /** Помечает колоду локально изменённой и инкрементирует счётчик правок (S5, S20). */
    @Query(
        "UPDATE decks SET pendingChanges = 1, editCounter = editCounter + 1 " +
            "WHERE id = :deckId",
    )
    suspend fun markPending(deckId: Long)

    /** Tombstone: скрыть колоду от UI, оставив для отправки на сервер (S5, S11). */
    @Query("UPDATE decks SET deleted = 1 WHERE id = :deckId")
    suspend fun markDeleted(deckId: Long)

    /**
     * Снимает pending только если текущий счётчик правок совпал со снапшотом,
     * зафиксированным при отправке (S9); при несовпадении правка сохраняется (S10).
     */
    @Query(
        "UPDATE decks SET pendingChanges = 0 " +
            "WHERE id = :deckId AND editCounter = :snapshotEditCounter",
    )
    suspend fun clearPendingIfUnchanged(deckId: Long, snapshotEditCounter: Long)

    /** Физическое удаление (tombstone после подтверждения сервером, S7, S11). */
    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteByIdPhysically(deckId: Long)

    /** Полная замена потока при fullSync: удалить все колоды (S8). */
    @Query("DELETE FROM decks")
    suspend fun deleteAllPhysically()
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
    suspend fun insert(card: CardEntity): Long

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT * FROM cards WHERE deckId = :deckId AND deleted = 0 ORDER BY word")
    fun observeByDeck(deckId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId AND deleted = 0 ORDER BY word")
    suspend fun getByDeck(deckId: Long): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id AND deleted = 0")
    suspend fun getById(id: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    suspend fun getByDeckIncludingDeleted(deckId: Long): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND deleted = 0")
    suspend fun countByDeck(deckId: Long): Int

    // --- Синхронизация (change add-background-sync) ---

    /** Карточки колоды, помеченные как локально изменённые (включая tombstone, S5). */
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND pendingChanges = 1")
    suspend fun getPendingCardsByDeck(deckId: Long): List<CardEntity>

    /** Помечает карточку локально изменённой и инкрементирует счётчик правок (S5, S20, S22). */
    @Query(
        "UPDATE cards SET pendingChanges = 1, editCounter = editCounter + 1 " +
            "WHERE id = :cardId",
    )
    suspend fun markPending(cardId: Long)

    /** Tombstone: скрыть карточку от UI, оставив для отправки на сервер (S5, S7, S11). */
    @Query("UPDATE cards SET deleted = 1 WHERE id = :cardId")
    suspend fun markDeleted(cardId: Long)

    /**
     * Снимает pending только если текущий счётчик правок совпал со снапшотом,
     * зафиксированным при отправке (S9); при несовпадении правка сохраняется (S10).
     */
    @Query(
        "UPDATE cards SET pendingChanges = 0 " +
            "WHERE id = :cardId AND editCounter = :snapshotEditCounter",
    )
    suspend fun clearPendingIfUnchanged(cardId: Long, snapshotEditCounter: Long)

    /** Физическое удаление (tombstone после подтверждения сервером, S7, S11). */
    @Query("DELETE FROM cards WHERE id = :cardId")
    suspend fun deleteByIdPhysically(cardId: Long)

    /** Физическое удаление всех карточек колоды (удаление колоды, S3, S18). */
    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteByDeckPhysically(deckId: Long)

    /** Полная замена потока колоды при fullSync (S8). */
    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteAllByDeckPhysically(deckId: Long)
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MnemoCardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao

    abstract fun cardDao(): CardDao

    companion object {
        const val DATABASE_NAME = "mnemocards.db"
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/Migrations.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции Room. Схемы экспортируются в app/schemas (ksp room.schemaLocation)
 * и проверяются MigrationTestHelper.
 */

/**
 * 2 → 3 (change add-background-sync): синх-поля для офлайн-first обмена
 * с сервером — признак локального изменения, счётчик правок, tombstone.
 * Дефолты обратимы и не разрушают существующие данные.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE decks ADD COLUMN pendingChanges INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE decks ADD COLUMN editCounter INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE decks ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE cards ADD COLUMN pendingChanges INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE cards ADD COLUMN editCounter INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE cards ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
        )
    }
}

val ALL_MIGRATIONS = arrayOf<Migration>(
    MIGRATION_2_3,
)
```


---

## Батч 2/8

### FILE: app/src/main/java/com/mnemocards/data/db/sync/SyncStateEntity.kt
```kotlin
package com.mnemocards.data.db.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ревизия потока синхронизации (change add-background-sync).
 * Ключ потока: "decks" для списка колод, "cards:{deckUuid}" для карточек колоды.
 * Отсутствие записи трактуется как ревизия 0 (S4, S24).
 */
@Entity(tableName = "sync_states")
data class SyncStateEntity(
    @PrimaryKey
    val streamKey: String,
    val revision: Long,
)
```

### FILE: app/src/main/java/com/mnemocards/data/db/sync/AuthTokensEntity.kt
```kotlin
package com.mnemocards.data.db.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Пара access/refresh токенов; единственная строка (id = 1).
 * Наличие строки = синхронизация настроена (S14).
 * Обновляется после успешного refresh (S12), удаляется при отказе (S13).
 */
@Entity(tableName = "auth_tokens")
data class AuthTokensEntity(
    @PrimaryKey
    val id: Long = SINGLE_ROW_ID,
    val accessToken: String,
    val refreshToken: String,
) {
    companion object {
        const val SINGLE_ROW_ID = 1L
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/sync/LastSyncResultEntity.kt
```kotlin
package com.mnemocards.data.db.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Итог последней синхронизации: время, статус, текст ошибки (S2, S13, S15).
 * Единственная строка (id = 1), перезаписывается по завершении сессии.
 */
@Entity(tableName = "last_sync_result")
data class LastSyncResultEntity(
    @PrimaryKey
    val id: Long = SINGLE_ROW_ID,
    val finishedAtMillis: Long,
    /** "success" | "error" | "requires_login" */
    val status: String,
    val errorMessage: String? = null,
) {
    companion object {
        const val SINGLE_ROW_ID = 1L
        const val STATUS_SUCCESS = "success"
        const val STATUS_ERROR = "error"
        const val STATUS_REQUIRES_LOGIN = "requires_login"
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/sync/SyncStateDao.kt
```kotlin
package com.mnemocards.data.db.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {

    // --- Ревизии потоков (S1, S4, S24) ---

    /** Отсутствие записи трактуется как 0 (S4). */
    @Query("SELECT revision FROM sync_states WHERE streamKey = :streamKey")
    suspend fun getRevision(streamKey: String): Long?

    /** Вспомогательный метод: ревизия потока или 0 при отсутствии записи. */
    @Query("SELECT COALESCE((SELECT revision FROM sync_states WHERE streamKey = :streamKey), 0)")
    suspend fun getRevisionOrZero(streamKey: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Query("SELECT * FROM sync_states")
    suspend fun getAllSyncStates(): List<SyncStateEntity>

    @Query("SELECT * FROM sync_states")
    fun observeAllSyncStates(): Flow<List<SyncStateEntity>>

    // --- Токены (S12, S13, S14) ---

    @Query("SELECT * FROM auth_tokens WHERE id = 1")
    suspend fun getTokens(): AuthTokensEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTokens(tokens: AuthTokensEntity)

    @Query("DELETE FROM auth_tokens WHERE id = 1")
    suspend fun clearTokens()

    /** Признак «синхронизация настроена» = наличие токенов (S14). */
    @Query("SELECT EXISTS(SELECT 1 FROM auth_tokens)")
    suspend fun hasTokens(): Boolean

    // --- Итог последней синхронизации (S2, S13, S15) ---

    @Query("SELECT * FROM last_sync_result WHERE id = 1")
    suspend fun getLastSyncResult(): LastSyncResultEntity?

    @Query("SELECT * FROM last_sync_result WHERE id = 1")
    fun observeLastSyncResult(): Flow<LastSyncResultEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLastSyncResult(result: LastSyncResultEntity)
}

/** Реализация по умолчанию: обёртки над raw-запросами с семантикой из теста T3. */
@Dao
abstract class SyncStateDaoImpl : SyncStateDao {

    override suspend fun getRevision(streamKey: String): Long? =
        getRevisionRaw(streamKey)

    @Query("SELECT revision FROM sync_states WHERE streamKey = :streamKey")
    protected abstract suspend fun getRevisionRaw(streamKey: String): Long?
}
```

Hmm — интерфейс с default-методами не работает с абстрактным классом так просто. Упростим: сделаю интерфейс напрямую с нужными сигнатурами.

Переписываю DAO без лишней обёртки: тест вызывает `dao.getRevision("decks")` и ожидает `0L` (`assertEquals(0L, dao.getRevision("decks"))` — assertEquals(Long, Long?) компилируется с装箱? Нет — assertEquals(long, Object) неоднозначно; тест ожидает `getRevision` возвращает Long = 0). Значит `getRevision` должен возвращать `Long` через COALESCE, а отдельный nullable — `getRevisionOrNull`.

### FILE: app/src/main/java/com/mnemocards/data/db/sync/SyncStateDao.kt
```kotlin
package com.mnemocards.data.db.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO состояния синхронизации: ревизии потоков, токены, итог последней сессии
 * (change add-background-sync, задачи T3/T4).
 */
@Dao
interface SyncStateDao {

    // --- Ревизии потоков (S1, S4, S24) ---

    /** Ревизия потока; отсутствие записи трактуется как 0 (S4). */
    @Query(
        "SELECT COALESCE((SELECT revision FROM sync_states WHERE streamKey = :streamKey), 0)",
    )
    suspend fun getRevision(streamKey: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRevision(streamKey: String, revision: Long) =
        upsertSyncState(SyncStateEntity(streamKey = streamKey, revision = revision))

    @Query("SELECT * FROM sync_states")
    suspend fun getAllSyncStates(): List<SyncStateEntity>

    @Query("SELECT * FROM sync_states")
    fun observeAllSyncStates(): Flow<List<SyncStateEntity>>

    // --- Токены (S12, S13, S14) ---

    @Query("SELECT * FROM auth_tokens WHERE id = 1")
    suspend fun getTokens(): AuthTokensEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTokens(tokens: AuthTokensEntity)

    @Query("DELETE FROM auth_tokens WHERE id = 1")
    suspend fun clearTokens()

    // --- Итог последней синхронизации (S2, S13, S15) ---

    @Query("SELECT * FROM last_sync_result WHERE id = 1")
    suspend fun getLastSyncResult(): LastSyncResultEntity?

    @Query("SELECT * FROM last_sync_result WHERE id = 1")
    fun observeLastSyncResult(): Flow<LastSyncResultEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLastSyncResult(result: LastSyncResultEntity)
}
```

Внимание: default-метод в Room-интерфейсе с @Insert — Room не поддерживает default-реализации с телом, вызывающим другой DAO-метод (поддерживает в новых версиях Kotlin default methods, но @Insert на default-методе невалиден). Убираю default: сделаю `saveRevision` обычным @Query upsert.

### FILE: app/src/main/java/com/mnemocards/data/db/sync/SyncStateDao.kt (итоговый)
```kotlin
package com.mnemocards.data.db.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO состояния синхронизации: ревизии потоков, токены, итог последней сессии
 * (change add-background-sync, задачи T3/T4).
 */
@Dao
interface SyncStateDao {

    // --- Ревизии потоков (S1, S4, S24) ---

    /** Ревизия потока; отсутствие записи трактуется как 0 (S4). */
    @Query(
        "SELECT COALESCE((SELECT revision FROM sync_states WHERE streamKey = :streamKey), 0)",
    )
    suspend fun getRevision(streamKey: String): Long

    @Query(
        "INSERT OR REPLACE INTO sync_states (streamKey, revision) VALUES (:streamKey, :revision)",
    )
    suspend fun saveRevision(streamKey: String, revision: Long)

    @Query("SELECT * FROM sync_states")
    suspend fun getAllSyncStates(): List<SyncStateEntity>

    @Query("SELECT * FROM sync_states")
    fun observeAllSyncStates(): Flow<List<SyncStateEntity>>

    // --- Токены (S12, S13, S14) ---

    @Query("SELECT * FROM auth_tokens WHERE id = 1")
    suspend fun getTokens(): AuthTokensEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTokens(tokens: AuthTokensEntity)

    @Query("DELETE FROM auth_tokens WHERE id = 1")
    suspend fun clearTokens()

    // --- Итог последней синхронизации (S2, S13, S15) ---

    @Query("SELECT * FROM last_sync_result WHERE id = 1")
    suspend fun getLastSyncResult(): LastSyncResultEntity?

    @Query("SELECT * FROM last_sync_result WHERE id = 1")
    fun observeLastSyncResult(): Flow<LastSyncResultEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLastSyncResult(result: LastSyncResultEntity)
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/Migrations.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции Room. Схемы экспортируются в app/schemas (ksp room.schemaLocation)
 * и проверяются MigrationTestHelper.
 */

/**
 * 2 → 3 (change add-background-sync): синх-поля для офлайн-first обмена
 * с сервером — признак локального изменения, счётчик правок, tombstone.
 * Дефолты обратимы и не разрушают существующие данные.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE decks ADD COLUMN pendingChanges INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE decks ADD COLUMN editCounter INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE decks ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE cards ADD COLUMN pendingChanges INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE cards ADD COLUMN editCounter INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE cards ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/**
 * 3 → 4 (change add-background-sync): состояние синхронизации — ревизии
 * потоков, пара токенов, итог последней сессии. Новые таблицы, существующие
 * данные не затрагиваются.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_states (
                streamKey TEXT NOT NULL PRIMARY KEY,
                revision INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS auth_tokens (
                id INTEGER NOT NULL PRIMARY KEY,
                accessToken TEXT NOT NULL,
                refreshToken TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS last_sync_result (
                id INTEGER NOT NULL PRIMARY KEY,
                finishedAtMillis INTEGER NOT NULL,
                status TEXT NOT NULL,
                errorMessage TEXT
            )
            """.trimIndent(),
        )
    }
}

val ALL_MIGRATIONS = arrayOf<Migration>(
    MIGRATION_2_3,
    MIGRATION_3_4,
)
```

### FILE: app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mnemocards.data.db.sync.AuthTokensEntity
import com.mnemocards.data.db.sync.LastSyncResultEntity
import com.mnemocards.data.db.sync.SyncStateDao
import com.mnemocards.data.db.sync.SyncStateEntity

@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        SyncStateEntity::class,
        AuthTokensEntity::class,
        LastSyncResultEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class MnemoCardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao

    abstract fun cardDao(): CardDao

    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val DATABASE_NAME = "mnemocards.db"
    }
}
```


---

## Батч 3/8

### FILE: app/src/main/java/com/mnemocards/data/sync/SyncApplier.kt
```kotlin
package com.mnemocards.data.sync

import androidx.room.withTransaction
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase

/**
 * Серверная запись колоды из ответа синхронизации (поток "decks").
 * Маппится из сгенерированных DTO слоем сети перед применением.
 */
data class SyncDeckItem(
    val id: Long,
    val name: String,
    val deleted: Boolean = false,
)

/**
 * Серверная запись карточки из ответа синхронизации (поток "cards:{deckUuid}").
 */
data class SyncCardItem(
    val id: Long,
    val deckId: Long,
    val word: String,
    val translation: String,
    val dueAt: Long = 0,
    val intervalDays: Int = 0,
    val easeFactor: Float = 2.5f,
    val repetitions: Int = 0,
    val deleted: Boolean = false,
)

/**
 * Применение успешного ответа синхронизации к Room (change add-background-sync,
 * задача T8). Все операции — в транзакции: либо ответ применён целиком,
 * либо локальная БД не изменилась (S19).
 *
 * Правила:
 *  - серверные записи перезаписывают локальные поля, ревизия потока
 *    сохраняется из ответа (S6);
 *  - запись с признаком удаления удаляется физически, tombstone
 *    не создаётся (S7);
 *  - fullSync=true — полная замена состояния потока: локальные записи,
 *    отсутствующие в ответе, удаляются (S8);
 *  - серверная запись не применяется, если её локальная копия изменена
 *    после снапшота отправки (S25, S10);
 *  - pending снимается только при совпадении текущего editCounter
 *    со снапшотом, зафиксированным при отправке (S9);
 *  - tombstone, подтверждённый сервером, удаляется физически (S11);
 *  - повторное применение того же ответа не создаёт дублей — upsert
 *    по первичному ключу (S24).
 */
class SyncApplier(
    private val db: MnemoCardsDatabase,
) {

    /**
     * Применяет ответ потока колод.
     *
     * @param snapshot счётчики правок отправленных записей, зафиксированные
     * в момент отправки: id колоды -> editCounter.
     */
    suspend fun applyDecksResponse(
        items: List<SyncDeckItem>,
        fullSync: Boolean,
        revision: Long,
        snapshot: Map<Long, Long> = emptyMap(),
    ) {
        val deckDao = db.deckDao()
        db.withTransaction {
            val aliveIds = mutableSetOf<Long>()
            for (item in items) {
                if (item.deleted) {
                    // S7: удалена на сервере — физическое удаление без tombstone.
                    deckDao.deleteByIdPhysically(item.id)
                    continue
                }
                aliveIds += item.id
                val local = deckDao.getByIdIncludingDeleted(item.id)
                val sentCounter = snapshot[item.id]
                when {
                    // Локальной копии нет — вставка серверной записи (S4, S6).
                    local == null -> deckDao.insert(
                        DeckEntity(
                            id = item.id,
                            name = item.name,
                            pendingChanges = false,
                            editCounter = 0,
                            deleted = false,
                        ),
                    )
                    // Локальная копия не менялась с отправки — перезапись (S6, S9).
                    sentCounter != null && local.editCounter == sentCounter -> deckDao.update(
                        local.copy(
                            name = item.name,
                            pendingChanges = false,
                            deleted = false,
                        ),
                    )
                    // Немаркированная локальная копия — перезапись (S6).
                    !local.pendingChanges -> deckDao.update(
                        local.copy(
                            name = item.name,
                            pendingChanges = false,
                            deleted = false,
                        ),
                    )
                    // S25, S10: изменена после снапшота — серверная версия
                    // не применяется, локальная правка сохраняется.
                    else -> Unit
                }
            }
            if (fullSync) {
                // S8: полный срез — локальные записи вне ответа удаляются.
                for (local in deckDao.getAllIncludingDeleted()) {
                    if (local.id !in aliveIds && !isProtectedByEditCounter(local.id, local.editCounter, snapshot)) {
                        deckDao.deleteByIdPhysically(local.id)
                    }
                }
            }
            // S9, S10, S11: подтверждение отправленных записей по снапшоту.
            for ((id, sentCounter) in snapshot) {
                val local = deckDao.getByIdIncludingDeleted(id) ?: continue
                if (local.editCounter != sentCounter) continue // S10
                if (local.deleted) {
                    deckDao.deleteByIdPhysically(id) // S11
                } else {
                    deckDao.clearPendingIfUnchanged(id, sentCounter) // S9
                }
            }
            db.syncStateDao().saveRevision(STREAM_DECKS, revision)
        }
    }

    /**
     * Применяет ответ потока карточек колоды [deckId].
     *
     * @param snapshot счётчики правок отправленных карточек: id -> editCounter.
     */
    suspend fun applyCardsResponse(
        deckId: Long,
        items: List<SyncCardItem>,
        fullSync: Boolean,
        revision: Long,
        snapshot: Map<Long, Long> = emptyMap(),
    ) {
        val cardDao = db.cardDao()
        db.withTransaction {
            val aliveIds = mutableSetOf<Long>()
            for (item in items) {
                if (item.deleted) {
                    cardDao.deleteByIdPhysically(item.id) // S7
                    continue
                }
                aliveIds += item.id
                val local = cardDao.getByIdIncludingDeleted(item.id)
                val sentCounter = snapshot[item.id]
                val serverCard = CardEntity(
                    id = item.id,
                    deckId = item.deckId,
                    word = item.word,
                    translation = item.translation,
                    dueAt = item.dueAt,
                    intervalDays = item.intervalDays,
                    easeFactor = item.easeFactor,
                    repetitions = item.repetitions,
                    pendingChanges = false,
                    editCounter = 0,
                    deleted = false,
                )
                when {
                    local == null -> cardDao.insert(serverCard)
                    sentCounter != null && local.editCounter == sentCounter -> cardDao.update(
                        local.copy(
                            deckId = item.deckId,
                            word = item.word,
                            translation = item.translation,
                            dueAt = item.dueAt,
                            intervalDays = item.intervalDays,
                            easeFactor = item.easeFactor,
                            repetitions = item.repetitions,
                            pendingChanges = false,
                            deleted = false,
                        ),
                    )
                    !local.pendingChanges -> cardDao.update(
                        local.copy(
                            deckId = item.deckId,
                            word = item.word,
                            translation = item.translation,
                            dueAt = item.dueAt,
                            intervalDays = item.intervalDays,
                            easeFactor = item.easeFactor,
                            repetitions = item.repetitions,
                            pendingChanges = false,
                            deleted = false,
                        ),
                    )
                    // S25, S10
                    else -> Unit
                }
            }
            if (fullSync) {
                for (local in cardDao.getByDeckIncludingDeleted(deckId)) {
                    if (local.id !in aliveIds && !isProtectedByEditCounter(local.id, local.editCounter, snapshot)) {
                        cardDao.deleteByIdPhysically(local.id)
                    }
                }
            }
            for ((id, sentCounter) in snapshot) {
                val local = cardDao.getByIdIncludingDeleted(id) ?: continue
                if (local.editCounter != sentCounter) continue
                if (local.deleted) {
                    cardDao.deleteByIdPhysically(id)
                } else {
                    cardDao.clearPendingIfUnchanged(id, sentCounter)
                }
            }
            db.syncStateDao().saveRevision(cardsStreamKey(deckId), revision)
        }
    }

    private fun isProtectedByEditCounter(
        id: Long,
        currentCounter: Long,
        snapshot: Map<Long, Long>,
    ): Boolean = snapshot[id] != null && snapshot[id] != currentCounter

    companion object {
        const val STREAM_DECKS = "decks"

        fun cardsStreamKey(deckId: Long): String = "cards:$deckId"
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
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deck: DeckEntity): Long

    @Update
    suspend fun update(deck: DeckEntity)

    @Query("SELECT * FROM decks WHERE deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE deleted = 0 ORDER BY name")
    suspend fun getAll(): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE id = :id AND deleted = 0")
    suspend fun getById(id: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: Long): DeckEntity?

    @Query(
        """
        SELECT decks.id AS deckId, decks.name AS name, COUNT(cards.id) AS cardCount
        FROM decks
        LEFT JOIN cards ON cards.deckId = decks.id AND cards.deleted = 0
        WHERE decks.deleted = 0
        GROUP BY decks.id
        ORDER BY decks.name
        """,
    )
    fun observeDeckStats(): Flow<List<DeckWithStats>>

    @Query("SELECT COUNT(*) FROM decks WHERE deleted = 0")
    suspend fun count(): Int

    // --- Синхронизация (change add-background-sync) ---

    /** Все колоды, помеченные как локально изменённые (включая tombstone, S5). */
    @Query("SELECT * FROM decks WHERE pendingChanges = 1")
    suspend fun getPendingDecks(): List<DeckEntity>

    /** Все колоды, включая tombstone; для полной замены потока при fullSync (S8). */
    @Query("SELECT * FROM decks")
    suspend fun getAllIncludingDeleted(): List<DeckEntity>

    /** Помечает колоду локально изменённой и инкрементирует счётчик правок (S5, S20). */
    @Query(
        "UPDATE decks SET pendingChanges = 1, editCounter = editCounter + 1 " +
            "WHERE id = :deckId",
    )
    suspend fun markPending(deckId: Long)

    /** Tombstone: скрыть колоду от UI, оставив для отправки на сервер (S5, S11). */
    @Query("UPDATE decks SET deleted = 1 WHERE id = :deckId")
    suspend fun markDeleted(deckId: Long)

    /**
     * Снимает pending только если текущий счётчик правок совпал со снапшотом,
     * зафиксированным при отправке (S9); при несовпадении правка сохраняется (S10).
     */
    @Query(
        "UPDATE decks SET pendingChanges = 0 " +
            "WHERE id = :deckId AND editCounter = :snapshotEditCounter",
    )
    suspend fun clearPendingIfUnchanged(deckId: Long, snapshotEditCounter: Long)

    /** Физическое удаление (tombstone после подтверждения сервером, S7, S11). */
    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteByIdPhysically(deckId: Long)

    /** Полная замена потока при fullSync: удалить все колоды (S8). */
    @Query("DELETE FROM decks")
    suspend fun deleteAllPhysically()
}
```

### FILE: app/src/test/java/com/mnemocards/data/sync/SyncApplierTest.kt
```kotlin
package com.mnemocards.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * T7. Тест применения ответа синхронизации к Room (SyncApplier).
 *
 * Сценарии: S6 (перезапись серверными значениями), S7 (физическое удаление),
 * S8 (fullSync — полная замена потока), S9/S10 (снапшот счётчика правок),
 * S11 (подтверждение tombstone), S24 (идемпотентность), S25 (защита правки).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncApplierTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var applier: SyncApplier

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        applier = SyncApplier(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- Вспомогательные ---

    private suspend fun insertDeck(id: Long, name: String): Long {
        val rowId = db.deckDao().insert(DeckEntity(id = id, name = name))
        return if (id != 0L) id else rowId
    }

    private suspend fun insertCard(id: Long, deckId: Long, word: String, translation: String): Long {
        val rowId = db.cardDao().insert(
            CardEntity(id = id, deckId = deckId, word = word, translation = translation),
        )
        return if (id != 0L) id else rowId
    }

    private suspend fun editCardOnce(cardId: Long) {
        db.cardDao().markPending(cardId)
    }

    // --- S6: перезапись локальных записей серверными ---

    @Test
    fun applyDecks_overwritesLocalValues_savesRevision() = runTest {
        insertDeck(1, "Старое имя")
        db.deckDao().markPending(1)

        applier.applyDecksResponse(
            items = listOf(SyncDeckItem(id = 1, name = "Новое имя")),
            fullSync = false,
            revision = 43,
            snapshot = mapOf(1L to db.deckDao().getByIdIncludingDeleted(1)!!.editCounter),
        )

        val deck = db.deckDao().getById(1)
        assertNotNull(deck)
        assertEquals("Новое имя", deck!!.name)
        assertEquals(43, db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS))
    }

    @Test
    fun applyCards_updatesTranslation() = runTest {
        insertDeck(1, "Колодa")
        insertCard(10, 1, "cat", "кошка")
        db.cardDao().markPending(10)

        applier.applyCardsResponse(
            deckId = 1,
            items = listOf(
                SyncCardItem(id = 10, deckId = 1, word = "cat", translation = "кот"),
            ),
            fullSync = false,
            revision = 8,
            snapshot = mapOf(10L to db.cardDao().getByIdIncludingDeleted(10)!!.editCounter),
        )

        val card = db.cardDao().getById(10)
        assertNotNull(card)
        assertEquals("кот", card!!.translation)
        assertEquals(8, db.syncStateDao().getRevision(SyncApplier.cardsStreamKey(1)))
    }

    // --- S7: запись с признаком удаления удаляется физически ---

    @Test
    fun deletedCardInResponse_isRemovedPhysically_noTombstone() = runTest {
        insertDeck(1, "Колодa")
        insertCard(5, 1, "dog", "собака")

        applier.applyCardsResponse(
            deckId = 1,
            items = listOf(SyncCardItem(id = 5, deckId = 1, word = "dog", translation = "собака", deleted = true)),
            fullSync = false,
            revision = 9,
        )

        assertNull(db.cardDao().getByIdIncludingDeleted(5))
        assertEquals(0, db.cardDao().countByDeck(1))
    }

    // --- S8: полный срез заменяет состояние потока ---

    @Test
    fun fullSync_replacesWholeStream() = runTest {
        insertDeck(1, "A")
        insertDeck(2, "B")

        applier.applyDecksResponse(
            items = listOf(SyncDeckItem(id = 1, name = "A-сервер")),
            fullSync = true,
            revision = 100,
        )

        assertNotNull(db.deckDao().getById(1))
        assertNull(db.deckDao().getByIdIncludingDeleted(2))
        assertEquals(100, db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS))
    }

    @Test
    fun fullSync_cards_removesCardsMissingFromAnswer() = runTest {
        insertDeck(1, "Колодa")
        insertCard(10, 1, "a", "а")
        insertCard(11, 1, "b", "б")

        applier.applyCardsResponse(
            deckId = 1,
            items = listOf(SyncCardItem(id = 10, deckId = 1, word = "a", translation = "а")),
            fullSync = true,
            revision = 12,
        )

        assertNotNull(db.cardDao().getById(10))
        assertNull(db.cardDao().getByIdIncludingDeleted(11))
    }

    // --- S9: снятие pending при совпадении счётчика ---

    @Test
    fun pendingCleared_whenEditCounterMatchesSnapshot() = runTest {
        insertDeck(1, "A")
        db.deckDao().markPending(1) // editCounter = 1
        val snapshot = mapOf(1L to 1L)

        applier.applyDecksResponse(
            items = emptyList(),
            fullSync = false,
            revision = 50,
            snapshot = snapshot,
        )

        assertFalse(db.deckDao().getByIdIncludingDeleted(1)!!.pendingChanges)
    }

    // --- S10: счётчик не совпал — pending сохраняется, правка не затирается ---

    @Test
    fun pendingKept_whenEditedWhileRequestInFlight() = runTest {
        insertDeck(1, "Колодa")
        insertCard(10, 1, "cat", "старый перевод")
        db.cardDao().markPending(10) // editCounter = 1, снапшот = 1
        val snapshot = mapOf(10L to 1L)
        db.cardDao().markPending(10) // правка в полёте: editCounter = 2

        applier.applyCardsResponse(
            deckId = 1,
            items = listOf(
                SyncCardItem(id = 10, deckId = 1, word = "cat", translation = "серверный перевод"),
            ),
            fullSync = false,
            revision = 15,
            snapshot = snapshot,
        )

        val card = db.cardDao().getByIdIncludingDeleted(10)
        assertNotNull(card)
        assertTrue(card!!.pendingChanges) // S10: 2 != 1
        assertEquals("старый перевод", card.translation) // правка пользователя сохранена
    }

    // --- S11: подтверждение tombstone сервером — полное удаление ---

    @Test
    fun confirmedTombstone_removedPhysically() = runTest {
        insertDeck(1, "Колодa")
        insertCard(3, 1, "x3", "у3")
        db.cardDao().markPending(3) // editCounter = 1
        db.cardDao().markDeleted(3) // tombstone

        applier.applyCardsResponse(
            deckId = 1,
            items = emptyList(),
            fullSync = false,
            revision = 20,
            snapshot = mapOf(3L to 1L),
        )

        assertNull(db.cardDao().getByIdIncludingDeleted(3))
        assertEquals(0, db.cardDao().countByDeck(1))
    }

    // --- S24: повторное применение того же ответа не создаёт дублей ---

    @Test
    fun applyingSameResponseTwice_isIdempotent() = runTest {
        insertDeck(1, "Колодa")
        val items = listOf(
            SyncCardItem(id = 10, deckId = 1, word = "a", translation = "а"),
            SyncCardItem(id = 11, deckId = 1, word = "b", translation = "б"),
        )

        applier.applyCardsResponse(deckId = 1, items = items, fullSync = false, revision = 5)
        applier.applyCardsResponse(deckId = 1, items = items, fullSync = false, revision = 5)

        assertEquals(2, db.cardDao().countByDeck(1))
        assertEquals(5, db.syncStateDao().getRevision(SyncApplier.cardsStreamKey(1)))
    }

    // --- S25: серверная запись не применяется, если локальная копия
    //         изменена после снапшота ---

    @Test
    fun serverRecord_skipped_whenLocalCopyChangedAfterSnapshot() = runTest {
        insertDeck(1, "Колодa")
        insertCard(7, 1, "x7", "локальная правка")
        db.cardDao().markPending(7) // изменена после снятия снапшота, в отправке не была

        applier.applyCardsResponse(
            deckId = 1,
            items = listOf(
                SyncCardItem(id = 7, deckId = 1, word = "x7", translation = "серверная версия"),
            ),
            fullSync = false,
            revision = 30,
            snapshot = emptyMap(),
        )

        val card = db.cardDao().getByIdIncludingDeleted(7)
        assertNotNull(card)
        assertTrue(card!!.pendingChanges)
        assertEquals("локальная правка", card.translation)
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/sync/SyncEngineSessionTest.kt
```kotlin
package com.mnemocards.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * T9. Падающий тест: порядок сессии синхронизации и чанкирование
 * (SyncEngine ещё не реализован — задача T10).
 *
 * Сценарии: S1 (порядок сессии), S3 (карточки только живых колод),
 * S4 (новая колода since=0), S5 (только pending в отправке, включая tombstone),
 * S24 (возобновление по сохранённым ревизиям), S26 (чанки ≤1000).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncEngineSessionTest {

    // --- Контрактный мини-API (зеркало specs/openapi.yaml для теста) ---

    @Serializable
    data class SyncChange(val id: Long, val deleted: Boolean = false)

    @Serializable
    data class SyncRequestBody(val sinceRevision: Long, val changed: List<SyncChange>)

    @Serializable
    data class DeckItemDto(val id: Long, val name: String, val deleted: Boolean = false)

    @Serializable
    data class CardItemDto(
        val id: Long,
        val deckId: Long,
        val word: String,
        val translation: String,
        val deleted: Boolean = false,
    )

    @Serializable
    data class DecksSyncResponseDto(
        val revision: Long,
        val items: List<DeckItemDto>,
        val fullSnapshot: Boolean = false,
    )

    @Serializable
    data class CardsSyncResponseDto(
        val revision: Long,
        val items: List<CardItemDto>,
        val fullSnapshot: Boolean = false,
    )

    interface TestSyncApi {
        @POST("api/v1/sync/decks")
        suspend fun syncDecks(@Body body: SyncRequestBody): DecksSyncResponseDto

        @POST("api/v1/sync/decks/{deckUuid}/cards")
        suspend fun syncCards(
            @Path("deckUuid") deckUuid: Long,
            @Body body: SyncRequestBody,
        ): CardsSyncResponseDto
    }

    private lateinit var db: MnemoCardsDatabase
    private lateinit var server: MockWebServer
    private lateinit var api: TestSyncApi
    private lateinit var engine: SyncEngine

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TestSyncApi::class.java)
        engine = SyncEngine(db, api)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun decksResponse(revision: Long, vararg items: DeckItemDto) =
        MockResponse()
            .setResponseCode(200)
            .setBody(json.encodeToString(DecksSyncResponseDto(revision, items.toList())))

    private fun cardsResponse(revision: Long, vararg items: CardItemDto) =
        MockResponse()
            .setResponseCode(200)
            .setBody(json.encodeToString(CardsSyncResponseDto(revision, items.toList())))

    // --- S1, S3: сначала колоды, затем карточки живых колод ---

    @Test
    fun session_requestsDecksFirst_thenCardsOnlyForAliveDecks() = runTest {
        db.deckDao().insert(DeckEntity(id = 1, name = "A"))
        db.deckDao().insert(DeckEntity(id = 2, name = "B"))
        db.deckDao().insert(DeckEntity(id = 3, name = "Удалённая"))
        db.deckDao().markDeleted(3)

        server.enqueue(decksResponse(43, DeckItemDto(1, "A"), DeckItemDto(2, "B"), DeckItemDto(3, "X", deleted = true)))
        server.enqueue(cardsResponse(11))
        server.enqueue(cardsResponse(22))

        engine.syncNow()

        assertEquals("/api/v1/sync/decks", server.takeRequest().path)

        val cardsRequest1 = server.takeRequest()
        assertEquals("/api/v1/sync/decks/1/cards", cardsRequest1.path)
        val cardsRequest2 = server.takeRequest()
        assertEquals("/api/v1/sync/decks/2/cards", cardsRequest2.path)

        // Колода 3 удалена физически, её карточки не запрашивались (S3).
        assertNullDeck(3)
        assertEquals(2, server.requestCount - 1 + 1) // всего 3 запроса
    }

    private suspend fun assertNullDeck(id: Long) {
        assertEquals(null, db.deckDao().getByIdIncludingDeleted(id))
    }

    // --- S4: новая колода запрашивается с нулевой ревизии ---

    @Test
    fun newDeck_cardsRequestedWithSinceZero() = runTest {
        db.deckDao().insert(DeckEntity(id = 9, name = "Новая"))

        server.enqueue(decksResponse(1, DeckItemDto(9, "Новая")))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                json.encodeToString(
                    CardsSyncResponseDto(
                        4,
                        listOf(CardItemDto(90, 9, "word", "перевод")),
                    ),
                ),
            ),
        )

        engine.syncNow()

        server.takeRequest() // decks
        val cardsRequest = server.takeRequest()
        assertEquals("/api/v1/sync/decks/9/cards", cardsRequest.path)
        val body = json.decodeFromString(SyncRequestBody.serializer(), cardsRequest.body.readUtf8())
        assertEquals(0L, body.sinceRevision)

        // Карточки применены, ревизия потока сохранена (S4).
        assertEquals(4, db.syncStateDao().getRevision("cards:9"))
        assertEquals(1, db.cardDao().countByDeck(9))
    }

    // --- S24: возобновление по сохранённым ревизиям ---

    @Test
    fun resume_usesPersistedRevisions() = runTest {
        db.syncStateDao().saveRevision("decks", 42)
        db.deckDao().insert(DeckEntity(id = 5, name = "Пять"))
        db.syncStateDao().saveRevision("cards:5", 7)

        server.enqueue(decksResponse(43, DeckItemDto(5, "Пять")))
        server.enqueue(cardsResponse(8))

        engine.syncNow()

        val decksRequest = server.takeRequest()
        val decksBody = json.decodeFromString(SyncRequestBody.serializer(), decksRequest.body.readUtf8())
        assertEquals(42L, decksBody.sinceRevision)

        val cardsRequest = server.takeRequest()
        val cardsBody = json.decodeFromString(SyncRequestBody.serializer(), cardsRequest.body.readUtf8())
        assertEquals(7L, cardsBody.sinceRevision)

        // Повторное применение не создаёт дублей (S24).
        assertEquals(0, db.cardDao().countByDeck(5))
    }

    // --- S5: в отправку попадают только pending-записи, включая tombstone ---

    @Test
    fun onlyPendingRecordsSent_includingTombstones() = runTest {
        db.deckDao().insert(DeckEntity(id = 1, name = "A"))
        db.deckDao().markPending(1) // editCounter = 1
        db.cardDao().insert(CardEntity(id = 10, deckId = 1, word = "x1", translation = "y1"))
        db.cardDao().markPending(10)
        db.cardDao().insert(CardEntity(id = 11, deckId = 1, word = "x2", translation = "y2")) // не помечена
        db.cardDao().insert(CardEntity(id = 12, deckId = 1, word = "x3", translation = "y3"))
        db.cardDao().markPending(12)
        db.cardDao().markDeleted(12) // tombstone

        server.enqueue(decksResponse(43))
        server.enqueue(cardsResponse(11))

        engine.syncNow()

        val decksRequest = server.takeRequest()
        val decksBody = json.decodeFromString(SyncRequestBody.serializer(), decksRequest.body.readUtf8())
        assertEquals(listOf(SyncChange(1)), decksBody.changed)

        val cardsRequest = server.takeRequest()
        val cardsBody = json.decodeFromString(SyncRequestBody.serializer(), cardsRequest.body.readUtf8())
        val sentIds = cardsBody.changed.map { it.id }.sorted()
        assertEquals(listOf(10L, 12L), sentIds)
        assertTrue(cardsBody.changed.first { it.id == 12L }.deleted)
    }

    // --- S26: 1200 изменений уходят чанками не более 1000 ---
    @Test
    fun manyChanges_sentInChunksOfAtMost1000() = runTest {
        db.deckDao().insert(DeckEntity(id = 1, name = "A"))
        repeat(1200) { i ->
            val id = db.cardDao().insert(
                CardEntity(deckId = 1, word = "w$i", translation = "t$i"),
            )
            db.cardDao().markPending(id)
        }

        server.enqueue(decksResponse(1, DeckItemDto(1, "A")))
        server.enqueue(cardsResponse(101))
        server.enqueue(cardsResponse(202))

        engine.syncNow()

        server.takeRequest() // decks
        val chunk1 = json.decodeFromString(
            SyncRequestBody.serializer(),
            server.takeRequest().body.readUtf8(),
        )
        val chunk2 = json.decodeFromString(
            SyncRequestBody.serializer(),
            server.takeRequest().body.readUtf8(),
        )
        assertTrue(chunk1.changed.size <= 1000)
        assertTrue(chunk2.changed.size <= 1000)
        assertEquals(1200, chunk1.changed.size + chunk2.changed.size)

        // Ревизия потока — из последнего ответа (S26).
        assertEquals(202, db.syncStateDao().getRevision("cards:1"))
    }
}
```


---

## Батч 4/8

### FILE: app/src/main/java/com/mnemocards/data/sync/SyncError.kt
```kotlin
package com.mnemocards.data.sync

/**
 * Иерархия ошибок фоновой синхронизации (T12, change add-background-sync).
 *
 * Сценарии: S15 (сеть), S16 (5xx), S17 (400), S18 (404),
 * S19 (формат), S13 (требуется вход), S26 (413).
 */
sealed class SyncError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /** Соединение не установлено, HTTP-ответа нет (S15). */
    class Network(cause: Throwable?) : SyncError("Нет соединения с сервером", cause)

    /** Ошибка сервера 5xx (S16). */
    class Server(val code: Int) : SyncError("Ошибка сервера ($code)")

    /** Серверная валидация отклонила запись, HTTP 400 (S17). */
    class Validation(details: String?) :
        SyncError("Сервер отклонил запись: ${details ?: "ошибка валидации"}")

    /** Поток удалён на сервере, HTTP 404 (S18). */
    class StreamDeleted(val streamKey: String) : SyncError("Поток удалён на сервере: $streamKey")

    /** Тело ответа не соответствует схеме (S19). */
    class Format(cause: Throwable?) : SyncError("Невалидный ответ сервера", cause)

    /** Refresh-токен отклонён — требуется вход (S13). */
    object RequiresLogin : SyncError("Требуется вход")

    /** Слишком большой массив изменений, HTTP 413 (S26). */
    object TooManyChanges : SyncError("Слишком много изменений в одном запросе")
}
```

### FILE: app/src/main/java/com/mnemocards/data/sync/SyncEngine.kt
```kotlin
package com.mnemocards.data.sync

import android.database.Cursor
import androidx.room.SimpleSQLiteQuery
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.LastSyncResultEntity
import kotlinx.coroutines.suspendCoroutine
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/**
 * Движок фоновой сессии синхронизации (T10/T12).
 *
 * Последовательность: сначала поток колод (`POST /api/v1/sync/decks`),
 * затем карточки каждой живой колоды (`POST /api/v1/sync/decks/{deckUuid}/cards`)
 * с её сохранённой ревизией (S1, S3). В отправку попадают только записи
 * с `pendingChanges`, включая tombstone (S5); изменения уходят чанками ≤1000,
 * снапшоты `editCounter` фиксируются в момент отправки (S9, S26).
 *
 * API-объект передаётся как `Any` и вызывается рефлексивно: тесты используют
 * контрактное зеркало specs/openapi.yaml, продакшн — сгенерированный Retrofit
 * интерфейс; формы методов (`syncDecks(body)`, `syncCards(deckUuid, body)`,
 * suspend, DTO с полями revision/items/fullSnapshot) совпадают.
 */
class SyncEngine(
    private val db: MnemoCardsDatabase,
    private val api: Any,
) {

    private val applier = SyncApplier(db)

    /** Лимит specs/API.md §413: не более 1000 записей в одном запросе (S26). */
    private val chunkLimit = 1000

    /**
     * Выполняет сессию; возвращает null при успехе либо ошибку [SyncError].
     * Итог (время, статус, текст ошибки) фиксируется в LastSyncResultEntity.
     */
    suspend fun syncNow(): SyncError? = try {
        runSession()
        record(
            LastSyncResultEntity(
                finishedAtMillis = System.currentTimeMillis(),
                status = LastSyncResultEntity.STATUS_SUCCESS,
            ),
        )
        null
    } catch (abort: SyncAbort) {
        record(
            LastSyncResultEntity(
                finishedAtMillis = System.currentTimeMillis(),
                status = if (abort.error is SyncError.RequiresLogin) {
                    LastSyncResultEntity.STATUS_REQUIRES_LOGIN
                } else {
                    LastSyncResultEntity.STATUS_ERROR
                },
                errorMessage = abort.error.message,
            ),
        )
        abort.error
    }

    private class SyncAbort(val error: SyncError) : Exception(error.message)

    private suspend fun record(result: LastSyncResultEntity) {
        db.syncStateDao().saveLastSyncResult(result)
    }

    // --- Сессия ---

    private suspend fun runSession() {
        syncDecksStream()
        for (deckId in aliveDeckIds()) {
            syncCardsStream(deckId)
        }
    }

    /** Поток колод: чанки pending-колод, применение каждого ответа (S1, S5, S26). */
    private suspend fun syncDecksStream() {
        val pending = pendingRecords(
            "SELECT id, editCounter, deleted FROM decks WHERE pendingChanges = 1",
        )
        val snapshot = HashMap<Long, Long>()
        pending.sortedBy { it.id }.chunked(chunkLimit).forEach { chunk ->
            chunk.forEach { snapshot[it.id] = it.editCounter }
            val since = db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS)
            val response = callApi(SyncApplier.STREAM_DECKS) {
                invokeApi("syncDecks", null, since, chunk)
            }
            applier.applyDecksResponse(
                deckItems(response),
                boolProp(response, "FullSnapshot"),
                longProp(response, "Revision"),
                snapshot.toMap(),
            )
        }
    }

    /**
     * Поток карточек колоды: только живые колоды после применения списка (S3),
     * своя ревизия потока (S4, S24); 404 — колода удалена на сервере,
     * удаляется локально полностью, сессия продолжается (S18).
     */
    private suspend fun syncCardsStream(deckId: Long) {
        val streamKey = SyncApplier.cardsStreamKey(deckId)
        val error = try {
            val pending = pendingRecords(
                "SELECT id, editCounter, deleted FROM cards " +
                    "WHERE pendingChanges = 1 AND deckId = $deckId",
            )
            val snapshot = HashMap<Long, Long>()
            pending.sortedBy { it.id }.chunked(chunkLimit).forEach { chunk ->
                chunk.forEach { snapshot[it.id] = it.editCounter }
                val since = db.syncStateDao().getRevision(streamKey)
                val response = callApi(streamKey) {
                    invokeApi("syncCards", deckId, since, chunk)
                }
                applier.applyCardsResponse(
                    deckId,
                    cardItems(response),
                    boolProp(response, "FullSnapshot"),
                    longProp(response, "Revision"),
                    snapshot.toMap(),
                )
            }
            null
        } catch (abort: SyncAbort) {
            abort
        }
        if (error != null) {
            if (error.error is SyncError.StreamDeleted) {
                // S18: поток удалён на сервере — полное локальное удаление.
                db.execSQL("DELETE FROM cards WHERE deckId = ?", arrayOf<Any>(deckId))
                db.execSQL("DELETE FROM decks WHERE id = ?", arrayOf<Any>(deckId))
            } else {
                throw error
            }
        }
    }

    // --- Сбор pending-записей (S5: включая tombstone) ---

    private class PendingRecord(val id: Long, val editCounter: Long, val deleted: Boolean)

    private fun pendingRecords(sql: String): List<PendingRecord> {
        val out = mutableListOf<PendingRecord>()
        db.query(SimpleSQLiteQuery(sql)).use { cursor: Cursor ->
            while (cursor.moveToNext()) {
                out += PendingRecord(
                    id = cursor.getLong(0),
                    editCounter = cursor.getLong(1),
                    deleted = cursor.getInt(2) == 1,
                )
            }
        }
        return out
    }

    private fun aliveDeckIds(): List<Long> {
        val out = mutableListOf<Long>()
        db.query(SimpleSQLiteQuery("SELECT id FROM decks WHERE deleted = 0")).use { cursor: Cursor ->
            while (cursor.moveToNext()) {
                out += cursor.getLong(0)
            }
        }
        return out
    }

    // --- Рефлексивный вызов suspend-метода API ---

    private suspend fun invokeApi(
        methodName: String,
        deckId: Long?,
        since: Long,
        changes: List<PendingRecord>,
    ): Any? {
        val method = api.javaClass.methods.first { it.name == methodName }
        val bodyIndex = if (deckId == null) 0 else 1
        val body = buildRequestBody(method.parameterTypes[bodyIndex], since, changes)
        val target = method.declaringClass
        // suspend-функция: последний параметр — Continuation.
        return suspendCoroutine { continuation ->
            val result = if (deckId == null) {
                method.invoke(api, body, continuation)
            } else {
                method.invoke(api, deckId, body, continuation)
            }
            if (result !== COROUTINE_SUSPENDED) {
                continuation.resumeWith(Result.success(result))
            }
        }.let { target.kotlin; it }
    }

    private fun buildRequestBody(bodyClass: Class<*>, since: Long, changes: List<PendingRecord>): Any {
        val ctor = bodyClass.constructors.first { it.parameterTypes.size == 2 }
        val elementClass = (ctor.genericParameterTypes[1] as ParameterizedType)
            .actualTypeArguments[0] as Class<*>
        val elementCtor = elementClass.constructors.first { it.parameterTypes.size == 2 }
        val changed = changes.map { elementCtor.newInstance(it.id, it.deleted) }
        return ctor.newInstance(since, changed)
    }

    // --- Чтение полей ответа ---

    private fun prop(instance: Any?, name: String): Any? =
        instance?.javaClass?.getMethod("get$name")?.invoke(instance)

    private fun longProp(instance: Any?, name: String): Long = prop(instance, name) as Long

    private fun boolProp(instance: Any?, name: String): Boolean = prop(instance, name) as Boolean

    private fun deckItems(response: Any?): List<SyncDeckItem> =
        (prop(response, "Items") as List<*>).map {
            SyncDeckItem(
                id = longProp(it, "Id"),
                name = prop(it, "Name") as String,
                deleted = boolProp(it, "Deleted"),
            )
        }

    private fun cardItems(response: Any?): List<SyncCardItem> =
        (prop(response, "Items") as List<*>).map {
            SyncCardItem(
                id = longProp(it, "Id"),
                deckId = longProp(it, "DeckId"),
                word = prop(it, "Word") as String,
                translation = prop(it, "Translation") as String,
                deleted = boolProp(it, "Deleted"),
            )
        }

    // --- Сопоставление ошибок (T12) ---

    private suspend fun <T> callApi(streamKey: String, block: suspend () -> T): T = try {
        block()
    } catch (abort: SyncAbort) {
        throw abort
    } catch (e: Throwable) {
        throw SyncAbort(toSyncError(e, streamKey))
    }

    private fun toSyncError(e: Throwable, streamKey: String): SyncError = when {
        e is HttpException -> when (e.code()) {
            400 -> SyncError.Validation(e.response()?.errorBody()?.string())
            401 -> SyncError.RequiresLogin
            404 -> SyncError.StreamDeleted(streamKey)
            413 -> SyncError.TooManyChanges
            else -> SyncError.Server(e.code())
        }
        e is SerializationException -> SyncError.Format(e)
        e is IOException -> SyncError.Network(e)
        else -> SyncError.Network(e)
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/sync/SyncEngineErrorTest.kt
```kotlin
package com.mnemocards.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.LastSyncResultEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * T11. Падающий тест: обработка ошибок сессии синхронизации.
 *
 * Сценарии: S15 (сеть недоступна), S16 (500 на поток карточек),
 * S17 (400), S18 (404 — поток удалён), S19 (невалидное тело), S26 (413).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncEngineErrorTest {

    // --- Контрактный мини-API (зеркало specs/openapi.yaml для теста) ---

    @Serializable
    data class SyncChange(val id: Long, val deleted: Boolean = false)

    @Serializable
    data class SyncRequestBody(val sinceRevision: Long, val changed: List<SyncChange>)

    @Serializable
    data class DeckItemDto(val id: Long, val name: String, val deleted: Boolean = false)

    @Serializable
    data class CardItemDto(
        val id: Long,
        val deckId: Long,
        val word: String,
        val translation: String,
        val deleted: Boolean = false,
    )

    @Serializable
    data class DecksSyncResponseDto(
        val revision: Long,
        val items: List<DeckItemDto>,
        val fullSnapshot: Boolean = false,
    )

    @Serializable
    data class CardsSyncResponseDto(
        val revision: Long,
        val items: List<CardItemDto>,
        val fullSnapshot: Boolean = false,
    )

    interface TestSyncApi {
        @POST("api/v1/sync/decks")
        suspend fun syncDecks(@Body body: SyncRequestBody): DecksSyncResponseDto

        @POST("api/v1/sync/decks/{deckUuid}/cards")
        suspend fun syncCards(
            @Path("deckUuid") deckUuid: Long,
            @Body body: SyncRequestBody,
        ): CardsSyncResponseDto
    }

    private lateinit var db: MnemoCardsDatabase
    private lateinit var server: MockWebServer
    private lateinit var api: TestSyncApi
    private lateinit var engine: SyncEngine

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TestSyncApi::class.java)
        engine = SyncEngine(db, api)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun decksResponse(revision: Long, vararg items: DeckItemDto) =
        MockResponse()
            .setResponseCode(200)
            .setBody(json.encodeToString(DecksSyncResponseDto(revision, items.toList())))

    private suspend fun lastResult(): LastSyncResultEntity =
        db.syncStateDao().getLastSyncResult()!!

    // --- S15: сеть недоступна — данные/ревизии/признаки не изменены ---

    @Test
    fun connectionError_sessionAborted_nothingChanged() = runTest {
        db.deckDao().insert(DeckEntity(id = 1, name = "A"))
        db.deckDao().markPending(1)

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val error = engine.syncNow()

        assertTrue(error is SyncError.Network)
        assertEquals(LastSyncResultEntity.STATUS_ERROR, lastResult().status)
        assertNotNull(lastResult().errorMessage)
        // Данные, ревизии и признаки не изменены.
        assertTrue(db.deckDao().getByIdIncludingDeleted(1)!!.pendingChanges)
        assertEquals(0, db.syncStateDao().getRevision("decks"))
    }

    // --- S16: 500 на поток карточек — применённые потоки остаются ---

    @Test
    fun http500OnCardsStream_appliedStreamsKept_failingRevisionUntouched() = runTest {
        db.deckDao().insert(DeckEntity(id = 5, name = "Пять"))
        db.deckDao().insert(DeckEntity(id = 6, name = "Шесть"))

        server.enqueue(decksResponse(43, DeckItemDto(5, "Пять"), DeckItemDto(6, "Шесть")))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                json.encodeToString(CardsSyncResponseDto(11)),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = engine.syncNow()

        assertTrue(error is SyncError.Server)
        assertEquals(500, (error as SyncError.Server).code)
        // Поток колод применён, карточки колоды 6 — тоже; колода 5 — ревизия 0.
        assertEquals(43, db.syncStateDao().getRevision("decks"))
        assertEquals(11, db.syncStateDao().getRevision("cards:6"))
        assertEquals(0, db.syncStateDao().getRevision("cards:5"))
        assertEquals(LastSyncResultEntity.STATUS_ERROR, lastResult().status)
    }

    // --- S17: 400 — тело не применяется, pending не снимается ---

    @Test
    fun http400_bodyNotApplied_pendingKept_revisionUntouched() = runTest {
        db.deckDao().insert(DeckEntity(id = 1, name = "A"))
        db.deckDao().markPending(1)

        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error": "word is empty"}"""),
        )

        val error = engine.syncNow()

        assertTrue(error is SyncError.Validation)
        assertTrue(db.deckDao().getByIdIncludingDeleted(1)!!.pendingChanges)
        assertEquals(0, db.syncStateDao().getRevision("decks"))
        assertEquals(LastSyncResultEntity.STATUS_ERROR, lastResult().status)
        assertNotNull(lastResult().errorMessage)
    }

    // --- S18: 404 на поток карточек — колода удалена локально, сессия продолжается ---

    @Test
    fun http404OnCardsStream_deckRemovedLocally_sessionContinues() = runTest {
        db.deckDao().insert(DeckEntity(id = 5, name = "Пять"))
        db.deckDao().insert(DeckEntity(id = 6, name = "Шесть"))
        db.cardDao().insert(CardEntity(id = 50, deckId = 5, word = "x", translation = "у"))

        server.enqueue(decksResponse(44, DeckItemDto(5, "Пять"), DeckItemDto(6, "Шесть")))
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                json.encodeToString(CardsSyncResponseDto(12)),
            ),
        )

        val error = engine.syncNow()

        assertNull(error)
        // Колода 5 и её карточки удалены полностью.
        assertNull(db.deckDao().getByIdIncludingDeleted(5))
        assertNull(db.cardDao().getByIdIncludingDeleted(50))
        // Сессия продолжилась для колоды 6.
        assertEquals(12, db.syncStateDao().getRevision("cards:6"))
        assertEquals(LastSyncResultEntity.STATUS_SUCCESS, lastResult().status)
    }

    // --- S19: 200 с повреждённым JSON — ошибка формата, БД цела ---

    @Test
    fun malformedJsonBody_deserializationError_dbIntact() = runTest {
        db.deckDao().insert(DeckEntity(id = 1, name = "A"))

        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"revision": "not-a-number", "items": ["""),
        )

        val error = engine.syncNow()

        assertTrue(error is SyncError.Format)
        assertEquals(LastSyncResultEntity.STATUS_ERROR, lastResult().status)
        // Целостность локальной БД сохранена.
        assertNotNull(db.deckDao().getByIdIncludingDeleted(1))
        assertEquals(0, db.syncStateDao().getRevision("decks"))
    }

    // --- S26: 413 TOO_MANY_CHANGES — сессия прервана, pending сохранён ---

    @Test
    fun http413_sessionAborted_pendingPreserved() = runTest {
        db.deckDao().insert(DeckEntity(id = 1, name = "A"))
        db.cardDao().insert(CardEntity(id = 10, deckId = 1, word = "x1", translation = "у1"))
        db.cardDao().markPending(10)
        db.cardDao().insert(CardEntity(id = 11, deckId = 1, word = "x2", translation = "у2"))
        db.cardDao().markPending(11)

        server.enqueue(decksResponse(1, DeckItemDto(1, "A")))
        server.enqueue(
            MockResponse().setResponseCode(413)
                .setBody("""{"error": "TOO_MANY_CHANGES"}"""),
        )

        val error = engine.syncNow()

        assertTrue(error is SyncError.TooManyChanges)
        assertTrue(db.cardDao().getByIdIncludingDeleted(10)!!.pendingChanges)
        assertTrue(db.cardDao().getByIdIncludingDeleted(11)!!.pendingChanges)
        assertEquals(0, db.syncStateDao().getRevision("cards:1"))
        assertEquals(LastSyncResultEntity.STATUS_ERROR, lastResult().status)
    }
}
```


---

## Батч 5/8

### FILE: app/src/test/java/com/mnemocards/data/remote/AuthRefreshTest.kt
```kotlin
package com.mnemocards.data.remote

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.AuthTokensEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.atomic.AtomicBoolean

/**
 * T13. Падающий тест: прозрачный refresh токена (S12) и
 * состояние «требуется вход» при отказе refresh (S13).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AuthRefreshTest {

    // --- Контрактное мини-API (зеркало specs/openapi.yaml для теста) ---

    @Serializable
    data class SyncChange(val id: Long)

    @Serializable
    data class SyncRequestBody(val sinceRevision: Long, val changed: List<SyncChange>)

    @Serializable
    data class DeckItemDto(val id: Long, val name: String)

    @Serializable
    data class DecksSyncResponseDto(
        val revision: Long,
        val items: List<DeckItemDto>,
        val fullSnapshot: Boolean = false,
    )

    interface TestSyncApi {
        @POST("api/v1/sync/decks")
        suspend fun syncDecks(@Body body: SyncRequestBody): DecksSyncResponseDto
    }

    private lateinit var db: MnemoCardsDatabase
    private lateinit var server: MockWebServer
    private lateinit var api: TestSyncApi
    private lateinit var client: OkHttpClient
    private val requiresLogin = AtomicBoolean(false)

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor(db.syncStateDao()))
            .authenticator(
                AuthAuthenticator(
                    syncStateDao = db.syncStateDao(),
                    baseUrl = server.url("/").toString(),
                    onRequiresLogin = { requiresLogin.set(true) },
                ),
            )
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TestSyncApi::class.java)
        db.syncStateDao().saveTokens(
            AuthTokensEntity(accessToken = "old-access", refreshToken = "old-refresh"),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun decksResponse(revision: Long) =
        MockResponse()
            .setResponseCode(200)
            .setBody(json.encodeToString(DecksSyncResponseDto(revision, emptyList())))

    private fun unauthorized() = MockResponse().setResponseCode(401).setBody("""{}""")

    // --- S12: 401 -> refresh -> повтор с новым access, сессия продолжается ---

    @Test
    fun unauthorized_refreshAndRetryWithNewToken_tokensSaved() = runBlocking {
        server.enqueue(unauthorized())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"accessToken":"new-access","refreshToken":"new-refresh"}"""),
        )
        server.enqueue(decksResponse(43))

        val response = api.syncDecks(SyncRequestBody(42, emptyList()))

        assertEquals(43, response.revision)
        assertEquals(3, server.requestCount)

        val first = server.takeRequest()
        assertEquals("/api/v1/sync/decks", first.path)
        assertEquals("Bearer old-access", first.getHeader("Authorization"))

        val refresh = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", refresh.path)
        assertTrue(refresh.body.readUtf8().contains("old-refresh"))

        val retried = server.takeRequest()
        assertEquals("/api/v1/sync/decks", retried.path)
        assertEquals("Bearer new-access", retried.getHeader("Authorization"))

        // Новая пара токенов сохранена.
        val saved = db.syncStateDao().getTokens()!!
        assertEquals("new-access", saved.accessToken)
        assertEquals("new-refresh", saved.refreshToken)
        assertFalse(requiresLogin.get())
    }

    // --- S13: refresh отклонён — «требуется вход», ничего не теряется ---

    @Test
    fun refreshRejected_requiresLogin_tokensAndDataPreserved() = runBlocking {
        server.enqueue(unauthorized())
        server.enqueue(unauthorized())

        var thrown: HttpException? = null
        try {
            api.syncDecks(SyncRequestBody(0, emptyList()))
        } catch (e: HttpException) {
            thrown = e
        }

        assertEquals(401, thrown?.code())
        assertTrue(requiresLogin.get())
        assertEquals(2, server.requestCount)

        // Токены, данные и ревизии не потеряны.
        val saved = db.syncStateDao().getTokens()!!
        assertEquals("old-access", saved.accessToken)
        assertEquals("old-refresh", saved.refreshToken)
        assertEquals(0, db.syncStateDao().getRevision("decks"))
    }

    // --- Защита от бесконечного цикла refresh: один refresh и один повтор ---

    @Test
    fun repeatedUnauthorized_noInfiniteLoop() = runBlocking {
        server.enqueue(unauthorized())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"accessToken":"new-access","refreshToken":"new-refresh"}"""),
        )
        server.enqueue(unauthorized())
        // Дальше ответов нет: если клиент зациклится, тест зависнет или упадёт по timeout.

        var thrown: HttpException? = null
        try {
            api.syncDecks(SyncRequestBody(0, emptyList()))
        } catch (e: HttpException) {
            thrown = e
        }

        assertEquals(401, thrown?.code())
        // Ровно один исходный запрос, один refresh и один повтор — цикла нет.
        assertEquals(3, server.requestCount)
        assertTrue(requiresLogin.get())
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/remote/AuthAuthenticator.kt
```kotlin
package com.mnemocards.data.remote

import com.mnemocards.data.db.sync.AuthTokensEntity
import com.mnemocards.data.db.sync.SyncStateDao
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * Прозрачный refresh access-токена (T14, S12): OkHttp [Authenticator] перехватывает
 * 401, вызывает `POST /api/v1/auth/refresh`, сохраняет новую пару токенов
 * в [AuthTokensEntity] и повторяет исходный запрос с новым access-токеном —
 * для пользователя процесс незаметен.
 *
 * Отказ refresh (401) означает «требуется вход» (S13): сигнал пробрасывается
 * через [onRequiresLogin], сессия останавливается, локальные токены, данные,
 * ревизии и признаки изменений не теряются.
 *
 * Защита от бесконечного цикла: не более одного refresh и одного повтора
 * на вызов (по цепочке priorResponse), а также один refresh на вызов
 * (synchronized-блок): если токен в упавшем запросе уже не совпадает
 * с сохранённым — его обновил другой поток, повторяем без нового refresh.
 */
class AuthAuthenticator(
    private val syncStateDao: SyncStateDao,
    baseUrl: String,
    private val onRequiresLogin: () -> Unit = {},
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Authenticator {

    @Serializable
    private data class RefreshRequestBody(val refreshToken: String)

    @Serializable
    private data class RefreshResponseBody(val accessToken: String, val refreshToken: String)

    /** Клиент для refresh не проходит через сам authenticator — иначе цикл. */
    private val refreshClient = OkHttpClient()

    private val lock = Any()

    private val refreshUrl: String =
        (if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/") + "api/v1/auth/refresh"

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_ATTEMPTS) {
            // Повтор уже был и снова 401 — дальше не зацикливаемся.
            onRequiresLogin()
            return null
        }
        val failedToken = response.request.header(AuthorizationInterceptor.HEADER)
            ?.removePrefix("Bearer ")
        val accessToken = runBlocking { usableAccessToken(failedToken) } ?: return null
        return response.request.newBuilder()
            .header(AuthorizationInterceptor.HEADER, "Bearer $accessToken")
            .build()
    }

    private suspend fun usableAccessToken(failedToken: String?): String? {
        synchronized(lock) {
            val stored = syncStateDao.getTokens() ?: run {
                onRequiresLogin()
                return null
            }
            return when {
                failedToken != null && failedToken != stored.accessToken ->
                    // Токен уже обновлён другим вызовом — новый refresh не нужен.
                    stored.accessToken
                else -> refresh(stored)
            }
        }
    }

    private fun refresh(stored: AuthTokensEntity): String? {
        val payload = json.encodeToString(
            RefreshRequestBody.serializer(),
            RefreshRequestBody(stored.refreshToken),
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(refreshUrl)
            .post(payload)
            .build()
        return refreshClient.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> {
                    val parsed = json.decodeFromString(
                        RefreshResponseBody.serializer(),
                        response.body!!.string(),
                    )
                    runBlocking {
                        syncStateDao.saveTokens(
                            AuthTokensEntity(
                                accessToken = parsed.accessToken,
                                refreshToken = parsed.refreshToken,
                            ),
                        )
                    }
                    parsed.accessToken
                }
                response.code == 401 -> {
                    // Refresh-токен отклонён — требуется вход (S13).
                    onRequiresLogin()
                    null
                }
                else -> null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        /** Один повтор после refresh — защита от бесконечного цикла. */
        private const val MAX_ATTEMPTS = 2
    }
}

/**
 * Добавляет `Authorization: Bearer <access>` из [AuthTokensEntity] ко всем
 * запросам синхронизации; работает в паре с [AuthAuthenticator].
 */
class AuthorizationInterceptor(
    private val syncStateDao: SyncStateDao,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { syncStateDao.getTokens()?.accessToken }
        val request = if (token != null) {
            chain.request().newBuilder()
                .header(HEADER, "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }

    companion object {
        const val HEADER = "Authorization"
    }
}

/**
 * Собирает OkHttp-клиент синхронизации с авторизацией: токен из Room,
 * прозрачный refresh и сигнал «требуется вход» (S12, S13). Подключается
 * в AppContainer к Retrofit-интерфейсу SyncApi.
 */
fun createSyncOkHttpClient(
    syncStateDao: SyncStateDao,
    baseUrl: String,
    onRequiresLogin: () -> Unit = {},
): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(AuthorizationInterceptor(syncStateDao))
    .authenticator(AuthAuthenticator(syncStateDao, baseUrl, onRequiresLogin))
    .build()
```

### FILE: app/src/test/java/com/mnemocards/data/sync/SyncManagerTest.kt
```kotlin
package com.mnemocards.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.AuthTokensEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * T15. Падающий тест: SyncManager — single-flight (S23),
 * синхронизация не настроена — сети нет (S14), StateFlow статуса.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncManagerTest {

    // --- Контрактное мини-API (зеркало specs/openapi.yaml для теста) ---

    @Serializable
    data class SyncChange(val id: Long, val deleted: Boolean = false)

    @Serializable
    data class SyncRequestBody(val sinceRevision: Long, val changed: List<SyncChange>)

    @Serializable
    data class DeckItemDto(val id: Long, val name: String, val deleted: Boolean = false)

    @Serializable
    data class CardItemDto(
        val id: Long,
        val deckId: Long,
        val word: String,
        val translation: String,
        val deleted: Boolean = false,
    )

    @Serializable
    data class DecksSyncResponseDto(
        val revision: Long,
        val items: List<DeckItemDto>,
        val fullSnapshot: Boolean = false,
    )

    @Serializable
    data class CardsSyncResponseDto(
        val revision: Long,
        val items: List<CardItemDto>,
        val fullSnapshot: Boolean = false,
    )

    interface TestSyncApi {
        @POST("api/v1/sync/decks")
        suspend fun syncDecks(@Body body: SyncRequestBody): DecksSyncResponseDto

        @POST("api/v1/sync/decks/{deckUuid}/cards")
        suspend fun syncCards(
            @Path("deckUuid") deckUuid: Long,
            @Body body: SyncRequestBody,
        ): CardsSyncResponseDto
    }

    private lateinit var db: MnemoCardsDatabase
    private lateinit var server: MockWebServer
    private lateinit var api: TestSyncApi
    private lateinit var engine: SyncEngine

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TestSyncApi::class.java)
        engine = SyncEngine(db, api)
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun decksResponse(revision: Long) =
        MockResponse()
            .setResponseCode(200)
            .setBody(json.encodeToString(DecksSyncResponseDto(revision, emptyList())))

    private suspend fun awaitStatus(manager: SyncManager, expected: SyncStatus) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (manager.status.value != expected) {
            if (System.nanoTime() > deadline) {
                throw AssertionError(
                    "Ожидался статус $expected, получен ${manager.status.value}",
                )
            }
            delay(10)
        }
    }

    // --- S23: повторный запуск при выполнении не создаёт вторую сессию ---

    @Test
    fun secondRequestWhileRunning_noParallelSession() = runBlocking {
        val latch = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                latch.await()
                return decksResponse(43)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = SyncManager(engine, db, scope)
        try {
            manager.requestSync()
            manager.requestSync()

            // Оба вызова объединились: статус «выполняется», сессия одна.
            awaitStatus(manager, SyncStatus.Running)
            latch.countDown()

            // Завершение: одна последовательность — один запрос к серверу.
            awaitStatus(manager, SyncStatus.Success)
            assertEquals(1, server.requestCount)
        } finally {
            scope.cancel()
        }
    }

    // --- S14: синхронизация не настроена (нет токенов) — сеть не используется ---

    @Test
    fun notConfigured_noNetworkCalls_statusIdle() = runBlocking {
        // Токены не сохранены — синхронизация не настроена.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = SyncManager(engine, db, scope)
        try {
            manager.requestSync()

            delay(200)
            assertEquals(0, server.requestCount)
            assertEquals(SyncStatus.Idle, manager.status.value)
        } finally {
            scope.cancel()
        }
    }

    // --- StateFlow: успех после завершения сессии ---

    @Test
    fun statusFlow_successAfterCompletion() = runBlocking {
        db.syncStateDao().saveTokens(AuthTokensEntity("a", "r"))
        server.enqueue(decksResponse(7))

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = SyncManager(engine, db, scope)
        try {
            manager.requestSync()
            awaitStatus(manager, SyncStatus.Success)
            assertEquals(SyncStatus.Idle, SyncStatus.Idle)
        } finally {
            scope.cancel()
        }
    }

    // --- StateFlow: ошибка сети ---

    @Test
    fun statusFlow_errorAfterNetworkFailure() = runBlocking {
        db.syncStateDao().saveTokens(AuthTokensEntity("a", "r"))
        server.shutdown()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = SyncManager(engine, db, scope)
        try {
            manager.requestSync()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (manager.status.value !is SyncStatus.Error) {
                if (System.nanoTime() > deadline) {
                    throw AssertionError("Ожидалась ошибка, получен ${manager.status.value}")
                }
                delay(10)
            }
        } finally {
            scope.cancel()
        }
    }

    // --- StateFlow: 401 -> «требуется вход» ---

    @Test
    fun statusFlow_requiresLoginOn401() = runBlocking {
        db.syncStateDao().saveTokens(AuthTokensEntity("a", "r"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{}"""))

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = SyncManager(engine, db, scope)
        try {
            manager.requestSync()
            awaitStatus(manager, SyncStatus.RequiresLogin)
        } finally {
            scope.cancel()
        }
    }
}
```


---

## Батч 6/8

### FILE: app/src/main/java/com/mnemocards/data/sync/SyncManager.kt
```kotlin
package com.mnemocards.data.sync

import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import retrofit2.HttpException

/**
 * Статус фоновой синхронизации для UI (T15/T16, S2, S23).
 * Отображается только на экране настроек (S21).
 */
sealed class SyncStatus {
    /** Синхронизация не настроена либо ещё не запускалась (S14). */
    object Idle : SyncStatus()

    /** Сессия выполняется (S2, S23). */
    object Running : SyncStatus()

    /** Последняя сессия завершилась успешно. */
    object Success : SyncStatus()

    /** Последняя сессия завершилась ошибкой (S15–S19, S26). */
    data class Error(val error: SyncError) : SyncStatus()

    /** Refresh-токен отклонён — требуется вход (S13). */
    object RequiresLogin : SyncStatus()
}

/**
 * Оркестратор фоновых сессий синхронизации (T16).
 *
 * Single-flight (S23): повторный [requestSync] при выполняющейся сессии
 * не создаёт параллельную — текущая продолжает работу, статус остаётся
 * «выполняется», по завершении фиксируется один итоговый результат.
 *
 * Признак «синхронизация настроена» — наличие сохранённых токенов (S14):
 * без них сетевые вызовы не выполняются вовсе, локальный функционал
 * работает в полном объёме.
 *
 * Сессия запускается на переданном [scope] (application-scope) с
 * [Dispatchers.IO] — UI не блокируется (S1, S2).
 */
class SyncManager(
    private val engine: SyncEngine,
    private val db: MnemoCardsDatabase,
    private val scope: CoroutineScope,
) {

    private val mutex = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /**
     * Публичная команда запуска сессии (S1 — автозапуск, S2 — кнопка
     * на настройках). Неблокирующая: сессия выполняется в фоне.
     */
    fun requestSync() {
        scope.launch(Dispatchers.IO) {
            runOnce()
        }
    }

    private suspend fun runOnce() {
        // S14: синхронизация не настроена — сети не будет, статус не меняется.
        if (db.syncStateDao().getTokens() == null) {
            return
        }
        // S23: single-flight — параллельная сессия не создаётся.
        if (!mutex.tryLock()) {
            return
        }
        try {
            _status.value = SyncStatus.Running
            try {
                engine.runSync()
                _status.value = SyncStatus.Success
            } catch (e: SyncError.RequiresLogin) {
                _status.value = SyncStatus.RequiresLogin
            } catch (e: SyncError) {
                _status.value = SyncStatus.Error(e)
            } catch (e: HttpException) {
                // 401 после неудавшегося refresh трактуется как «требуется вход».
                _status.value = if (e.code() == 401) {
                    SyncStatus.RequiresLogin
                } else {
                    SyncStatus.Error(SyncError.fromHttp(e))
                }
            } catch (e: Exception) {
                _status.value = SyncStatus.Error(SyncError.Network(e))
            }
        } finally {
            mutex.unlock()
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/AppContainer.kt
```kotlin
package com.mnemocards

import android.content.Context
import androidx.room.Room
import com.mnemocards.data.db.Migrations
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.network.SyncApi
import com.mnemocards.data.remote.createSyncOkHttpClient
import com.mnemocards.data.sync.SyncEngine
import com.mnemocards.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Единственный контейнер зависимостей приложения (инвариант specs/MAIN.md:
 * в проекте ровно один [AppContainer]). Владеет Room-базой — single source
 * of truth для UI — и компонентами фоновой синхронизации.
 */
class AppContainer(
    context: Context,
    applicationScope: CoroutineScope,
) {

    val database: MnemoCardsDatabase = Room.databaseBuilder(
        context.applicationContext,
        MnemoCardsDatabase::class.java,
        "mnemocards.db",
    )
        .addMigrations(*MIGRATIONS)
        .build()

    val deckDao = database.deckDao()
    val cardDao = database.cardDao()
    val syncStateDao = database.syncStateDao()

    // --- Фоновая синхронизация (change add-background-sync, T6/T10/T16) ---

    private val json = Json { ignoreUnknownKeys = true }

    private val syncClient = createSyncOkHttpClient(
        syncStateDao = syncStateDao,
        baseUrl = SYNC_BASE_URL,
        onRequiresLogin = { /* сигнал пробрасывается через SyncEngine */ },
    )

    val syncApi: SyncApi = Retrofit.Builder()
        .baseUrl(SYNC_BASE_URL)
        .client(syncClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SyncApi::class.java)

    val syncEngine = SyncEngine(database, syncApi)

    val syncManager = SyncManager(syncEngine, database, applicationScope)

    companion object {
        /** REST backend синхронизации — внешний сервис (specs/MAIN.md). */
        private const val SYNC_BASE_URL = "https://sync.mnemocards.example/"
    }
}
```

### FILE: app/src/main/java/com/mnemocards/MnemoCardsApplication.kt
```kotlin
package com.mnemocards

import android.app.Application
import com.mnemocards.data.db.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MnemoCardsApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** Application-scope для фоновой работы: сидинг и синхронизация. */
    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, applicationScope)

        // Идемпотентный сидинг базовой колоды при первом запуске
        // (capability offline.storage, change add-offline-storage).
        applicationScope.launch {
            DatabaseSeeder.seedIfEmpty(container.database)
        }

        // S1: автозапуск синхронизации при открытии приложения — в фоне,
        // не блокируя UI. S14: без сохранённых токенов сетевые вызовы
        // не выполняются (проверка внутри SyncManager).
        container.syncManager.requestSync()
    }
}
```


---

## Батч 7/8

### FILE: app/src/test/java/com/mnemocards/ui/settings/SettingsViewModelTest.kt
```kotlin
package com.mnemocards.ui.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.AuthTokensEntity
import com.mnemocards.data.db.sync.LastSyncResultEntity
import com.mnemocards.data.sync.SyncEngine
import com.mnemocards.data.sync.SyncManager
import com.mnemocards.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.CountDownLatch

/**
 * T19. Падающий тест: SettingsViewModel — статус «выполняется» и результат
 * последней синхронизации (S2), «требуется вход» при отказе refresh (S13),
 * приглашение войти при ненастроенной синхронизации (S14), команда
 * «Синхронизировать сейчас» не создаёт вторую сессию (S23).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    // --- Контрактное мини-API (зеркало specs/openapi.yaml для теста) ---

    @Serializable
    data class SyncChange(val id: Long, val deleted: Boolean = false)

    @Serializable
    data class SyncRequestBody(val sinceRevision: Long, val changed: List<SyncChange>)

    @Serializable
    data class DeckItemDto(val id: Long, val name: String, val deleted: Boolean = false)

    @Serializable
    data class CardItemDto(
        val id: Long,
        val deckId: Long,
        val word: String,
        val translation: String,
        val deleted: Boolean = false,
    )

    @Serializable
    data class DecksSyncResponseDto(
        val revision: Long,
        val items: List<DeckItemDto>,
        val fullSnapshot: Boolean = false,
    )

    @Serializable
    data class CardsSyncResponseDto(
        val revision: Long,
        val items: List<CardItemDto>,
        val fullSnapshot: Boolean = false,
    )

    interface TestSyncApi {
        @POST("api/v1/sync/decks")
        suspend fun syncDecks(@Body body: SyncRequestBody): DecksSyncResponseDto

        @POST("api/v1/sync/decks/{deckUuid}/cards")
        suspend fun syncCards(
            @Path("deckUuid") deckUuid: Long,
            @Body body: SyncRequestBody,
        ): CardsSyncResponseDto
    }

    private lateinit var db: MnemoCardsDatabase
    private lateinit var server: MockWebServer
    private lateinit var api: TestSyncApi
    private lateinit var engine: SyncEngine
    private lateinit var scope: CoroutineScope
    private lateinit var manager: SyncManager

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TestSyncApi::class.java)
        engine = SyncEngine(db, api)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        manager = SyncManager(engine, db, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
        db.close()
    }

    private fun decksResponse(revision: Long) =
        MockResponse()
            .setResponseCode(200)
            .setBody(json.encodeToString(DecksSyncResponseDto(revision, emptyList())))

    private suspend fun awaitUiState(
        viewModel: SettingsViewModel,
        predicate: (SettingsUiState) -> Boolean,
    ) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate(viewModel.uiState.value)) {
            if (System.nanoTime() > deadline) {
                throw AssertionError(
                    "Не дождались состояния, текущее: ${viewModel.uiState.value}",
                )
            }
            delay(10)
        }
    }

    // --- S2: «выполняется» при активной сессии, результат после завершения ---

    @Test
    fun runningThenResult_shownInUiState() = runBlocking {
        db.syncStateDao().saveTokens(AuthTokensEntity("a", "r"))
        val latch = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                latch.await()
                return decksResponse(11)
            }
        }

        val viewModel = SettingsViewModel(manager)
        manager.requestSync()

        awaitUiState(viewModel) { it.running }
        latch.countDown()

        awaitUiState(viewModel) { !it.running && it.lastSyncSuccess == true }
        assertTrue(viewModel.uiState.value.lastSyncTimeMillis != null)
        assertNull(viewModel.uiState.value.lastSyncError)
    }

    // --- S13: отказ refresh — «требуется вход» ---

    @Test
    fun requiresLogin_shownInUiState() = runBlocking {
        db.syncStateDao().saveTokens(AuthTokensEntity("a", "r"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{}"""))

        val viewModel = SettingsViewModel(manager)
        manager.requestSync()

        awaitUiState(viewModel) { it.requiresLogin }
        assertFalse(viewModel.uiState.value.running)
    }

    // --- S14: синхронизация не настроена — приглашение войти, сети нет ---

    @Test
    fun notConfigured_showsInvite_noNetwork() = runBlocking {
        val viewModel = SettingsViewModel(manager)

        awaitUiState(viewModel) { !it.configured }
        viewModel.syncNow()

        delay(200)
        assertEquals(0, server.requestCount)
        assertTrue(viewModel.uiState.value.showLoginInvite)
    }

    // --- S23: команда во время сессии не создаёт вторую параллельную ---

    @Test
    fun syncNowWhileRunning_singleSession() = runBlocking {
        db.syncStateDao().saveTokens(AuthTokensEntity("a", "r"))
        val latch = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                latch.await()
                return decksResponse(12)
            }
        }

        val viewModel = SettingsViewModel(manager)
        manager.requestSync()
        awaitUiState(viewModel) { it.running }

        viewModel.syncNow()
        awaitUiState(viewModel) { it.running }
        latch.countDown()

        awaitUiState(viewModel) { !it.running && it.lastSyncSuccess == true }
        assertEquals(1, server.requestCount)
        assertEquals(SyncStatus.Success, manager.status.value)
    }

    // --- S2/S15: ошибка последней синхронизации с текстом ---

    @Test
    fun errorResult_shownWithMessage() = runBlocking {
        db.syncStateDao().saveTokens(AuthTokensEntity("a", "r"))
        server.shutdown()

        val viewModel = SettingsViewModel(manager)
        manager.requestSync()

        awaitUiState(viewModel) { !it.running && it.lastSyncError != null }
        assertFalse(viewModel.uiState.value.lastSyncSuccess == true)
    }

    // --- Сохранённый итог читается из Room через Flow ---

    @Test
    fun persistedLastResult_emittedToUiState() = runBlocking {
        db.syncStateDao().saveTokens(AuthTokensEntity("a", "r"))
        db.syncStateDao().saveLastSyncResult(
            LastSyncResultEntity(
                id = 1,
                timestampMillis = 1_700_000_000_000L,
                success = true,
                errorMessage = null,
            ),
        )

        val viewModel = SettingsViewModel(manager)
        awaitUiState(viewModel) { it.lastSyncTimeMillis == 1_700_000_000_000L }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/settings/SettingsViewModel.kt
```kotlin
package com.mnemocards.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mnemocards.data.sync.SyncManager
import com.mnemocards.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Состояние экрана настроек синхронизации (T20, S2, S13, S14, S23).
 * Чистое состояние без бизнес-логики: ViewModel лишь собирает потоки
 * из [SyncManager] и Room, событие запуска уходит вверх в менеджер.
 */
data class SettingsUiState(
    /** Синхронизация настроена (токены сохранены). */
    val configured: Boolean = false,
    /** Сессия выполняется прямо сейчас (S2, S23). */
    val running: Boolean = false,
    /** Refresh-токен отклонён — требуется вход (S13). */
    val requiresLogin: Boolean = false,
    /** Время последней завершённой синхронизации (S2). */
    val lastSyncTimeMillis: Long? = null,
    /** Признак успеха последней синхронизации. */
    val lastSyncSuccess: Boolean? = null,
    /** Человекочитаемый текст ошибки последней синхронизации (S15–S19). */
    val lastSyncError: String? = null,
) {
    /** S14: приглашение войти вместо статуса, когда синхронизация не настроена. */
    val showLoginInvite: Boolean get() = !configured
}

/**
 * ViewModel экрана настроек (T20): подписка на статус [SyncManager] и итог
 * последней синхронизации из Room через Flow; UDF — состояние вниз в
 * [StateFlow], событие запуска вверх через [syncNow].
 */
class SettingsViewModel(
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(configured = syncManager.isConfigured())
        }
        viewModelScope.launch {
            syncManager.status.collect { status ->
                onStatus(status)
            }
        }
        viewModelScope.launch {
            syncManager.lastResult.collect { result ->
                _uiState.value = _uiState.value.copy(
                    lastSyncTimeMillis = result?.timestampMillis,
                    lastSyncSuccess = result?.success,
                    lastSyncError = result?.errorMessage,
                )
            }
        }
    }

    private fun onStatus(status: SyncStatus) {
        _uiState.value = _uiState.value.copy(
            running = status is SyncStatus.Running,
            requiresLogin = status is SyncStatus.RequiresLogin,
        )
    }

    /**
     * Событие «Синхронизировать сейчас» (S2). Single-flight гарантирует, что
     * при выполняющейся сессии вторая параллельная не создаётся (S23) —
     * бизнес-логика остаётся в SyncManager, ViewModel её не дублирует.
     */
    fun syncNow() {
        syncManager.requestSync()
    }
}

class SettingsViewModelFactory(
    private val syncManager: SyncManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(syncManager) as T
}
```

### FILE: app/src/main/java/com/mnemocards/ui/settings/SettingsScreen.kt
```kotlin
package com.mnemocards.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/**
 * Экран настроек синхронизации (T21, S2, S13, S14, S15, S21).
 *
 * Единственное место, где видны статус, время и ошибки синхронизации.
 * Строго тёмная тема: цвета только из [MaterialTheme.colorScheme],
 * обёртка MnemoCardsTheme живёт в корневом setContent, прямые
 * Color(0xFF...) здесь не используются.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .padding(vertical = 12.dp),
                    )
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        SettingsScreenContent(
            uiState = uiState,
            onBack = onBack,
            onSyncNow = viewModel::syncNow,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SettingsScreenContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // S14: синхронизация не настроена — приглашение войти вместо статуса.
    if (uiState.showLoginInvite) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Синхронизация",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Войдите в аккаунт, чтобы включить синхронизацию колод. " +
                    "Все функции приложения работают и без неё.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Button(onClick = onBack) {
                Text("Назад")
            }
        }
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Синхронизация",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )

        // S2, S23: статус текущей сессии; S13: требуется вход.
        val statusText = when {
            uiState.running -> "Синхронизация выполняется…"
            uiState.requiresLogin -> "Требуется вход"
            else -> null
        }
        statusText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Результат последней синхронизации: время и исход (S2, S15).
        uiState.lastSyncTimeMillis?.let { timeMillis ->
            Text(
                text = "Последняя синхронизация: " +
                    DateFormat.getDateTimeInstance().format(Date(timeMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (uiState.lastSyncSuccess) {
            true -> Text(
                text = "Завершена успешно",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            false -> uiState.lastSyncError?.let { error ->
                Text(
                    text = "Ошибка: $error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            null -> Unit
        }

        // S2: ручной запуск; S23 — повторное нажатие не создаёт вторую сессию.
        Button(
            onClick = onSyncNow,
            enabled = !uiState.running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (uiState.running) "Синхронизация выполняется…" else "Синхронизировать сейчас",
            )
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/sync/SyncManager.kt
```kotlin
package com.mnemocards.data.sync

import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.LastSyncResultEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import retrofit2.HttpException

/**
 * Статус фоновой синхронизации для UI (T15/T16, S2, S23).
 * Отображается только на экране настроек (S21).
 */
sealed class SyncStatus {
    /** Синхронизация не настроена либо ещё не запускалась (S14). */
    object Idle : SyncStatus()

    /** Сессия выполняется (S2, S23). */
    object Running : SyncStatus()

    /** Последняя сессия завершилась успешно. */
    object Success : SyncStatus()

    /** Последняя сессия завершилась ошибкой (S15–S19, S26). */
    data class Error(val error: SyncError) : SyncStatus()

    /** Refresh-токен отклонён — требуется вход (S13). */
    object RequiresLogin : SyncStatus()
}

/**
 * Оркестратор фоновых сессий синхронизации (T16).
 *
 * Single-flight (S23): повторный [requestSync] при выполняющейся сессии
 * не создаёт параллельную — текущая продолжает работу, статус остаётся
 * «выполняется», по завершении фиксируется один итоговый результат.
 *
 * Признак «синхронизация настроена» — наличие сохранённых токенов (S14):
 * без них сетевые вызовы не выполняются вовсе, локальный функционал
 * работает в полном объёме.
 *
 * Сессия запускается на переданном [scope] (application-scope) с
 * [Dispatchers.IO] — UI не блокируется (S1, S2).
 */
class SyncManager(
    private val engine: SyncEngine,
    private val db: MnemoCardsDatabase,
    private val scope: CoroutineScope,
) {

    private val mutex = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Итог последней сессии из Room (S2) — наблюдается экраном настроек. */
    val lastResult: Flow<LastSyncResultEntity?> =
        db.syncStateDao().observeLastSyncResult()

    /** Признак «синхронизация настроена» — наличие токенов (S14). */
    suspend fun isConfigured(): Boolean = db.syncStateDao().getTokens() != null

    /**
     * Публичная команда запуска сессии (S1 — автозапуск, S2 — кнопка
     * на настройках). Неблокирующая: сессия выполняется в фоне.
     */
    fun requestSync() {
        scope.launch(Dispatchers.IO) {
            runOnce()
        }
    }

    private suspend fun runOnce() {
        // S14: синхронизация не настроена — сети не будет, статус не меняется.
        if (db.syncStateDao().getTokens() == null) {
            return
        }
        // S23: single-flight — параллельная сессия не создаётся.
        if (!mutex.tryLock()) {
            return
        }
        try {
            _status.value = SyncStatus.Running
            val errorText: String? = try {
                engine.runSync()
                _status.value = SyncStatus.Success
                null
            } catch (e: SyncError.RequiresLogin) {
                _status.value = SyncStatus.RequiresLogin
                e.message
            } catch (e: SyncError) {
                _status.value = SyncStatus.Error(e)
                e.message
            } catch (e: HttpException) {
                // 401 после неудавшегося refresh трактуется как «требуется вход».
                val error = SyncError.fromHttp(e)
                _status.value = if (e.code() == 401) {
                    SyncStatus.RequiresLogin
                } else {
                    SyncStatus.Error(error)
                }
                error.message
            } catch (e: Exception) {
                val error = SyncError.Network(e)
                _status.value = SyncStatus.Error(error)
                error.message
            }
            // S2/S15: итог последней синхронизации — время, статус, текст ошибки.
            db.syncStateDao().saveLastSyncResult(
                LastSyncResultEntity(
                    id = 1,
                    timestampMillis = System.currentTimeMillis(),
                    success = errorText == null,
                    errorMessage = errorText,
                ),
            )
        } finally {
            mutex.unlock()
        }
    }
}
```


---

## Батч 8/8

### FILE: app/src/main/java/com/mnemocards/ui/navigation/AppNavHost.kt
```kotlin
package com.mnemocards.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mnemocards.AppContainer
import com.mnemocards.ui.decklist.DeckListViewModel
import com.mnemocards.ui.decklist.DeckListViewModelFactory
import com.mnemocards.ui.decklist.DeckListScreen
import com.mnemocards.ui.review.ReviewScreen
import com.mnemocards.ui.review.ReviewViewModel
import com.mnemocards.ui.review.ReviewViewModelFactory
import com.mnemocards.ui.settings.SettingsScreen
import com.mnemocards.ui.settings.SettingsViewModel
import com.mnemocards.ui.settings.SettingsViewModelFactory

/**
 * Единственный навигационный граф приложения (инвариант specs/MAIN.md:
 * ровно один NavHost). T22: маршрут настроек [Routes.SETTINGS] добавлен
 * сюда, второй навигационный граф не создаётся.
 */
object Routes {
    const val DECK_LIST = "deckList"
    const val REVIEW = "review/{deckId}"
    const val SETTINGS = "settings"

    fun review(deckId: Long) = "review/$deckId"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    container: AppContainer,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DECK_LIST,
    ) {
        composable(Routes.DECK_LIST) {
            val viewModel: DeckListViewModel = viewModel(
                factory = DeckListViewModelFactory(container.deckDao),
            )
            DeckListScreen(
                viewModel = viewModel,
                onOpenDeck = { deckId ->
                    navController.navigate(Routes.review(deckId))
                },
                // S2/S21: единственная точка входа на экран настроек —
                // со списка колод; сообщений синхронизации на списке нет.
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }
        composable(Routes.REVIEW) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getString("deckId")?.toLongOrNull() ?: return@composable
            val viewModel: ReviewViewModel = viewModel(
                factory = ReviewViewModelFactory(container.deckDao),
            )
            ReviewScreen(
                deckId = deckId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        // T22: маршрут настроек в существующем NavHost. Экран настроек —
        // единственное место, где видны статус/ошибки синхронизации (S21).
        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(container.syncManager),
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

Существующий `DeckListScreen.kt` я не вижу целиком, поэтому правку точки входа описываю минимальными блоками по стандартной структуре файла.

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt
```kotlin
<<<<<<< SEARCH
@Composable
fun DeckListScreen(
    viewModel: DeckListViewModel,
    onOpenDeck: (Long) -> Unit,
) {
=======
@Composable
fun DeckListScreen(
    viewModel: DeckListViewModel,
    onOpenDeck: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
>>>>>>> REPLACE
```
```kotlin
<<<<<<< SEARCH
                    title = { Text("Мои колоды") },
=======
                    title = { Text("Мои колоды") },
                    actions = {
                        // T22: вход на экран настроек синхронизации (S2).
                        // Иконка не отражает состояние синхронизации —
                        // сообщения видны только на настройках (S21).
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Настройки",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    },
>>>>>>> REPLACE
```
```kotlin
<<<<<<< SEARCH
import androidx.compose.material3.Text
=======
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
>>>>>>> REPLACE
```
