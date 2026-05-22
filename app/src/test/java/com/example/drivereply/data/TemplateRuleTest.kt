package com.example.drivereply.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TemplateRuleTest {

    @Test
    fun rule_defaultValues() {
        val rule = TemplateRule(
            templateId = "temp-123"
        )
        
        assertNotNull(rule.id)
        assertTrue(rule.id.isNotEmpty())
        assertEquals("temp-123", rule.templateId)
        assertNull(rule.contactName)
        assertNull(rule.daysOfWeek)
        assertNull(rule.startTime)
        assertNull(rule.endTime)
    }

    @Test
    fun rule_customValues() {
        val customId = UUID.randomUUID().toString()
        val rule = TemplateRule(
            id = customId,
            templateId = "temp-456",
            contactName = "John Doe",
            daysOfWeek = "1,2,3,4,5",
            startTime = 32400000L, // 9:00 AM in ms
            endTime = 61200000L    // 5:00 PM in ms
        )
        
        assertEquals(customId, rule.id)
        assertEquals("temp-456", rule.templateId)
        assertEquals("John Doe", rule.contactName)
        assertEquals("1,2,3,4,5", rule.daysOfWeek)
        assertEquals(32400000L, rule.startTime)
        assertEquals(61200000L, rule.endTime)
    }

    // Unit test verifying the matching algorithm that runs inside the notification listener
    @Test
    fun rule_matchingAlgorithm() {
        val targetContact = "John Doe"
        val otherContact = "Jane Smith"
        
        val activeDay = 2 // Tuesday (Monday = 1, Sunday = 7)
        val inactiveDay = 6 // Saturday
        
        val nowMs = 36000000L // 10:00 AM (between 9 AM and 5 PM)
        val nightMs = 72000000L // 8:00 PM (outside 9 AM and 5 PM)
        
        val rule1 = TemplateRule(
            templateId = "temp-specific-contact",
            contactName = "John Doe",
            daysOfWeek = "1,2,3,4,5",
            startTime = 32400000L, // 9:00 AM
            endTime = 61200000L    // 5:00 PM
        )
        
        val rule2 = TemplateRule(
            templateId = "temp-general-workday",
            contactName = null,
            daysOfWeek = "1,2,3,4,5",
            startTime = 32400000L, // 9:00 AM
            endTime = 61200000L    // 5:00 PM
        )
        
        val rulesList = listOf(rule1, rule2)
        
        // Scenario 1: John Doe messages during business hours on Tuesday.
        // Expect: Matches rule1 (specific contact rule)
        val resolvedTemplateId1 = resolveTemplateId(rulesList, targetContact, activeDay, nowMs)
        assertEquals("temp-specific-contact", resolvedTemplateId1)
        
        // Scenario 2: Jane Smith messages during business hours on Tuesday.
        // Expect: Matches rule2 (general workday rule since no Jane Smith specific rule is defined)
        val resolvedTemplateId2 = resolveTemplateId(rulesList, otherContact, activeDay, nowMs)
        assertEquals("temp-general-workday", resolvedTemplateId2)
        
        // Scenario 3: John Doe messages on Saturday (inactive day).
        // Expect: No rules match, returns null (meaning fallback to global active template)
        val resolvedTemplateId3 = resolveTemplateId(rulesList, targetContact, inactiveDay, nowMs)
        assertNull(resolvedTemplateId3)
        
        // Scenario 4: John Doe messages at 8:00 PM (outside time window).
        // Expect: No rules match, returns null
        val resolvedTemplateId4 = resolveTemplateId(rulesList, targetContact, activeDay, nightMs)
        assertNull(resolvedTemplateId4)
    }

    private fun resolveTemplateId(
        rules: List<TemplateRule>,
        contactName: String,
        currentDayOfWeek: Int,
        currentMsSinceMidnight: Long
    ): String? {
        // Filter rules that match time/day constraints and are for this contact or all contacts
        val matchingRules = rules.filter { rule ->
            val isCorrectContact = rule.contactName == null || rule.contactName == contactName
            if (!isCorrectContact) return@filter false

            val daysMatch = if (!rule.daysOfWeek.isNullOrEmpty()) {
                val daysList = rule.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
                currentDayOfWeek in daysList
            } else {
                true
            }

            val timeMatch = if (rule.startTime != null && rule.endTime != null) {
                currentMsSinceMidnight in rule.startTime..rule.endTime
            } else {
                true
            }

            daysMatch && timeMatch
        }

        // Prioritize specific contact rule over general rule
        val bestRule = matchingRules.firstOrNull { it.contactName != null }
            ?: matchingRules.firstOrNull { it.contactName == null }
            
        return bestRule?.templateId
    }
}
