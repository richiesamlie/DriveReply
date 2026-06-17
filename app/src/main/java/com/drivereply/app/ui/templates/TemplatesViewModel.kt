package com.drivereply.app.ui.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivereply.app.DriveReplyApplication
import com.drivereply.app.data.MessageTemplate
import com.drivereply.app.data.TemplateRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TemplatesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val templateDao = app.database.messageTemplateDao()
    private val templateRuleDao = app.database.templateRuleDao()

    val templates: StateFlow<List<MessageTemplate>> = templateDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val templateRules: StateFlow<List<TemplateRule>> = templateRuleDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun setActiveTemplate(id: String) {
        viewModelScope.launch {
            templateDao.setActive(id)
        }
    }

    fun deleteTemplate(template: MessageTemplate) {
        viewModelScope.launch {
            templateDao.delete(template)
        }
    }
}
