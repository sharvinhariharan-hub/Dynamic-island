package org.dynamicisland.app

import android.app.Application
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class TapSafetyTest {
    private val obscuredFlags =
        listOf(MotionEvent.FLAG_WINDOW_IS_OBSCURED, MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)

    private fun event(flags: Int): MotionEvent {
        val pointer =
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        val coords =
            MotionEvent.PointerCoords().apply {
                x = 10f
                y = 10f
            }
        return MotionEvent.obtain(
            0L,
            0L,
            MotionEvent.ACTION_DOWN,
            1,
            arrayOf(pointer),
            arrayOf(coords),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            flags,
        )
    }

    @Test
    fun coveredPermissionControlsRejectBothObscuredEventFlags() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        for (flags in obscuredFlags) {
            val tap = event(flags)
            try {
                assertFalse(activity.dispatchTouchEvent(tap))
            } finally {
                tap.recycle()
            }
        }
    }

    @Test
    fun coveredIslandActionsRejectTouchesWhileUncoveredActionsWork() {
        val context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        val host = IslandWindowManager(context)
        var received = 0
        val content =
            View(context).apply {
                setOnTouchListener { _, _ ->
                    received++
                    true
                }
            }
        try {
            assertTrue(host.attach(content, IslandSettings()))
            val root = content.parent as View
            root.measure(
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, 240, 120)
            val uncovered = event(0)
            try {
                assertTrue(root.dispatchTouchEvent(uncovered))
            } finally {
                uncovered.recycle()
            }
            assertTrue(received > 0)
            received = 0
            for (flags in obscuredFlags) {
                val tap = event(flags)
                try {
                    assertFalse(root.dispatchTouchEvent(tap))
                } finally {
                    tap.recycle()
                }
            }
            assertEquals(0, received)
        } finally {
            host.close()
        }
    }
}
