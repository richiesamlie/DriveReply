package com.drivereply.app.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivereply.app.DriveReplyApplication
import com.drivereply.app.data.MessageTemplate
import com.drivereply.app.service.DriveReplyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class OnboardingUiState(
    val isCompleting: Boolean = false,
    val isCompleted: Boolean = false
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DriveReplyApplication
    private val templateDao = app.database.messageTemplateDao()
    private val preferencesManager = app.preferencesManager

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun completeOnboarding(
        templateName: String,
        templateBody: String,
        detectionMode: String = "ACTIVITY"
    ) {
        if (_uiState.value.isCompleting || _uiState.value.isCompleted) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCompleting = true)
            try {
                val existingActive = templateDao.getActiveSuspend()
                if (existingActive == null) {
                    templateDao.deactivateAll()
                    templateDao.insert(
                        MessageTemplate(
                            id = UUID.randomUUID().toString(),
                            name = templateName.trim(),
                            body = templateBody.trim(),
                            isActive = true
                        )
                    )
                } else {
                    val updated = existingActive.copy(
                        name = templateName.trim(),
                        body = templateBody.trim(),
                        isActive = true
                    )
                    templateDao.update(updated)
                }

                val enableActivityRecognition = detectionMode.equals("ACTIVITY", ignoreCase = true)
                preferencesManager.setActivityRecognitionEnabled(enableActivityRecognition)
                preferencesManager.setServiceEnabled(true)
                DriveReplyService.start(app)

                preferencesManager.setHasCompletedOnboarding(true)
                _uiState.value = _uiState.value.copy(isCompleted = true)
            } finally {
                _uiState.value = _uiState.value.copy(isCompleting = false)
            }
        }
    }
}
