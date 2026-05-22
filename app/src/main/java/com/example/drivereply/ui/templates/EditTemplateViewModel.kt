package com.example.drivereply.ui.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivereply.DriveReplyApplication
import com.example.drivereply.data.MessageTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class EditTemplateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val templateDao = app.database.messageTemplateDao()

    private val _templateState = MutableStateFlow<MessageTemplate?>(null)
    val templateState: StateFlow<MessageTemplate?> = _templateState.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    fun loadTemplate(templateId: String?) {
        if (templateId == null) {
            _templateState.value = null
            return
        }
        viewModelScope.launch {
            val template = templateDao.getById(templateId)
            _templateState.value = template
        }
    }

    fun saveTemplate(name: String, body: String, existingId: String?) {
        if (name.isBlank() || body.isBlank()) return

        viewModelScope.launch {
            if (existingId != null) {
                val existing = templateDao.getById(existingId)
                if (existing != null) {
                    val updated = existing.copy(name = name.trim(), body = body.trim())
                    templateDao.update(updated)
                }
            } else {
                val newTemplate = MessageTemplate(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    body = body.trim(),
                    isActive = false
                )
                templateDao.insert(newTemplate)
            }
            _isSaved.value = true
        }
    }

    fun resetSavedState() {
        _isSaved.value = false
    }
}
