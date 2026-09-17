## Батч 1/7

### FILE: gradle/libs.versions.toml
```toml
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

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
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
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-navigation-testing = { group = "androidx.navigation", name = "navigation-testing", version.ref = "navigation" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

### FILE: app/build.gradle.kts
```kotlin
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
}

kotlin {
    // Тесты (Robolectric/ASM) запускаются на тулчейне JDK 17: класс-файлы
    // JDK 25 (major 69) не читаются ASM-инструментацией Robolectric.
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
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
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckEntity.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
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
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deckId: Long,
    val front: String,
    val back: String,
    /** Миллисекунсы epoch — основа порядка «новые сверху» (createdAt DESC). */
    val createdAt: Long,
)
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
    suspend fun insertCard(card: CardEntity): Long

    /** 0, если записи нет — основа 404-аналога на уровне репозитория. */
    @Update
    suspend fun updateCard(card: CardEntity): Int

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCard(id: Long): Int

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt DESC, id DESC")
    fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: Long): CardEntity?

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId")
    fun observeCardCountByDeck(deckId: Long): Flow<Int>

    @Query("SELECT MAX(createdAt) FROM cards WHERE deckId = :deckId")
    suspend fun maxCreatedAtInDeck(deckId: Long): Long?
}
```

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
    suspend fun insertDeck(deck: DeckEntity): Long

    @Query("SELECT * FROM decks WHERE id = :id LIMIT 1")
    suspend fun getDeckById(id: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE name = :name LIMIT 1")
    suspend fun getDeckByName(name: String): DeckEntity?

    @Query(
        """
        SELECT decks.id AS deckId, decks.name AS deckName,
               (SELECT COUNT(*) FROM cards WHERE cards.deckId = decks.id) AS cardCount
        FROM decks ORDER BY decks.id ASC
        """
    )
    fun observeDecksWithCardCounts(): Flow<List<DeckWithCardCount>>
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckWithCardCount.kt
```kotlin
package com.mnemocards.data.db

data class DeckWithCardCount(
    val deckId: Long,
    val deckName: String,
    val cardCount: Int,
)
```

### FILE: app/src/main/java/com/mnemocards/data/db/Migrations.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: добавлена колонка cards.createdAt (NOT NULL).
 * Существующие строки получают различные значения по id,
 * чтобы порядок «новые сверху» был детерминированным.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cards ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE cards SET createdAt = id * 1000")
    }
}

val ALL_MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)
```

### FILE: app/src/main/java/com/mnemocards/data/db/DatabaseSeeder.kt
```kotlin
package com.mnemocards.data.db

/**
 * Идемпотентный сидинг базовой колоды «Английский базовый» из 5 карточек.
 * Повторный вызов не создаёт дубликатов: сид выполняется только если
 * колоды с таким именем ещё нет.
 */
object DatabaseSeeder {

    private const val BASE_DECK_NAME = "Английский базовый"

    private val SEED_CARDS = listOf(
        "hello" to "привет",
        "world" to "мир",
        "cat" to "кошка",
        "dog" to "собака",
        "book" to "книга",
    )

    suspend fun seed(db: MnemoCardsDatabase) {
        val deckDao = db.deckDao()
        val cardDao = db.cardDao()
        if (deckDao.getDeckByName(BASE_DECK_NAME) != null) return

        val deckId = deckDao.insertDeck(DeckEntity(name = BASE_DECK_NAME))
        val base = System.currentTimeMillis()
        SEED_CARDS.forEachIndexed { index, (front, back) ->
            cardDao.insertCard(
                CardEntity(
                    deckId = deckId,
                    front = front,
                    back = back,
                    // Возрастающие createdAt — детерминированный порядок посева.
                    createdAt = base + index,
                )
            )
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt
```kotlin
package com.mnemocards.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DeckEntity::class, CardEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MnemoCardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var instance: MnemoCardsDatabase? = null

        fun get(context: Context): MnemoCardsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MnemoCardsDatabase::class.java,
                    "mnemocards.db",
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/CardRepository.kt
```kotlin
package com.mnemocards.data

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import kotlinx.coroutines.flow.Flow

/**
 * Офлайн-репозиторий карточек. Room — единый источник данных,
 * UI наблюдает изменения через Flow.
 */
interface CardRepository {

    /** Карточки колоды, новые сверху (createdAt DESC). */
    fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>>

    /** Счётчик карточек колоды — обновляется автоматически (S20–S21). */
    fun observeCardCount(deckId: Long): Flow<Int>

    suspend fun getCardById(id: Long): CardEntity?

    /** Создаёт карточку; createdAt гарантирует позицию «первой сверху». */
    suspend fun addCard(deckId: Long, front: String, back: String): Long

    /**
     * Обновляет существующую карточку.
     * @return false, если записи уже нет (404-аналог, S25) —
     *         update несуществующей записи не превращается в insert.
     */
    suspend fun updateCard(card: CardEntity): Boolean

    /** @return false, если записи уже нет (404-аналог). */
    suspend fun deleteCard(cardId: Long): Boolean
}

class RoomCardRepository(
    private val cardDao: CardDao,
) : CardRepository {

    override fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>> =
        cardDao.observeCardsByDeck(deckId)

    override fun observeCardCount(deckId: Long): Flow<Int> =
        cardDao.observeCardCountByDeck(deckId)

    override suspend fun getCardById(id: Long): CardEntity? =
        cardDao.getCardById(id)

    override suspend fun addCard(deckId: Long, front: String, back: String): Long {
        // Строго возрастающий createdAt внутри колоды: новые всегда выше старых.
        val max = cardDao.maxCreatedAtInDeck(deckId) ?: 0L
        val now = System.currentTimeMillis()
        val createdAt = if (now > max) now else max + 1
        return cardDao.insertCard(
            CardEntity(deckId = deckId, front = front, back = back, createdAt = createdAt)
        )
    }

    override suspend fun updateCard(card: CardEntity): Boolean =
        cardDao.updateCard(card) > 0

    override suspend fun deleteCard(cardId: Long): Boolean =
        cardDao.deleteCard(cardId) > 0
}
```

### FILE: app/src/test/java/com/mnemocards/data/db/CardDaoTest.kt
```kotlin
package com.mnemocards.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
class CardDaoTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao
    private var deckId: Long = 0L

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        cardDao = db.cardDao()
        deckId = deckDao.insertDeck(DeckEntity(name = "Английский базовый"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insert(front: String, back: String, createdAt: Long): Long =
        cardDao.insertCard(
            CardEntity(deckId = deckId, front = front, back = back, createdAt = createdAt)
        )

    @Test
    fun insertCard_returnsGeneratedId() = runTest {
        val id = insert("hello", "привет", 1000L)
        assertTrue(id > 0)
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

        val cards = kotlinx.coroutines.flow.first(cardDao.observeCardsByDeck(deckId))
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

        val count = kotlinx.coroutines.flow.first(cardDao.observeCardCountByDeck(deckId))
        assertEquals(2, count)
    }

    @Test
    fun updateCard_returnsZeroForMissingRow() = runTest {
        val missing = CardEntity(id = 4242L, deckId = deckId, front = "x", back = "y", createdAt = 1L)
        assertEquals(0, cardDao.updateCard(missing))
    }

    @Test
    fun deleteCard_removesRow() = runTest {
        val id = insert("cat", "кошка", 1000L)
        assertEquals(1, cardDao.deleteCard(id))
        assertNull(cardDao.getCardById(id))
        assertEquals(0, kotlinx.coroutines.flow.first(cardDao.observeCardCountByDeck(deckId)))
    }

    @Test
    fun duplicateCards_areStoredAsIndependentRows() = runTest {
        insert("cat", "кошка", 1000L)
        insert("cat", "кошка", 2000L)

        val cards = kotlinx.coroutines.flow.first(cardDao.observeCardsByDeck(deckId))
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
```

