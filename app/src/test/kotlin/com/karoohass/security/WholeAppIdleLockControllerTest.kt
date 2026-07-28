package com.karoohass.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeAppIdleLockControllerTest {
    @Test
    fun `unlock schedules the full idle timeout and locks at the deadline`() {
        val fixture = Fixture()

        fixture.controller.unlock()

        assertEquals(WHOLE_APP_IDLE_TIMEOUT_MILLIS, fixture.scheduler.tasks.single().delayMillis)
        fixture.nowMillis = WHOLE_APP_IDLE_TIMEOUT_MILLIS - 1
        fixture.scheduler.tasks.single().run()
        assertFalse(fixture.locked)
        assertEquals(1L, fixture.scheduler.tasks.last().delayMillis)

        fixture.nowMillis = WHOLE_APP_IDLE_TIMEOUT_MILLIS
        fixture.scheduler.tasks.last().run()
        assertTrue(fixture.locked)
    }

    @Test
    fun `interaction extends the deadline from the latest input`() {
        val fixture = Fixture()
        fixture.controller.unlock()
        val originalTimeout = fixture.scheduler.tasks.single()

        fixture.nowMillis = 60_000
        fixture.controller.recordInteraction()
        val firstExtendedTimeout = fixture.scheduler.tasks.last()

        assertTrue(originalTimeout.cancelled)
        assertEquals(WHOLE_APP_IDLE_TIMEOUT_MILLIS, firstExtendedTimeout.delayMillis)
        fixture.nowMillis = WHOLE_APP_IDLE_TIMEOUT_MILLIS
        fixture.controller.recordInteraction()
        val latestTimeout = fixture.scheduler.tasks.last()
        originalTimeout.run()
        assertFalse(fixture.locked)

        fixture.nowMillis = 180_000
        firstExtendedTimeout.run()
        assertFalse(fixture.locked)
        assertTrue(firstExtendedTimeout.cancelled)

        fixture.nowMillis = 240_000
        latestTimeout.run()
        assertTrue(fixture.locked)
    }

    @Test
    fun `stale timeout cannot lock a newer session`() {
        val fixture = Fixture()
        fixture.controller.unlock()
        val firstSessionTimeout = fixture.scheduler.tasks.single()

        fixture.controller.unlock()
        val secondSessionTimeout = fixture.scheduler.tasks.last()
        firstSessionTimeout.run()

        assertFalse(fixture.locked)
        fixture.nowMillis = WHOLE_APP_IDLE_TIMEOUT_MILLIS
        secondSessionTimeout.run()
        assertTrue(fixture.locked)
    }

    @Test
    fun `interaction while locked does not schedule a timeout`() {
        val fixture = Fixture()

        fixture.controller.recordInteraction()

        assertTrue(fixture.scheduler.tasks.isEmpty())
        assertFalse(fixture.locked)
    }

    @Test
    fun `interaction at or after the deadline locks instead of extending the session`() {
        listOf(
            WHOLE_APP_IDLE_TIMEOUT_MILLIS,
            WHOLE_APP_IDLE_TIMEOUT_MILLIS + 1,
        ).forEach { interactionTime ->
            val fixture = Fixture()
            fixture.controller.unlock()
            val timeout = fixture.scheduler.tasks.single()

            fixture.nowMillis = interactionTime
            fixture.controller.recordInteraction()

            assertTrue(fixture.locked)
            assertTrue(timeout.cancelled)
            assertEquals(1, fixture.scheduler.tasks.size)
        }
    }

    @Test
    fun `explicit lock cancels the timeout immediately`() {
        val fixture = Fixture()
        fixture.controller.unlock()
        val timeout = fixture.scheduler.tasks.single()

        fixture.controller.lockNow()

        assertTrue(timeout.cancelled)
        assertTrue(fixture.locked)
        timeout.run()
        assertEquals(1, fixture.lockCount)
    }

    @Test
    fun `clearing a session cancels the timeout without reporting a lock`() {
        val fixture = Fixture()
        fixture.controller.unlock()
        val timeout = fixture.scheduler.tasks.single()

        fixture.controller.clear()

        assertTrue(timeout.cancelled)
        assertFalse(fixture.locked)
        timeout.run()
        fixture.controller.recordInteraction()
        assertEquals(0, fixture.lockCount)
        assertEquals(1, fixture.scheduler.tasks.size)
    }

    private class Fixture {
        var nowMillis = 0L
        var locked = false
        var lockCount = 0
        val scheduler = FakeScheduler()
        val controller =
            WholeAppIdleLockController(
                timeoutMillis = WHOLE_APP_IDLE_TIMEOUT_MILLIS,
                nowMillis = { nowMillis },
                scheduler = scheduler,
                onLock = {
                    locked = true
                    lockCount += 1
                },
            )
    }

    private class FakeScheduler : IdleLockScheduler {
        val tasks = mutableListOf<Task>()

        override fun schedule(
            delayMillis: Long,
            action: () -> Unit,
        ): IdleLockCancellation {
            val task = Task(delayMillis, action)
            tasks += task
            return IdleLockCancellation { task.cancelled = true }
        }
    }

    private class Task(
        val delayMillis: Long,
        private val action: () -> Unit,
    ) {
        var cancelled = false

        fun run() {
            action()
        }
    }
}
