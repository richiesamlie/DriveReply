package com.example.drivereply.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MessageTemplateTest {

    @Test
    fun template_defaultValues() {
        val template = MessageTemplate(
            name = "Test Name",
            body = "Test Body"
        )
        
        assertNotNull(template.id)
        assertTrue(template.id.isNotEmpty())
        assertEquals("Test Name", template.name)
        assertEquals("Test Body", template.body)
        assertFalse(template.isActive)
        assertTrue(template.createdAt > 0)
    }

    @Test
    fun template_customValues() {
        val customId = UUID.randomUUID().toString()
        val timestamp = 123456789L
        val template = MessageTemplate(
            id = customId,
            name = "Custom Name",
            body = "Custom Body",
            isActive = true,
            createdAt = timestamp
        )
        
        assertEquals(customId, template.id)
        assertEquals("Custom Name", template.name)
        assertEquals("Custom Body", template.body)
        assertTrue(template.isActive)
        assertEquals(timestamp, template.createdAt)
    }
}
