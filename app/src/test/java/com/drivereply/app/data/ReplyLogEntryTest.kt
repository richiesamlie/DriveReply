package com.drivereply.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ReplyLogEntryTest {

    @Test
    fun replyLog_defaultValues() {
        val entry = ReplyLogEntry(
            contactName = "John Doe",
            templateName = "Driving",
            messageSent = "I am driving 🚗"
        )

        assertNotNull(entry.id)
        assertTrue(entry.id.isNotEmpty())
        assertEquals("John Doe", entry.contactName)
        assertEquals("Driving", entry.templateName)
        assertEquals("I am driving 🚗", entry.messageSent)
        assertEquals("com.whatsapp", entry.packageName) // verify default value
        assertTrue(entry.timestamp > 0)
    }

    @Test
    fun replyLog_customValues() {
        val customId = UUID.randomUUID().toString()
        val customTimestamp = 987654321L
        val entry = ReplyLogEntry(
            id = customId,
            contactName = "Jane Smith",
            templateName = "Busy",
            messageSent = "I'm busy",
            packageName = "org.telegram.messenger",
            timestamp = customTimestamp
        )

        assertEquals(customId, entry.id)
        assertEquals("Jane Smith", entry.contactName)
        assertEquals("Busy", entry.templateName)
        assertEquals("I'm busy", entry.messageSent)
        assertEquals("org.telegram.messenger", entry.packageName)
        assertEquals(customTimestamp, entry.timestamp)
    }
}
