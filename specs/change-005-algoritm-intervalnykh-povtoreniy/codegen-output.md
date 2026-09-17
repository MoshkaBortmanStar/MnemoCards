## Батч 1/7

### FILE: app/src/test/java/com/mnemocards/domain/scheduler/Sm2SchedulerTest.kt
```kotlin
package com.mnemocards.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Параметризованный тест SM-2 планировщика по спеке add-sm2-scheduling (S1–S12, S15, S23).
 */
class Sm2SchedulerTest {

    private val now = 1_700_000_000_000L // фиксированный «сейчас»

    private fun newState(
        ef: Double = 2.5,
        repetition: Int = 0,
        intervalDays: Int = 0,
        dueDate: Long = now,
    ) = SchedulingState(
        easinessFactor = ef,
        repetitionNumber = repetition,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )

    private val newCard get() = newState()
    private val repeatingCard get() = newState(ef = 2.5, repetition = 2, intervalDays = 6)

    private fun Duration.inDays(): Int = inWholeDays.toInt()

    // --- S1–S4: новая карточка (EF=2.5, №0, интервал 0) ---

    @Test
    fun `S1 new card Easy`() {
        val next = Sm2Scheduler.next(newCard, ReviewGrade.EASY, now)
        assertEquals(2.6, next.easinessFactor, 1e-9)
        assertEquals(1, next.repetitionNumber)
        assertEquals(4, next.intervalDays)
        assertEquals(now + 4.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S2 new card Good`() {
        val next = Sm2Scheduler.next(newCard, ReviewGrade.GOOD, now)
        assertEquals(2.5, next.easinessFactor, 1e-9)
        assertEquals(1, next.repetitionNumber)
        assertEquals(1, next.intervalDays)
        assertEquals(now + 1.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S3 new card Hard`() {
        val next = Sm2Scheduler.next(newCard, ReviewGrade.HARD, now)
        assertEquals(2.35, next.easinessFactor, 1e-9)
        assertEquals(1, next.repetitionNumber)
        assertEquals(1, next.intervalDays)
        assertEquals(now + 1.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S4 new card Forgot`() {
        val next = Sm2Scheduler.next(newCard, ReviewGrade.FORGOT, now)
        assertEquals(2.3, next.easinessFactor, 1e-9)
        assertEquals(0, next.repetitionNumber)
        assertEquals(0, next.intervalDays)
        assertEquals(now + 10.minutes.inWholeMilliseconds, next.dueDate)
    }

    // --- S5–S8: повторяемая карточка (EF=2.5, №2, интервал 6) ---

    @Test
    fun `S5 repeating card Good`() {
        val next = Sm2Scheduler.next(repeatingCard, ReviewGrade.GOOD, now)
        assertEquals(2.5, next.easinessFactor, 1e-9)
        assertEquals(3, next.repetitionNumber)
        assertEquals(15, next.intervalDays) // round(6 × 2.5)
        assertEquals(now + 15.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S6 repeating card Easy`() {
        val next = Sm2Scheduler.next(repeatingCard, ReviewGrade.EASY, now)
        assertEquals(2.6, next.easinessFactor, 1e-9)
        assertEquals(3, next.repetitionNumber)
        assertEquals(20, next.intervalDays) // round(6 × 2.6 × 1.3)
        assertEquals(now + 20.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S7 repeating card Hard`() {
        val next = Sm2Scheduler.next(repeatingCard, ReviewGrade.HARD, now)
        assertEquals(2.35, next.easinessFactor, 1e-9)
        assertEquals(3, next.repetitionNumber)
        assertEquals(7, next.intervalDays) // max(1, round(6 × 1.2))
        assertEquals(now + 7.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S8 repeating card Forgot`() {
        val next = Sm2Scheduler.next(repeatingCard, ReviewGrade.FORGOT, now)
        assertEquals(2.3, next.easinessFactor, 1e-9)
        assertEquals(0, next.repetitionNumber)
        assertEquals(0, next.intervalDays)
        assertEquals(now + 10.minutes.inWholeMilliseconds, next.dueDate)
    }

    // --- S9, S10: клампы EF ---

    @Test
    fun `S9 EF clamp lower bound on Forgot`() {
        val next = Sm2Scheduler.next(newState(ef = 1.3), ReviewGrade.FORGOT, now)
        assertEquals(1.3, next.easinessFactor, 1e-9)
        assertEquals(0, next.repetitionNumber)
        assertEquals(0, next.intervalDays)
        assertEquals(now + 10.minutes.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S10 EF clamp upper bound on Easy`() {
        val state = newState(ef = 2.75, repetition = 2, intervalDays = 6)
        val next = Sm2Scheduler.next(state, ReviewGrade.EASY, now)
        assertEquals(2.8, next.easinessFactor, 1e-9)
        assertEquals(3, next.repetitionNumber)
        assertEquals(22, next.intervalDays) // round(6 × 2.8 × 1.3)
        assertEquals(now + 22.days.inWholeMilliseconds, next.dueDate)
    }

    // --- S11: нормализация повреждённого EF до расчёта ---

    @Test
    fun `S11 corrupted EF is normalized then Easy applied`() {
        val state = newState(ef = 0.9)
        val next = Sm2Scheduler.next(state, ReviewGrade.EASY, now)
        assertEquals(1.4, next.easinessFactor, 1e-9) // 1.3 + 0.10
        assertEquals(1, next.repetitionNumber)
        assertEquals(4, next.intervalDays)
        assertEquals(now + 4.days.inWholeMilliseconds, next.dueDate)
    }

    // --- S12: минимальный интервал graduating-оценок ---

    @Test
    fun `S12 minimum interval of graduating grades is 1 day`() {
        val state = newState(ef = 1.3, repetition = 2, intervalDays = 1)
        val next = Sm2Scheduler.next(state, ReviewGrade.GOOD, now)
        assertEquals(1, next.intervalDays) // round(1 × 1.3) = 1, не 0
        assertEquals(3, next.repetitionNumber)
    }

    // --- S23: карточка №1 + «Хорошо» → фиксированные 6 дней ---

    @Test
    fun `S23 repetition 1 Good gives fixed 6 days`() {
        val state = newState(ef = 2.5, repetition = 1, intervalDays = 1)
        val next = Sm2Scheduler.next(state, ReviewGrade.GOOD, now)
        assertEquals(6, next.intervalDays)
        assertEquals(2, next.repetitionNumber)
        assertEquals(now + 6.days.inWholeMilliseconds, next.dueDate)
    }

    // --- S15: превью совпадает с применяемым результатом ---

    @Test
    fun `S15 preview matches applied result for all grades`() {
        listOf(newCard, repeatingCard, newState(ef = 1.3), newState(ef = 2.75, repetition = 2, intervalDays = 6))
            .forEach { state ->
                val previews = Sm2Scheduler.preview(state, now)
                assertEquals(4, previews.size)
                ReviewGrade.entries.forEach { grade ->
                    val applied = Sm2Scheduler.next(state, grade, now)
                    val expectedDuration = when (grade) {
                        ReviewGrade.FORGOT -> 10.minutes
                        else -> applied.intervalDays.days
                    }
                    assertEquals(
                        "grade=$grade ef=${state.easinessFactor} rep=${state.repetitionNumber}",
                        expectedDuration,
                        previews.getValue(grade),
                    )
                }
            }
    }

    // --- S14 (числа): превью повторяемой карточки ---

    @Test
    fun `S14 preview values for repeating card`() {
        val previews = Sm2Scheduler.preview(repeatingCard, now)
        assertEquals(10.minutes, previews.getValue(ReviewGrade.FORGOT))
        assertEquals(7.days, previews.getValue(ReviewGrade.HARD))
        assertEquals(15.days, previews.getValue(ReviewGrade.GOOD))
        assertEquals(20.days, previews.getValue(ReviewGrade.EASY))
    }
}
```

### FILE: app/src/main/java/com/mnemocards/domain/scheduler/ReviewGrade.kt
```kotlin
package com.mnemocards.domain.scheduler

/**
 * Четыре эквивалентные оценки ответа: «Забыл» / «Трудно» / «Хорошо» / «Легко».
 */
enum class ReviewGrade {
    FORGOT,
    HARD,
    GOOD,
    EASY,
}
```

### FILE: app/src/main/java/com/mnemocards/domain/scheduler/SchedulingState.kt
```kotlin
package com.mnemocards.domain.scheduler

/**
 * Состояние планирования карточки по SM-2.
 *
 * @param easinessFactor коэффициент лёгкости, всегда в диапазоне [1.3, 2.8].
 * @param repetitionNumber номер успешного повторения (0 — новая карточка).
 * @param intervalDays интервал до следующего показа в целых днях
 *   (0 — только у оценки «Забыл»; 10-минутный сдвиг живёт в [dueDate]).
 * @param dueDate момент следующего показа, epochMillis.
 */
data class SchedulingState(
    val easinessFactor: Double,
    val repetitionNumber: Int,
    val intervalDays: Int,
    val dueDate: Long,
) {
    companion object {
        const val MIN_EF = 1.3
        const val MAX_EF = 2.8
        const val DEFAULT_EF = 2.5

        /** Параметры новой карточки: EF=2.5, №0, интервал 0, due=«сейчас». */
        fun fresh(now: Long): SchedulingState = SchedulingState(
            easinessFactor = DEFAULT_EF,
            repetitionNumber = 0,
            intervalDays = 0,
            dueDate = now,
        )
    }
}
```

### FILE: app/src/main/java/com/mnemocards/domain/scheduler/Sm2Scheduler.kt
```kotlin
package com.mnemocards.domain.scheduler

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * SM-2 планировщик (change add-sm2-scheduling).
 *
 * Единая функция расчёта используется и для подписей на кнопках ([preview]),
 * и для применения оценки ([next]) — расхождение подписи и результата исключено.
 */
object Sm2Scheduler {

    /** Сдвиг для «Забыл»: карточка возвращается через 10 минут. */
    private val FORGOT_DELAY = 10.minutes

    /**
     * Применяет оценку к состоянию карточки.
     *
     * @param now текущий момент, epochMillis — база для нового dueDate.
     */
    fun next(state: SchedulingState, grade: ReviewGrade, now: Long): SchedulingState {
        // Повреждённый EF (ниже минимума) нормализуется к границе до расчёта.
        val ef = state.easinessFactor.coerceIn(SchedulingState.MIN_EF, SchedulingState.MAX_EF)

        return when (grade) {
            ReviewGrade.FORGOT -> {
                val nextEf = max(SchedulingState.MIN_EF, ef - 0.20)
                // Прогресс сброшен; 10-минутный сдвиг живёт только в dueDate.
                SchedulingState(
                    easinessFactor = nextEf,
                    repetitionNumber = 0,
                    intervalDays = 0,
                    dueDate = now + FORGOT_DELAY.inWholeMilliseconds,
                )
            }

            ReviewGrade.HARD -> {
                val nextEf = max(SchedulingState.MIN_EF, ef - 0.15)
                val interval = if (state.repetitionNumber == 0) {
                    1
                } else {
                    max(1, (state.intervalDays * 1.2).roundToInt())
                }
                SchedulingState(
                    easinessFactor = nextEf,
                    repetitionNumber = state.repetitionNumber + 1,
                    intervalDays = interval,
                    dueDate = now + interval.days.inWholeMilliseconds,
                )
            }

            ReviewGrade.GOOD -> {
                val interval = when (state.repetitionNumber) {
                    0 -> 1
                    1 -> 6
                    else -> max(1, (state.intervalDays * ef).roundToInt())
                }
                SchedulingState(
                    easinessFactor = ef,
                    repetitionNumber = state.repetitionNumber + 1,
                    intervalDays = interval,
                    dueDate = now + interval.days.inWholeMilliseconds,
                )
            }

            ReviewGrade.EASY -> {
                val nextEf = min(SchedulingState.MAX_EF, ef + 0.10)
                val interval = if (state.repetitionNumber == 0) {
                    4
                } else {
                    max(1, (state.intervalDays * nextEf * 1.3).roundToInt())
                }
                SchedulingState(
                    easinessFactor = nextEf,
                    repetitionNumber = state.repetitionNumber + 1,
                    intervalDays = interval,
                    dueDate = now + interval.days.inWholeMilliseconds,
                )
            }
        }
    }

    /**
     * Превью интервалов для четырёх кнопок оценки. Использует тот же расчёт,
     * что и [next]: интервал каждой кнопки — это интервал, который будет применён.
     */
    fun preview(state: SchedulingState, now: Long): Map<ReviewGrade, Duration> =
        ReviewGrade.entries.associateWith { grade ->
            val applied = next(state, grade, now)
            if (grade == ReviewGrade.FORGOT) FORGOT_DELAY else applied.intervalDays.days
        }
}
```

### FILE: app/src/test/java/com/mnemocards/ui/review/IntervalLabelTest.kt
```kotlin
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
```


---

## Батч 2/7

### FILE: app/src/main/java/com/mnemocards/ui/review/IntervalLabel.kt
```kotlin
package com.mnemocards.ui.review

import kotlin.time.Duration

/**
 * Форматирование интервала повторения для подписи на кнопке оценки (S13).
 *
 * Дни склоняются по правилам русской плюрализации (день / дня / дней),
 * минуты сокращаются до «N мин».
 */
fun formatInterval(duration: Duration): String {
    val days = duration.inWholeDays
    return if (days >= 1) {
        "$days ${pluralizeDays(days)}"
    } else {
        "${duration.inWholeMinutes} мин"
    }
}

private fun pluralizeDays(n: Long): String = when {
    n % 10 == 1L && n % 100 != 11L -> "день"
    n % 10 in 2L..4L && n % 100 !in 12L..14L -> "дня"
    else -> "дней"
}
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
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

### FILE: app/schemas/com.mnemocards.data.MnemoCardsDatabase/1.json
```json
{
  "formatVersion": 1,
  "database": {
    "version": 1,
    "identityHash": "a1b2c3d4e5f60718293a4b5c6d7e8f90",
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
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deckId` INTEGER NOT NULL, `front` TEXT NOT NULL, `back` TEXT NOT NULL, FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
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
            ]
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
    "setupQueries": [
      "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
      "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a1b2c3d4e5f60718293a4b5c6d7e8f90')"
    ]
  }
}
```

### FILE: app/src/androidTest/java/com/mnemocards/data/Migration1to2Test.kt
```kotlin
package com.mnemocards.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S28: миграция Room 1→2 без потери данных — добавление полей планирования
 * со значениями по умолчанию; деструктивная миграция не используется.
 */
@RunWith(AndroidJUnit4::class)
class Migration1to2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MnemoCardsDatabase::class.java,
    )

    @Test
    fun `S28 migration preserves data and assigns scheduling defaults`() {
        // БД предыдущей версии схемы: 1 колода, 5 карточек, полей планирования нет.
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO decks (name) VALUES ('Базовая')")
            val cards = listOf(
                "cat" to "кошка",
                "dog" to "собака",
                "house" to "дом",
                "water" to "вода",
                "book" to "книга",
            )
            cards.forEach { (front, back) ->
                execSQL("INSERT INTO cards (deckId, front, back) VALUES (1, '$front', '$back')")
            }
            close()
        }

        val beforeMigration = System.currentTimeMillis()
        // Деструктивная миграция не используется: применяем MIGRATION_1_2.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)
        val afterMigration = System.currentTimeMillis()

        db.query("SELECT id, front, back, easinessFactor, repetitionNumber, intervalDays, dueDate FROM cards").use { cursor ->
            var count = 0
            val seenFronts = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                count++
                seenFronts.add(cursor.getString(1))
                assertEquals(2.5, cursor.getDouble(3), 1e-9)
                assertEquals(0, cursor.getInt(4))
                assertEquals(0, cursor.getInt(5))
                // dueDate = момент миграции: «сейчас», карточка сразу готова к показу.
                val due = cursor.getLong(6)
                assertTrue("dueDate должно быть не раньше начала миграции", due >= beforeMigration)
                assertTrue("dueDate должно быть не позже конца миграции", due <= afterMigration)
            }
            assertEquals(5, count)
            assertEquals(setOf("cat", "dog", "house", "water", "book"), seenFronts)
        }
    }

    private companion object {
        const val TEST_DB = "migration-1to2-test.db"
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/CardEntity.kt
```kotlin
package com.mnemocards.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Карточка — пара «слово + перевод» с параметрами планирования SM-2
 * (change add-sm2-scheduling). Поля планирования добавлены в версии схемы 2
 * миграцией [Migrations.MIGRATION_1_2] со значениями по умолчанию.
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
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val deckId: Long,
    val front: String,
    val back: String,
    @ColumnInfo(defaultValue = "2.5")
    val easinessFactor: Double = 2.5,
    @ColumnInfo(defaultValue = "0")
    val repetitionNumber: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val intervalDays: Int = 0,
    /** Момент следующего показа, epochMillis. Дефолт проставляется при вставке. */
    @ColumnInfo(defaultValue = "0")
    val dueDate: Long = 0L,
)
```

### FILE: app/src/main/java/com/mnemocards/data/MnemoCardsDatabase.kt
```kotlin
package com.mnemocards.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.DeckDao

/**
 * Локальная БД — single source of truth для UI (offline-first).
 *
 * Версия 2: у карточек появились поля планирования SM-2
 * (easinessFactor, repetitionNumber, intervalDays, dueDate) —
 * миграция [Migrations.MIGRATION_1_2].
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

### FILE: app/src/main/java/com/mnemocards/data/Migrations.kt
```kotlin
package com.mnemocards.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции Room. Деструктивная миграция (fallbackToDestructiveMigration)
 * не используется — данные пользователя сохраняются.
 */
object Migrations {

    /**
     * 1 → 2: добавление полей планирования SM-2 в таблицу cards.
     *
     * Значения по умолчанию соответствуют новой карточке: EF=2.5, №0,
     * интервал 0, dueDate=момент миграции — все существующие карточки
     * становятся готовыми к повторению сегодня (S28).
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.execSQL("ALTER TABLE cards ADD COLUMN easinessFactor REAL NOT NULL DEFAULT 2.5")
            db.execSQL("ALTER TABLE cards ADD COLUMN repetitionNumber INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE cards ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE cards ADD COLUMN dueDate INTEGER NOT NULL DEFAULT $now")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
```


---

## Батч 3/7

### FILE: app/src/androidTest/java/com/mnemocards/data/DatabaseSeederTest.kt
```kotlin
package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.DeckDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S24, S29: идемпотентный сидинг базовой колоды с параметрами планирования
 * по умолчанию — EF=2.5, №0, интервал 0, dueDate=«сейчас» (все 5 карточек
 * готовы к повторению сегодня); повторный запуск не дублирует данные.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSeederTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        cardDao = db.cardDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `S29 first run seeds 5 cards with scheduling defaults`() = runBlocking {
        val before = System.currentTimeMillis()
        DatabaseSeeder.seedIfEmpty(deckDao, cardDao)
        val after = System.currentTimeMillis()

        val cards = cardDao.getCardsByDeck(deckId = 1L)
        assertEquals(5, cards.size)
        cards.forEach { card ->
            assertEquals(2.5, card.easinessFactor, 1e-9)
            assertEquals(0, card.repetitionNumber)
            assertEquals(0, card.intervalDays)
            // dueDate = «сейчас»: новая карточка готова к показу немедленно (S24).
            assertTrue("dueDate должно быть в окне сидинга", card.dueDate in before..after)
        }
    }

    @Test
    fun `S29 second run does not duplicate seeded data`() = runBlocking {
        DatabaseSeeder.seedIfEmpty(deckDao, cardDao)
        DatabaseSeeder.seedIfEmpty(deckDao, cardDao)

        val cards = cardDao.getCardsByDeck(deckId = 1L)
        assertEquals(5, cards.size)
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/DatabaseSeeder.kt
```
package com.mnemocards.data

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.DeckDao

/**
 * Идемпотентный сидинг базовой колоды при первом запуске.
 *
 * Файл перенесён из `data/db/DatabaseSeeder.kt` (change add-sm2-scheduling):
 * сущности и сидер живут в пакете `data`, DAO — в `data.db`.
 *
 * При вставке карточкам проставляются дефолты планирования SM-2
 * (EF=2.5, №0, интервал 0, dueDate=«сейчас») — новая карточка без истории
 * сразу попадает в отбор по сроку (S24, S29).
 */
object DatabaseSeeder {

    const val BASE_DECK_NAME = "Базовая"

    /** Стартовый набор из 5 карточек «слово + перевод». */
    private val BASE_CARDS = listOf(
        "cat" to "кошка",
        "dog" to "собака",
        "house" to "дом",
        "water" to "вода",
        "book" to "книга",
    )

    /**
     * Сеет базовую колоду, если в БД ещё нет колод. Повторный вызов
     * ничего не меняет (идемпотентность).
     *
     * @param now текущий момент, epochMillis — dueDate новых карточек.
     */
    suspend fun seedIfEmpty(
        deckDao: DeckDao,
        cardDao: CardDao,
        now: Long = System.currentTimeMillis(),
    ) {
        if (deckDao.countDecks() > 0) return

        val deckId = deckDao.insertDeck(DeckEntity(name = BASE_DECK_NAME))
        cardDao.insertCards(
            BASE_CARDS.map { (front, back) ->
                CardEntity(
                    deckId = deckId,
                    front = front,
                    back = back,
                    easinessFactor = SchedulingState.DEFAULT_EF,
                    repetitionNumber = 0,
                    intervalDays = 0,
                    dueDate = now,
                )
            },
        )
    }
}
```

### FILE: app/src/androidTest/java/com/mnemocards/data/CardDaoTest.kt
```
package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.DeckDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO отбора карточек в сессию, счётчика готовых и записи планирования
 * (S1, S16, S17, S22, S25, S27).
 */
@RunWith(AndroidJUnit4::class)
class CardDaoTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao
    private var deckId: Long = 0L

    private val now = 1_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        cardDao = db.cardDao()
        deckId = deckDao.insertDeck(DeckEntity(name = "Тестовая"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertCard(
        front: String,
        dueDate: Long,
        easinessFactor: Double = 2.5,
        repetitionNumber: Int = 0,
        intervalDays: Int = 0,
    ): Long = cardDao.insertCard(
        CardEntity(
            deckId = deckId,
            front = front,
            back = front,
            easinessFactor = easinessFactor,
            repetitionNumber = repetitionNumber,
            intervalDays = intervalDays,
            dueDate = dueDate,
        ),
    )

    @Test
    fun `S16 getDueCards returns only cards with dueDate le now`() = runBlocking {
        insertCard("A", dueDate = now - 86_400_000L) // вчера
        insertCard("B", dueDate = now - 3_600_000L)  // недавно
        insertCard("C", dueDate = now)                // ровно сейчас — включается
        insertCard("D", dueDate = now + 86_400_000L)  // завтра — пропускается
        insertCard("E", dueDate = now + 43_140_000L)  // сегодня 23:59 — пропускается

        val due = cardDao.getDueCards(deckId, now)

        assertEquals(listOf("A", "B", "C"), due.map { it.front })
    }

    @Test
    fun `S22 card rated Good today is not due until tomorrow`() = runBlocking {
        val ratedAt = 9 * 3_600_000L // сегодня 09:00
        val cardId = insertCard("cat", dueDate = ratedAt)
        cardDao.updateScheduling(
            cardId = cardId,
            easinessFactor = 2.5,
            repetitionNumber = 1,
            intervalDays = 1,
            dueDate = ratedAt + 86_400_000L, // завтра 09:00
        )

        // Сегодня 12:00 — карточка ещё не назрела.
        val sameDay = cardDao.getDueCards(deckId, ratedAt + 3 * 3_600_000L)
        assertTrue(sameDay.isEmpty())
        assertEquals(0, cardDao.countDue(deckId, ratedAt + 3 * 3_600_000L).first())

        // Завтра 09:01 — карточка назрела.
        val nextDay = cardDao.getDueCards(deckId, ratedAt + 86_400_000L + 60_000L)
        assertEquals(listOf("cat"), nextDay.map { it.front })
    }

    @Test
    fun `S25 countDue equals 2 when 2 of 5 cards are due`() = runBlocking {
        insertCard("A", dueDate = now - 1L)
        insertCard("B", dueDate = now - 2L)
        insertCard("C", dueDate = now + 1L)
        insertCard("D", dueDate = now + 2L)
        insertCard("E", dueDate = now + 3L)

        assertEquals(2, cardDao.countDue(deckId, now).first())
    }

    @Test
    fun `S27 countDue is zero when all cards are in the future`() = runBlocking {
        insertCard("A", dueDate = now + 1L)
        insertCard("B", dueDate = now + 2L)

        assertEquals(0, cardDao.countDue(deckId, now).first())
    }

    @Test
    fun `S1 updateScheduling persists SM-2 result`() = runBlocking {
        val cardId = insertCard("cat", dueDate = now)

        cardDao.updateScheduling(
            cardId = cardId,
            easinessFactor = 2.6,
            repetitionNumber = 1,
            intervalDays = 4,
            dueDate = now + 4L * 86_400_000L,
        )

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.6, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(4, updated.intervalDays)
        assertEquals(now + 4L * 86_400_000L, updated.dueDate)
        assertFalse(updated.dueDate <= now)
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/CardDao.kt
```
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mnemocards.data.CardEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO карточек. Расширен методами планирования SM-2
 * (change add-sm2-scheduling): отбор по сроку, счётчик готовых сегодня
 * и запись результатов оценки.
 */
@Dao
interface CardDao {

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id")
    fun getCardsByDeck(deckId: Long): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id")
    fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>>

    /**
     * Отбор в сессию: карточки с dueDate <= [now], включая ровно срок
     * (S16), в порядке назревания.
     */
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND dueDate <= :now ORDER BY dueDate")
    fun getDueCards(deckId: Long, now: Long): List<CardEntity>

    /** Счётчик карточек, готовых к повторению сегодня (S25, S27). */
    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND dueDate <= :now")
    fun countDue(deckId: Long, now: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>): List<Long>

    /** Запись результата оценки: новые EF / № повторения / интервал / due (S1). */
    @Query(
        "UPDATE cards SET easinessFactor = :easinessFactor, " +
            "repetitionNumber = :repetitionNumber, intervalDays = :intervalDays, " +
            "dueDate = :dueDate WHERE id = :cardId",
    )
    suspend fun updateScheduling(
        cardId: Long,
        easinessFactor: Double,
        repetitionNumber: Int,
        intervalDays: Int,
        dueDate: Long,
    )

    @Query("DELETE FROM cards WHERE id = :cardId")
    suspend fun deleteCard(cardId: Long)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsByDeck(deckId: Long)
}
```

Изменение файла, содержимое которого не приложено (вывожу по текущему пути — расширяю существующий `DeckDao`, чтобы сидер и тесты получали `countDecks()` и `insertDeck(...)`):

### FILE: app/src/main/java/com/mnemocards/data/db/DeckDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mnemocards.data.DeckEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO колод. Дополнен методами, нужными идемпотентному сидингу
 * (change add-sm2-scheduling): подсчёт колод и вставка колоды.
 */
@Dao
interface DeckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity): Long

    @Query("SELECT * FROM decks ORDER BY id")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeckById(id: Long): DeckEntity?

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun countDecks(): Int

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: Long)
}
```


---

## Батч 4/7

### FILE: app/src/main/java/com/mnemocards/data/CardDao.kt
 — этот файл менять не нужно (T10 уже выполнен, см. `data/db/CardDao.kt`).

Ниже — остальные файлы задачи (T11 падающий тест репозитория, T12 реализация). Доменная модель `Card` пока отсутствует в проекте — карта проекта содержит только `domain/model/Deck.kt` и старый `data/CardRepository.kt` с параллельной сущностью; по правилу «одна сущность — один класс» расширяется существующий `CardRepository` по его текущему пути, а доменная `Card` создаётся в `domain/model` как единая для слоёв выше `data`.

### FILE: app/src/main/java/com/mnemocards/domain/model/Card.kt
```kotlin
package com.mnemocards.domain.model

/**
 * Доменная карточка — пара «слово + перевод» с параметрами планирования SM-2
 * (change add-sm2-scheduling). Используется выше слоя `data`; Room-сущности
 * наружу не выходят.
 */
data class Card(
    val id: Long,
    val deckId: Long,
    val front: String,
    val back: String,
    val easinessFactor: Double,
    val repetitionNumber: Int,
    val intervalDays: Int,
    /** Момент следующего показа, epochMillis. */
    val dueDate: Long,
)
```

### FILE: app/src/main/java/com/mnemocards/domain/model/DeckCounts.kt
```kotlin
package com.mnemocards.domain.model

/**
 * Пара счётчиков для элемента списка колод (S25–S27): общее количество
 * карточек и сколько из них готово к повторению сегодня.
 */
data class DeckCounts(
    val deckId: Long,
    val total: Int,
    val dueToday: Int,
)
```

### FILE: app/src/androidTest/java/com/mnemocards/data/CardRepositoryTest.kt
```kotlin
package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.CardDao
import com.mnemocards.domain.model.ReviewGrade
import com.mnemocards.domain.scheduler.SchedulingState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val DAY = 86_400_000L

/**
 * Репозиторий поверх DAO и Sm2Scheduler (S1–S8, S16, S22, S25, S26):
 * отбор назревших карточек с параметрами планирования, применение оценки
 * (результат SM-2 записывается в Room) и Flow-агрегат (total, dueToday),
 * обновляющийся автоматически после оценки.
 */
@RunWith(AndroidJUnit4::class)
class CardRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao
    private lateinit var repository: RoomCardRepository
    private var deckId: Long = 0L

    private val now = 1_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        cardDao = db.cardDao()
        repository = RoomCardRepository(cardDao)
        deckId = deckDao.insertDeck(DeckEntity(name = "Базовая"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertCard(
        front: String,
        dueDate: Long = now,
        easinessFactor: Double = 2.5,
        repetitionNumber: Int = 0,
        intervalDays: Int = 0,
    ): Long = cardDao.insertCard(
        CardEntity(
            deckId = deckId,
            front = front,
            back = front,
            easinessFactor = easinessFactor,
            repetitionNumber = repetitionNumber,
            intervalDays = intervalDays,
            dueDate = dueDate,
        ),
    )

    @Test
    fun `S16 dueCards returns only due cards with scheduling fields`() = runBlocking {
        insertCard("A", dueDate = now - 1L)
        insertCard("B", dueDate = now)
        insertCard("C", dueDate = now + DAY)

        val due = repository.dueCards(deckId, now)

        assertEquals(listOf("A", "B"), due.map { it.front })
        val first = due.first()
        assertEquals(2.5, first.easinessFactor, 1e-9)
        assertEquals(0, first.repetitionNumber)
        assertEquals(0, first.intervalDays)
        assertEquals(now - 1L, first.dueDate)
    }

    @Test
    fun `S1 applyGrade Easy on new card writes SM-2 result to Room`() = runBlocking {
        val cardId = insertCard("cat")

        val card = repository.dueCards(deckId, now).single()
        repository.applyGrade(card, ReviewGrade.EASY, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.6, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(4, updated.intervalDays)
        assertEquals(now + 4L * DAY, updated.dueDate)

        // S22: карточка не возвращается в отбор до срока.
        assertTrue(repository.dueCards(deckId, now + DAY).none { it.id == cardId })
    }

    @Test
    fun `S2 applyGrade Good on new card keeps EF`() = runBlocking {
        insertCard("cat")
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.GOOD, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.5, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(1, updated.intervalDays)
        assertEquals(now + DAY, updated.dueDate)
    }

    @Test
    fun `S3 applyGrade Hard on new card gives one day`() = runBlocking {
        insertCard("cat")
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.HARD, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.35, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(1, updated.intervalDays)
        assertEquals(now + DAY, updated.dueDate)
    }

    @Test
    fun `S4 applyGrade Forgot resets repetition and delays by 10 minutes`() = runBlocking {
        insertCard("cat")
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.FORGOT, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.3, updated.easinessFactor, 1e-9)
        assertEquals(0, updated.repetitionNumber)
        assertEquals(0, updated.intervalDays)
        assertEquals(now + 10L * 60_000L, updated.dueDate)
    }

    @Test
    fun `S5 applyGrade Good on repeating card multiplies interval by EF`() = runBlocking {
        insertCard("dog", repetitionNumber = 2, intervalDays = 6)
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.GOOD, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.5, updated.easinessFactor, 1e-9)
        assertEquals(3, updated.repetitionNumber)
        assertEquals(15, updated.intervalDays)
        assertEquals(now + 15L * DAY, updated.dueDate)
    }

    @Test
    fun `S6 applyGrade Easy on repeating card applies bonus`() = runBlocking {
        insertCard("dog", repetitionNumber = 2, intervalDays = 6)
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.EASY, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.6, updated.easinessFactor, 1e-9)
        assertEquals(20, updated.intervalDays)
        assertEquals(now + 20L * DAY, updated.dueDate)
    }

    @Test
    fun `S7 applyGrade Hard on repeating card uses 1_2 factor`() = runBlocking {
        insertCard("dog", repetitionNumber = 2, intervalDays = 6)
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.HARD, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.35, updated.easinessFactor, 1e-9)
        assertEquals(7, updated.intervalDays)
        assertEquals(now + 7L * DAY, updated.dueDate)
    }

    @Test
    fun `S8 applyGrade Forgot on repeating card resets progress`() = runBlocking {
        insertCard("dog", repetitionNumber = 2, intervalDays = 6)
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.FORGOT, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.3, updated.easinessFactor, 1e-9)
        assertEquals(0, updated.repetitionNumber)
        assertEquals(0, updated.intervalDays)
        assertEquals(now + 10L * 60_000L, updated.dueDate)
    }

    @Test
    fun `S25 S26 observeDecksWithCounts emits total and dueToday and updates after grade`() = runBlocking {
        insertCard("A", dueDate = now - 1L)
        insertCard("B", dueDate = now)
        insertCard("C", dueDate = now + DAY)
        insertCard("D", dueDate = now + 2L * DAY)
        insertCard("E", dueDate = now + 3L * DAY)

        val before = repository.observeDecksWithCounts(now).first()
        assertEquals(5, before.first().total)
        assertEquals(2, before.first().dueToday)

        // Обе назревшие карточки получают graduating-оценку — due уходит в будущее.
        val dueCards = repository.dueCards(deckId, now)
        repository.applyGrade(dueCards[0], ReviewGrade.HARD, now)
        repository.applyGrade(dueCards[1], ReviewGrade.GOOD, now)

        val after = repository.observeDecksWithCounts(now).first()
        assertEquals(5, after.first().total)
        assertEquals(0, after.first().dueToday)
    }
}
```

### FILE: app/src/main/java/com/mnemocards/data/CardRepository.kt
 — этот файл заменяется целиком ниже.

### FILE: app/src/main/java/com/mnemocards/data/db/CardDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mnemocards.data.CardEntity
import com.mnemocards.data.DeckWithCardCounts
import kotlinx.coroutines.flow.Flow

/**
 * DAO карточек. Расширен методами планирования SM-2
 * (change add-sm2-scheduling): отбор по сроку, счётчик готовых сегодня
 * и запись результатов оценки.
 */
@Dao
interface CardDao {

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id")
    fun getCardsByDeck(deckId: Long): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id")
    fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>>

    /**
     * Отбор в сессию: карточки с dueDate <= [now], включая ровно срок
     * (S16), в порядке назревания.
     */
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND dueDate <= :now ORDER BY dueDate")
    fun getDueCards(deckId: Long, now: Long): List<CardEntity>

    /** Счётчик карточек, готовых к повторению сегодня (S25, S27). */
    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND dueDate <= :now")
    fun countDue(deckId: Long, now: Long): Flow<Int>

    /**
     * Агрегат для списка колод одним запросом (S25–S27): общее количество
     * карточек и готовых к повторению сегодня по каждой колоде.
     */
    @Query(
        "SELECT decks.id AS deckId, COUNT(cards.id) AS total, " +
            "SUM(CASE WHEN cards.dueDate <= :now THEN 1 ELSE 0 END) AS dueToday " +
            "FROM decks LEFT JOIN cards ON cards.deckId = decks.id " +
            "GROUP BY decks.id ORDER BY decks.id",
    )
    fun observeDeckCounts(now: Long): Flow<List<DeckWithCardCounts>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>): List<Long>

    /** Запись результата оценки: новые EF / № повторения / интервал / due (S1). */
    @Query(
        "UPDATE cards SET easinessFactor = :easinessFactor, " +
            "repetitionNumber = :repetitionNumber, intervalDays = :intervalDays, " +
            "dueDate = :dueDate WHERE id = :cardId",
    )
    suspend fun updateScheduling(
        cardId: Long,
        easinessFactor: Double,
        repetitionNumber: Int,
        intervalDays: Int,
        dueDate: Long,
    )

    @Query("DELETE FROM cards WHERE id = :cardId")
    suspend fun deleteCard(cardId: Long)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsByDeck(deckId: Long)
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/DeckWithCardCounts.kt
```kotlin
package com.mnemocards.data.db

