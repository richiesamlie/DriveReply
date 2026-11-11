package com.drivereply.app.ui.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivereply.app.DriveReplyApplication
import com.drivereply.app.data.MessageTemplate
import com.drivereply.app.data.TemplateRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class EditTemplateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val templateDao = app.database.messageTemplateDao()
    private val templateRuleDao = app.database.templateRuleDao()

    private val _templateState = MutableStateFlow<MessageTemplate?>(null)
    val templateState: StateFlow<MessageTemplate?> = _templateState.asStateFlow()

    private val _ruleState = MutableStateFlow<TemplateRule?>(null)
    val ruleState: StateFlow<TemplateRule?> = _ruleState.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _isSaving = MutableStateFlow(false)

    fun loadTemplate(templateId: String?) {
        _templateState.value = null
        _ruleState.value = null
        if (templateId == null) {
            return
        }
        viewModelScope.launch {
            val template = templateDao.getById(templateId)
            val rule = templateRuleDao.getByTemplateId(templateId).firstOrNull()
            _ruleState.value = rule
            _templateState.value = template
        }
    }

    fun saveTemplate(
        name: String,
        body: String,
        existingId: String?,
        contactName: String? = null,
        daysOfWeek: String? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ) {
        if (name.isBlank() || body.isBlank() || _isSaving.value) return
        _isSaving.value = true

        viewModelScope.launch {
            try {
                val targetId: String
                if (existingId != null) {
                    targetId = existingId
                    val existing = templateDao.getById(existingId)
                    if (existing != null) {
                        val updated = existing.copy(name = name.trim(), body = body.trim())
                        templateDao.update(updated)
                    }
                } else {
                    targetId = UUID.randomUUID().toString()
                    val newTemplate = MessageTemplate(
                        id = targetId,
                        name = name.trim(),
                        body = body.trim(),
                        isActive = false
                    )
                    templateDao.insert(newTemplate)
                }

                templateRuleDao.deleteByTemplateId(targetId)
                val cleanContact = contactName?.trim()?.takeIf { it.isNotBlank() }
                val cleanDays = daysOfWeek?.trim()?.takeIf { it.isNotBlank() }
                val validStart = if (startTime != null && endTime != null) startTime else null
                val validEnd = if (startTime != null && endTime != null) endTime else null

                if (cleanContact != null || cleanDays != null || (validStart != null && validEnd != null)) {
                    templateRuleDao.insert(
                        TemplateRule(
                            templateId = targetId,
                            contactName = cleanContact,
                            daysOfWeek = cleanDays,
                            startTime = validStart,
                            endTime = validEnd
                        )
                    )
                }

                _isSaved.value = true
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun resetSavedState() {
        _isSaved.value = false
    }
}
