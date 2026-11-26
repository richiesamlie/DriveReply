package com.drivereply.app.service

import com.drivereply.app.service.DriveReplyService.DrivingStateSource
import com.drivereply.app.service.WhatsAppNotificationListener.NotificationMessageSnapshot
import com.drivereply.app.service.WhatsAppNotificationListener.PreDrivingSkipReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test
    fun preDrivingFilter_skipsRepostedBaselineWithIdenticalSignatureOrOlderTimestamp() {
        val listener = WhatsAppNotificationListener()
        val drivingStartedAt = 100_000L
        val sigOld = listener.computeContentSignature("msg|1|90000|Alice|Hey earlier")
        val baseline = NotificationMessageSnapshot(
            messageTimestampMs = 90_000L,
            contentSignature = sigOld,
            postTimeMs = 90_050L
        )

        // App re-posts unread chat while driving (even if postTimeMs or messageTimestampMs changes)
        val repostSameContent = NotificationMessageSnapshot(
            messageTimestampMs = 105_000L,
            contentSignature = sigOld,
            postTimeMs = 105_010L
        )
        assertEquals(
            PreDrivingSkipReason.UNCHANGED_PRE_DRIVING_NOTIFICATION,
            listener.shouldSkipAsPreDrivingMessage(drivingStartedAt, repostSameContent, baseline)
        )

        // App re-posts with same/older message timestamp and reformatted text
        val repostSameTimestamp = NotificationMessageSnapshot(
            messageTimestampMs = 90_000L,
            contentSignature = listener.computeContentSignature("msg|2|90000|Alice|Summary"),
            postTimeMs = 105_010L
        )
        assertEquals(
            PreDrivingSkipReason.UNCHANGED_PRE_DRIVING_NOTIFICATION,
            listener.shouldSkipAsPreDrivingMessage(drivingStartedAt, repostSameTimestamp, baseline)
        )
    }

    @Test
    fun preDrivingFilter_skipsPredatingMessageWithoutBaselineAndAllowsNewMessages() {
        val listener = WhatsAppNotificationListener()
        val drivingStartedAt = 100_000L
        val sigOld = listener.computeContentSignature("msg|1|80000|Bob|Before drive")

        // No baseline, but message timestamp is well before drivingStartedAtMs - tolerance
        val oldNoBaseline = NotificationMessageSnapshot(
            messageTimestampMs = drivingStartedAt - WhatsAppNotificationListener.CLOCK_SKEW_TOLERANCE_MS - 1_000L,
            contentSignature = sigOld,
            postTimeMs = 110_000L
        )
        assertEquals(
            PreDrivingSkipReason.MESSAGE_PREDATES_DRIVING_SESSION,
            listener.shouldSkipAsPreDrivingMessage(drivingStartedAt, oldNoBaseline, baseline = null)
        )

        // No messageTimestampMs and postTimeMs predates session start beyond tolerance
        val oldPostTimeFallback = NotificationMessageSnapshot(
            messageTimestampMs = null,
            contentSignature = null,
            postTimeMs = drivingStartedAt - WhatsAppNotificationListener.CLOCK_SKEW_TOLERANCE_MS - 1L
        )
        assertEquals(
            PreDrivingSkipReason.MESSAGE_PREDATES_DRIVING_SESSION,
            listener.shouldSkipAsPreDrivingMessage(drivingStartedAt, oldPostTimeFallback, baseline = null)
        )

        // Genuinely new message in a pre-existing unread conversation is allowed
        val baseline = NotificationMessageSnapshot(
            messageTimestampMs = 80_000L,
            contentSignature = sigOld,
            postTimeMs = 80_010L
        )
        val newInSameChat = NotificationMessageSnapshot(
            messageTimestampMs = 102_000L,
            contentSignature = listener.computeContentSignature("msg|2|102000|Bob|Are you on the way?"),
            postTimeMs = 102_050L
        )
        assertNull(
            listener.shouldSkipAsPreDrivingMessage(drivingStartedAt, newInSameChat, baseline)
        )

        // Brand-new message arriving within clock-skew tolerance is allowed
        val withinTolerance = NotificationMessageSnapshot(
            messageTimestampMs = drivingStartedAt - 2_000L,
            contentSignature = listener.computeContentSignature("msg|1|98000|Cara|Call me"),
            postTimeMs = 100_100L
        )
        assertNull(
            listener.shouldSkipAsPreDrivingMessage(drivingStartedAt, withinTolerance, baseline = null)
        )
    }

    @Test
    fun computeContentSignature_isDeterministicAndSensitiveToMessageChanges() {
        val listener = WhatsAppNotificationListener()
        val a1 = listener.computeContentSignature("msg|1|1000|Alice|Hi")
        val a2 = listener.computeContentSignature("msg|1|1000|Alice|Hi")
        val diffCount = listener.computeContentSignature("msg|2|1000|Alice|Hi")
        val diffTs = listener.computeContentSignature("msg|1|2000|Alice|Hi")

        assertEquals(64, a1.length)
        assertEquals(a1, a2)
        assertNotEquals(a1, diffCount)
        assertNotEquals(a1, diffTs)
    }
}
