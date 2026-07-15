package com.aqua.aqualight.ui.tabs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.user.UserProfileOperations
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.ui.tabs.settings.device.DeviceSettingsDeviceOverviewUi
import com.aqua.aqualight.ui.tabs.settings.device.DeviceStatusSnapshotMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(
    userProfileOperations: UserProfileOperations,
    private val devicesRepository: DevicesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        devicesRepository.start(viewModelScope)
        viewModelScope.launch {
            combine(
                userProfileOperations.profile,
                devicesRepository.devices
            ) { profile, snapshots ->
                SettingsUiState(
                    username = profile.username,
                    email = profile.email,
                    profilePhotoUrl = profile.profilePhotoUrl,
                    deviceOverview = DeviceStatusSnapshotMapper.overview(snapshots)
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
