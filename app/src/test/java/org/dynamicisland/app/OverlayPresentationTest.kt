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
}
