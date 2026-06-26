package com.aqua.aqualight.ui.tabs.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.user.UserPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val userPrefs = UserPreferencesManager.create(application.applicationContext)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettingsOverview()
    }

    private fun observeSettingsOverview() {
        viewModelScope.launch {
            userPrefs.userPrefsFlow.collect { prefs ->
                _uiState.update {
                    SettingsUiState(
                        username = prefs.username,
                        email = prefs.email,
                        profilePhotoUrl = prefs.profilePhotoUrl
                    )
                }
            }
        }
    }
}

data class SettingsUiState(
    val username: String = "",
    val email: String = "",
    val profilePhotoUrl: String = ""
)
