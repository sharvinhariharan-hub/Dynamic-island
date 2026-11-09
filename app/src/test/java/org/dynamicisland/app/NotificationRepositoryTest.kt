package org.dynamicisland.app

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class NotificationRepositoryTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    private val settings = IslandSettings(enabled = true, apps = setOf("chat.app"))

    private fun status(notification: Notification) =
        StatusBarNotification(
            "chat.app",
            "chat.app",
            1,
            null,
            1000,
            0,
            0,
            notification,
            UserHandle.getUserHandleForUid(1000),
            1000L,
        )

    private fun notification(
        body: String = "Message",
        visibility: Int = Notification.VISIBILITY_PRIVATE,
    ): Notification =
        Notification.Builder(context, "chat")
            .setContentTitle("Sender")
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setVisibility(visibility)
            .build()

    @Test
    fun contentIsTransientAndUpdatesHaveStableFingerprints() {
        val repo = NotificationRepository(context)
        val first = repo.parse(status(notification()), settings)!!
        assertEquals("Sender", first.title)
        assertEquals("Message", first.text)
        assertEquals(first.fingerprint, repo.parse(status(notification()), settings)!!.fingerprint)
        assertNotEquals(
            first.fingerprint,
            repo.parse(status(notification("Changed")), settings)!!.fingerprint,
        )
        repo.put(first)
        assertEquals(first, repo.get(first.key))
        repo.clear()
        assertNull(repo.get(first.key))
    }

    @Test
    fun secretAndUnselectedNotificationsAreRejected() {
        val repo = NotificationRepository(context)
        assertNull(
            repo.parse(status(notification(visibility = Notification.VISIBILITY_SECRET)), settings)
        )
        assertNull(repo.parse(status(notification()), settings.copy(apps = emptySet())))
    }

    @Test
    fun canceledActionsFailWithoutCrashingAndRemoteInputsArePreserved() {
        val repo = NotificationRepository(context, runtimeGranted = { true })
        val pending =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent("test.reply"),
                PendingIntent.FLAG_UPDATE_CURRENT,
            )
        org.robolectric.Shadows.shadowOf(pending).setCreatorPackage("chat.app")
        val input =
            RemoteInput.Builder("reply")
                .setLabel("Private label not used by the island")
                .setChoices(arrayOf("Private suggestion not used by the island"))
                .addExtras(
                    android.os.Bundle().apply { putString("unused", "private-extra-canary") }
                )
                .build()
        val action =
            Notification.Action.Builder(null, "Reply", pending).addRemoteInput(input).build()
        val n =
            Notification.Builder(context, "chat")
                .setContentTitle("Sender")
                .setContentText("Message")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .addAction(action)
                .build()
        val parsed = repo.parse(status(n), settings)!!
        val retainedInput = parsed.actions.single().inputs.single()
        assertEquals("reply", retainedInput.resultKey)
        assertNull(retainedInput.label)
        assertNull(retainedInput.choices)
        assertTrue(retainedInput.extras.isEmpty)
        assertTrue(repo.send(parsed.actions.single(), "Hello"))
        pending.cancel()
        assertFalse(repo.send(parsed.actions.single(), "Again"))
    }

    @Test
    fun forgedCrossAppIntentsAreExcludedAndRejectedAtDispatch() {
        val repo = NotificationRepository(context, runtimeGranted = { true }) { false }
        val foreign =
            PendingIntent.getBroadcast(
                context,
                88,
                Intent("foreign"),
                PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val n =
            Notification.Builder(context, "chat")
                .setContentTitle("Sender")
                .setContentText("Message")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(foreign)
                .addAction(Notification.Action.Builder(null, "Foreign action", foreign).build())
                .build()
        val parsed = repo.parse(status(n), settings)!!
        assertNull(parsed.pending)
        assertTrue(parsed.actions.isEmpty())
        assertFalse(repo.send(IslandAction("Forged", foreign, sourcePackage = "chat.app")))
        assertFalse(repo.open(parsed.copy(pending = foreign)))
    }

    @Test
    fun lockIsRecheckedAtDispatchAfterRendering() {
        var locked = false
        val repo = NotificationRepository(context, runtimeGranted = { true }) { locked }
        val pending =
            PendingIntent.getBroadcast(
                context,
                89,
                Intent("reply.lock"),
                PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val action = IslandAction("Reply", pending, arrayOf(RemoteInput.Builder("reply").build()))
        assertTrue(repo.send(action, "Allowed while unlocked"))
        locked = true
        assertFalse(repo.send(action, "Must not send"))
        val item =
            IslandNotification(
                "key",
                context.packageName,
                "Sender",
                "Message",
                null,
                pending,
                emptyList(),
                Notification.VISIBILITY_PRIVATE,
                "hash",
            )
        assertFalse(repo.open(item))
    }

    @Test
    fun permissionsAreRecheckedWhenInteractingAfterRevocation() {
        var granted = true
        val repo = NotificationRepository(context, runtimeGranted = { granted }) { false }
        val pending =
            PendingIntent.getBroadcast(
                context,
                91,
                Intent("grant.revocation"),
                PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val action = IslandAction("Action", pending)
        assertTrue(repo.send(action))
        granted = false
        assertFalse(repo.send(action))
        val item =
            IslandNotification(
                "key",
                context.packageName,
                "Sender",
                "Message",
                null,
                pending,
                emptyList(),
                Notification.VISIBILITY_PRIVATE,
                "hash",
            )
        assertFalse(repo.open(item))
    }

    @Test
    fun hiddenAndLockedModesDoNotExtractPayloadOrActions() {
        val sensitive = notification("private-body-canary")
        val neverRead =
            object : CharSequence {
                override val length = 4

                override fun get(index: Int) = 'x'

                override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = this

                override fun toString(): String = error("Hidden payload must not be extracted")
            }
        sensitive.extras.putCharSequence(Notification.EXTRA_TITLE, neverRead)
        sensitive.extras.putCharSequence(Notification.EXTRA_TEXT, neverRead)
        for ((hidden, locked) in listOf(true to false, false to true)) {
            val repo = NotificationRepository(context, runtimeGranted = { true }) { locked }
            val parsed = repo.parse(status(sensitive), settings.copy(hideContent = hidden))!!
            assertTrue(parsed.redacted)
            assertEquals("New notification", parsed.title)
            assertEquals("", parsed.text)
            assertTrue(parsed.actions.isEmpty())
        }
    }

    @Test
    fun retainedPayloadExpiresAndUpdatesDoNotExtendItsLifetime() {
        var now = 0L
        val repo = NotificationRepository(context, elapsedMillis = { now }) { false }
        val item = repo.parse(status(notification("payload")), settings)!!
        repo.put(item)
        now = 59_000L
        repo.put(item.copy(text = "updated payload"))
        assertEquals(1000L, repo.nextExpiryDelayMillis())
        now = 60_000L
        assertNull(repo.get(item.key))
        assertEquals(listOf(item.key), repo.removeExpired())
        assertNull(repo.nextExpiryDelayMillis())
    }

    @Test
    fun notificationProcessingDoesNotWritePrivateFilesOrPreferences() {
        val sbn = status(notification("private-payload-canary-184937"))
        val repo = NotificationRepository(context) { false }
        val root = java.io.File(context.applicationInfo.dataDir).apply { mkdirs() }
        fun snapshot(): Map<String, List<Byte>> =
            root
                .walkTopDown()
                .filter { it.isFile }
                .associate { it.relativeTo(root).path to it.readBytes().toList() }
        val before = snapshot()
        val parsed = repo.parse(sbn, settings)!!
        repo.put(parsed)
        assertEquals(before, snapshot())
        repo.remove(parsed.key)
        repo.clear()
        assertEquals(before, snapshot())
    }
}
