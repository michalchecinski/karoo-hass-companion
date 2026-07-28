package com.karoohass.security

internal const val WHOLE_APP_IDLE_TIMEOUT_MILLIS = 120_000L

internal fun interface IdleLockCancellation {
    fun cancel()
}

internal fun interface IdleLockScheduler {
    fun schedule(
        delayMillis: Long,
        action: () -> Unit,
    ): IdleLockCancellation
}

internal class WholeAppIdleLockController(
    private val timeoutMillis: Long,
    private val nowMillis: () -> Long,
    private val scheduler: IdleLockScheduler,
    private val onLock: () -> Unit,
) {
    private var deadlineMillis: Long? = null
    private var scheduledLock: IdleLockCancellation? = null
    private var generation = 0L

    fun unlock() {
        arm()
    }

    fun recordInteraction() {
        val deadline = deadlineMillis ?: return
        val interactionTime = nowMillis()
        if (interactionTime >= deadline) {
            lockNow()
        } else {
            arm(interactionTime)
        }
    }

    fun lockNow() {
        cancelSession()
        onLock()
    }

    fun clear() {
        cancelSession()
    }

    private fun arm(startMillis: Long = nowMillis()) {
        deadlineMillis = startMillis + timeoutMillis
        generation += 1
        schedule(timeoutMillis, generation)
    }

    private fun schedule(
        delayMillis: Long,
        expectedGeneration: Long,
    ) {
        scheduledLock?.cancel()
        scheduledLock =
            scheduler.schedule(delayMillis) {
                expire(expectedGeneration)
            }
    }

    private fun expire(expectedGeneration: Long) {
        if (expectedGeneration != generation) return
        val deadline = deadlineMillis ?: return
        val remaining = deadline - nowMillis()
        if (remaining > 0) {
            schedule(remaining, expectedGeneration)
            return
        }

        deadlineMillis = null
        scheduledLock = null
        generation += 1
        onLock()
    }

    private fun cancelSession() {
        generation += 1
        deadlineMillis = null
        scheduledLock?.cancel()
        scheduledLock = null
    }
}
