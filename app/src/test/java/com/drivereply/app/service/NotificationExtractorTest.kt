package com.drivereply.app.service

import com.drivereply.app.service.DriveReplyService.DrivingStateSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationExtractorTest {

    @After
    fun tearDown() {
        DriveReplyService.resetStateForTest()
    }

    @Test
    fun packageFilter_matchesAll9SupportedApps() {
        assertEquals(9, SupportedMessagingPackages.supportedApps.size)
        assertTrue(SupportedMessagingPackages.contains("com.whatsapp"))
        assertTrue(SupportedMessagingPackages.contains("com.whatsapp.w4b"))
        assertTrue(SupportedMessagingPackages.contains("org.telegram.messenger"))
        assertTrue(SupportedMessagingPackages.contains("org.thunderdog.challegram"))
        assertTrue(SupportedMessagingPackages.contains("org.thoughtcrime.securesms"))
        assertTrue(SupportedMessagingPackages.contains("com.facebook.orca"))
        assertTrue(SupportedMessagingPackages.contains("com.google.android.apps.messaging"))
        assertTrue(SupportedMessagingPackages.contains("com.samsung.android.messaging"))
        assertTrue(SupportedMessagingPackages.contains("com.android.messaging"))
    }

    @Test
    fun packageFilter_ignoresUnsupportedApps() {
        assertFalse(SupportedMessagingPackages.contains("com.instagram.android"))
        assertFalse(SupportedMessagingPackages.contains("com.twitter.android"))
        assertFalse(SupportedMessagingPackages.contains("com.android.mms"))
    }

    @Test
    fun buildDedupeKey_prioritizesTagThenConversationTitleThenContactThenId() {
        val listener = WhatsAppNotificationListener()

        assertEquals(
            "com.whatsapp|tag:chat-thread-1",
            listener.buildDedupeKey(
                packageName = "com.whatsapp",
                tag = " chat-thread-1 ",
                notificationId = 10,
                conversationTitle = "Family Group",
                contactName = "Alice"
            )
        )

        assertEquals(
            "com.whatsapp|conv:family group",
            listener.buildDedupeKey(
                packageName = "com.whatsapp",
                tag = null,
                notificationId = 10,
                conversationTitle = " Family Group ",
                contactName = "Alice"
            )
        )

        assertEquals(
            "org.telegram.messenger|contact:alice",
            listener.buildDedupeKey(
                packageName = "org.telegram.messenger",
                tag = "  ",
                notificationId = 10,
                conversationTitle = null,
                contactName = " Alice "
            )
        )

        assertEquals(
            "org.thoughtcrime.securesms|id:42",
            listener.buildDedupeKey(
                packageName = "org.thoughtcrime.securesms",
                tag = null,
                notificationId = 42,
                conversationTitle = "",
                contactName = "   "
            )
        )
    }

    @Test
    fun drivingState_multiSourceTrackingAndManualOverride() {
        DriveReplyService.resetStateForTest()
        assertFalse(DriveReplyService.isDriving.value)

        // Activate Bluetooth and GPS Speed concurrently
        DriveReplyService.setDrivingState(true, DrivingStateSource.BLUETOOTH)
        DriveReplyService.setDrivingState(true, DrivingStateSource.GPS_SPEED)
        assertTrue(DriveReplyService.isDriving.value)

        // Stopping Bluetooth while GPS_SPEED is still active keeps driving = true
        DriveReplyService.setDrivingState(false, DrivingStateSource.BLUETOOTH)
        assertTrue(DriveReplyService.isDriving.value)

        // Stopping GPS_SPEED transitions driving = false
        DriveReplyService.setDrivingState(false, DrivingStateSource.GPS_SPEED)
        assertFalse(DriveReplyService.isDriving.value)

        // Manual simulation override blocks automatic stop triggers
        DriveReplyService.setDrivingState(true, DrivingStateSource.MANUAL_SIMULATION)
        assertTrue(DriveReplyService.isDriving.value)
        DriveReplyService.setDrivingState(false, DrivingStateSource.ACTIVITY_RECOGNITION)
        assertTrue(DriveReplyService.isDriving.value)

        // Turning off manual simulation stops driving
        DriveReplyService.setDrivingState(false, DrivingStateSource.MANUAL_SIMULATION)
        assertFalse(DriveReplyService.isDriving.value)
    }

    @Test
    fun conversationDedupe_enforcesSessionAndCooldownAndRollback() {
        DriveReplyService.resetStateForTest()
        val key = "com.whatsapp|tag:conv1"
        val t0 = System.currentTimeMillis()

        assertFalse(DriveReplyService.hasRepliedToConversation(key, t0))
        assertTrue(DriveReplyService.markConversationReplied(key, t0))
        assertTrue(DriveReplyService.hasRepliedToConversation(key, t0))
        // Second mark in same session fails
        assertFalse(DriveReplyService.markConversationReplied(key, t0 + 1_000L))

        // Clearing session contacts preserves cooldown within 15 minutes
        DriveReplyService.clearRepliedContacts()
        assertTrue(DriveReplyService.hasRepliedToConversation(key, t0 + 60_000L))

        // After 15 minutes cooldown expires, conversation can be replied to again
        val afterCooldown = t0 + DriveReplyService.CONVERSATION_COOLDOWN_MS + 1L
        assertFalse(DriveReplyService.hasRepliedToConversation(key, afterCooldown))
        assertTrue(DriveReplyService.markConversationReplied(key, afterCooldown))

        // Rollback on send failure unmarks both session and cooldown
        DriveReplyService.unmarkConversationReplied(key)
        assertFalse(DriveReplyService.hasRepliedToConversation(key, afterCooldown))
    }
}
