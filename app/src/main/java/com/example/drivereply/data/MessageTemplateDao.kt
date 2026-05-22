package com.example.drivereply.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageTemplateDao {

    @Query("SELECT * FROM message_templates ORDER BY createdAt ASC")
    fun getAll(): Flow<List<MessageTemplate>>

    @Query("SELECT * FROM message_templates WHERE isActive = 1 LIMIT 1")
    fun getActive(): Flow<MessageTemplate?>

    @Query("SELECT * FROM message_templates WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSuspend(): MessageTemplate?

    @Query("SELECT * FROM message_templates WHERE id = :id")
    suspend fun getById(id: String): MessageTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: MessageTemplate)

    @Update
    suspend fun update(template: MessageTemplate)

    @Delete
    suspend fun delete(template: MessageTemplate)

    @Query("UPDATE message_templates SET isActive = 0")
    suspend fun deactivateAll()

    @Transaction
    suspend fun setActive(id: String) {
        deactivateAll()
        activateById(id)
    }

    @Query("UPDATE message_templates SET isActive = 1 WHERE id = :id")
    suspend fun activateById(id: String)
}
