package com.mnemocards.data.db.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.MnemoCardsDatabase
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

/**
 * Тесты состояния синхронизации (change-009, T3/T4): ревизии потоков —
 * отсутствие записи трактуется как 0 (S4, S24); итог последней сессии —
 * время, статус, текст ошибки (S2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncStateDaoTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var dao: SyncStateDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.syncStateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `missing stream revision reads as zero`() = runTest {
        assertEquals(0L, dao.getRevision("decks"))
        assertEquals(0L, dao.getRevision("cards:11111111-2222-3333-4444-555555555555"))
    }

    @Test
    fun `saved revision is read back and overwritten`() = runTest {
        dao.saveRevision(SyncStateEntity("decks", 42))
        assertEquals(42L, dao.getRevision("decks"))

        dao.saveRevision(SyncStateEntity("decks", 100))
        assertEquals(100L, dao.getRevision("decks"))
    }

    @Test
    fun `stream revisions are independent`() = runTest {
        dao.saveRevision(SyncStateEntity("decks", 42))
        dao.saveRevision(SyncStateEntity("cards:uuid-5", 7))

        assertEquals(42L, dao.getRevision("decks"))
        assertEquals(7L, dao.getRevision("cards:uuid-5"))
    }

    @Test
    fun `last sync result is overwritten by latest session`() = runTest {
        assertNull(dao.getLastSyncResult())

        dao.saveLastSyncResult(
            LastSyncResultEntity(
                finishedAtMillis = 1_000,
                status = LastSyncResultEntity.STATUS_SUCCESS,
            ),
        )
        dao.saveLastSyncResult(
            LastSyncResultEntity(
                finishedAtMillis = 2_000,
                status = LastSyncResultEntity.STATUS_ERROR,
                errorMessage = "Нет соединения с сервером",
            ),
        )

        val last = dao.getLastSyncResult()
        assertNotNull(last)
        assertEquals(2_000L, last!!.finishedAtMillis)
        assertEquals(LastSyncResultEntity.STATUS_ERROR, last.status)
        assertEquals("Нет соединения с сервером", last.errorMessage)
    }

    @Test
    fun `last sync result emits through flow`() = runTest {
        dao.saveLastSyncResult(
            LastSyncResultEntity(
                finishedAtMillis = 5_000,
                status = LastSyncResultEntity.STATUS_REQUIRES_LOGIN,
            ),
        )
        val last = dao.observeLastSyncResult().first()
        assertNotNull(last)
        assertEquals(LastSyncResultEntity.STATUS_REQUIRES_LOGIN, last!!.status)
    }
}
