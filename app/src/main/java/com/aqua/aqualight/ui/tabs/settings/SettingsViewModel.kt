package com.aqua.aqualight.ui.tabs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceStatusOperations
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.ui.tabs.settings.device.DeviceSettingsDeviceOverviewUi
import com.aqua.aqualight.ui.tabs.settings.device.DeviceStatusSnapshotMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(
    userProfileOperations: UserProfileOperations,
    private val deviceStatusOperations: DeviceStatusOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        deviceStatusOperations.start(viewModelScope)
        viewModelScope.launch {
            combine(
                userProfileOperations.profile,
                deviceStatusOperations.statuses
            ) { profile, statuses ->
                SettingsUiState(
                    username = profile.username,
                    email = profile.email,
                    profilePhotoUrl = profile.profilePhotoUrl,
                    deviceOverview = DeviceStatusSnapshotMapper.overview(statuses)
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}

data class SettingsUiState(
    val username: String = "",
    val email: String = "",
    val profilePhotoUrl: String = "",
    val deviceOverview: DeviceSettingsDeviceOverviewUi = DeviceSettingsDeviceOverviewUi()
)
