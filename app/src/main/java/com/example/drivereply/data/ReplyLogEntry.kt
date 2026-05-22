package com.example.drivereply.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "reply_log")
data class ReplyLogEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val contactName: String,
    val templateName: String,
    val messageSent: String,
    val timestamp: Long = System.currentTimeMillis()
)