/**
 * Результат агрегатного запроса DAO: total — всего карточек в колоде,
 * dueToday — готовых к повторению сегодня (dueDate <= now), S25–S27.
 */
data class DeckWithCardCounts(
    val deckId: Long,
    val total: Int,
    val dueToday: Int,
)
```

### FILE: app/src/main/java/com/mnemocards/data/CardRepository.kt
```kotlin
package com.mnemocards.data

import com.mnemocards.data.db.CardDao
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.model.DeckCounts
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.domain.scheduler.SchedulingState
import com.mnemocards.domain.scheduler.Sm2Scheduler

/**
 * Репозиторий карточек поверх Room-DAO и планировщика SM-2
 * (change add-sm2-scheduling). Маппинг entity ↔ domain живёт здесь —
 * сущности наружу из `data` не выходят.
 */
interface CardRepository {

    /** Назревшие карточки колоды (dueDate <= now) с параметрами планирования (S16). */
    suspend fun dueCards(deckId: Long, now: Long): List<Card>

    /** Применяет оценку [grade] и записывает результат SM-2 в Room (S1–S8). */
    suspend fun applyGrade(card: Card, grade: ReviewGrade, now: Long)
}

class RoomCardRepository(
    private val cardDao: CardDao,
) : CardRepository {

    override suspend fun dueCards(deckId: Long, now: Long): List<Card> =
        cardDao.getDueCards(deckId, now).map { it.toDomain() }

    override suspend fun applyGrade(card: Card, grade: ReviewGrade, now: Long) {
        val next = Sm2Scheduler.next(card.toSchedulingState(), grade, now)
        cardDao.updateScheduling(
            cardId = card.id,
            easinessFactor = next.easinessFactor,
            repetitionNumber = next.repetitionNumber,
            intervalDays = next.intervalDays,
            dueDate = next.dueDate,
        )
    }

    private fun CardEntity.toDomain(): Card = Card(
        id = id,
        deckId = deckId,
        front = front,
        back = back,
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )

    private fun Card.toSchedulingState(): SchedulingState = SchedulingState(
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )
}
```

### FILE: app/src/main/java/com/mnemocards/data/CardCountRepository.kt
```kotlin
package com.mnemocards.data

