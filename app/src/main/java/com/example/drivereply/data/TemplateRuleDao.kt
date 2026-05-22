package com.example.drivereply.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateRuleDao {

    @Query("SELECT * FROM template_rules")
    fun getAll(): Flow<List<TemplateRule>>

    @Query("SELECT * FROM template_rules WHERE id = :id")
    suspend fun getById(id: String): TemplateRule?

    @Query("SELECT * FROM template_rules WHERE templateId = :templateId")
    fun getRulesForTemplate(templateId: String): Flow<List<TemplateRule>>

    @Query("SELECT * FROM template_rules WHERE contactName = :contactName OR contactName IS NULL")
    suspend fun getRulesForContact(contactName: String): List<TemplateRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: TemplateRule)

    @Update
    suspend fun update(rule: TemplateRule)

    @Delete
    suspend fun delete(rule: TemplateRule)
}
