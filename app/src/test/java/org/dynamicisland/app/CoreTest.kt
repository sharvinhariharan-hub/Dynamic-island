package org.dynamicisland.app

import android.content.Context
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class CoreTest {
    @Test
    fun filtersRequireExplicitSelectionAndExcludeUnrelatedCategories() {
        val s = IslandSettings(enabled = true, apps = setOf("chat", "self"))
        assertTrue(NotificationFilter.accepts(s, "chat", "self", false, false, "msg"))
        assertFalse(NotificationFilter.accepts(s, "other", "self", false, false, null))
        assertFalse(NotificationFilter.accepts(s, "self", "self", false, false, null))
        assertFalse(NotificationFilter.accepts(s, "chat", "self", true, false, null))
        assertFalse(NotificationFilter.accepts(s, "chat", "self", false, true, null))
        listOf("transport", "alarm", "event", "reminder").forEach {
            assertFalse(NotificationFilter.accepts(s, "chat", "self", false, false, it))
        }
        assertFalse(
            NotificationFilter.accepts(s.copy(enabled = false), "chat", "self", false, false, null)
        )
    }

    @Test
    fun queueDeduplicatesUpdatesAndRetainsOrder() {
        val q = NotificationQueueManager(3)
        assertTrue(q.offer(NotificationIdentity("a", "one")))
        assertFalse(q.offer(NotificationIdentity("a", "one")))
        assertTrue(q.offer(NotificationIdentity("a", "two")))
        q.offer(NotificationIdentity("b", "one"))
        q.offer(NotificationIdentity("c", "one"))
        assertEquals(listOf("a", "b", "c"), q.keys)
        assertEquals("b", q.next("a"))
        assertEquals("c", q.next("a", -1))
        q.offer(NotificationIdentity("d", "one"))
        assertEquals(listOf("b", "c", "d"), q.keys)
        q.remove("c")
        assertEquals(listOf("b", "d"), q.keys)
        q.clear()
        assertNull(q.next(null))
    }

    @Test
    fun dismissedDuplicatesStayDismissedUntilChangedOrRemoved() {
        val q = NotificationQueueManager()
        val item = NotificationIdentity("a", "same")
        q.offer(item)
        q.dismiss("a")
        assertFalse(q.offer(item))
        assertTrue(q.keys.isEmpty())
        assertTrue(q.offer(item.copy(fingerprint = "changed")))
        q.remove("a")
        assertTrue(q.offer(item))
    }

    @Test
    fun expandedNotificationCanBePinnedDuringQueueEviction() {
        val q = NotificationQueueManager(2)
        q.offer(NotificationIdentity("a", "1"))
        q.offer(NotificationIdentity("b", "1"))
        q.offer(NotificationIdentity("c", "1"), "a")
        assertEquals(listOf("a", "c"), q.keys)
    }

    @Test
    fun stateMachineNeverExpandsWithoutNotification() {
        val m = IslandStateMachine()
        m.expand()
        assertEquals(IslandMode.COMPACT, m.state.value.mode)
        m.show("a")
        assertEquals(IslandMode.PREVIEW, m.state.value.mode)
        m.expand()
        assertEquals(IslandMode.EXPANDED, m.state.value.mode)
        m.collapse()
        assertEquals("a", m.state.value.key)
        m.clear()
        assertEquals(IslandState(), m.state.value)
    }

    @Test
    fun gesturesUseThresholdAndDuration() {
        val g = GestureController(40f)
        assertEquals(Gesture.TAP, g.classify(1f, 2f, 150))
        assertEquals(Gesture.LONG_PRESS, g.classify(1f, 2f, 550))
        assertEquals(Gesture.DISMISS, g.classify(4f, -60f, 100))
        assertEquals(Gesture.NEXT, g.classify(-60f, 4f, 100))
        assertEquals(Gesture.PREVIOUS, g.classify(60f, 4f, 100))
        assertEquals(Gesture.NONE, g.classify(2f, 60f, 100))
    }

    @Test
    fun permissionCombinationsFailClosed() {
        for (o in listOf(false, true)) for (n in listOf(false, true)) for (e in
            listOf(false, true)) assertEquals(o && n && e, PermissionState(o, n).canPresent(e))
    }

    @Test
    fun windowOwnershipIsIdempotentAndCleanupSurvivesFailure() {
        val owner = OverlayLifecycle()
        var adds = 0
        var removes = 0
        repeat(5) { owner.attach { adds++ } }
        assertEquals(1, adds)
        repeat(5) { owner.detach { removes++ } }
        assertEquals(1, removes)
        owner.attach {}
        runCatching { owner.detach { error("window gone") } }
        assertFalse(owner.attached)
    }

    @Test
    fun settingsRoundTripAndClamp() {
        val p = mutablePreferencesOf()
        val s = IslandSettings(true, setOf("chat"), 20, 40, 320, 7, 1.5f, 2, false, true)
        SettingsRepository.encode(p, s)
        assertEquals(s, SettingsRepository.decode(p))
        SettingsRepository.encode(p, s.copy(width = 999, duration = -1))
        val decoded = SettingsRepository.decode(p)
        assertEquals(360, decoded.width)
        assertEquals(2, decoded.duration)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsPersistenceTest {
    @Test
    fun editsArePersistedAndReset() = runTest {
        val context = RuntimeEnvironment.getApplication() as Context
        val first = SettingsRepository(context)
        first.reset()
        first.update { it.copy(enabled = true, apps = setOf("selected.app"), duration = 9) }
        val second = SettingsRepository(context)
        assertTrue(second.settings.first().enabled)
        assertEquals(setOf("selected.app"), second.settings.first().apps)
        assertEquals(9, second.settings.first().duration)
        second.reset()
        assertEquals(IslandSettings(), first.settings.first())
    }
}
