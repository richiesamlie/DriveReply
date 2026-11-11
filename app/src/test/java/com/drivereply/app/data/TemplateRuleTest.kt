package com.drivereply.app.data

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

    // Unit test verifying the production matching algorithm in TemplateRule
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
        
        val rulesList = listOf(rule2, rule1)
        
        // Scenario 1: John Doe messages during business hours on Tuesday.
        // Expect: Matches rule1 (specific contact rule prioritized even when rule2 is first in list)
        val resolvedRule1 = TemplateRule.resolveBestRule(rulesList, targetContact, activeDay, nowMs)
        assertEquals("temp-specific-contact", resolvedRule1?.templateId)
        
        // Scenario 2: Jane Smith messages during business hours on Tuesday.
        // Expect: Matches rule2 (general workday rule since no Jane Smith specific rule is defined)
        val resolvedRule2 = TemplateRule.resolveBestRule(rulesList, otherContact, activeDay, nowMs)
        assertEquals("temp-general-workday", resolvedRule2?.templateId)
        
        // Scenario 3: John Doe messages on Saturday (inactive day).
        // Expect: No rules match, returns null (meaning fallback to global active template)
        val resolvedRule3 = TemplateRule.resolveBestRule(rulesList, targetContact, inactiveDay, nowMs)
        assertNull(resolvedRule3)
        
        // Scenario 4: John Doe messages at 8:00 PM (outside time window).
        // Expect: No rules match, returns null
        val resolvedRule4 = TemplateRule.resolveBestRule(rulesList, targetContact, activeDay, nightMs)
        assertNull(resolvedRule4)
    }

    @Test
    fun rule_overnightTimeWindow_matchesAcrossMidnight() {
        val overnightRule = TemplateRule(
            templateId = "temp-night-shift",
            contactName = null,
            daysOfWeek = "1,2,3,4,5,6,7",
            startTime = 22L * 3600_000L, // 22:00
            endTime = 6L * 3600_000L     // 06:00
        )

        // 23:00 -> inside overnight window
        assertTrue(overnightRule.matches("Anyone", 1, 23L * 3600_000L))
        // 02:00 -> inside overnight window
        assertTrue(overnightRule.matches("Anyone", 1, 2L * 3600_000L))
        // 12:00 -> outside overnight window
        org.junit.Assert.assertFalse(overnightRule.matches("Anyone", 1, 12L * 3600_000L))
    }

    @Test
    fun rule_contactMatching_isCaseAndWhitespaceInsensitive() {
        val rule = TemplateRule(
            templateId = "temp-contact",
            contactName = "  John Doe  "
        )
        assertTrue(rule.matches("john doe", 3, 10_000L))
        assertTrue(rule.matches("  JOHN DOE ", 3, 10_000L))
        org.junit.Assert.assertFalse(rule.matches("Jane Doe", 3, 10_000L))
    }

    @Test
    fun rule_matchingRules_ordersSpecificBeforeGeneral() {
        val general = TemplateRule(templateId = "general-id", contactName = null)
        val specific = TemplateRule(templateId = "specific-id", contactName = "Alice")
        val ordered = TemplateRule.matchingRules(
            rules = listOf(general, specific),
            contactName = "alice",
            dayOfWeek = 1,
            currentMsSinceMidnight = 0L
        )
        assertEquals(listOf("specific-id", "general-id"), ordered.map { it.templateId })
    }
}
