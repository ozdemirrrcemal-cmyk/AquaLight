package com.aqua.aqualight.ui.tabs.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.ui.tabs.settings.device.DeviceSettingsDeviceOverviewUi
import com.aqua.aqualight.ui.tabs.settings.device.DeviceStatusSnapshotMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val userPrefs = UserPreferencesManager.create(application.applicationContext)
    private val devicesRepository = DevicesRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        devicesRepository.start(viewModelScope)
        observeSettingsOverview()
    }

    private fun observeSettingsOverview() {
        viewModelScope.launch {
            combine(
                userPrefs.userPrefsFlow,
                devicesRepository.devices
            ) { prefs, snapshots ->
                SettingsUiState(
                    username = prefs.username,
                    email = prefs.email,
                    profilePhotoUrl = prefs.profilePhotoUrl,
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