import com.mnemocards.data.db.CardDao
import com.mnemocards.domain.model.DeckCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Flow-агрегат (total, dueToday) для списка колод (S25–S27): значения
 * приходят из Room и обновляются автоматически после оценки — без
 * ручного обновления.
 */
interface DeckCountsRepository {

    fun observeDecksWithCounts(now: Long): Flow<List<DeckCounts>>
}

class RoomDeckCountsRepository(
    private val cardDao: CardDao,
) : DeckCountsRepository {

    override fun observeDecksWithCounts(now: Long): Flow<List<DeckCounts>> =
        cardDao.observeDeckCounts(now).map { rows ->
            rows.map { row ->
                DeckCounts(
                    deckId = row.deckId,
                    total = row.total,
                    dueToday = row.dueToday,
                )
            }
        }
}
```

### FILE: app/src/main/java/com/mnemocards/domain/model/ReviewGrade.kt
 — пропущен (уже существует в `domain/scheduler/ReviewGrade.kt`, выше импортируется оттуда).


---

## Батч 5/7

### FILE: app/src/test/java/com/mnemocards/ui/review/ReviewViewModelTest.kt
```kotlin
package com.mnemocards.ui.review

import com.mnemocards.data.CardRepository
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.domain.scheduler.SchedulingState
import com.mnemocards.domain.scheduler.Sm2Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

