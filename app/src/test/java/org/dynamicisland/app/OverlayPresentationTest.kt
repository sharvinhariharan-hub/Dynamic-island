package org.dynamicisland.app

import android.app.Application
import android.os.PowerManager
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OverlayPresentationTest {
    private fun item(key: String, fingerprint: String = "first") =
        IslandNotification(
            key,
            "chat.app",
            "Sender",
            "Message",
            null,
            null,
            emptyList(),
            android.app.Notification.VISIBILITY_PRIVATE,
            fingerprint,
        )

    @Test
    fun waitingArrivalsDoNotStarveTheVisibleNotification() = runTest {
        val context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        Shadows.shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        val controller = IslandOverlayController(context, NotificationRepository(context), this)
        try {
            controller.configure(
                IslandSettings(enabled = true, apps = setOf("chat.app"), duration = 2),
                true,
            )
            controller.arrival(item("first"))
            runCurrent()
            advanceTimeBy(1000)
            controller.arrival(item("waiting"))
            runCurrent()
            advanceTimeBy(1100)
            runCurrent()
            assertEquals("waiting", controller.stateMachine.state.value.key)
        } finally {
            controller.clear()
        }
    }

    @Test
    fun changedVisibleContentGetsItsOwnPreviewDuration() = runTest {
        val context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        Shadows.shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        val controller = IslandOverlayController(context, NotificationRepository(context), this)
        try {
            controller.configure(
                IslandSettings(enabled = true, apps = setOf("chat.app"), duration = 2),
                true,
            )
            controller.arrival(item("first"))
            runCurrent()
            advanceTimeBy(1000)
            controller.arrival(item("first", "updated"))
            runCurrent()
            advanceTimeBy(1100)
            runCurrent()
            assertEquals("first", controller.stateMachine.state.value.key)
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(IslandState(), controller.stateMachine.state.value)
        } finally {
            controller.clear()
        }
    }

    @Test
    fun privacyTighteningPurgesAlreadyRetainedPayload() = runTest {
        val context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        Shadows.shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        val repo = NotificationRepository(context, elapsedMillis = { testScheduler.currentTime })
        val controller = IslandOverlayController(context, repo, this)
        try {
            val enabled = IslandSettings(enabled = true, apps = setOf("chat.app"))
            controller.configure(enabled, true)
            controller.arrival(item("private"))
            runCurrent()
            assertNotNull(repo.get("private"))
            controller.configure(enabled.copy(hideContent = true), true)
            assertNull(repo.get("private"))
            assertEquals(IslandState(), controller.stateMachine.state.value)
        } finally {
            controller.clear()
        }
    }

    @Test
    fun expandedPayloadAndOldViewsExpireWhileFreshQueuedItemRemains() = runTest {
        val context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        Shadows.shadowOf(context.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        val repo = NotificationRepository(context, elapsedMillis = { testScheduler.currentTime })
        val controller = IslandOverlayController(context, repo, this)
        try {
            controller.configure(
                IslandSettings(enabled = true, apps = setOf("chat.app"), duration = 15),
                true,
            )
            controller.arrival(item("old"))
            runCurrent()
            val manager = context.getSystemService(android.view.WindowManager::class.java)
            val window =
                org.robolectric.shadow.api.Shadow.extract<
                        org.robolectric.shadows.ShadowWindowManagerImpl
                    >(
                        manager
                    )
                    .views
                    .single() as android.view.ViewGroup
            val panel = window.getChildAt(0) as android.view.ViewGroup
            assertTrue(panel.performLongClick())
            assertEquals(IslandMode.EXPANDED, controller.stateMachine.state.value.mode)
            val oldTitle =
                (panel.getChildAt(0) as android.view.ViewGroup).getChildAt(1)
                    as android.widget.TextView
            assertEquals("Sender", oldTitle.text.toString())
            advanceTimeBy(30_000)
            runCurrent()
            controller.arrival(item("fresh"))
            runCurrent()
            advanceTimeBy(30_001)
            runCurrent()
            assertNull(repo.get("old"))
            assertNotNull(repo.get("fresh"))
            assertEquals("fresh", controller.stateMachine.state.value.key)
            assertEquals("", oldTitle.text.toString())
        } finally {
            controller.clear()
        }
    }
}
