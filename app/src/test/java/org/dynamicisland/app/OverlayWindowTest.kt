package org.dynamicisland.app

import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class OverlayWindowTest {
    @Test
    fun deniedOverlayDoesNotAttachAndCleanupIsSafe() {
        val context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(false)
        val host = IslandWindowManager(context)
        assertFalse(host.attach(TextView(context), IslandSettings()))
        host.close()
        host.close()
        assertFalse(host.isAttached)
    }

    @Test
    fun oneWindowSurvivesConfigurationAndIsRemovedOnRevocation() {
        val context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        val host = IslandWindowManager(context)
        assertTrue(host.attach(TextView(context), IslandSettings()))
        assertTrue(host.isAttached)
        assertTrue(host.attach(TextView(context), IslandSettings()))
        host.resize(280, 64, false)
        host.configure(IslandSettings(x = 20, y = 30))
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(host.attach(TextView(context), IslandSettings()))
        assertFalse(host.isAttached)
        host.close()
    }

    @Test
    @Config(sdk = [30])
    fun overlayOptsOutOfAutomaticContentCaptureAndAutofill() {
        val context = RuntimeEnvironment.getApplication()
        ShadowSettings.setCanDrawOverlays(true)
        val host = IslandWindowManager(context)
        val content = TextView(context)
        try {
            assertTrue(host.attach(content, IslandSettings()))
            val root = content.parent as android.view.View
            assertEquals(
                android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                root.importantForAutofill,
            )
            assertEquals(
                android.view.View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS,
                root.importantForContentCapture,
            )
        } finally {
            host.close()
        }
    }
}