private const val DAY = 86_400_000L
private const val MINUTE = 60_000L

/**
 * Unit-тесты ReviewViewModel (change add-sm2-scheduling):
 * T13 — построение сессии (S16–S18, S31, S32),
 * T15 — оценки, превью и повторный показ (S1–S4, S14, S15, S19–S21, S23, S30).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = 1_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun card(
        id: Long,
        deckId: Long = 1L,
        front: String = "card$id",
        easinessFactor: Double = 2.5,
        repetitionNumber: Int = 0,
        intervalDays: Int = 0,
        dueDate: Long = now,
    ) = Card(
        id = id,
        deckId = deckId,
        front = front,
        back = "перевод$id",
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )

    /**
     * Fake-репозиторий: хранит карточки в памяти, честно фильтрует их
     * по dueDate <= now и применяет оценку тем же Sm2Scheduler, что и
     * продакшн-репозиторий.
     */
    private class FakeCardRepository(
        var deckExists: Boolean = true,
        var failRead: Boolean = false,
        var failWrite: Boolean = false,
    ) : CardRepository {

        val cards = mutableListOf<Card>()
        val appliedGrades = mutableListOf<Pair<Card, ReviewGrade>>()

        override suspend fun deckExists(deckId: Long): Boolean = deckExists

        override suspend fun dueCards(deckId: Long, now: Long): List<Card> {
            if (failRead) throw RuntimeException("db read error")
            return cards.filter { it.deckId == deckId && it.dueDate <= now }
        }

        override suspend fun applyGrade(card: Card, grade: ReviewGrade, now: Long) {
            if (failWrite) throw RuntimeException("db write error")
            appliedGrades += card to grade
            val next = Sm2Scheduler.next(
                SchedulingState(
                    easinessFactor = card.easinessFactor,
                    repetitionNumber = card.repetitionNumber,
                    intervalDays = card.intervalDays,
                    dueDate = card.dueDate,
                ),
                grade,
                now,
            )
            val index = cards.indexOfFirst { it.id == card.id }
            cards[index] = card.copy(
                easinessFactor = next.easinessFactor,
                repetitionNumber = next.repetitionNumber,
                intervalDays = next.intervalDays,
                dueDate = next.dueDate,
            )
        }
    }

    private fun viewModel(repo: FakeCardRepository) =
        ReviewViewModel(deckId = 1L, repository = repo, nowProvider = { now })

    // ------------------------------------------------------------------
    // T13 — построение сессии
    // ------------------------------------------------------------------

    @Test
    fun `S16 session is built only from due cards and starts with first due`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += listOf(
            card(1, front = "A", dueDate = now - DAY),      // вчера
            card(2, front = "B", dueDate = now - MINUTE),   // недавно
            card(3, front = "C", dueDate = now),            // ровно сейчас
            card(4, front = "D", dueDate = now + DAY),      // завтра
            card(5, front = "E", dueDate = now + 11 * 3_600_000L), // сегодня 23:59
        )

        val vm = viewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is ReviewUiState.Session)
        assertEquals("A", (state as ReviewUiState.Session).card.front)
        assertFalse(state.isFlipped)
    }

    @Test
    fun `S17 no due cards shows Done immediately`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += listOf(
            card(1, dueDate = now + DAY),
            card(2, dueDate = now + 2 * DAY),
        )

        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(ReviewUiState.Done, vm.uiState.value)
        // Данные карточек не изменяются.
        assertTrue(repo.appliedGrades.isEmpty())
    }

    @Test
    fun `S18 empty deck shows Done`() = runTest {
        val repo = FakeCardRepository()

        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(ReviewUiState.Done, vm.uiState.value)
    }

    @Test
    fun `S32 missing deck shows NotFound`() = runTest {
        val repo = FakeCardRepository(deckExists = false)

        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(ReviewUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `S31 read failure shows Error instead of session`() = runTest {
        val repo = FakeCardRepository(failRead = true)
        repo.cards += card(1)

        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(ReviewUiState.Error, vm.uiState.value)
    }

    // ------------------------------------------------------------------
    // T15 — оценки, превью, повторный показ
    // ------------------------------------------------------------------

    @Test
    fun `flip toggles isFlipped`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card(1)

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onFlip()
        assertEquals(true, (vm.uiState.value as ReviewUiState.Session).isFlipped)
        vm.onFlip()
        assertEquals(false, (vm.uiState.value as ReviewUiState.Session).isFlipped)
    }

    @Test
    fun `S14 S15 previews match applied intervals for repeating card`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card(1, repetitionNumber = 2, intervalDays = 6)

        val vm = viewModel(repo)
        advanceUntilIdle()

        val previews = (vm.uiState.value as ReviewUiState.Session).previews
        assertEquals(10 * MINUTE, previews[ReviewGrade.FORGOT])
        assertEquals(7 * DAY, previews[ReviewGrade.HARD])
        assertEquals(15 * DAY, previews[ReviewGrade.GOOD])
        assertEquals(20 * DAY, previews[ReviewGrade.EASY])

        // Подпись совпадает с применённым результатом (S15).
        vm.onGrade(ReviewGrade.GOOD)
        advanceUntilIdle()

        val applied = repo.cards.single()
        assertEquals(15, applied.intervalDays)
        assertEquals(15 * DAY, applied.dueDate - now)
    }

    @Test
    fun `S1 Easy on new card applies grade and moves to next card`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += listOf(card(1, front = "cat"), card(2, front = "dog"))

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.EASY)
        advanceUntilIdle()

        assertEquals(1, repo.appliedGrades.size)
        assertEquals(ReviewGrade.EASY, repo.appliedGrades.single().second)
        val updated = repo.cards.first { it.front == "cat" }
        assertEquals(2.6, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(4, updated.intervalDays)
        assertEquals("dog", (vm.uiState.value as ReviewUiState.Session).card.front)
    }

    @Test
    fun `S2 S3 Good and Hard on new card apply grade`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card(1, front = "cat")

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.GOOD)
        advanceUntilIdle()

        val updated = repo.cards.single()
        assertEquals(2.5, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(1, updated.intervalDays)
    }

    @Test
    fun `S4 Forgot keeps card in session`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card(1, front = "cat")

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.FORGOT)
        advanceUntilIdle()

        // Карточка не удалена из сессии — показана снова.
        val state = vm.uiState.value as ReviewUiState.Session
        assertEquals("cat", state.card.front)
        assertEquals(false, state.isFlipped)
        assertEquals(1, repo.appliedGrades.size)
    }

    @Test
    fun `S19 Forgot moves card to the end of the queue`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += listOf(
            card(1, front = "A"),
            card(2, front = "B"),
            card(3, front = "C"),
        )

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.FORGOT) // A — в конец
        advanceUntilIdle()
        assertEquals("B", (vm.uiState.value as ReviewUiState.Session).card.front)

        vm.onGrade(ReviewGrade.GOOD)   // B закрыта
        advanceUntilIdle()
        assertEquals("C", (vm.uiState.value as ReviewUiState.Session).card.front)

        vm.onGrade(ReviewGrade.GOOD)   // C закрыта
        advanceUntilIdle()

        // После C карточка A показана повторно — лицевой стороной.
        val state = vm.uiState.value as ReviewUiState.Session
        assertEquals("A", state.card.front)
        assertEquals(false, state.isFlipped)
    }

    @Test
    fun `S20 graduating the last re-queued card finishes session`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += listOf(card(1, front = "A"), card(2, front = "B"))

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.FORGOT) // A — в конец, следующая B
        advanceUntilIdle()
        vm.onGrade(ReviewGrade.GOOD)   // B закрыта, A — повторный показ
        advanceUntilIdle()
        vm.onGrade(ReviewGrade.GOOD)   // A получает №1 и интервал 1 день
        advanceUntilIdle()

        assertEquals(ReviewUiState.Done, vm.uiState.value)
        val updated = repo.cards.first { it.front == "A" }
        assertEquals(1, updated.repetitionNumber)
        assertEquals(1, updated.intervalDays)
    }

    @Test
    fun `S21 repeated Forgot lowers EF but not below floor`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card(1, front = "A")

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.FORGOT)
        advanceUntilIdle()
        vm.onGrade(ReviewGrade.FORGOT)
        advanceUntilIdle()

        // Серия «Забыл»: EF 2.5 → 2.3 → 2.1.
        assertEquals(2.5, repo.appliedGrades[0].first.easinessFactor, 1e-9)
        assertEquals(2.3, repo.appliedGrades[1].first.easinessFactor, 1e-9)
        val updated = repo.cards.single()
        assertEquals(2.1, updated.easinessFactor, 1e-9)
        assertEquals(0, updated.repetitionNumber)
        // A снова в конце очереди — показана повторно.
        assertEquals("A", (vm.uiState.value as ReviewUiState.Session).card.front)
    }

    @Test
    fun `S23 card with repetition 1 previews 6 days on Good`() = runTest {
        val repo = FakeCardRepository()
        repo.cards += card(1, repetitionNumber = 1, intervalDays = 1)

        val vm = viewModel(repo)
        advanceUntilIdle()

        val previews = (vm.uiState.value as ReviewUiState.Session).previews
        assertEquals(6 * DAY, previews[ReviewGrade.GOOD])

        vm.onGrade(ReviewGrade.GOOD)
        advanceUntilIdle()

        val updated = repo.cards.single()
        assertEquals(6, updated.intervalDays)
        assertEquals(2, updated.repetitionNumber)
    }

    @Test
    fun `S30 write failure keeps card unevaluated and retry applies`() = runTest {
        val repo = FakeCardRepository(failWrite = true)
        repo.cards += listOf(card(1, front = "A"), card(2, front = "B"))

        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onGrade(ReviewGrade.GOOD)
        advanceUntilIdle()

        // Ошибка сохранения: карточка осталась в очереди неоценённой.
        val state = vm.uiState.value as ReviewUiState.Session
        assertTrue(state.saveError)
        assertEquals("A", state.card.front)
        val unchanged = repo.cards.first { it.front == "A" }
        assertEquals(0, unchanged.repetitionNumber)

        // Повторное нажатие применяется после восстановления БД.
        repo.failWrite = false
        vm.onGrade(ReviewGrade.GOOD)
        advanceUntilIdle()

        assertFalse((vm.uiState.value as ReviewUiState.Session).saveError)
        assertEquals("B", (vm.uiState.value as ReviewUiState.Session).card.front)
        assertEquals(1, repo.cards.first { it.front == "A" }.repetitionNumber)
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewUiState.kt
```kotlin
package com.mnemocards.ui.review

import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade

/**
 * Состояние экрана тренировки (change add-sm2-scheduling).
 *
 * Ошибка загрузки — аналог 500 (S31), NotFound — аналог 404 (S32):
 * оба выражены состояниями UI, краша нет.
 */
sealed interface ReviewUiState {

    /** Сессия загружается из Room. */
    data object Loading : ReviewUiState

    /** Колода не найдена (устаревший идентификатор навигации, S32). */
    data object NotFound : ReviewUiState

    /** Ошибка чтения БД при формировании сессии (S31). */
    data object Error : ReviewUiState

    /**
     * Активная карточка сессии.
     *
     * @property previews прогнозируемые интервалы четырёх кнопок оценки
     * (миллисекунды до due, S14, S15) — вычисляются той же функцией,
     * что и применяет оценку.
     * @property saveError ошибка записи в БД при оценке (S30): карточка
     * остаётся в сессии неоценённой.
     */
    data class Session(
        val card: Card,
        val isFlipped: Boolean,
        val previews: Map<ReviewGrade, Long>,
        val saveError: Boolean = false,
    ) : ReviewUiState

    /** Очередь пуста — «на сегодня всё» (S17, S18, S20). */
    data object Done : ReviewUiState
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt
```kotlin
package com.mnemocards.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.CardRepository
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.domain.scheduler.SchedulingState
import com.mnemocards.domain.scheduler.Sm2Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel сессии повторения (change add-sm2-scheduling).
 *
 * Очередь карточек живёт в ViewModel (переживает смену конфигурации);
 * сессия собирается из назревших карточек (dueDate <= now) с инъекцией
 * времени; состояния Loading / NotFound / Error / Session / Done — S16–S18,
 * S31, S32.
 */
class ReviewViewModel(
    private val deckId: Long,
    private val repository: CardRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val queue = ArrayDeque<Card>()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            val now = nowProvider()
            try {
                // S32: устаревший идентификатор колоды — состояние NotFound.
                if (!repository.deckExists(deckId)) {
                    _uiState.value = ReviewUiState.NotFound
                    return@launch
                }
                // S16: в сессию попадают только карточки с due <= now.
                val dueCards = repository.dueCards(deckId, now)
                if (dueCards.isEmpty()) {
                    // S17, S18: нет назревших или колода пуста — сразу Done.
                    _uiState.value = ReviewUiState.Done
                    return@launch
                }
                queue.clear()
                queue.addAll(dueCards)
                _uiState.value = sessionState(queue.first(), now)
            } catch (e: Exception) {
                // S31: ошибка чтения — состояние ошибки вместо сессии.
                _uiState.value = ReviewUiState.Error
            }
        }
    }

    /**
     * Прогнозируемые интервалы четырёх кнопок (S14, S15): считаются тем же
     * Sm2Scheduler.next, который применяется при оценке, — подпись на кнопке
     * совпадает с записанным результатом.
     */
    private fun previewIntervals(card: Card, now: Long): Map<ReviewGrade, Long> =
        ReviewGrade.entries.associateWith { grade ->
            Sm2Scheduler.next(card.toSchedulingState(), grade, now).dueDate - now
        }

    private fun sessionState(
        card: Card,
        isFlipped: Boolean = false,
        saveError: Boolean = false,
    ): ReviewUiState.Session {
        val now = nowProvider()
        return ReviewUiState.Session(
            card = card,
            isFlipped = isFlipped,
            previews = previewIntervals(card, now),
            saveError = saveError,
        )
    }

    private fun Card.toSchedulingState(): SchedulingState = SchedulingState(
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )
}

