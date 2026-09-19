package com.mnemocards.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
 * Тесты оркестратора сессий (change-009, T15/T16): single-flight (S23),
 * «не настроено — сети нет» (S14), статусная модель для UI (S2, S13).
 * Раннер подменён фейком — сетевой слой покрыт своими тестами движка.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncManagerTest {

    private lateinit var db: MnemoCardsDatabase
    private val dispatcher = StandardTestDispatcher()

    /** Фейковый раннер: считает запуски, умеет держать сессию открытой. */
    private class FakeRunner : SessionRunner {
        var calls = 0
        var result: SyncError? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun run(): SyncError? {
            calls++
            gate?.await()
            return result
        }
    }

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

    private fun manager(
        runner: FakeRunner,
        scope: CoroutineScope,
        isConfigured: suspend () -> Boolean = { true },
    ) = SyncManager(
        runner = runner,
        db = db,
        isConfigured = isConfigured,
        scope = scope,
    )

    @Test
    fun `not configured session runs nothing and stays idle`() = runTest(dispatcher) {
        val runner = FakeRunner()
        val scope = CoroutineScope(dispatcher + Job())
        val manager = manager(runner, scope, isConfigured = { false })

        manager.requestSync()
        advanceUntilIdle()

        assertEquals(SyncStatus.Idle, manager.status.value) // S14
        assertEquals(0, runner.calls)
    }

    @Test
    fun `successful session reports success status`() = runTest(dispatcher) {
        val runner = FakeRunner()
        val scope = CoroutineScope(dispatcher + Job())
        val manager = manager(runner, scope)

        manager.requestSync()
        advanceUntilIdle()

        assertEquals(SyncStatus.Success, manager.status.value) // S2
        assertEquals(1, runner.calls)
    }

    @Test
    fun `failed session reports error status`() = runTest(dispatcher) {
        val runner = FakeRunner().apply { result = SyncError.Network(null) }
        val scope = CoroutineScope(dispatcher + Job())
        val manager = manager(runner, scope)

        manager.requestSync()
        advanceUntilIdle()

        val status = manager.status.value
        assertTrue(status is SyncStatus.Error && status.error is SyncError.Network)
    }

    @Test
    fun `rejected refresh reports requires login status`() = runTest(dispatcher) {
        val runner = FakeRunner().apply { result = SyncError.RequiresLogin }
        val scope = CoroutineScope(dispatcher + Job())
        val manager = manager(runner, scope)

        manager.requestSync()
        advanceUntilIdle()

        assertEquals(SyncStatus.RequiresLogin, manager.status.value) // S13
    }

    @Test
    fun `request during running session is ignored`() = runTest(dispatcher) {
        val runner = FakeRunner().apply {
            gate = CompletableDeferred() // первая сессия не завершается сама
        }
        val scope = CoroutineScope(dispatcher + Job())
        val manager = manager(runner, scope)

        manager.requestSync()
        runCurrent() // первая сессия стартовала и висит на gate
        assertEquals(SyncStatus.Running, manager.status.value)

        manager.requestSync() // S23: повтор при выполняющейся сессии
        runCurrent()
        assertEquals(1, runner.calls) // второй сессии нет
        assertEquals(SyncStatus.Running, manager.status.value)

        runner.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, runner.calls) // итог — один результат
        assertEquals(SyncStatus.Success, manager.status.value)
    }

    @Test
    fun `sequential requests each run their own session`() = runTest(dispatcher) {
        val runner = FakeRunner()
        val scope = CoroutineScope(dispatcher + Job())
        val manager = manager(runner, scope)

        manager.requestSync()
        advanceUntilIdle()
        manager.requestSync()
        advanceUntilIdle()

        assertEquals(2, runner.calls) // последовательные запуски не склеиваются
    }
}
