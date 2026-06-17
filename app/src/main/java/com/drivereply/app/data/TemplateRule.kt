package com.drivereply.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "template_rules")
data class TemplateRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val templateId: String,          // Linked to MessageTemplate
    val contactName: String? = null, // Specific contact trigger (null = all)
    val daysOfWeek: String? = null,  // Comma-separated active days (e.g. "1,2,3,4,5" where 1=Monday, 7=Sunday)
    val startTime: Long? = null,     // Active window start (milliseconds from midnight)
    val endTime: Long? = null        // Active window end
)
