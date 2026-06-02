package com.example.drivereply.ui.onboarding

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

data class OnboardingUiState(
    val isCompleting: Boolean = false
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
        onComplete: () -> Unit
    ) {
        if (_uiState.value.isCompleting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCompleting = true)

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
            }

            preferencesManager.setHasCompletedOnboarding(true)
            _uiState.value = _uiState.value.copy(isCompleting = false)
            onComplete()
        }
    }
}
