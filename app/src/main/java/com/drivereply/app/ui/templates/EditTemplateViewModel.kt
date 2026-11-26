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
import java.util.Locale
import java.util.UUID

class EditTemplateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val templateDao = app.database.messageTemplateDao()
    private val templateRuleDao = app.database.templateRuleDao()

    private val _templateState = MutableStateFlow<MessageTemplate?>(null)
    val templateState: StateFlow<MessageTemplate?> = _templateState.asStateFlow()

    private val _rulesState = MutableStateFlow<List<TemplateRule>>(emptyList())
    val rulesState: StateFlow<List<TemplateRule>> = _rulesState.asStateFlow()

    private val _initialRules = MutableStateFlow<List<TemplateRule>>(emptyList())
    val initialRules: StateFlow<List<TemplateRule>> = _initialRules.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    private var loadedTemplateKey: String? = null

    fun loadTemplate(templateId: String?) {
        val targetKey = templateId ?: "__new__"
        if (loadedTemplateKey == targetKey) return
        loadedTemplateKey = targetKey

        _templateState.value = null
        _rulesState.value = emptyList()
        _initialRules.value = emptyList()
        if (templateId == null) {
            return
        }
        viewModelScope.launch {
            val template = templateDao.getById(templateId)
            val existingRules = templateRuleDao.getByTemplateId(templateId)
            _rulesState.value = existingRules
            _initialRules.value = existingRules
            _templateState.value = template
        }
    }

    fun saveRuleDraft(
        ruleId: String?,
        existingId: String?,
        contactName: String?,
        selectedDays: Set<Int>,
        startTimeMs: Long?,
        endTimeMs: Long?
    ) {
        val cleanContact = contactName?.trim()?.takeIf { it.isNotBlank() }
        val cleanDays = selectedDays
            .filter { it in 1..7 }
            .sorted()
            .takeIf { it.isNotEmpty() && it.size < 7 }
            ?.joinToString(",")
        val validWindow = startTimeMs != null && endTimeMs != null && startTimeMs != endTimeMs
        if (cleanContact == null && cleanDays == null && !validWindow) {
            return
        }
        val targetTemplateId = existingId ?: ""

        val draft = TemplateRule(
            id = ruleId ?: UUID.randomUUID().toString(),
            templateId = targetTemplateId,
            contactName = cleanContact,
            daysOfWeek = cleanDays,
            startTime = if (validWindow) startTimeMs else null,
            endTime = if (validWindow) endTimeMs else null
        )

        val current = _rulesState.value
        val isDuplicate = current.any { existing ->
            existing.id != draft.id &&
                existing.contactName?.trim()?.lowercase(Locale.ROOT) == draft.contactName?.lowercase(Locale.ROOT) &&
                existing.daysOfWeek == draft.daysOfWeek &&
                existing.startTime == draft.startTime &&
                existing.endTime == draft.endTime
        }
        if (isDuplicate) return

        _rulesState.value = if (ruleId != null && current.any { it.id == ruleId }) {
            current.map { if (it.id == ruleId) draft else it }
        } else {
            current + draft
        }
    }

    fun deleteRuleDraft(ruleId: String) {
        _rulesState.value = _rulesState.value.filterNot { it.id == ruleId }
    }

    fun saveTemplate(name: String, body: String, existingId: String?) {
        if (name.isBlank() || body.isBlank() || _isSaving.value) return
        _isSaving.value = true

        viewModelScope.launch {
            try {
                val savedTemplate: MessageTemplate
                val targetId: String
                if (existingId != null) {
                    targetId = existingId
                    val existing = templateDao.getById(existingId) ?: return@launch
                    savedTemplate = existing.copy(name = name.trim(), body = body.trim())
                    templateDao.update(savedTemplate)
                } else {
                    targetId = UUID.randomUUID().toString()
                    savedTemplate = MessageTemplate(
                        id = targetId,
                        name = name.trim(),
                        body = body.trim(),
                        isActive = false
                    )
                    templateDao.insert(savedTemplate)
                }

                val savedRules = _rulesState.value.map { it.copy(templateId = targetId) }
                templateRuleDao.replaceRulesForTemplate(targetId, savedRules)
                _rulesState.value = savedRules
                _initialRules.value = savedRules
                _templateState.value = savedTemplate
                loadedTemplateKey = targetId
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