/**
 * Фабрика ViewModel с параметрами навигации (deckId) и зависимостями
 * из AppContainer.
 */
class ReviewViewModelFactory(
    private val deckId: Long,
    private val repository: CardRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReviewViewModel(deckId, repository) as T
}
```

### FILE: app/src/main/java/com/mnemocards/data/db/CardDao.kt
```kotlin
package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mnemocards.data.CardEntity
import com.mnemocards.data.DeckWithCardCounts
import kotlinx.coroutines.flow.Flow

/**
 * DAO карточек. Расширен методами планирования SM-2
 * (change add-sm2-scheduling): отбор по сроку, счётчик готовых сегодня
 * и запись результатов оценки.
 */
@Dao
interface CardDao {

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id")
    fun getCardsByDeck(deckId: Long): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id")
    fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>>

    /**
     * Отбор в сессию: карточки с dueDate <= [now], включая ровно срок
     * (S16), в порядке назревания.
     */
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND dueDate <= :now ORDER BY dueDate")
    fun getDueCards(deckId: Long, now: Long): List<CardEntity>

    /** Счётчик карточек, готовых к повторению сегодня (S25, S27). */
    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND dueDate <= :now")
    fun countDue(deckId: Long, now: Long): Flow<Int>

    /**
     * Агрегат для списка колод одним запросом (S25–S27): общее количество
     * карточек и готовых к повторению сегодня по каждой колоде.
     */
    @Query(
        "SELECT decks.id AS deckId, COUNT(cards.id) AS total, " +
            "SUM(CASE WHEN cards.dueDate <= :now THEN 1 ELSE 0 END) AS dueToday " +
            "FROM decks LEFT JOIN cards ON cards.deckId = decks.id " +
            "GROUP BY decks.id ORDER BY decks.id",
    )
    fun observeDeckCounts(now: Long): Flow<List<DeckWithCardCounts>>