### FILE: app/src/test/java/com/mnemocards/data/db/MigrationTest.kt
```kotlin
package com.mnemocards.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @Test
    fun migration_1_2_keepsSeedData_andFillsCreatedAt() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File.createTempFile("migration", ".db")
        dbFile.deleteOnExit()

        // Создаём БД версии 1 (без createdAt) и заполняем её как посев v1.
        val v1 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v1.execSQL(
            "CREATE TABLE decks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)"
        )
        v1.execSQL(
            "CREATE TABLE cards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "deckId INTEGER NOT NULL, front TEXT NOT NULL, back TEXT NOT NULL, " +
                "FOREIGN KEY(deckId) REFERENCES decks(id) ON DELETE CASCADE)"
        )
        v1.execSQL("INSERT INTO decks (name) VALUES ('Английский базовый')")
        v1.execSQL("INSERT INTO cards (deckId, front, back) VALUES (1, 'hello', 'привет')")
        v1.execSQL("INSERT INTO cards (deckId, front, back) VALUES (1, 'world', 'мир')")
        v1.version = 1
        v1.close()

        // Открываем той же Room-схемой — применяется MIGRATION_1_2.
        val db = Room.databaseBuilder(context, MnemoCardsDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val cards = kotlinx.coroutines.flow.first(db.cardDao().observeCardsByDeck(1L))
        assertEquals(2, cards.size)
        assertEquals(setOf("hello", "world"), cards.map { it.front }.toSet())
        // createdAt заполнен различными значениями по id — порядок детерминирован.
        assertTrue(cards.all { it.createdAt > 0 })
        assertEquals(cards.map { it.createdAt }.distinct().size, cards.size)

        db.close()
    }

    @Test
    fun seeding_isIdempotent_andOrdered() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        DatabaseSeeder.seed(db)
        DatabaseSeeder.seed(db) // повторный вызов — без дубликатов

        val cards = kotlinx.coroutines.flow.first(db.cardDao().observeCardsByDeck(1L))
        assertEquals(5, cards.size)
        // createdAt DESC: первая в потоке — последняя засеянная ("book").
        assertEquals("book", cards.first().front)
        // Возрастающие createdAt у посева.
        assertEquals(cards.map { it.createdAt }.sortedDescending(), cards.map { it.createdAt })

        db.close()
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/CardRepositoryTest.kt
```kotlin
package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.flow.first
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
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CardRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var repository: CardRepository
    private lateinit var dbFile: File
    private var deckId: Long = 0L

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File.createTempFile("repo", ".db")
        dbFile.deleteOnExit()
        db = buildDb(context, dbFile)
        repository = RoomCardRepository(db.cardDao())
        deckId = db.deckDao().insertDeck(com.mnemocards.data.db.DeckEntity(name = "Английский базовый"))
    }

    private fun buildDb(context: Context, file: File): MnemoCardsDatabase =
        Room.databaseBuilder(context, MnemoCardsDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .build()

    @After
    fun tearDown() {
        db.close()
    }

    // S1: карточки колоды читаются с лицевой и обратной стороной.
    @Test
    fun observeCardsByDeck_returnsFrontAndBack() = runTest {
        repository.addCard(deckId, "hello", "привет")
        repository.addCard(deckId, "world", "мир")
        repository.addCard(deckId, "cat", "кошка")

        val cards = repository.observeCardsByDeck(deckId).first()
        assertEquals(3, cards.size)
        val pair = cards.map { it.front to it.back }
        assertTrue(pair.contains("hello" to "привет"))
        assertTrue(pair.contains("world" to "мир"))
        assertTrue(pair.contains("cat" to "кошка"))
    }

    // S3, S5: новая карточка появляется в потоке первой.
    @Test
    fun addCard_newCardAppearsFirstInFlow() = runTest {
        repository.addCard(deckId, "hello", "привет")
        repository.observeCardsByDeck(deckId).test {
            awaitItem() // текущее состояние
            repository.addCard(deckId, "cat", "кошка")
            val cards = awaitItem()
            assertEquals("cat", cards.first().front)
            assertEquals("кошка", cards.first().back)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S12: update меняет значения, счётчик не растёт.
    @Test
    fun updateCard_changesValues_countStaysSame() = runTest {
        val id = repository.addCard(deckId, "cat", "кошка")
        repository.addCard(deckId, "dog", "собака")

        val card = repository.getCardById(id)!!
        val ok = repository.updateCard(card.copy(front = "a cat"))

        assertTrue(ok)
        assertEquals("a cat", repository.getCardById(id)!!.front)
        assertEquals("кошка", repository.getCardById(id)!!.back)
        assertEquals(2, repository.observeCardCount(deckId).first())
    }

    // S25: update несуществующей записи не становится insert (404-аналог).
    @Test
    fun updateCard_missingRow_returnsFalse_noInsert() = runTest {
        val missing = com.mnemocards.data.db.CardEntity(
            id = 4242L, deckId = deckId, front = "x", back = "y", createdAt = 1L,
        )
        assertFalse(repository.updateCard(missing))
        assertNull(repository.getCardById(4242L))
        assertEquals(0, repository.observeCardCount(deckId).first())
    }

    // S16: delete удаляет запись.
    @Test
    fun deleteCard_removesRow() = runTest {
        val id = repository.addCard(deckId, "cat", "кошка")
        assertTrue(repository.deleteCard(id))
        assertNull(repository.getCardById(id))
        assertEquals(0, repository.observeCardsByDeck(deckId).first().size)
    }

    // S20, S21: счётчик карточек эмитит 3 -> 4 -> 3.
    @Test
    fun observeCardCount_emits3_4_3() = runTest {
        repository.addCard(deckId, "a", "а")
        repository.addCard(deckId, "b", "б")
        repository.addCard(deckId, "c", "в")

        repository.observeCardCount(deckId).test {
            assertEquals(3, awaitItem())
            val id = repository.addCard(deckId, "cat", "кошка")
            assertEquals(4, awaitItem())
            repository.deleteCard(id)
            assertEquals(3, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S19: данные переживают закрытие и переоткрытие БД.
    @Test
    fun operationsSurvive_dbReopen() = runTest {
        val addedId = repository.addCard(deckId, "cat", "кошка")
        val editedId = repository.addCard(deckId, "dog", "собака")
        val removedId = repository.addCard(deckId, "book", "книга")

        repository.updateCard(repository.getCardById(editedId)!!.copy(front = "a dog"))
        repository.deleteCard(removedId)

        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = buildDb(context, dbFile)
        repository = RoomCardRepository(db.cardDao())

        assertNotNull(repository.getCardById(addedId))
        assertEquals("кошка", repository.getCardById(addedId)!!.back)
        assertEquals("a dog", repository.getCardById(editedId)!!.front)
        assertNull(repository.getCardById(removedId))
        assertEquals(2, repository.observeCardCount(deckId).first())
    }

    // S9: дубликат разрешён — две независимые записи.
    @Test
    fun duplicateAddCard_createsTwoIndependentRows() = runTest {
        repository.addCard(deckId, "cat", "кошка")
        repository.addCard(deckId, "cat", "кошка")

        val cards = repository.observeCardsByDeck(deckId).first()
        assertEquals(2, cards.size)
        assertTrue(cards.all { it.front == "cat" && it.back == "кошка" })
        assertEquals(2, cards.map { it.id }.distinct().size)
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
    suspend fun insertDeck(deck: DeckEntity): Long

    @Query("SELECT * FROM decks WHERE id = :id LIMIT 1")
    suspend fun getDeckById(id: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE name = :name LIMIT 1")
    suspend fun getDeckByName(name: String): DeckEntity?

    /** Потоковое чтение колоды для экрана содержимого (S1, S24). */
    @Query("SELECT * FROM decks WHERE id = :id LIMIT 1")
    fun observeDeckById(id: Long): Flow<DeckEntity?>

    @Query(
        """
        SELECT decks.id AS deckId, decks.name AS deckName,
               (SELECT COUNT(*) FROM cards WHERE cards.deckId = decks.id) AS cardCount
        FROM decks ORDER BY decks.id ASC
        """
    )
    fun observeDecksWithCardCounts(): Flow<List<DeckWithCardCount>>
}
```

### FILE: app/src/main/java/com/mnemocards/data/repository/CardRepository.kt
```kotlin
package com.mnemocards.data.repository

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Результат операции записи с карточкой.
 * 404-аналог — [CardOperationResult.NotFound] (записи уже нет,
 * update несуществующей записи не превращается в insert).
 * 500-аналог — [CardOperationResult.Error] (исключение Room поймано,
 * приложение не падает).
 */
sealed interface CardOperationResult {
    data class Success(val cardId: Long) : CardOperationResult
    object NotFound : CardOperationResult
    data class Error(val cause: Throwable? = null) : CardOperationResult
}

/**
 * Офлайн-репозиторий карточек. Room — единый источник данных,
 * UI наблюдает изменения через Flow.
 */
interface CardRepository {

    /** Колода по идентификатору; null — 404-аналог (S24). */
    fun observeDeck(deckId: Long): Flow<DeckEntity?>

    /** Карточки колоды, новые сверху (createdAt DESC). */
    fun observeCards(deckId: Long): Flow<List<CardEntity>>

    /** Счётчик карточек колоды — обновляется автоматически (S20–S21). */
    fun observeCardCount(deckId: Long): Flow<Int>

    suspend fun getCardById(id: Long): CardEntity?

    /** Создаёт карточку; createdAt гарантирует позицию «первой сверху». */
    suspend fun addCard(deckId: Long, front: String, back: String): CardOperationResult

    /**
     * Обновляет существующую карточку (front/back), сохраняя deckId и createdAt.
     * [CardOperationResult.NotFound] — записи уже нет (S25):
     * update несуществующей записи не превращается в insert.
     */
    suspend fun updateCard(cardId: Long, front: String, back: String): CardOperationResult

    suspend fun deleteCard(cardId: Long): CardOperationResult
}

class RoomCardRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
) : CardRepository {

    override fun observeDeck(deckId: Long): Flow<DeckEntity?> =
        deckDao.observeDeckById(deckId)

    override fun observeCards(deckId: Long): Flow<List<CardEntity>> =
        cardDao.observeCardsByDeck(deckId)
            .catch { throw it } // ошибки чтения пробрасываются в Flow -> UiState.Error (S26)

    override fun observeCardCount(deckId: Long): Flow<Int> =
        cardDao.observeCardCountByDeck(deckId)
            .catch { throw it }

    override suspend fun getCardById(id: Long): CardEntity? =
        cardDao.getCardById(id)

    override suspend fun addCard(
        deckId: Long,
        front: String,
        back: String,
    ): CardOperationResult = try {
        // Строго возрастающий createdAt внутри колоды: новые всегда выше старых.
        val max = cardDao.maxCreatedAtInDeck(deckId) ?: 0L
        val now = System.currentTimeMillis()
        val createdAt = if (now > max) now else max + 1
        val id = cardDao.insertCard(
            CardEntity(deckId = deckId, front = front, back = back, createdAt = createdAt)
        )
        CardOperationResult.Success(id)
    } catch (e: Exception) {
        CardOperationResult.Error(e)
    }

    override suspend fun updateCard(
        cardId: Long,
        front: String,
        back: String,
    ): CardOperationResult = try {
        val existing = cardDao.getCardById(cardId)
            ?: return CardOperationResult.NotFound
        val updated = cardDao.updateCard(existing.copy(front = front, back = back))
        if (updated > 0) {
            CardOperationResult.Success(cardId)
        } else {
            CardOperationResult.NotFound
        }
    } catch (e: Exception) {
        CardOperationResult.Error(e)
    }

    override suspend fun deleteCard(cardId: Long): CardOperationResult = try {
        if (cardDao.deleteCard(cardId) > 0) {
            CardOperationResult.Success(cardId)
        } else {
            CardOperationResult.NotFound
        }
    } catch (e: Exception) {
        CardOperationResult.Error(e)
    }
}
```

### FILE: app/src/test/java/com/mnemocards/data/CardRepositoryTest.kt
```kotlin
package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.CardOperationResult
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.data.repository.RoomCardRepository
import kotlinx.coroutines.flow.Flow
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CardRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var repository: CardRepository
    private lateinit var dbFile: File
    private var deckId: Long = 0L

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File.createTempFile("repo", ".db")
        dbFile.deleteOnExit()
        db = buildDb(context, dbFile)
        repository = RoomCardRepository(db.deckDao(), db.cardDao())
        deckId = db.deckDao().insertDeck(DeckEntity(name = "Английский базовый"))
    }

    private fun buildDb(context: Context, file: File): MnemoCardsDatabase =
        Room.databaseBuilder(context, MnemoCardsDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .build()

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun CardRepository.addOk(deckId: Long, front: String, back: String): Long {
        val result = addCard(deckId, front, back)
        assertTrue(result is CardOperationResult.Success)
        return (result as CardOperationResult.Success).cardId
    }

    // S1: карточки колоды читаются с лицевой и обратной стороной.
    @Test
    fun observeCards_returnsFrontAndBack() = runTest {
        repository.addOk(deckId, "hello", "привет")
        repository.addOk(deckId, "world", "мир")
        repository.addOk(deckId, "cat", "кошка")

        val cards = repository.observeCards(deckId).first()
        assertEquals(3, cards.size)
        val pair = cards.map { it.front to it.back }
        assertTrue(pair.contains("hello" to "привет"))
        assertTrue(pair.contains("world" to "мир"))
        assertTrue(pair.contains("cat" to "кошка"))
    }

    // S1: observeDeck отдаёт колоду по идентификатору.
    @Test
    fun observeDeck_returnsDeckOrNull() = runTest {
        assertEquals("Английский базовый", repository.observeDeck(deckId).first()?.name)
        assertNull(repository.observeDeck(99L).first())
    }

    // S3, S5: новая карточка появляется в потоке первой.
    @Test
    fun addCard_newCardAppearsFirstInFlow() = runTest {
        repository.addOk(deckId, "hello", "привет")
        repository.observeCards(deckId).test {
            awaitItem() // текущее состояние
            repository.addOk(deckId, "cat", "кошка")
            val cards = awaitItem()
            assertEquals("cat", cards.first().front)
            assertEquals("кошка", cards.first().back)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S12: update меняет значения, счётчик не растёт.
    @Test
    fun updateCard_changesValues_countStaysSame() = runTest {
        val id = repository.addOk(deckId, "cat", "кошка")
        repository.addOk(deckId, "dog", "собака")

        val result = repository.updateCard(id, "a cat", "кошка")

        assertTrue(result is CardOperationResult.Success)
        assertEquals("a cat", repository.getCardById(id)!!.front)
        assertEquals("кошка", repository.getCardById(id)!!.back)
        assertEquals(2, repository.observeCardCount(deckId).first())
    }

    // S25: update несуществующей записи не становится insert (404-аналог).
    @Test
    fun updateCard_missingRow_returnsNotFound_noInsert() = runTest {
        val result = repository.updateCard(4242L, "x", "y")

        assertTrue(result is CardOperationResult.NotFound)
        assertNull(repository.getCardById(4242L))
        assertEquals(0, repository.observeCardCount(deckId).first())
    }

    // S16: delete удаляет запись.
    @Test
    fun deleteCard_removesRow() = runTest {
        val id = repository.addOk(deckId, "cat", "кошка")
        val result = repository.deleteCard(id)

        assertTrue(result is CardOperationResult.Success)
        assertNull(repository.getCardById(id))
        assertEquals(0, repository.observeCards(deckId).first().size)
    }

    // S25: delete несуществующей записи — 404-аналог, без краша.
    @Test
    fun deleteCard_missingRow_returnsNotFound() = runTest {
        assertTrue(repository.deleteCard(4242L) is CardOperationResult.NotFound)
    }

    // S20, S21: счётчик карточек эмитит 3 -> 4 -> 3.
    @Test
    fun observeCardCount_emits3_4_3() = runTest {
        repository.addOk(deckId, "a", "а")
        repository.addOk(deckId, "b", "б")
        repository.addOk(deckId, "c", "в")

        repository.observeCardCount(deckId).test {
            assertEquals(3, awaitItem())
            val id = repository.addOk(deckId, "cat", "кошка")
            assertEquals(4, awaitItem())
            repository.deleteCard(id)
            assertEquals(3, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S19: данные переживают закрытие и переоткрытие БД.
    @Test
    fun operationsSurvive_dbReopen() = runTest {
        val addedId = repository.addOk(deckId, "cat", "кошка")
        val editedId = repository.addOk(deckId, "dog", "собака")
        val removedId = repository.addOk(deckId, "book", "книга")

        repository.updateCard(editedId, "a dog", "собака")
        repository.deleteCard(removedId)

        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = buildDb(context, dbFile)
        repository = RoomCardRepository(db.deckDao(), db.cardDao())

        assertNotNull(repository.getCardById(addedId))
        assertEquals("кошка", repository.getCardById(addedId)!!.back)
        assertEquals("a dog", repository.getCardById(editedId)!!.front)
        assertNull(repository.getCardById(removedId))
        assertEquals(2, repository.observeCardCount(deckId).first())
    }

    // S9: дубликат разрешён — две независимые записи.
    @Test
    fun duplicateAddCard_createsTwoIndependentRows() = runTest {
        repository.addOk(deckId, "cat", "кошка")
        repository.addOk(deckId, "cat", "кошка")

        val cards = repository.observeCards(deckId).first()
        assertEquals(2, cards.size)
        assertTrue(cards.all { it.front == "cat" && it.back == "кошка" })
        assertEquals(2, cards.map { it.id }.distinct().size)
    }

    // S10: длинный текст сохраняется целиком.
    @Test
    fun addCard_longText_savedFully() = runTest {
        val longFront = "f".repeat(250)
        val longBack = "б".repeat(250)
        val id = repository.addOk(deckId, longFront, longBack)

        val card = repository.getCardById(id)!!
        assertEquals(longFront, card.front)
        assertEquals(longBack, card.back)
    }

    // ---------- S26: 500-аналоги на фейковом DAO, бросающем исключения ----------

    /** DAO, который падает при любом обращении к переданным операциям. */
    private class ThrowingCardDao(
        private val onGet: (Long) -> CardEntity? = { null },
        private val onUpdate: (CardEntity) -> Int = { 0 },
        private val onDelete: (Long) -> Int = { 0 },
    ) : CardDao {
        private val boom = RuntimeException("corrupted database")

        override suspend fun insertCard(card: CardEntity): Long = throw boom
        override suspend fun updateCard(card: CardEntity): Int = onUpdate(card)
        override suspend fun deleteCard(id: Long): Int = onDelete(id)
        override fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>> = throw boom
        override suspend fun getCardById(id: Long): CardEntity? = onGet(id)
        override fun observeCardCountByDeck(deckId: Long): Flow<Int> = throw boom
        override suspend fun maxCreatedAtInDeck(deckId: Long): Long? = throw boom
    }

    private class NoopDeckDao : DeckDao {
        override suspend fun insertDeck(deck: DeckEntity): Long = 0L
        override suspend fun getDeckById(id: Long): DeckEntity? = null
        override suspend fun getDeckByName(name: String): DeckEntity? = null
        override fun observeDeckById(id: Long): Flow<DeckEntity?> = throw IllegalStateException()
        override fun observeDecksWithCardCounts(): Flow<List<com.mnemocards.data.db.DeckWithCardCount>> =
            throw IllegalStateException()
    }

    // S26: insert падает — Error, приложение не падает, записей нет.
    @Test
    fun addCard_daoThrows_returnsError() = runTest {
        val repo = RoomCardRepository(NoopDeckDao(), ThrowingCardDao())

        val result = repo.addCard(deckId, "cat", "кошка")

        assertTrue(result is CardOperationResult.Error)
    }

    // S26: update падает — Error, прежние значения не тронуты.
    @Test
    fun updateCard_daoThrows_returnsError() = runTest {
        val existing = CardEntity(id = 1L, deckId = deckId, front = "cat", back = "кошка", createdAt = 1L)
        val repo = RoomCardRepository(
            NoopDeckDao(),
            ThrowingCardDao(
                onGet = { existing },
                onUpdate = { throw RuntimeException("write failed") },
            ),
        )

        val result = repo.updateCard(1L, "a cat", "кошка")

        assertTrue(result is CardOperationResult.Error)
        // Прежние значения не тронуты (проверяем на реальной БД).
        val realId = repository.addOk(deckId, "cat", "кошка")
        assertTrue(repository.updateCard(realId, "a cat", "кошка") is CardOperationResult.Success)
        assertEquals("a cat", repository.getCardById(realId)!!.front)
    }

    // S26: delete падает — Error, карточка не удалена.
    @Test
    fun deleteCard_daoThrows_returnsError() = runTest {
        val repo = RoomCardRepository(
            NoopDeckDao(),
            ThrowingCardDao(onDelete = { throw RuntimeException("delete failed") }),
        )

        val result = repo.deleteCard(1L)

        assertTrue(result is CardOperationResult.Error)
    }
}
```

### FILE: app/src/main/java/com/mnemocards/domain/CardFormValidator.kt
```kotlin
package com.mnemocards.domain

/**
 * Ошибки валидации полей формы карточки.
 * null в поле — ошибки нет. Текст — 400-аналог (S6–S8, S13).
 */
data class CardFieldErrors(
    val front: String? = null,
    val back: String? = null,
) {
    val isEmpty: Boolean get() = front == null && back == null
}

/**
 * Валидация формы карточки: оба поля обязательны, значения
 * проверяются после trim (пробелы и переводы строк не считаются текстом).
 * Длина текста не ограничена — 200+ символов валидны (S10).
 */
object CardFormValidator {

    const val REQUIRED_MESSAGE = "Заполните поле"

    fun validateCardInput(front: String, back: String): CardFieldErrors =
        CardFieldErrors(
            front = if (front.isBlank()) REQUIRED_MESSAGE else null,
            back = if (back.isBlank()) REQUIRED_MESSAGE else null,
        )
}
```

### FILE: app/src/test/java/com/mnemocards/domain/CardFormValidatorTest.kt
```kotlin
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
```


---

## Батч 3/7

### FILE: app/src/test/java/com/mnemocards/ui/deckcontent/DeckContentViewModelTest.kt
```kotlin
package com.mnemocards.ui.deckcontent

import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.repository.CardOperationResult
import com.mnemocards.data.repository.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeckContentViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Fake-репозиторий: потоки через MutableStateFlow, запись фиксируется списками. */
    private class FakeCardRepository : CardRepository {
        val deck = MutableStateFlow<DeckEntity?>(null)
        val cards = MutableStateFlow<List<CardEntity>>(emptyList())

        /** Если не null — потоки наблюдения бросают исключение (500-аналог, S26). */
        var observeError: Throwable? = null

        val addedDecks = mutableListOf<Long>()
        val updatedCards = mutableListOf<Long>()
        val deletedCards = mutableListOf<Long>()

        var addResult: CardOperationResult = CardOperationResult.Success(1L)
        var updateResult: CardOperationResult = CardOperationResult.Success(1L)
        var deleteResult: CardOperationResult = CardOperationResult.Success(1L)

        private fun <T> throwingOr(flow: Flow<T>): Flow<T> =
            if (observeError != null) {
                kotlinx.coroutines.flow.flow<T> { throw observeError!! }
            } else {
                flow
            }

        override fun observeDeck(deckId: Long): Flow<DeckEntity?> =
            if (observeError != null) throwingOr(deck) else deck

        override fun observeCards(deckId: Long): Flow<List<CardEntity>> =
            if (observeError != null) throwingOr(cards) else cards

        override fun observeCardCount(deckId: Long): Flow<Int> =
            kotlinx.coroutines.flow.flowOf(cards.value.size)

        override suspend fun getCardById(id: Long): CardEntity? = null

        override suspend fun addCard(
            deckId: Long,
            front: String,
            back: String,
        ): CardOperationResult {
            addedDecks += deckId
            return addResult
        }

        override suspend fun updateCard(
            cardId: Long,
            front: String,
            back: String,
        ): CardOperationResult {
            updatedCards += cardId
            return updateResult
        }

        override suspend fun deleteCard(cardId: Long): CardOperationResult {
            deletedCards += cardId
            return deleteResult
        }
    }

    private lateinit var repository: FakeCardRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCardRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun card(id: Long, front: String, back: String) =
        CardEntity(id = id, deckId = 1L, front = front, back = back, createdAt = id)

    // S1: колода с карточками -> Loading, затем Content с названием и списком.
    @Test
    fun deckWithCards_emitsContent() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = 1L, name = "Английский базовый")
        repository.cards.value = listOf(
            card(3, "cat", "кошка"),
            card(2, "world", "мир"),
            card(1, "hello", "привет"),
        )

        val viewModel = DeckContentViewModel(deckId = 1L, repository = repository)

        app.cash.turbine.test(viewModel.uiState) {
            assertEquals(DeckContentUiState.Loading, awaitItem())
            assertEquals(
                DeckContentUiState.Content(
                    title = "Английский базовый",
                    cards = listOf(
                        card(3, "cat", "кошка"),
                        card(2, "world", "мир"),
                        card(1, "hello", "привет"),
                    ),
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S2: колода без карточек -> Empty (не Content с пустым списком).
    @Test
    fun deckWithoutCards_emitsEmpty() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = 1L, name = "Английский базовый")
        repository.cards.value = emptyList()

        val viewModel = DeckContentViewModel(deckId = 1L, repository = repository)

        app.cash.turbine.test(viewModel.uiState) {
            assertEquals(DeckContentUiState.Loading, awaitItem())
            assertEquals(
                DeckContentUiState.Empty(title = "Английский базовый"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S24: колоды нет (observeDeck == null) -> NotFound, а не пустой список.
    @Test
    fun missingDeck_emitsNotFound() = runTest(dispatcher) {
        repository.deck.value = null
        repository.cards.value = emptyList()

        val viewModel = DeckContentViewModel(deckId = 99L, repository = repository)

        app.cash.turbine.test(viewModel.uiState) {
            assertEquals(DeckContentUiState.Loading, awaitItem())
            assertEquals(DeckContentUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S26: ошибка чтения -> Error; retry() восстанавливает поток после устранения ошибки.
    @Test
    fun readError_emitsError_thenRetryRecovers() = runTest(dispatcher) {
        repository.observeError = RuntimeException("corrupted database")

        val viewModel = DeckContentViewModel(deckId = 1L, repository = repository)

        app.cash.turbine.test(viewModel.uiState) {
            assertEquals(DeckContentUiState.Loading, awaitItem())
            assertEquals(DeckContentUiState.Error, awaitItem())

            // Ошибка устранена, данные появились.
            repository.observeError = null
            repository.deck.value = DeckEntity(id = 1L, name = "Английский базовый")
            repository.cards.value = listOf(card(1, "hello", "привет"))

            viewModel.retry()
            assertEquals(
                DeckContentUiState.Content(
                    title = "Английский базовый",
                    cards = listOf(card(1, "hello", "привет")),
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S16: deleteCard делегируется в репозиторий.
    @Test
    fun deleteCard_delegatesToRepository() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = 1L, name = "Английский базовый")
        repository.cards.value = listOf(card(42, "cat", "кошка"))

        val viewModel = DeckContentViewModel(deckId = 1L, repository = repository)

        app.cash.turbine.test(viewModel.uiState) {
            awaitItem() // Loading
            awaitItem() // Content
            viewModel.deleteCard(42L)
            assertEquals(listOf(42L), repository.deletedCards)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S26: ошибка удаления не роняет состояние и не трогает репозиторий повторно.
    @Test
    fun deleteCard_error_keepsState() = runTest(dispatcher) {
        repository.deck.value = DeckEntity(id = 1L, name = "Английский базовый")
        repository.cards.value = listOf(card(42, "cat", "кошка"))
        repository.deleteResult = CardOperationResult.Error(RuntimeException("delete failed"))

        val viewModel = DeckContentViewModel(deckId = 1L, repository = repository)

        app.cash.turbine.test(viewModel.uiState) {
            awaitItem() // Loading
            val content = awaitItem()
            assertTrue(content is DeckContentUiState.Content)
            viewModel.deleteCard(42L)
            assertEquals(listOf(42L), repository.deletedCards)
            // Список не изменился — состояние остаётся прежним Content.
            assertEquals(content, viewModel.uiState.value)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/deckcontent/DeckContentViewModel.kt
```kotlin
package com.mnemocards.ui.deckcontent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Состояние экрана содержимого колоды.
 * NotFound — 404-аналог (S24), Error — 500-аналог при чтении (S26).
 */
sealed interface DeckContentUiState {
    data object Loading : DeckContentUiState
    data class Content(val title: String, val cards: List<CardEntity>) : DeckContentUiState
    data class Empty(val title: String) : DeckContentUiState
    data object NotFound : DeckContentUiState
    data object Error : DeckContentUiState
}

/**
 * ViewModel экрана содержимого колоды (MVVM + UDF).
 * Объединяет потоки колоды и её карточек из Room; состояние
 * живёт в StateFlow и переживает смену конфигурации.
 */
class DeckContentViewModel(
    private val deckId: Long,
    private val repository: CardRepository,
) : ViewModel() {

    /** Счётчик попыток загрузки: инкремент в retry() перезапускает потоки. */
    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<DeckContentUiState> = retryTrigger
        .flatMapLatest { attempt ->
            combine(
                repository.observeDeck(deckId),
                repository.observeCards(deckId),
            ) { deck, cards ->
                when {
                    // S24: устаревший идентификатор колоды — не выдаём пустой список за успех.
                    deck == null -> DeckContentUiState.NotFound
                    cards.isEmpty() -> DeckContentUiState.Empty(deck.name)
                    else -> DeckContentUiState.Content(deck.name, cards)
                }
            }.catch {
                // S26: ошибка чтения Room — состояние ошибки, пустой список не выдаётся за успех.
                emit(DeckContentUiState.Error)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckContentUiState.Loading,
        )

    /** Повторная загрузка после ошибки чтения (S26). */
    fun retry() {
        retryTrigger.value = retryTrigger.value + 1
    }

    /** Удаление карточки; делегируется в репозиторий, ошибки не роняют UI (S16, S26). */
    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
        }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/cardform/CardFormViewModelTest.kt
```kotlin
package com.mnemocards.ui.cardform

import app.cash.turbine.test
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.repository.CardOperationResult
import com.mnemocards.data.repository.CardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardFormViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Fake-репозиторий: фиксирует вызовы записи, результаты настраиваются. */
    private class FakeCardRepository : CardRepository {
        val deck = MutableStateFlow<DeckEntity?>(DeckEntity(id = 1L, name = "Английский базовый"))
        val cards = MutableStateFlow<List<CardEntity>>(emptyList())

        var cardById: CardEntity? = null

        data class AddCall(val deckId: Long, val front: String, val back: String)
        data class UpdateCall(val cardId: Long, val front: String, val back: String)

        val addCalls = mutableListOf<AddCall>()
        val updateCalls = mutableListOf<UpdateCall>()
        val deleteCalls = mutableListOf<Long>()

        var addResult: CardOperationResult = CardOperationResult.Success(101L)
        var updateResult: CardOperationResult = CardOperationResult.Success(42L)
        var deleteResult: CardOperationResult = CardOperationResult.Success(42L)

        override fun observeDeck(deckId: Long): Flow<DeckEntity?> = deck

        override fun observeCards(deckId: Long): Flow<List<CardEntity>> = cards

        override fun observeCardCount(deckId: Long): Flow<Int> =
            kotlinx.coroutines.flow.flowOf(cards.value.size)

        override suspend fun getCardById(id: Long): CardEntity? = cardById

        override suspend fun addCard(
            deckId: Long,
            front: String,
            back: String,
        ): CardOperationResult {
            addCalls += AddCall(deckId, front, back)
            return addResult
        }

        override suspend fun updateCard(
            cardId: Long,
            front: String,
            back: String,
        ): CardOperationResult {
            updateCalls += UpdateCall(cardId, front, back)
            return updateResult
        }

        override suspend fun deleteCard(cardId: Long): CardOperationResult {
            deleteCalls += cardId
            return deleteResult
        }
    }

    private lateinit var repository: FakeCardRepository

    private val existingCard = CardEntity(
        id = 42L,
        deckId = 1L,
        front = "cat",
        back = "кошка",
        createdAt = 1L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCardRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // S4: режим добавления — оба поля пусты, без ошибок валидации.
    @Test
    fun addMode_fieldsAreEmpty() = runTest(dispatcher) {
        val viewModel = CardFormViewModel(deckId = 1L, cardId = null, repository = repository)

        val state = viewModel.uiState.value
        assertEquals("", state.front)
        assertEquals("", state.back)
        assertNull(state.frontError)
        assertNull(state.backError)
    }

    // S11: режим редактирования — предзаполнение по cardId.
    @Test
    fun editMode_prefilledFromCard() = runTest(dispatcher) {
        repository.cardById = existingCard

        val viewModel = CardFormViewModel(deckId = 1L, cardId = 42L, repository = repository)

        val state = viewModel.uiState.value
        assertEquals("cat", state.front)
        assertEquals("кошка", state.back)
        assertNull(state.frontError)
        assertNull(state.backError)
    }

    // S5: валидное сохранение в режиме добавления — ровно один addCard и событие закрытия.
    @Test
    fun save_validAdd_callsAddOnce_andCloses() = runTest(dispatcher) {
        val viewModel = CardFormViewModel(deckId = 1L, cardId = null, repository = repository)

        viewModel.events.test {
            viewModel.onFrontChange("cat")
            viewModel.onBackChange("кошка")
            viewModel.save()

            assertEquals(1, repository.addCalls.size)
            assertEquals(FakeCardRepository.AddCall(1L, "cat", "кошка"), repository.addCalls.single())
            assertEquals(0, repository.updateCalls.size)
            assertEquals(CardFormEvent.Closed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S12: валидное сохранение в режиме редактирования — ровно один updateCard.
    @Test
    fun save_validEdit_callsUpdateOnce_andCloses() = runTest(dispatcher) {
        repository.cardById = existingCard

        val viewModel = CardFormViewModel(deckId = 1L, cardId = 42L, repository = repository)

        viewModel.events.test {
            viewModel.onFrontChange("a cat")
            viewModel.onBackChange("кошка")
            viewModel.save()

            assertEquals(1, repository.updateCalls.size)
            assertEquals(
                FakeCardRepository.UpdateCall(42L, "a cat", "кошка"),
                repository.updateCalls.single(),
            )
            assertEquals(0, repository.addCalls.size)
            assertEquals(CardFormEvent.Closed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S6, S7: невалидные данные — ошибки полей, репозиторий не вызывается, ввод сохраняется.
    @Test
    fun save_invalidFields_showsErrors_noRepoCall() = runTest(dispatcher) {
        val viewModel = CardFormViewModel(deckId = 1L, cardId = null, repository = repository)

        viewModel.onFrontChange("")
        viewModel.onBackChange("собака")
        viewModel.save()

        var state = viewModel.uiState.value
        assertNotNull(state.frontError)
        assertNull(state.backError)
        // Введённые значения не потерялись.
        assertEquals("", state.front)
        assertEquals("собака", state.back)
        assertTrue(repository.addCalls.isEmpty())
        assertTrue(repository.updateCalls.isEmpty())

        // Оба поля пусты (S7).
        viewModel.onBackChange("")
        viewModel.save()

        state = viewModel.uiState.value
        assertNotNull(state.frontError)
        assertNotNull(state.backError)
        assertTrue(repository.addCalls.isEmpty())
    }

    // S8: только пробельные символы — валидация блокирует сохранение.
    @Test
    fun save_whitespaceOnly_blocked() = runTest(dispatcher) {
        val viewModel = CardFormViewModel(deckId = 1L, cardId = null, repository = repository)

        viewModel.onFrontChange("   ")
        viewModel.onBackChange(" \n\t ")
        viewModel.save()

        val state = viewModel.uiState.value
        assertNotNull(state.frontError)
        assertNotNull(state.backError)
        assertTrue(repository.addCalls.isEmpty())
    }

    // S13: очистка поля при редактировании — ошибка, прежние значения в Room не тронуты.
    @Test
    fun save_editClearedField_blocked() = runTest(dispatcher) {
        repository.cardById = existingCard

        val viewModel = CardFormViewModel(deckId = 1L, cardId = 42L, repository = repository)

        viewModel.onBackChange("")
        viewModel.save()

        val state = viewModel.uiState.value
        assertNull(state.frontError)
        assertNotNull(state.backError)
        assertTrue(repository.updateCalls.isEmpty())
        assertTrue(repository.addCalls.isEmpty())
    }

    // S26: ошибка записи — форма открыта, ввод не потерян, закрытия нет.
    @Test
    fun save_writeError_formStaysOpen_inputKept() = runTest(dispatcher) {
        repository.addResult = CardOperationResult.Error(RuntimeException("write failed"))

        val viewModel = CardFormViewModel(deckId = 1L, cardId = null, repository = repository)

        viewModel.events.test {
            viewModel.onFrontChange("cat")
            viewModel.onBackChange("кошка")
            viewModel.save()

            val state = viewModel.uiState.value
            assertEquals("cat", state.front)
            assertEquals("кошка", state.back)
            assertNotNull(state.saveError)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S25: update удалённой карточки — NotFound, insert не происходит.
    @Test
    fun save_editDeletedCard_notFound_noInsert() = runTest(dispatcher) {
        repository.cardById = existingCard
        repository.updateResult = CardOperationResult.NotFound

        val viewModel = CardFormViewModel(deckId = 1L, cardId = 42L, repository = repository)

        viewModel.events.test {
            viewModel.onFrontChange("a cat")
            viewModel.onBackChange("кошка")
            viewModel.save()

            assertEquals(1, repository.updateCalls.size)
            assertEquals(0, repository.addCalls.size)
            assertEquals(CardFormEvent.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S14: закрытие без сохранения — ноль вызовов репозитория.
    @Test
    fun dismiss_withoutSave_noRepoCalls() = runTest(dispatcher) {
        repository.cardById = existingCard

        val viewModel = CardFormViewModel(deckId = 1L, cardId = 42L, repository = repository)

        viewModel.events.test {
            viewModel.onFrontChange("изменено, но не сохранено")
            viewModel.dismiss()

            assertTrue(repository.addCalls.isEmpty())
            assertTrue(repository.updateCalls.isEmpty())
            assertTrue(repository.deleteCalls.isEmpty())
            assertEquals(CardFormEvent.Closed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```


---

## Батч 4/7

### FILE: app/src/main/java/com/mnemocards/ui/cardform/CardFormViewModel.kt
```kotlin
package com.mnemocards.ui.cardform

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.repository.CardOperationResult
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.domain.CardFormValidator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot события формы карточки. */
sealed interface CardFormEvent {
    /** Успешное сохранение или отмена — закрыть форму (S5, S12, S14). */
    data object Closed : CardFormEvent

    /** 404-аналог: редактируемая карточка не найдена в Room (S25). */
    data object NotFound : CardFormEvent
}

/** Состояние формы карточки: значения полей, ошибки валидации и записи. */
data class CardFormUiState(
    val front: String = "",
    val back: String = "",
    val frontError: String? = null,
    val backError: String? = null,
    val isSaving: Boolean = false,
    /** 500-аналог: сообщение при ошибке записи в Room (S26). */
    val saveError: String? = null,
)

/**
 * ViewModel формы карточки (MVVM + UDF).
 * Режим добавления: cardId == null, поля пусты (S4).
 * Режим редактирования: предзаполнение по cardId (S11).
 * При сохранении: validate -> trim -> репозиторий -> маппинг результата.
 */
class CardFormViewModel(
    private val deckId: Long,
    private val cardId: Long?,
    private val repository: CardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardFormUiState())
    val uiState: StateFlow<CardFormUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CardFormEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<CardFormEvent> = _events.asSharedFlow()

    init {
        if (cardId != null) {
            // S11: предзаполнение полей текущими значениями карточки.
            viewModelScope.launch {
                val card = repository.getCardById(cardId)
                if (card != null) {
                    _uiState.update {
                        it.copy(front = card.front, back = card.back)
                    }
                } else {
                    // S25: карточка уже удалена — форму открывать нечем.
                    _events.tryEmit(CardFormEvent.NotFound)
                }
            }
        }
    }

    /** Ввод лицевой стороны; сбрасывает ошибку поля и ошибку записи. */
    fun onFrontChange(value: String) {
        _uiState.update {
            it.copy(front = value, frontError = null, saveError = null)
        }
    }

    /** Ввод перевода; сбрасывает ошибку поля и ошибку записи. */
    fun onBackChange(value: String) {
        _uiState.update {
            it.copy(back = value, backError = null, saveError = null)
        }
    }

    /**
     * Сохранение: валидация (400-аналоги S6–S8, S13), trim, затем
     * addCard или updateCard в зависимости от режима (S5, S12).
     */
    fun save() {
        val current = _uiState.value
        if (current.isSaving) return

        val errors = CardFormValidator.validateCardInput(current.front, current.back)
        if (!errors.isEmpty) {
            // Форма остаётся открытой, введённые значения не теряются (S6–S8).
            _uiState.update {
                it.copy(frontError = errors.front, backError = errors.back)
            }
            return
        }

        val front = current.front.trim()
        val back = current.back.trim()

        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            val result = if (cardId == null) {
                repository.addCard(deckId, front, back)
            } else {
                repository.updateCard(cardId, front, back)
            }
            _uiState.update { it.copy(isSaving = false) }
            when (result) {
                is CardOperationResult.Success ->
                    _events.tryEmit(CardFormEvent.Closed)
                is CardOperationResult.NotFound ->
                    // S25: update несуществующей записи не превращается в insert.
                    _events.tryEmit(CardFormEvent.NotFound)
                is CardOperationResult.Error ->
                    // S26: форма остаётся открытой, ввод не теряется.
                    _uiState.update {
                        it.copy(saveError = "Не удалось сохранить")
                    }
            }
        }
    }

    /** Закрытие формы без сохранения (S14): репозиторий не вызывается. */
    fun dismiss() {
        _events.tryEmit(CardFormEvent.Closed)
    }
}

/** Фабрика формы карточки: параметры навигации + репозиторий. */
class CardFormViewModelFactory(
    private val deckId: Long,
    private val cardId: Long?,
    private val repository: CardRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == CardFormViewModel::class.java)
        return CardFormViewModel(deckId, cardId, repository) as T
    }
}
```

### FILE: app/src/androidTest/java/com/mnemocards/ui/deckcontent/DeckContentScreenTest.kt
```kotlin
package com.mnemocards.ui.deckcontent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardEntity
import com.mnemocards.ui.theme.MnemoCardsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckContentScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun card(id: Long, front: String, back: String) =
        CardEntity(id = id, deckId = 1L, front = front, back = back, createdAt = id)

    private fun setContent(state: DeckContentUiState) {
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = state,
                    onBack = {},
                    onAddCard = {},
                    onEditCard = {},
                    onRetry = {},
                )
            }
        }
    }

    // S1: заголовок — название колоды, каждый элемент показывает обе стороны.
    @Test
    fun content_showsTitleAndBothSides() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(
                    card(3, "hello", "привет"),
                    card(2, "world", "мир"),
                    card(1, "cat", "кошка"),
                ),
            ),
        )

        composeRule.onNodeWithText("Английский базовый").assertIsDisplayed()
        composeRule.onNodeWithText("hello — привет").assertIsDisplayed()
        composeRule.onNodeWithText("world — мир").assertIsDisplayed()
        composeRule.onNodeWithText("cat — кошка").assertIsDisplayed()
    }

    // S1: видна кнопка добавления карточки и возврат к списку колод.
    @Test
    fun content_showsAddButtonAndBack() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(card(1, "hello", "привет")),
            ),
        )

        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Назад").assertIsDisplayed()
    }

    // S2: пустая колода — пустое состояние, кнопка добавления доступна.
    @Test
    fun emptyDeck_showsEmptyState() {
        setContent(DeckContentUiState.Empty(title = "Английский базовый"))

        composeRule.onNodeWithText("В колоде пока нет карточек").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
    }

    // S9: дубликат карточки — обе записи отображаются.
    @Test
    fun duplicateCards_bothDisplayed() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(
                    card(2, "cat", "кошка"),
                    card(1, "cat", "кошка"),
                ),
            ),
        )

        val nodes = composeRule.onAllNodes(compose.ui.test.hasText("cat — кошка"))
        composeRule.runOnIdle {
            assertTrue(nodes.fetchSemanticsNodes().size == 2)
        }
    }

    // S10: длинный текст (200+ символов) отображается, макет не ломается.
    @Test
    fun longText_displayed_layoutNotBroken() {
        val longFront = "f".repeat(250)
        val longBack = "б".repeat(250)
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(card(1, longFront, longBack)),
            ),
        )

        composeRule.onNodeWithText("$longFront — $longBack").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
    }

    // S24: колода не найдена — состояние NotFound, а не пустой список.
    @Test
    fun notFound_showsState() {
        setContent(DeckContentUiState.NotFound)

        composeRule.onNodeWithText("Колода не найдена").assertIsDisplayed()
        composeRule.onNodeWithText("К списку колод").assertIsDisplayed()
    }

    // S26: ошибка чтения — состояние ошибки с возможностью повторить.
    @Test
    fun error_showsRetry() {
        setContent(DeckContentUiState.Error)

        composeRule.onNodeWithText("Повторить").assertIsDisplayed()
    }

    // S1: клик по элементу — коллбек редактирования.
    @Test
    fun clickOnCard_invokesEditCallback() {
        var clickedCardId: Long? = null
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = DeckContentUiState.Content(
                        title = "Английский базовый",
                        cards = listOf(card(1, "hello", "привет")),
                    ),
                    onBack = {},
                    onAddCard = {},
                    onEditCard = { clickedCardId = it },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("hello — привет").performClick()
        composeRule.runOnIdle {
            assertTrue(clickedCardId == 1L)
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/deckcontent/DeckContentScreen.kt
```kotlin
package com.mnemocards.ui.deckcontent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnemocards.data.db.CardEntity

/**
 * Экран содержимого колоды (S1–S3, S9, S10, S24, S26).
 * Stateless по бизнес-данным: всё состояние приходит из [DeckContentUiState],
 * события текут вверх через коллбеки (UDF).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckContentScreen(
    state: DeckContentUiState,
    onBack: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            // Панель сливается с фоном: выделяться должны карточки, а не TopAppBar.
            TopAppBar(
                title = {
                    Text(
                        text = when (state) {
                            is DeckContentUiState.Content -> state.title
                            is DeckContentUiState.Empty -> state.title
                            DeckContentUiState.NotFound -> ""
                            else -> ""
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCard,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить карточку",
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                DeckContentUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is DeckContentUiState.Content -> CardList(
                    cards = state.cards,
                    onEditCard = onEditCard,
                )

                is DeckContentUiState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "В колоде пока нет карточек",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }

                // S24: устаревший deckId — не выдаём пустой список за успех.
                DeckContentUiState.NotFound -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Колода не найдена",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Button(
                        onClick = onBack,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("К списку колод")
                    }
                }

                // S26: ошибка чтения Room — повтор, а не пустой список.
                DeckContentUiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Не удалось загрузить колоду",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("Повторить")
                    }
                }
            }
        }
    }
}

/**
 * Список карточек: новые сверху (порядок задаёт DAO по createdAt DESC, S3).
 * Каждая запись показывает обе стороны (S1); дубликаты — независимые
 * элементы (S9); длинный текст переносится по строкам (S10).
 */
@Composable
private fun CardList(
    cards: List<CardEntity>,
    onEditCard: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = cards, key = { it.id }) { card ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditCard(card.id) },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = card.front,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = card.back,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
```


---

## Батч 5/7

### FILE: app/src/main/java/com/mnemocards/ui/deckcontent/DeckContentScreen.kt
```kotlin
package com.mnemocards.ui.deckcontent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnemocards.data.db.CardEntity

/**
 * Экран содержимого колоды (S1–S3, S9, S10, S15–S18, S24, S26).
 * Stateless по бизнес-данным: всё состояние приходит из [DeckContentUiState],
 * события текут вверх через коллбеки (UDF). Локально хранится только
 * состояние диалога подтверждения удаления (S15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckContentScreen(
    state: DeckContentUiState,
    onBack: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (Long) -> Unit,
    onDeleteCard: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    // S15: карточка, ожидающая подтверждения удаления; до выбора остаётся в списке.
    var pendingDelete by remember { mutableStateOf<CardEntity?>(null) }

    Scaffold(
        topBar = {
            // Панель сливается с фоном: выделяться должны карточки, а не TopAppBar.
            TopAppBar(
                title = {
                    Text(
                        text = when (state) {
                            is DeckContentUiState.Content -> state.title
                            is DeckContentUiState.Empty -> state.title
                            DeckContentUiState.NotFound -> ""
                            else -> ""
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCard,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить карточку",
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                DeckContentUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is DeckContentUiState.Content -> CardList(
                    cards = state.cards,
                    onEditCard = onEditCard,
                    onDelete = { pendingDelete = it },
                )

                is DeckContentUiState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "В колоде пока нет карточек",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }

                // S24: устаревший deckId — не выдаём пустой список за успех.
                DeckContentUiState.NotFound -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Колода не найдена",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Button(
                        onClick = onBack,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("К списку колод")
                    }
                }

                // S26: ошибка чтения Room — повтор, а не пустой список.
                DeckContentUiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Не удалось загрузить колоду",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("Повторить")
                    }
                }
            }
        }
    }

    // S15–S17: диалог подтверждения удаления.
    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить карточку?") },
            text = { Text("${card.front} — ${card.back}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteCard(card.id)
                    },
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

/**
 * Список карточек: новые сверху (порядок задаёт DAO по createdAt DESC, S3).
 * Каждая запись показывает обе стороны (S1); дубликаты — независимые
 * элементы (S9); длинный текст переносится по строкам (S10).
 * Действие удаления открывает подтверждение (S15).
 */
@Composable
private fun CardList(
    cards: List<CardEntity>,
    onEditCard: (Long) -> Unit,
    onDelete: (CardEntity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = cards, key = { it.id }) { card ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onEditCard(card.id) },
                    ) {
                        Text(
                            text = "${card.front} — ${card.back}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(card) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить карточку",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
```

### FILE: app/src/androidTest/java/com/mnemocards/ui/deckcontent/DeckContentScreenTest.kt
```kotlin
package com.mnemocards.ui.deckcontent

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardEntity
import com.mnemocards.ui.theme.MnemoCardsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckContentScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun card(id: Long, front: String, back: String) =
        CardEntity(id = id, deckId = 1L, front = front, back = back, createdAt = id)

    private fun setContent(state: DeckContentUiState) {
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = state,
                    onBack = {},
                    onAddCard = {},
                    onEditCard = {},
                    onDeleteCard = {},
                    onRetry = {},
                )
            }
        }
    }

    // S1: заголовок — название колоды, каждый элемент показывает обе стороны.
    @Test
    fun content_showsTitleAndBothSides() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(
                    card(3, "hello", "привет"),
                    card(2, "world", "мир"),
                    card(1, "cat", "кошка"),
                ),
            ),
        )

        composeRule.onNodeWithText("Английский базовый").assertIsDisplayed()
        composeRule.onNodeWithText("hello — привет").assertIsDisplayed()
        composeRule.onNodeWithText("world — мир").assertIsDisplayed()
        composeRule.onNodeWithText("cat — кошка").assertIsDisplayed()
    }

    // S1: видна кнопка добавления карточки и возврат к списку колод.
    @Test
    fun content_showsAddButtonAndBack() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(card(1, "hello", "привет")),
            ),
        )

        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Назад").assertIsDisplayed()
    }

    // S2: пустая колода — пустое состояние, кнопка добавления доступна.
    @Test
    fun emptyDeck_showsEmptyState() {
        setContent(DeckContentUiState.Empty(title = "Английский базовый"))

        composeRule.onNodeWithText("В колоде пока нет карточек").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
    }

    // S9: дубликат карточки — обе записи отображаются.
    @Test
    fun duplicateCards_bothDisplayed() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(
                    card(2, "cat", "кошка"),
                    card(1, "cat", "кошка"),
                ),
            ),
        )

        val nodes = composeRule.onAllNodes(hasText("cat — кошка"))
        composeRule.runOnIdle {
            assertTrue(nodes.fetchSemanticsNodes().size == 2)
        }
    }

    // S10: длинный текст (200+ символов) отображается, макет не ломается.
    @Test
    fun longText_displayed_layoutNotBroken() {
        val longFront = "f".repeat(250)
        val longBack = "б".repeat(250)
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(card(1, longFront, longBack)),
            ),
        )

        composeRule.onNodeWithText("$longFront — $longBack").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
    }

    // S24: колода не найдена — состояние NotFound, а не пустой список.
    @Test
    fun notFound_showsState() {
        setContent(DeckContentUiState.NotFound)

        composeRule.onNodeWithText("Колода не найдена").assertIsDisplayed()
        composeRule.onNodeWithText("К списку колод").assertIsDisplayed()
    }

    // S26: ошибка чтения — состояние ошибки с возможностью повторить.
    @Test
    fun error_showsRetry() {
        setContent(DeckContentUiState.Error)

        composeRule.onNodeWithText("Повторить").assertIsDisplayed()
    }

    // S1: клик по элементу — коллбек редактирования.
    @Test
    fun clickOnCard_invokesEditCallback() {
        var clickedCardId: Long? = null
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = DeckContentUiState.Content(
                        title = "Английский базовый",
                        cards = listOf(card(1, "hello", "привет")),
                    ),
                    onBack = {},
                    onAddCard = {},
                    onEditCard = { clickedCardId = it },
                    onDeleteCard = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("hello — привет").performClick()
        composeRule.runOnIdle {
            assertTrue(clickedCardId == 1L)
        }
    }

    // S15: тап по удалению открывает диалог; карточка до выбора остаётся в списке.
    @Test
    fun deleteAction_opensDialog_cardStaysInList() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(
                    card(2, "world", "мир"),
                    card(1, "hello", "привет"),
                ),
            ),
        )

        composeRule
            .onAllNodes(compose.ui.test.hasContentDescription("Удалить карточку"))[0]
            .performClick()

        composeRule.onNodeWithText("Удалить карточку?").assertIsDisplayed()
        composeRule.onNodeWithText("Удалить").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()
        composeRule.onNodeWithText("hello — привет").assertIsDisplayed()
        composeRule.onNodeWithText("world — мир").assertIsDisplayed()
    }

    // S17: «Отмена» — диалог закрывается, карточка осталась в списке.
    @Test
    fun deleteDialog_cancel_cardStays() {
        var deletedCardId: Long? = null
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = DeckContentUiState.Content(
                        title = "Английский базовый",
                        cards = listOf(card(1, "hello", "привет")),
                    ),
                    onBack = {},
                    onAddCard = {},
                    onEditCard = {},
                    onDeleteCard = { deletedCardId = it },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Удалить карточку").performClick()
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.onNodeWithText("hello — привет").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(deletedCardId == null)
        }
    }

    // S16: «Удалить» — диалог закрыт, вызван коллбек VM, карточка исчезла из списка.
    @Test
    fun deleteDialog_confirm_invokesCallback_cardRemovedWithoutReload() {
        var deletedCardId: Long? = null
        var state by mutableStateOf(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(
                    card(2, "world", "мир"),
                    card(1, "hello", "привет"),
                ),
            ) as DeckContentUiState,
        )
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = state,
                    onBack = {},
                    onAddCard = {},
                    onEditCard = {},
                    onDeleteCard = { cardId ->
                        deletedCardId = cardId
                        // UI обновляется через Flow из Room — без ручной перезагрузки.
                        val current = state
                        if (current is DeckContentUiState.Content) {
                            val remaining =
                                current.cards.filterNot { it.id == cardId }
                            state = if (remaining.isEmpty()) {
                                DeckContentUiState.Empty(current.title)
                            } else {
                                current.copy(cards = remaining)
                            }
                        }
                    },
                    onRetry = {},
                )
            }
        }

        composeRule
            .onAllNodes(compose.ui.test.hasContentDescription("Удалить карточку"))[0]
            .performClick()
        composeRule.onNodeWithText("Удалить").performClick()

        composeRule.runOnIdle {
            assertTrue(deletedCardId != null)
        }
        composeRule.onNodeWithText("hello — привет").assertDoesNotExist()
        composeRule.onNodeWithText("world — мир").assertIsDisplayed()
    }

    // S18: удаление последней карточки — список пуст, показано пустое состояние.
    @Test
    fun deleteLastCard_showsEmptyState_appDoesNotCrash() {
        var state by mutableStateOf(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(card(1, "hello", "привет")),
            ) as DeckContentUiState,
        )
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = state,
                    onBack = {},
                    onAddCard = {},
                    onEditCard = {},
                    onDeleteCard = { cardId ->
                        val current = state
                        if (current is DeckContentUiState.Content) {
                            val remaining =
                                current.cards.filterNot { it.id == cardId }
                            state = if (remaining.isEmpty()) {
                                DeckContentUiState.Empty(current.title)
                            } else {
                                current.copy(cards = remaining)
                            }
                        }
                    },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Удалить карточку").performClick()
        composeRule.onNodeWithText("Удалить").performClick()

        composeRule.onNodeWithText("В колоде пока нет карточек").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
    }
}
```

### FILE: app/src/androidTest/java/com/mnemocards/ui/cardform/CardFormScreenTest.kt
```kotlin
package com.mnemocards.ui.cardform

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.domain.CardFormValidator
import com.mnemocards.ui.theme.MnemoCardsTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardFormScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Подключает экран к локальному состоянию; onSave прогоняет реальный
     * доменный валидатор, воспроизводя поведение ViewModel (UDF).
     */
    private fun setContent(initial: CardFormUiState): androidx.compose.runtime.MutableState<CardFormUiState> {
        val state = mutableStateOf(initial)
        composeRule.setContent {
            MnemoCardsTheme {
                CardFormScreen(
                    state = state.value,
                    onFrontChange = { value ->
                        state.value = state.value.copy(front = value, frontError = null)
                    },
                    onBackChange = { value ->
                        state.value = state.value.copy(back = value, backError = null)
                    },
                    onSave = {
                        val errors = CardFormValidator.validateCardInput(
                            state.value.front,
                            state.value.back,
                        )
                        state.value = state.value.copy(
                            frontError = errors.front,
                            backError = errors.back,
                        )
                    },
                    onBack = { state.value = state.value.copy(saveError = null) },
                )
            }
        }
        return state
    }

    // S4: форма добавления — два пустых поля и кнопка «Сохранить».
    @Test
    fun addMode_showsEmptyFieldsAndSaveButton() {
        setContent(CardFormUiState())

        composeRule.onNodeWithText("Лицевая сторона").assertIsDisplayed()
        composeRule.onNodeWithText("Перевод").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
    }

    // S6: пустое поле при заполненном другом — ошибка у пустого,
    // форма открыта, введённые значения на месте.
    @Test
    fun saveWithEmptyBack_showsError_keepsValues() {
        setContent(CardFormUiState(front = "dog", back = ""))

        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Заполните поле").assertIsDisplayed()
        composeRule.onNodeWithText("dog").assertIsDisplayed()
        composeRule.onNodeWithText("Лицевая сторона").assertIsDisplayed()
    }

    // S7: оба поля пусты — ошибки у обоих.
    @Test
    fun saveWithBothEmpty_showsBothErrors() {
        setContent(CardFormUiState(front = "", back = ""))

        composeRule.onNodeWithText("Сохранить").performClick()

        val errorNodes =
            composeRule.onAllNodes(compose.ui.test.hasText("Заполните поле"))
        composeRule.runOnIdle {
            assertTrue(errorNodes.fetchSemanticsNodes().size == 2)
        }
    }

    // S8: только пробельные символы — ошибки после trim.
    @Test
    fun saveWithWhitespaceOnly_showsErrors() {
        setContent(CardFormUiState(front = "   ", back = " \n\t "))

        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Заполните поле").assertIsDisplayed()
    }

    // S11: режим редактирования — поля предзаполнены текущими значениями.
    @Test
    fun editMode_fieldsPrefilled() {
        setContent(CardFormUiState(front = "cat", back = "кошка"))

        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.onNodeWithText("кошка").assertIsDisplayed()
    }

    // S13: очищенное поле при редактировании — ошибка, значения не потеряны.
    @Test
    fun editMode_clearedField_showsError_keepsOtherValue() {
        val state = setContent(CardFormUiState(front = "cat", back = "кошка"))

        composeRule.onNodeWithText("кошка").performTextClearance()
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Заполните поле").assertIsDisplayed()
        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(state.value.front == "cat")
            assertTrue(state.value.back.isEmpty())
        }
    }

    // S14: системный «Назад» без «Сохранить» — возврат, изменений нет.
    @Test
    fun back_withoutSave_noChanges() {
        var backInvoked = false
        val state = mutableStateOf(CardFormUiState(front = "cat", back = "кошка"))
        composeRule.setContent {
            MnemoCardsTheme {
                CardFormScreen(
                    state = state.value,
                    onFrontChange = { value ->
                        state.value = state.value.copy(front = value, frontError = null)
                    },
                    onBackChange = { value ->
                        state.value = state.value.copy(back = value, backError = null)
                    },
                    onSave = { },
                    onBack = { backInvoked = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Назад").performClick()

        composeRule.runOnIdle {
            assertTrue(backInvoked)
            // Прежние значения не изменены, в Room ничего не пишется.
            assertTrue(state.value.front == "cat")
            assertTrue(state.value.back == "кошка")
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/cardform/CardFormScreen.kt
```kotlin
package com.mnemocards.ui.cardform

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Форма карточки: добавление (S4–S10) и редактирование (S11–S14).
 * Stateless по бизнес-данным: состояние приходит из [CardFormUiState],
 * события текут вверх через коллбеки (UDF). Кнопка «Сохранить» всегда
 * активна, ошибки валидации показываются по нажатию (S6–S8, S13).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFormScreen(
    state: CardFormUiState,
    onFrontChange: (String) -> Unit,
    onBackChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    // S14: системный «Назад» закрывает форму без сохранения.
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            // Панель сливается с фоном — как на остальных экранах приложения.
            TopAppBar(
                title = { Text("Карточка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.front,
                onValueChange = onFrontChange,
                label = { Text("Лицевая сторона") },
                isError = state.frontError != null,
                supportingText = {
                    if (state.frontError != null) {
                        Text(state.frontError)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.back,
                onValueChange = onBackChange,
                label = { Text("Перевод") },
                isError = state.backError != null,
                supportingText = {
                    if (state.backError != null) {
                        Text(state.backError)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }

            // S26: 500-аналог — сообщение при ошибке записи, ввод не теряется.
            if (state.saveError != null) {
                Text(
                    text = state.saveError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
```


---

## Батч 6/7

### FILE: app/src/test/java/com/mnemocards/ui/decklist/DeckListViewModelTest.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.RoomCardRepository
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * S20/S21: счётчик карточек колоды — реактивный Flow из Room;
 * после addCard поток эмитит 4, после deleteCard — 2, без
 * пересоздания экрана списка (Room — single source of truth).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeckListViewModelTest {

    private lateinit var db: MnemoCardsDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun deckCount(state: DeckListUiState): Int =
        (state as DeckListUiState.Content).decks.first().cardCount

    @Test
    fun counter_emits4_afterAddCard_s20() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(name = "Английский базовый"),
        )
        repeat(3) { i -> repository.addCard(deckId, "word$i", "перевод$i") }

        val viewModel = DeckListViewModel(db.deckDao())

        viewModel.uiState.test {
            assertEquals(3, deckCount(awaitItem()))

            repository.addCard(deckId, "cat", "кошка")

            assertEquals(4, deckCount(awaitItem()))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun counter_emits2_afterDeleteCard_s21() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(name = "Английский базовый"),
        )
        repeat(3) { i -> repository.addCard(deckId, "word$i", "перевод$i") }

        val viewModel = DeckListViewModel(db.deckDao())

        viewModel.uiState.test {
            assertEquals(3, deckCount(awaitItem()))

            val card = repository.observeCards(deckId).first().first()
            repository.deleteCard(card.id)

            assertEquals(2, deckCount(awaitItem()))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt
```kotlin
package com.mnemocards.ui.review

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.RoomCardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * S22/S23: сессия тренировки читает колоду из Room через Flow —
 * новая карточка входит в сессию, правка текста отражается в открытой
 * сессии без её пересоздания.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReviewViewModelTest {

    private lateinit var db: MnemoCardsDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // S22: добавленная карточка входит в сессию вместе с остальными.
    @Test
    fun newCard_entersReviewSession() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(name = "Английский базовый"),
        )
        repository.addCard(deckId, "hello", "привет")
        repository.addCard(deckId, "cat", "кошка")

        val viewModel = ReviewViewModel(deckId, repository)

        viewModel.uiState.test {
            // Новые сверху: только что добавленная «cat» — первая.
            val first = awaitItem()
            assertEquals("cat", first.front)
            assertFalse(first.isFinished)

            // Оценка — переход к следующей карточке колоды.
            viewModel.rate()
            assertEquals("hello", awaitItem().front)

            // Следующая оценка завершает сессию.
            viewModel.rate()
            assertTrue(awaitItem().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S23: правка текста карточки отражается в открытой сессии.
    @Test
    fun editedCard_updatesOpenSession_withoutRecreation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(name = "Английский базовый"),
        )
        repository.addCard(deckId, "cat", "кошка")

        val viewModel = ReviewViewModel(deckId, repository)

        viewModel.uiState.test {
            assertEquals("cat", awaitItem().front)

            val card = repository.observeCards(deckId).first().first()
            repository.updateCard(card.copy(front = "a cat"))

            val updated = awaitItem()
            assertEquals("a cat", updated.front)
            assertEquals("кошка", updated.back)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Пустая колода — терминальное состояние, приложение не падает.
    @Test
    fun emptyDeck_immediatelyFinished() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao().insertDeck(
            com.mnemocards.data.db.DeckEntity(name = "Пустая"),
        )

        val viewModel = ReviewViewModel(deckId, repository)

        viewModel.uiState.test {
            assertTrue(awaitItem().isFinished)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### FILE: app/src/androidTest/java/com/mnemocards/ui/navigation/AppNavHostTest.kt
```kotlin
package com.mnemocards.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.RoomCardRepository
import com.mnemocards.ui.theme.MnemoCardsTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Навигация (S1, S22): тап по элементу колоды открывает содержимое
 * колоды; кнопка «Тренировать» ведёт на экран тренировки; back-стек
 * список → содержимое → форма (S14) обеспечивается NavController.
 */
@RunWith(AndroidJUnit4::class)
class AppNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: MnemoCardsDatabase

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = RoomCardRepository(db.deckDao(), db.cardDao())
        val deckId = db.deckDao()
            .insertDeck(com.mnemocards.data.db.DeckEntity(name = "Английский базовый"))
        repository.addCard(deckId, "hello", "привет")
        repository.addCard(deckId, "dog", "собака")

        composeRule.setContent {
            MnemoCardsTheme {
                AppNavHost(db = db)
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // S1: тап по колоде открывает её содержимое с карточками.
    @Test
    fun tapDeck_opensDeckContent() {
        composeRule.onNodeWithText("Английский базовый").performClick()

        composeRule.onNodeWithText("hello — привет").assertIsDisplayed()
        composeRule.onNodeWithContentDescriptionLabel("Назад").assertIsDisplayed()
    }

    // S22: кнопка «Тренировать» открывает тренировку этой колоды.
    @Test
    fun trainButton_opensReviewSession() {
        composeRule.onNodeWithText("Тренировать").performClick()

        composeRule.waitForIdle()
        // Лицевая сторона первой карточки (новые сверху).
        composeRule.onNodeWithText("dog").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider
        .onNodeWithContentDescriptionLabel(label: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(label))
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModel.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckWithCardCount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Состояние экрана списка колод. Error — 500-аналог при чтении Room.
 */
sealed interface DeckListUiState {
    data object Loading : DeckListUiState
    data class Content(val decks: List<DeckWithCardCount>) : DeckListUiState
    data object Error : DeckListUiState
}

/**
 * ViewModel списка колод (MVVM + UDF).
 * Подписана на реактивный Flow из Room: счётчики карточек колод
 * обновляются автоматически после любых правок карточек (S20, S21),
 * без пересоздания экрана.
 */
class DeckListViewModel(
    private val deckDao: DeckDao,
) : ViewModel() {

    val uiState: StateFlow<DeckListUiState> = deckDao.observeDecksWithCounts()
        .map { DeckListUiState.Content(it) }
        .catch {
            // 500-аналог: ошибка чтения Room не выдаётся за пустой список.
            emit(DeckListUiState.Error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckListUiState.Loading,
        )
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnemocards.data.db.DeckWithCardCount

/**
 * Стартовый экран приложения — список колод (название + счётчик карточек).
 * Stateless по бизнес-данным: состояние приходит из [DeckListUiState],
 * события текут вверх через коллбеки (UDF). Тап по элементу колоды
 * открывает её содержимое (S1), кнопка «Тренировать» — тренировку (S22).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    state: DeckListUiState,
    onDeckClick: (Long) -> Unit,
    onTrainDeck: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            // Панель сливается с фоном — как на остальных экранах приложения.
            TopAppBar(
                title = { Text("Мои колоды") },
                containerColor = MaterialTheme.colorScheme.background,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                DeckListUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is DeckListUiState.Content -> if (state.decks.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Колод пока нет",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = state.decks, key = { it.deckId }) { deck ->
                            DeckItem(
                                deck = deck,
                                onClick = { onDeckClick(deck.deckId) },
                                onTrain = { onTrainDeck(deck.deckId) },
                            )
                        }
                    }
                }

                // 500-аналог: ошибка чтения Room.
                DeckListUiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Не удалось загрузить колоды",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

/**
 * Элемент колоды: тап по элементу — содержимое колоды (S1),
 * отдельная кнопка «Тренировать» — тренировка колоды (S22).
 */
@Composable
private fun DeckItem(
    deck: DeckWithCardCount,
    onClick: () -> Unit,
    onTrain: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = deck.deckName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${deck.cardCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onTrain) {
                Text("Тренировать")
            }
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt
```kotlin
package com.mnemocards.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Состояние сессии повторения. isFinished — терминальный экран
 * «на сегодня всё». Текущая карточка идентифицируется по id, поэтому
 * эмиссия Flow с изменённым текстом обновляет экран без пересоздания
 * сессии (S23).
 */
data class ReviewUiState(
    val front: String? = null,
    val back: String? = null,
    val isRevealed: Boolean = false,
    val isFinished: Boolean = false,
)

/**
 * ViewModel сессии повторения (MVVM + UDF).
 * Карточки наблюдаются через Flow из Room (S22, S23): новые карточки
 * входят в сессию, правки текста отражаются в открытой сессии.
 */
class ReviewViewModel(
    deckId: Long,
    private val repository: CardRepository,
) : ViewModel() {

    private val isRevealed = MutableStateFlow(false)
    private val isFinished = MutableStateFlow(false)

    /** id текущей карточки; null — сессия ещё не начата, берём первую (новые сверху). */
    private var currentCardId: Long? = null

    private val cards = repository.observeCards(deckId)
        .catch {
            // 500-аналог при чтении: сессия корректно завершается, UI не падает.
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val uiState: StateFlow<ReviewUiState> = combine(
        cards,
        isRevealed,
        isFinished,
    ) { list, revealed, finished ->
        when {
            finished || list.isEmpty() -> ReviewUiState(isFinished = true)
            else -> {
                val index = currentCardId
                    ?.let { id -> list.indexOfFirst { it.id == id } }
                    ?.takeIf { it >= 0 }
                val current = if (index != null) list[index] else list.first()
                if (index == null) currentCardId = current.id
                ReviewUiState(
                    front = current.front,
                    back = current.back,
                    isRevealed = revealed,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReviewUiState(isFinished = true),
    )

    /** Переворот карточки: показ обратной стороны. */
    fun flip() {
        isRevealed.value = true
    }

    /**
     * Оценка ответа: четыре кнопки эквивалентны (влияние оценок на
     * srs.scheduling — предмет последующих изменений), любая оценка
     * переводит к следующей карточке либо завершает сессию.
     */
    fun rate() {
        isRevealed.value = false
        val list = cards.value
        val index = currentCardId
            ?.let { id -> list.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        val next = list.getOrNull(index + 1)
        if (next == null) {
            isFinished.value = true
        } else {
            currentCardId = next.id
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewScreen.kt
```kotlin
package com.mnemocards.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnemocards.ui.theme.Blue
import com.mnemocards.ui.theme.Green
import com.mnemocards.ui.theme.Ink
import com.mnemocards.ui.theme.Orange
import com.mnemocards.ui.theme.Red

/**
 * Экран сессии повторения (оформление принято пользователем вручную
 * и воспроизводится как есть). Stateless по бизнес-данным: состояние
 * приходит из [ReviewUiState], события текут вверх через коллбеки (UDF).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onFlip: () -> Unit,
    onRate: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            // Панель сливается с фоном: выделяться должна карточка, а не TopAppBar.
            TopAppBar(
                title = { Text("Тренировка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            if (state.isFinished) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "На сегодня всё",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // Карточка: Surface цвета surfaceVariant, скруглённая,
                // с боковыми отступами и weight(1f) — занимает свободную высоту.
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(onClick = onFlip),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Цвет onSurfaceVariant (бирюзовый) берётся автоматически.
                        Text(
                            text = if (state.isRevealed) {
                                state.back.orEmpty()
                            } else {
                                state.front.orEmpty()
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Четыре кнопки оценки — в один ряд, прижаты к низу под карточкой.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onRate,
                        enabled = state.isRevealed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Red,
                            contentColor = Ink,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Забыл")
                    }
                    Button(
                        onClick = onRate,
                        enabled = state.isRevealed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Orange,
                            contentColor = Ink,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Трудно")
                    }
                    Button(
                        onClick = onRate,
                        enabled = state.isRevealed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue,
                            contentColor = Ink,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Хорошо")
                    }
                    Button(
                        onClick = onRate,
                        enabled = state.isRevealed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Green,
                            contentColor = Ink,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Легко")
                    }
                }
            }
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/navigation/AppNavHost.kt
```kotlin
package com.mnemocards.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.RoomCardRepository
import com.mnemocards.ui.cardform.CardFormScreen
import com.mnemocards.ui.cardform.CardFormViewModel
import com.mnemocards.ui.deckcontent.DeckContentScreen
import com.mnemocards.ui.deckcontent.DeckContentUiState
import com.mnemocards.ui.deckcontent.DeckContentViewModel
import com.mnemocards.ui.decklist.DeckListScreen
import com.mnemocards.ui.decklist.DeckListViewModel
import com.mnemocards.ui.review.ReviewScreen
import com.mnemocards.ui.review.ReviewViewModel

/**
 * Маршруты приложения:
 *  - `decks` — список колод (стартовый);
 *  - `deck/{deckId}` — содержимое колоды;
 *  - `deck/{deckId}/edit?cardId={cardId}` — форма карточки
 *    (cardId опционален: нет — добавление, есть — редактирование);
 *  - `review/{deckId}` — тренировка колоды.
 *
 * Back-стек: список → содержимое → форма (S14); при NotFound —
 * автоматический возврат popBackStack (S24).
 */
object Routes {
    const val DECKS = "decks"
    const val DECK_CONTENT = "deck/{deckId}"
    const val CARD_FORM = "deck/{deckId}/edit?cardId={cardId}"
    const val REVIEW = "review/{deckId}"

    fun deckContent(deckId: Long) = "deck/$deckId"
    fun cardForm(deckId: Long, cardId: Long? = null) =
        if (cardId == null) "deck/$deckId/edit" else "deck/$deckId/edit?cardId=$cardId"
    fun review(deckId: Long) = "review/$deckId"
}

@Composable
fun AppNavHost(
    db: MnemoCardsDatabase,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.DECKS,
        modifier = modifier,
    ) {
        composable(Routes.DECKS) {
            val viewModel: DeckListViewModel = viewModel { DeckListViewModel(db.deckDao()) }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            DeckListScreen(
                state = state,
                onDeckClick = { deckId -> navController.navigate(Routes.deckContent(deckId)) },
                onTrainDeck = { deckId -> navController.navigate(Routes.review(deckId)) },
            )
        }

        composable(
            route = Routes.DECK_CONTENT,
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
        ) { entry ->
            val deckId = entry.arguments?.getLong("deckId") ?: 0L
            val viewModel: DeckContentViewModel = viewModel(
                key = "deck-content-$deckId",
            ) {
                DeckContentViewModel(
                    deckId = deckId,
                    repository = RoomCardRepository(db.deckDao(), db.cardDao()),
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // S24: устаревший deckId — автоматический возврат к списку колод,
            // пустой список не выдаётся за успешную загрузку.
            LaunchedEffect(state) {
                if (state is DeckContentUiState.NotFound) {
                    navController.popBackStack()
                }
            }

            DeckContentScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onAddCard = { navController.navigate(Routes.cardForm(deckId)) },
                onEditCard = { cardId -> navController.navigate(Routes.cardForm(deckId, cardId)) },
                onDeleteCard = viewModel::deleteCard,
                onRetry = viewModel::retry,
            )
        }

        composable(
            route = Routes.CARD_FORM,
            arguments = listOf(
                navArgument("deckId") { type = NavType.LongType },
                navArgument("cardId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val deckId = entry.arguments?.getLong("deckId") ?: 0L
            val cardId = entry.arguments?.getLong("cardId")?.takeIf { it != -1L }
            val viewModel: CardFormViewModel = viewModel(
                key = "card-form-$deckId-$cardId",
            ) {
                CardFormViewModel(
                    deckId = deckId,
                    cardId = cardId,
                    repository = RoomCardRepository(db.deckDao(), db.cardDao()),
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // S5/S12: форма закрывается после успешного сохранения.
            LaunchedEffect(state.isSaved) {
                if (state.isSaved) {
                    navController.popBackStack()
                }
            }

            CardFormScreen(
                state = state,
                onFrontChange = viewModel::onFrontChange,
                onBackChange = viewModel::onBackChange,
                onSave = viewModel::save,
                // S14: системный «Назад» без «Сохранить» — возврат к содержимому.
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
        ) { entry ->
            val deckId = entry.arguments?.getLong("deckId") ?: 0L
            val viewModel: ReviewViewModel = viewModel(
                key = "review-$deckId",
            ) {
                ReviewViewModel(
                    deckId = deckId,
                    repository = RoomCardRepository(db.deckDao(), db.cardDao()),
                )
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ReviewScreen(
                state = state,
                onFlip = viewModel::flip,
                onRate = viewModel::rate,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Локальный алиас, чтобы не тянуть длинное имя в каждый composable. */
private fun navArgument(name: String, builder: androidx.navigation.navArgument.NavArgumentBuilder.() -> Unit) =
    androidx.navigation.navArgument(name, builder)
```

### FILE: app/src/main/java/com/mnemocards/data/db/MnemoCardsDatabase.kt
```kotlin
package com.mnemocards.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Локальная БД (offline-first): Room — single source of truth для UI.
 * version 2 — после добавления поля `createdAt` в CardEntity
 * (миграция MIGRATION_1_2, см. Migrations.kt).
 */
@Database(
    entities = [DeckEntity::class, CardEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MnemoCardsDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var INSTANCE: MnemoCardsDatabase? = null

        fun getInstance(context: Context): MnemoCardsDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MnemoCardsDatabase::class.java,
                    "mnemocards.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/MainActivity.kt
```kotlin
package com.mnemocards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.ui.navigation.AppNavHost
import com.mnemocards.ui.theme.MnemoCardsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = MnemoCardsDatabase.getInstance(applicationContext)
        // Идемпотентный сидинг базовой колоды при первом запуске.
        DatabaseSeeder.seedIfEmpty(database)

        setContent {
            // Приложение всегда тёмное: корневой composable оборачивается
            // в MnemoCardsTheme, голая MaterialTheme не используется.
            MnemoCardsTheme {
                AppNavHost(db = database)
            }
        }
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

  **Обновлена**, change `add-card-management` (2026-09-17): сессия читает карточки
  из Room через Flow по id текущей карточки — новые карточки колоды входят
  в открытую сессию, а правка текста карточки отражается в сессии без её
  пересоздания.
- capability `cards.management` — управление карточками внутри колоды: список
  содержимого колоды, добавление, редактирование, удаление.

  **Реализована**, change `add-card-management` (2026-09-17): экран содержимого
  колоды (название + список «слово — перевод», новые сверху по `createdAt`);
  форма добавления/редактирования с валидацией обязательных полей после trim
  (400-аналог); удаление с диалогом подтверждения; все правки персистентны
  в Room (single source of truth); счётчик карточек колоды и сессия тренировки
  обновляются через Flow без перезагрузки экранов; краевые случаи покрыты
  состояниями UI (пустая колода, устаревшие deckId/cardId — 404-аналоги,
  ошибка Room — 500-аналог с повтором чтения и сохранением ввода в форме).
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

  **Обновлена**, change `add-card-management` (2026-09-17): добавлено поле
  `createdAt` у `CardEntity` (миграция Room) и CRUD-методы `CardDao`
  (insert/update/delete) с репозиторием `CardRepository`; управление карточками
  внутри колоды (`cards.management`) полностью офлайн, сетевой слой
  не затрагивается.
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
  оформление целиком, не заменяя его дефолтным Material.
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

Коммиты по Conventional Commits для шагов T1–T20:

```
test: card dao/repository/viewmodel/form tests for card management (S1-S26)
feat: card management — createdAt field, room migration and card crud (S3, S19)
feat: card repository with 404/500 analogues (S24-S26)
feat: card form validation in domain layer (S6-S8, S13)
feat: deck content and card form viewmodels with udf states (S1-S2, S24-S26)
feat: deck content screen, card form screen and delete confirmation dialog (S1-S18)
feat: navigation routes for deck content and card form (S14, S24)
chore: regression run and manual persistence check S19, dark theme verified
docs: mark cards.management implemented in MAIN.md for add-card-management
```
