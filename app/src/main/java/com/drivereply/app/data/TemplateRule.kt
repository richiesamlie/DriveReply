package com.drivereply.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "template_rules",
    foreignKeys = [
        ForeignKey(
            entity = MessageTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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

        val start = startTime
        val end = endTime
        val hasTimeWindow = start != null && end != null
        val isOvernight = hasTimeWindow && start > end

        val effectiveDayOfWeek = if (isOvernight && currentMsSinceMidnight <= end) {
            if (dayOfWeek == 1) 7 else dayOfWeek - 1
        } else {
            dayOfWeek
        }

        val daysMatch = if (!daysOfWeek.isNullOrBlank()) {
            val daysList = daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
            effectiveDayOfWeek in daysList
        } else {
            true
        }
        if (!daysMatch) return false

        val timeMatch = if (hasTimeWindow) {
            if (!isOvernight) {
                currentMsSinceMidnight in start..end
            } else {
                currentMsSinceMidnight >= start || currentMsSinceMidnight <= end
            }
        } else {
            true
        }

        return timeMatch
    }

    companion object {
        private fun specificityScore(rule: TemplateRule): Int {
            var score = 0
            if (!rule.contactName.isNullOrBlank()) score += 4
            if (!rule.daysOfWeek.isNullOrBlank()) score += 2
            if (rule.startTime != null && rule.endTime != null) score += 1
            return score
        }

        fun matchingRules(
            rules: List<TemplateRule>,
            contactName: String,
            dayOfWeek: Int,
            currentMsSinceMidnight: Long
        ): List<TemplateRule> {
            return rules
                .filter { it.matches(contactName, dayOfWeek, currentMsSinceMidnight) }
                .sortedWith(
                    compareByDescending<TemplateRule> { specificityScore(it) }
                        .thenBy { it.id }
                )
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