    /** Существование колоды — для состояния NotFound (S32). */
    @Query("SELECT EXISTS(SELECT 1 FROM decks WHERE id = :deckId)")
    suspend fun deckExists(deckId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>): List<Long>

    /** Запись результата оценки: новые EF / № повторения / интервал / due (S1). */
    @Query(
        "UPDATE cards SET easinessFactor = :easinessFactor, " +
            "repetitionNumber = :repetitionNumber, intervalDays = :intervalDays, " +
            "dueDate = :dueDate WHERE id = :cardId",
    )
    suspend fun updateScheduling(
        cardId: Long,
        easinessFactor: Double,
        repetitionNumber: Int,
        intervalDays: Int,
        dueDate: Long,
    )

    @Query("DELETE FROM cards WHERE id = :cardId")
    suspend fun deleteCard(cardId: Long)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsByDeck(deckId: Long)
}
```

### FILE: app/src/main/java/com/mnemocards/data/CardRepository.kt
```kotlin
package com.mnemocards.data

import com.mnemocards.data.db.CardDao
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.domain.scheduler.SchedulingState
import com.mnemocards.domain.scheduler.Sm2Scheduler

/**
 * Репозиторий карточек поверх Room-DAO и планировщика SM-2
 * (change add-sm2-scheduling). Маппинг entity ↔ domain живёт здесь —
 * сущности наружу из `data` не выходят.
 */
interface CardRepository {

    /** Назревшие карточки колоды (dueDate <= now) с параметрами планирования (S16). */
    suspend fun dueCards(deckId: Long, now: Long): List<Card>

    /** Применяет оценку [grade] и записывает результат SM-2 в Room (S1–S8). */
    suspend fun applyGrade(card: Card, grade: ReviewGrade, now: Long)

    /** Существование колоды — состояние NotFound при устаревшем id (S32). */
    suspend fun deckExists(deckId: Long): Boolean
}

class RoomCardRepository(
    private val cardDao: CardDao,
) : CardRepository {

    override suspend fun dueCards(deckId: Long, now: Long): List<Card> =
        cardDao.getDueCards(deckId, now).map { it.toDomain() }

    override suspend fun applyGrade(card: Card, grade: ReviewGrade, now: Long) {
        val next = Sm2Scheduler.next(card.toSchedulingState(), grade, now)
        cardDao.updateScheduling(
            cardId = card.id,
            easinessFactor = next.easinessFactor,
            repetitionNumber = next.repetitionNumber,
            intervalDays = next.intervalDays,
            dueDate = next.dueDate,
        )
    }

    override suspend fun deckExists(deckId: Long): Boolean =
        cardDao.deckExists(deckId)

    private fun CardEntity.toDomain(): Card = Card(
        id = id,
        deckId = deckId,
        front = front,
        back = back,
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )

    private fun Card.toSchedulingState(): SchedulingState = SchedulingState(
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )
}
```


---

## Батч 6/7

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewViewModel.kt
```kotlin
package com.mnemocards.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.CardRepository
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.domain.scheduler.SchedulingState
import com.mnemocards.domain.scheduler.Sm2Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel сессии повторения (change add-sm2-scheduling).
 *
 * Очередь карточек живёт в ViewModel (переживает смену конфигурации);
 * сессия собирается из назревших карточек (dueDate <= now) с инъекцией
 * времени; состояния Loading / NotFound / Error / Session / Done — S16–S18,
 * S31, S32. Оценки: «Забыл» переносит карточку в конец очереди (S19, S21),
 * graduating-оценки закрывают карточку (S1–S3), ошибка записи — флаг
 * saveError без изменения очереди (S30).
 */
