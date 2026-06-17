package com.drivereply.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "message_templates")
data class MessageTemplate(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val body: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
