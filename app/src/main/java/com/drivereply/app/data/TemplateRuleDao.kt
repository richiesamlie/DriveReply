package com.drivereply.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM template_rules WHERE templateId = :templateId")
    suspend fun getByTemplateId(templateId: String): List<TemplateRule>

    @Query("SELECT * FROM template_rules WHERE LOWER(TRIM(contactName)) = LOWER(TRIM(:contactName)) OR contactName IS NULL")
    suspend fun getRulesForContact(contactName: String): List<TemplateRule>

    @Query("DELETE FROM template_rules WHERE templateId = :templateId")
    suspend fun deleteByTemplateId(templateId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: TemplateRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<TemplateRule>)

    @Update
    suspend fun update(rule: TemplateRule)

    @Delete
    suspend fun delete(rule: TemplateRule)

    @Transaction
    suspend fun replaceRulesForTemplate(templateId: String, rules: List<TemplateRule>) {
        deleteByTemplateId(templateId)
        if (rules.isNotEmpty()) {
            insertAll(rules.map { it.copy(templateId = templateId) })
        }
    }
}
