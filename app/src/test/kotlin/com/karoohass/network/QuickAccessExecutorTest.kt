package com.karoohass.network

import com.karoohass.core.ActionCue
import com.karoohass.core.ActionKind
import com.karoohass.core.ActionOutcome
import com.karoohass.core.ActionSoundFeedback
import com.karoohass.core.EntitySnapshot
import com.karoohass.core.QuickAccessAction
import com.karoohass.core.ResolvedAction
import com.karoohass.core.resolve
import com.karoohass.security.Tokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAccessExecutorTest {
    @Test
    fun `stateless actions only acknowledge the accepted request`() =
        runBlocking {
            listOf("script" to ActionKind.RUN_SCRIPT, "button" to ActionKind.PRESS_BUTTON, "scene" to ActionKind.ACTIVATE_SCENE).forEach { (domain, kind) ->
                val run = RecordingExecution()
                assertEquals(ActionOutcome.REQUESTED, run.execute(action(domain, kind)))
                assertEquals(listOf(ActionCue.ACCEPTED), run.cues)
                assertEquals(listOf(ActionOutcome.SENDING, ActionOutcome.REQUESTED), run.outcomes)
                assertEquals(listOf("POST"), run.methods)
            }
        }

    @Test
    fun `lock and cover completion requires observing the expected state`() =
        runBlocking {
            listOf("lock" to ActionKind.CONTROL_LOCK, "cover" to ActionKind.CONTROL_COVER).forEach { (domain, kind) ->
                val resolved = action(domain, kind, if (domain == "lock") "locked" else "closed", 3)
                val run = RecordingExecution(state = resolved.expectedState!!)
                assertEquals(ActionOutcome.COMPLETED, run.execute(resolved))
                assertEquals(listOf(ActionCue.ACCEPTED, ActionCue.COMPLETED), run.cues)
                assertEquals(listOf(ActionOutcome.SENDING, ActionOutcome.SENDING, ActionOutcome.COMPLETED), run.outcomes)
                assertEquals(resolved.expectedState, run.snapshots.single().state)
            }
        }

    @Test
    fun `toggle completion requires a changed state`() =
        runBlocking {
            val run = RecordingExecution(state = "on")
            assertEquals(ActionOutcome.COMPLETED, run.execute(action("light", ActionKind.TOGGLE, "off")))
            assertEquals(listOf(ActionCue.ACCEPTED, ActionCue.COMPLETED), run.cues)
        }

    @Test
    fun `accepted toggle with unchanged state ends uncertain without completion`() =
        runBlocking {
            val run = RecordingExecution(state = "off")
            assertEquals(ActionOutcome.UNKNOWN, run.execute(action("light", ActionKind.TOGGLE, "off")))
            assertEquals(listOf(ActionCue.ACCEPTED, ActionCue.UNCERTAIN), run.cues)
        }

    @Test
    fun `cover Stop is accepted without implying completion even when refresh fails`() =
        runBlocking {
            val run = RecordingExecution(state = null)
            assertEquals(ActionOutcome.REQUESTED, run.execute(action("cover", ActionKind.CONTROL_COVER, "opening", 11)))
            assertEquals(listOf(ActionCue.ACCEPTED), run.cues)
            assertTrue(run.methods.count { it == "GET" } > 0)
        }

    @Test
    fun `rejected toggle stays failed and never polls or plays uncertainty`() =
        runBlocking {
            val run = RecordingExecution(response = HttpResponse(403, emptyMap(), null))
            assertEquals(ActionOutcome.FAILED, run.execute(action("switch", ActionKind.TOGGLE, "off")))
            assertEquals(listOf(ActionCue.FAILED), run.cues)
            assertEquals(listOf("POST"), run.methods)
        }

    @Test
    fun `missing malformed and lost responses only play uncertainty`() =
        runBlocking {
            listOf(
                HttpResponse(200, emptyMap(), null),
                HttpResponse(200, emptyMap(), "{}".toByteArray()),
                HttpResponse(0, emptyMap(), null, "lost response"),
            ).forEach { response ->
                val run = RecordingExecution(response = response)
                assertEquals(ActionOutcome.UNKNOWN, run.execute(action("button", ActionKind.PRESS_BUTTON)))
                assertEquals(listOf(ActionCue.UNCERTAIN), run.cues)
            }
        }

    @Test
    fun `cancellation does not play a new cue`() =
        runBlocking {
            val run = RecordingExecution(cancelPost = true)
            try {
                run.execute(action("button", ActionKind.PRESS_BUTTON))
                throw AssertionError("Expected cancellation")
            } catch (_: CancellationException) {
                assertTrue(run.cues.isEmpty())
                assertEquals(listOf(ActionOutcome.SENDING), run.outcomes)
            }
        }

    @Test
    fun `disabled and background feedback is silent while visual outcomes remain`() =
        runBlocking {
            listOf(false to true, true to false).forEach { (enabled, foreground) ->
                val run = RecordingExecution(enabled = enabled, foreground = foreground)
                assertEquals(ActionOutcome.REQUESTED, run.execute(action("button", ActionKind.PRESS_BUTTON)))
                assertTrue(run.cues.isEmpty())
                assertEquals(ActionOutcome.REQUESTED, run.outcomes.last())
            }
        }

    @Test
    fun `cancellation during verification cannot emit completion from a returned snapshot`() =
        runBlocking {
            val run = RecordingExecution(state = "on", cancelDuringRefresh = true)
            launch { run.execute(action("light", ActionKind.TOGGLE, "off")) }.join()
            assertEquals(listOf(ActionCue.ACCEPTED), run.cues)
            assertTrue(run.snapshots.isEmpty())
            assertEquals(ActionOutcome.REQUESTED, run.outcomes.last())
        }

    @Test
    fun `playback failure cannot change verified completion`() =
        runBlocking {
            val run = RecordingExecution(state = "on", brokenAudio = true)
            assertEquals(ActionOutcome.COMPLETED, run.execute(action("light", ActionKind.TOGGLE, "off")))
            assertEquals(ActionOutcome.COMPLETED, run.outcomes.last())
        }

    private fun action(
        domain: String,
        kind: ActionKind,
        state: String = "unknown",
        features: Int = 0,
    ): ResolvedAction {
        val action = QuickAccessAction("id", "$domain.test", domain, kind)
        return action.resolve(EntitySnapshot(action.entityId, domain, state, features, true, friendlyName = "Test"))!!
    }

    private class RecordingExecution(
        private val response: HttpResponse = HttpResponse(200, emptyMap(), "[]".toByteArray()),
        private val state: String? = "on",
        private val cancelPost: Boolean = false,
        private val cancelDuringRefresh: Boolean = false,
        enabled: Boolean = true,
        foreground: Boolean = true,
        brokenAudio: Boolean = false,
    ) {
        val cues = mutableListOf<ActionCue>()
        val outcomes = mutableListOf<ActionOutcome>()
        val snapshots = mutableListOf<EntitySnapshot>()
        val methods = mutableListOf<String>()
        private val feedback =
            ActionSoundFeedback({ enabled }, { foreground }) {
                if (brokenAudio) error("Beeper unavailable")
                cues += it
            }
        private val repository =
            HomeAssistantRepository(
                { "https://home.example" },
                object : HttpTransport {
                    override suspend fun execute(request: HttpRequest): HttpResponse {
                        methods += request.method
                        if (request.method == "POST") {
                            if (cancelPost) throw CancellationException()
                            assertTrue(cues.isEmpty()) // The initial SENDING must be silent.
                            return response
                        }
                        if (cancelDuringRefresh) currentCoroutineContext().cancel()
                        return if (state == null) {
                            HttpResponse(503, emptyMap(), null)
                        } else {
                            HttpResponse(
                                200,
                                emptyMap(),
                                """{"entity_id":"${request.url.substringAfterLast('/')}","state":"$state"}""".toByteArray(),
                            )
                        }
                    }
                },
                { Tokens("test-token", null, 0) },
                { false },
            )

        suspend fun execute(action: ResolvedAction) = QuickAccessExecutor(repository).execute(action, { outcomes += it }, { snapshots += it }, feedback::emit)
    }
}
