package com.drivereply.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "template_rules",
    indices = [
        Index(value = ["templateId"]),
        Index(value = ["contactName"])
    ]
)
data class TemplateRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val templateId: String,          // Linked to MessageTemplate
    val contactName: String? = null, // Specific contact trigger (null = all)
    val daysOfWeek: String? = null,  // Comma-separated active days (e.g. "1,2,3,4,5" where 1=Monday, 7=Sunday)
    val startTime: Long? = null,     // Active window start (milliseconds from midnight)
    val endTime: Long? = null        // Active window end
) {
    fun matches(
        targetContactName: String,
        dayOfWeek: Int,
        currentMsSinceMidnight: Long
    ): Boolean {
        val ruleContact = contactName?.trim()?.takeIf { it.isNotEmpty() }
        if (ruleContact != null && !ruleContact.equals(targetContactName.trim(), ignoreCase = true)) {
            return false
        }

        val daysMatch = if (!daysOfWeek.isNullOrBlank()) {
            val daysList = daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
            dayOfWeek in daysList
        } else {
            true
        }
        if (!daysMatch) return false

        val timeMatch = if (startTime != null && endTime != null) {
            if (startTime <= endTime) {
                currentMsSinceMidnight in startTime..endTime
            } else {
                currentMsSinceMidnight >= startTime || currentMsSinceMidnight <= endTime
            }
        } else {
            true
        }

        return timeMatch
    }

    companion object {
        fun matchingRules(
            rules: List<TemplateRule>,
            contactName: String,
            dayOfWeek: Int,
            currentMsSinceMidnight: Long
        ): List<TemplateRule> {
            val matching = rules.filter { it.matches(contactName, dayOfWeek, currentMsSinceMidnight) }
            val specific = matching.filter { !it.contactName.isNullOrBlank() }
            val general = matching.filter { it.contactName.isNullOrBlank() }
            return specific + general
        }

        fun resolveBestRule(
            rules: List<TemplateRule>,
            contactName: String,
            dayOfWeek: Int,
            currentMsSinceMidnight: Long
        ): TemplateRule? {
            return matchingRules(rules, contactName, dayOfWeek, currentMsSinceMidnight).firstOrNull()
        }
    }
}