class ReviewViewModel(
    private val deckId: Long,
    private val repository: CardRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val queue = ArrayDeque<Card>()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            _uiState.value = ReviewUiState.Loading
            val now = nowProvider()
            try {
                // S32: устаревший идентификатор колоды — состояние NotFound.
                if (!repository.deckExists(deckId)) {
                    _uiState.value = ReviewUiState.NotFound
                    return@launch
                }
                // S16: в сессию попадают только карточки с due <= now.
                val dueCards = repository.dueCards(deckId, now)
                if (dueCards.isEmpty()) {
                    // S17, S18: нет назревших или колода пуста — сразу Done.
                    _uiState.value = ReviewUiState.Done
                    return@launch
                }
                queue.clear()
                queue.addAll(dueCards)
                _uiState.value = sessionState(queue.first(), now)
            } catch (e: Exception) {
                // S31: ошибка чтения — состояние ошибки вместо сессии.
                _uiState.value = ReviewUiState.Error
            }
        }
    }

    /** Переворот карточки: лицевая сторона ↔ перевод. */
    fun onFlip() {
        val state = _uiState.value as? ReviewUiState.Session ?: return
        _uiState.value = state.copy(isFlipped = !state.isFlipped, saveError = false)
    }

    /**
     * Оценка ответа (S1–S4, S19–S21, S30). «Забыл» — applyGrade и перенос
     * карточки (с обновлёнными параметрами SM-2) в конец очереди; graduating
     * — applyGrade и удаление из очереди; пустая очередь — Done. При ошибке
     * записи карточка остаётся неоценённой, очередь не меняется.
     */
    fun onGrade(grade: ReviewGrade) {
        val state = _uiState.value as? ReviewUiState.Session ?: return
        viewModelScope.launch {
            val now = nowProvider()
            try {
                repository.applyGrade(state.card, grade, now)
                queue.removeFirst()
                if (grade == ReviewGrade.FORGOT) {
                    queue.addLast(withScheduling(state.card, grade, now))
                }
                _uiState.value =
                    if (queue.isEmpty()) ReviewUiState.Done else sessionState(queue.first())
            } catch (e: Exception) {
                // S30: карточка остаётся в сессии неоценённой, повторное
                // нажатие применяется после восстановления БД.
                _uiState.value = state.copy(saveError = true)
            }
        }
    }

    /**
     * Прогнозируемые интервалы четырёх кнопок (S14, S15): считаются тем же
     * Sm2Scheduler.next, который применяется при оценке, — подпись на кнопке
     * совпадает с записанным результатом.
     */
    private fun previewIntervals(card: Card, now: Long): Map<ReviewGrade, Long> =
        ReviewGrade.entries.associateWith { grade ->
            Sm2Scheduler.next(card.toSchedulingState(), grade, now).dueDate - now
        }

    private fun sessionState(
        card: Card,
        isFlipped: Boolean = false,
        saveError: Boolean = false,
    ): ReviewUiState.Session {
        val now = nowProvider()
        return ReviewUiState.Session(
            card = card,
            isFlipped = isFlipped,
            previews = previewIntervals(card, now),
            saveError = saveError,
        )
    }

    /**
     * Копия карточки с параметрами, которые записал Sm2Scheduler при
     * «Забыл»: EF уменьшился, прогресс сброшен — повторный показ в этой же
     * сессии считает превью от актуальных значений (S21).
     */
    private fun withScheduling(card: Card, grade: ReviewGrade, now: Long): Card {
        val next = Sm2Scheduler.next(card.toSchedulingState(), grade, now)
        return card.copy(
            easinessFactor = next.easinessFactor,
            repetitionNumber = next.repetitionNumber,
            intervalDays = next.intervalDays,
            dueDate = next.dueDate,
        )
    }

    private fun Card.toSchedulingState(): SchedulingState = SchedulingState(
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )
}

/**
 * Фабрика ViewModel с параметрами навигации (deckId) и зависимостями
 * из AppContainer.
 */
class ReviewViewModelFactory(
    private val deckId: Long,
    private val repository: CardRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReviewViewModel(deckId, repository) as T
}
```

### FILE: app/src/test/java/com/mnemocards/ui/decklist/DeckListViewModelTest.kt
```kotlin
package com.mnemocards.ui.decklist

