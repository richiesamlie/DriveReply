package com.drivereply.app.service

import com.drivereply.app.service.DriveReplyService.DrivingStateSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DrivingStateControllerTest {

    @Before
    @After
    fun resetState() {
        DriveReplyService.resetStateForTest()
    }

    @Test
    fun multipleAutomaticSources_stayDrivingUntilLastSourceExits() {
        DriveReplyService.setDrivingState(true, DrivingStateSource.BLUETOOTH)
        DriveReplyService.setDrivingState(true, DrivingStateSource.GPS_SPEED)
        assertTrue(DriveReplyService.isDriving.value)

        // Speed dropping below threshold should not stop Bluetooth-triggered driving
        DriveReplyService.setDrivingState(false, DrivingStateSource.GPS_SPEED)
        assertTrue(DriveReplyService.isDriving.value)

        // Disconnecting Bluetooth stops driving when no other triggers remain
        DriveReplyService.setDrivingState(false, DrivingStateSource.BLUETOOTH)
        assertFalse(DriveReplyService.isDriving.value)
    }

    @Test
    fun manualSimulationOverride_blocksAutomaticStopUntilCleared() {
        DriveReplyService.setDrivingState(true, DrivingStateSource.MANUAL_SIMULATION)
        DriveReplyService.setDrivingState(true, DrivingStateSource.ACTIVITY_RECOGNITION)

        DriveReplyService.setDrivingState(false, DrivingStateSource.ACTIVITY_RECOGNITION)
        assertTrue(DriveReplyService.isDriving.value)

        DriveReplyService.setDrivingState(false, DrivingStateSource.AUTOMATIC)
        assertTrue(DriveReplyService.isDriving.value)

        DriveReplyService.setDrivingState(false, DrivingStateSource.MANUAL_SIMULATION)
        assertFalse(DriveReplyService.isDriving.value)
    }

    @Test
    fun conversationDeduplication_enforcesSessionAndCrossSessionCooldown() {
        val key = "com.whatsapp|tag:chat-1"
        val t0 = System.currentTimeMillis()

        assertFalse(DriveReplyService.hasRepliedToConversation(key, t0))
        assertTrue(DriveReplyService.markConversationReplied(key, t0))
        assertFalse(DriveReplyService.markConversationReplied(key, t0 + 1_000L))
        assertTrue(DriveReplyService.hasRepliedToConversation(key, t0 + 60_000L))

        // Transitioning off and back on clears session set, but 15m cooldown remains active
        DriveReplyService.setDrivingState(false, DrivingStateSource.BLUETOOTH)
        DriveReplyService.setDrivingState(true, DrivingStateSource.BLUETOOTH)
        assertTrue(
            DriveReplyService.hasRepliedToConversation(
                key,
                t0 + DriveReplyService.CONVERSATION_COOLDOWN_MS - 1_000L
            )
        )
        assertFalse(
            DriveReplyService.hasRepliedToConversation(
                key,
                t0 + DriveReplyService.CONVERSATION_COOLDOWN_MS + 1L
            )
        )
    }

    @Test
    fun drivingStartedAtMs_tracksSessionStartAndPreservesInitialAutomaticStart() {
        assertEquals(0L, DriveReplyService.drivingStartedAtMs)

        DriveReplyService.setDrivingState(true, DrivingStateSource.BLUETOOTH, nowMs = 10_000L)
        assertEquals(10_000L, DriveReplyService.drivingStartedAtMs)

        // Second automatic source activating concurrently keeps the original session start
        DriveReplyService.setDrivingState(true, DrivingStateSource.GPS_SPEED, nowMs = 20_000L)
        assertEquals(10_000L, DriveReplyService.drivingStartedAtMs)

        // Manual simulation explicitly starts/refreshes the session timestamp
        DriveReplyService.setDrivingState(true, DrivingStateSource.MANUAL_SIMULATION, nowMs = 30_000L)
        assertEquals(30_000L, DriveReplyService.drivingStartedAtMs)

        DriveReplyService.setDrivingState(false, DrivingStateSource.MANUAL_SIMULATION, nowMs = 40_000L)
        assertTrue(DriveReplyService.isDriving.value)
        assertEquals(30_000L, DriveReplyService.drivingStartedAtMs)

        DriveReplyService.setDrivingState(false, DrivingStateSource.BLUETOOTH, nowMs = 50_000L)
        DriveReplyService.setDrivingState(false, DrivingStateSource.GPS_SPEED, nowMs = 55_000L)
        assertFalse(DriveReplyService.isDriving.value)
        assertEquals(0L, DriveReplyService.drivingStartedAtMs)
    }
}
