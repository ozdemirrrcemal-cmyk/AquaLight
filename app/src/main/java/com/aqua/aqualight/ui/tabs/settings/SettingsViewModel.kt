package com.aqua.aqualight.ui.tabs.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.user.UserPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val userPrefs =
        UserPreferencesManager.create(
            appContext
        )

    private val devicesStore =
        DevicesDataStoreManager.create(
            appContext
        )

    private val _uiState =
        MutableStateFlow(
            SettingsUiState()
        )

    val uiState: StateFlow<SettingsUiState> =
        _uiState.asStateFlow()

    init {
        DevicePresenceMonitor.start(
            context = appContext
        )

        observeSettingsOverview()
    }

    private fun observeSettingsOverview() {
        viewModelScope.launch {
            combine(
                userPrefs.userPrefsFlow,
                devicesStore.devicesFlow,
                DevicePresenceMonitor.statuses
            ) { prefs, devices, statuses ->
                SettingsUiState(
                    username = prefs.username,
                    email = prefs.email,
                    profilePhotoUrl = prefs.profilePhotoUrl,
                    activeDeviceCount = devices.count { device ->
                        statuses[device.id]?.isOnline == true
                    }
                )
            }.collect { state ->
                _uiState.update {
                    state
                }
            }
        }
    }
}

data class SettingsUiState(
    val username: String = "",
    val email: String = "",
    val profilePhotoUrl: String = "",
    val activeDeviceCount: Int = 0
)