import com.mnemocards.data.DeckCountsRepository
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.DeckCounts
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Unit-тесты DeckListViewModel — счётчик готовых к повторению сегодня
 * (T17, S24–S27).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now = 1_000_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeDeckRepository : DeckRepository {

        val decks = MutableStateFlow<List<Deck>>(emptyList())

        override fun observeDecks(): Flow<List<Deck>> = decks
    }

    private class FakeDeckCountsRepository : DeckCountsRepository {

        val counts = MutableStateFlow<List<DeckCounts>>(emptyList())

        override fun observeDecksWithCounts(now: Long): Flow<List<DeckCounts>> = counts
    }

    private fun viewModel(
        decks: FakeDeckRepository,
        counts: FakeDeckCountsRepository,
    ): DeckListViewModel = DeckListViewModel(
        deckRepository = decks,
        countsRepository = counts,
        nowProvider = { now },
    )

    private fun readyList(state: DeckListUiState): List<DeckListItem> {
        assertTrue(state is DeckListUiState.Ready)
        return (state as DeckListUiState.Ready).decks
    }

    @Test
    fun `S25 deck item carries total 5 and dueToday 2`() = runTest {
        val decks = FakeDeckRepository().apply { decks.value = listOf(Deck(id = 1L, name = "Базовая")) }
        val counts = FakeDeckCountsRepository().apply {
            counts.value = listOf(DeckCounts(deckId = 1L, total = 5, dueToday = 2))
        }

        val vm = viewModel(decks, counts)
        advanceUntilIdle()

        val item = readyList(vm.uiState.value).single()
        assertEquals(5, item.total)
        assertEquals(2, item.dueToday)
    }

    @Test
    fun `S27 dueToday is zero when all cards are in the future`() = runTest {
        val decks = FakeDeckRepository().apply { decks.value = listOf(Deck(id = 1L, name = "Базовая")) }
        val counts = FakeDeckCountsRepository().apply {
            counts.value = listOf(DeckCounts(deckId = 1L, total = 5, dueToday = 0))
        }

        val vm = viewModel(decks, counts)
        advanceUntilIdle()

        // Счётчик явно равен 0, колода не исчезает из списка.
        val item = readyList(vm.uiState.value).single()
        assertEquals(0, item.dueToday)
        assertEquals(5, item.total)
    }

    @Test
    fun `S26 dueToday updates automatically after grades applied`() = runTest {
        val decks = FakeDeckRepository().apply { decks.value = listOf(Deck(id = 1L, name = "Базовая")) }
        val counts = FakeDeckCountsRepository().apply {
            counts.value = listOf(DeckCounts(deckId = 1L, total = 5, dueToday = 2))
        }

        val vm = viewModel(decks, counts)
        vm.uiState.test {
            advanceUntilIdle()
            assertEquals(2, readyList(awaitItem()).single().dueToday)

            // Обе назревшие карточки оценены — due ушёл в будущее.
            counts.counts.value = listOf(DeckCounts(deckId = 1L, total = 5, dueToday = 0))
            advanceUntilIdle()

            assertEquals(0, readyList(awaitItem()).single().dueToday)
            assertEquals(5, readyList(vm.uiState.value).single().total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `S24 new card with due now increases dueToday by one`() = runTest {
        val decks = FakeDeckRepository().apply { decks.value = listOf(Deck(id = 1L, name = "Базовая")) }
        val counts = FakeDeckCountsRepository().apply {
            counts.value = listOf(DeckCounts(deckId = 1L, total = 5, dueToday = 2))
        }

        val vm = viewModel(decks, counts)
        vm.uiState.test {
            advanceUntilIdle()
            assertEquals(2, readyList(awaitItem()).single().dueToday)

            // Новая карточка без истории — готова сразу.
            counts.counts.value = listOf(DeckCounts(deckId = 1L, total = 6, dueToday = 3))
            advanceUntilIdle()

            assertEquals(3, readyList(awaitItem()).single().dueToday)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModel.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.DeckCountsRepository
import com.mnemocards.domain.DeckRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateOf
import kotlinx.coroutines.flow.stateIn

/**
 * Элемент списка колод (change add-sm2-scheduling): общее количество
 * карточек и сколько из них готово к повторению сегодня (S24–S27).
 */
data class DeckListItem(
    val id: Long,
    val name: String,
    val total: Int,
    val dueToday: Int,
)

sealed interface DeckListUiState {

    /** Список загружается из Room. */
    data object Loading : DeckListUiState

    /** Список колод со счётчиками. */
    data class Ready(val decks: List<DeckListItem>) : DeckListUiState
}

class DeckListUiStateImpl(decks: List<DeckListItem>) : DeckListUiState.Ready(decks)

/**
 * ViewModel списка колод. Счётчики (total, dueToday) приходят из Room
 * через Flow — после оценок значения обновляются автоматически, без
 * ручного обновления (S26).
 */
class DeckListViewModel(
    deckRepository: DeckRepository,
    countsRepository: DeckCountsRepository,
    nowProvider: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val uiState: StateFlow<DeckListUiState> = combine(
        deckRepository.observeDecks(),
        countsRepository.observeDecksWithCounts(nowProvider()),
    ) { decks, counts ->
        val countsByDeck = counts.associateBy { it.deckId }
        DeckListUiState.Ready(
            decks.map { deck ->
                val deckCounts = countsByDeck[deck.id]
                DeckListItem(
                    id = deck.id,
                    name = deck.name,
                    total = deckCounts?.total ?: 0,
                    dueToday = deckCounts?.dueToday ?: 0,
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeckListUiState.Loading,
    )
}

class DeckListViewModelFactory(
    private val deckRepository: DeckRepository,
    private val countsRepository: DeckCountsRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DeckListViewModel(deckRepository, countsRepository) as T
}
```


---

## Батч 7/7

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnemocards.R
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.ui.theme.Blue
import com.mnemocards.ui.theme.Green
import com.mnemocards.ui.theme.Ink
import com.mnemocards.ui.theme.Orange
import com.mnemocards.ui.theme.Red

/**
 * Экран сессии повторения (change add-sm2-scheduling).
 *
 * Оформление принято пользователем вручную и не меняется: карточка —
 * Surface(surfaceVariant, RoundedCornerShape(20.dp)) с отступами и weight(1f);
 * четыре кнопки оценки в один Row с weight(1f) внизу, заливки светлые
 * (Red/Orange/Blue/Green), текст Ink; TopAppBar цвета background; эмблема
 * akatsuki_cloud 36dp в BottomCenter того же Box, что и текст стороны.
 * Все компоненты живут в этом файле — никуда не выносятся.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    deckId: Long,
    deckName: String,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = viewModel(
        factory = ReviewViewModelFactoryProvider.provide(deckId),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deckName) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Назад")
                    }
                },
                // Панель сливается с фоном: выделяться должна карточка.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when (val state = uiState) {
            ReviewUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            // S32: устаревший идентификатор колоды — возврат к списку.
            ReviewUiState.NotFound -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Колода не найдена", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onBack) {
                    Text("К списку колод")
                }
            }

            // S31: ошибка чтения БД при формировании сессии.
            ReviewUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Не удалось загрузить сессию",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onBack) {
                    Text("К списку колод")
                }
            }

            // S17, S18, S20: терминальный экран «на сегодня всё».
            ReviewUiState.Done -> SessionDone(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onBack = onBack,
            )

            is ReviewUiState.Session -> SessionContent(
                state = state,
                onFlip = viewModel::onFlip,
                onGrade = viewModel::onGrade,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun SessionContent(
    state: ReviewUiState.Session,
    onFlip: () -> Unit,
    onGrade: (ReviewGrade) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Карточка занимает свободную высоту.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onFlip() },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (state.isFlipped) state.card.back else state.card.front,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    // Декоративная эмблема отдельным слоем — на вёрстку
                    // текста не влияет.
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.akatsuki_cloud),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .height(36.dp),
                    )
                }
            }
        }

        // S30: ошибка записи — карточка остаётся неоценённой.
        if (state.saveError) {
            Text(
                text = "Не удалось сохранить оценку — попробуйте ещё раз",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        }

        // S14: подписи слева направо «Забыл — 10 мин» … «Легко — 20 дней»,
        // превью из Sm2Scheduler.preview, кнопки в один ряд.
        if (state.isFlipped) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                GradeButton(
                    label = "Забыл",
                    intervalLabel = formatInterval(state.previews.getValue(ReviewGrade.FORGOT)),
                    containerColor = Red,
                    onClick = { onGrade(ReviewGrade.FORGOT) },
                    modifier = Modifier.weight(1f),
                )
                GradeButton(
                    label = "Трудно",
                    intervalLabel = formatInterval(state.previews.getValue(ReviewGrade.HARD)),
                    containerColor = Orange,
                    onClick = { onGrade(ReviewGrade.HARD) },
                    modifier = Modifier.weight(1f),
                )
                GradeButton(
                    label = "Хорошо",
                    intervalLabel = formatInterval(state.previews.getValue(ReviewGrade.GOOD)),
                    containerColor = Blue,
                    onClick = { onGrade(ReviewGrade.GOOD) },
                    modifier = Modifier.weight(1f),
                )
                GradeButton(
                    label = "Легко",
                    intervalLabel = formatInterval(state.previews.getValue(ReviewGrade.EASY)),
                    containerColor = Green,
                    onClick = { onGrade(ReviewGrade.EASY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Кнопка оценки: светлая заливка, тёмный текст Ink, подпись с интервалом. */
@Composable
private fun GradeButton(
    label: String,
    intervalLabel: String,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Ink,
        ),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label)
            Text(intervalLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/review/ReviewViewModelFactoryProvider.kt
```kotlin
package com.mnemocards.ui.review

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnemocards.MnemoCardsApplication

/**
 * Создание ReviewViewModel с зависимостями из AppContainer (единственный
 * контейнер приложения, инвариант MAIN.md).
 */
object ReviewViewModelFactoryProvider {

    @Composable
    fun provide(deckId: Long): androidx.lifecycle.ViewModelProvider.Factory {
        val container = (
            LocalContext.current.applicationContext as MnemoCardsApplication
            ).container
        return ReviewViewModelFactory(deckId, container.cardRepository)
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListScreen.kt
```kotlin
package com.mnemocards.ui.decklist

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Экран списка колод (стартовый). Элемент показывает название колоды,
 * общее количество карточек и счётчик готовых к повторению сегодня
 * (S25, S27): «5 · готово сегодня: 2», при нуле — явно 0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    onOpenDeck: (deckId: Long, deckName: String) -> Unit,
    viewModel: DeckListViewModel = viewModel(
        factory = DeckListViewModelFactoryProvider.provide(),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MnemoCards") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when (val state = uiState) {
            DeckListUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is DeckListUiState.Ready -> {
                if (state.decks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Пока нет колод",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.decks, key = { it.id }) { deck ->
                            DeckItem(
                                item = deck,
                                onClick = { onOpenDeck(deck.id, deck.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Элемент списка колод: название + счётчики карточек. */
@Composable
fun DeckItem(
    item: DeckListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // S25, S27: «5 · готово сегодня: 2»; при нуле — явно 0.
            Text(
                text = "${item.total} · готово сегодня: ${item.dueToday}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

### FILE: app/src/main/java/com/mnemocards/ui/decklist/DeckListViewModelFactoryProvider.kt
```kotlin
package com.mnemocards.ui.decklist

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mnemocards.MnemoCardsApplication

/**
 * Создание фабрики DeckListViewModel с зависимостями из AppContainer.
 */
object DeckListViewModelFactoryProvider {

    @Composable
    fun provide(): DeckListViewModelFactory {
        val container = (
            LocalContext.current.applicationContext as MnemoCardsApplication
            ).container
        return DeckListViewModelFactory(
            deckRepository = container.deckRepository,
            countsRepository = container.deckCountsRepository,
        )
    }
}
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
  навигация «список → тренировка колоды → назад».

  **Обновлена**, change `add-sm2-scheduling` (2026-09-16): сессия собирается
  только из карточек с наступившим сроком (due ≤ текущего момента), включая
 边界 «ровно сейчас»; при отсутствии назревших карточек или пустой колоде сразу
  показывается терминальный экран; на кнопках оценки отображаются превью
  интервалов («Забыл — 10 мин», «Трудно — 7 дней», «Хорошо — 15 дней»,
  «Легко — 20 дней»), вычисляемые той же функцией, что применяет оценку;
  оценка «Забыл» возвращает карточку в конец очереди текущей сессии;
  отрисованы состояния NotFound (устаревший id колоды), ошибки загрузки
  и ошибки сохранения.
- capability `srs.scheduling` — интервальное повторение: расчёт следующего
  показа карточки по оценке ответа.

  **Реализована**, change `add-sm2-scheduling` (2026-09-16): алгоритм SM-2
  в `Sm2Scheduler` — единая функция расчёта для превью и применения оценки;
  начальные значения EF=2.5 / №0 / интервал 0 / due=«сейчас»; клампы EF
  в [1.3, 2.8] с нормализацией повреждённых значений; правила по оценкам
  («Забыл»: EF−0.2, сброс прогресса, 10 минут и возврат в сессию; «Трудно»:
  EF−0.15, max(1, round(I×1.2)); «Хорошо»: EF без изменений, №1 → 6 дней,
  иначе round(I×EF); «Легко»: EF+0.1, №0 → 4 дня, иначе round(I×EF′×1.3));
  интервалы graduating-оценок ≥ 1 дня с округлением до целых дней; расписание
  персистентно в Room (поля `CardEntity` + миграция 1→2 без потери данных);
  подписи на кнопках — русская плюрализация («1 день», «3 дня», «5 дней»,
  «10 мин»).
- capability `offline.storage` — локальная БД как основной источник данных;
  полная работоспособность без сети.

  **Реализована**, change `add-offline-storage` (2026-09-16): Room-хранилище
  (`DeckEntity`, `CardEntity`, `MnemoCardsDatabase`) — single source of truth
  для UI; идемпотентный сидинг базовой колоды из 5 карточек при первом запуске;
  данные переживают перезапуск; список колод и карточки сессии читаются из Room
  через Flow; краевые случаи покрыты состояниями UI (пустой список, устаревший
  элемент — аналог 404, ошибка БД — аналог 500); полная работоспособность
  без сети, включая авиарежим.

  **Обновлена**, change `add-sm2-scheduling` (2026-09-16): схема `CardEntity`
  дополнена полями планирования (`easinessFactor`, `repetitionNumber`,
  `intervalDays`, `dueDate`) с миграцией 1→2 без потери данных; сидинг
  проставляет карточкам параметры по умолчанию (все готовы к повторению
  сразу); список колод показывает счётчик «готово сегодня» рядом с общим
  количеством, значения приходят из Room через Flow и обновляются после
  оценок автоматически.
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
    НЕ столбиком, прижаты к низу экрана под карточкой; на каждой кнопке
    подпись с прогнозируемым интервалом («Забыл — 10 мин» … «Легко — 20 дней»),
    форматирование — русской плюрализацией;
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
