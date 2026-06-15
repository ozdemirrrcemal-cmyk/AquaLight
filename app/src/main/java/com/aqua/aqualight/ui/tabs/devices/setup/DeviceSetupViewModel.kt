package com.aqua.aqualight.ui.tabs.devices.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.setup.DeviceSetupEntryArgs
import com.aqua.aqualight.data.devices.setup.DeviceSetupUseCase
import com.aqua.aqualight.data.devices.setup.DeviceSetupFlowError
import com.aqua.aqualight.data.devices.setup.DeviceSetupFlowException
import com.aqua.aqualight.data.devices.setup.DeviceSetupProgress
import com.aqua.aqualight.data.devices.setup.DeviceSetupTarget
import com.aqua.aqualight.data.devices.setup.DeviceSetupTargetResolver
import com.aqua.aqualight.data.devices.setup.HomeWifiCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceSetupViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val appContext =
        application.applicationContext

    private val setupUseCase =
        DeviceSetupUseCase(
            context = appContext
        )

    private var setupTarget: DeviceSetupTarget? = null

    private val _uiState =
        MutableStateFlow(
            DeviceSetupUiState()
        )

    val uiState: StateFlow<DeviceSetupUiState> =
        _uiState.asStateFlow()

    private val _events =
        Channel<DeviceSetupEvent>(
            capacity = Channel.BUFFERED
        )

    val events: Flow<DeviceSetupEvent> =
        _events.receiveAsFlow()

    fun initialize(
        args: DeviceSetupEntryArgs
    ) {
        if (_uiState.value.initialized) {
            return
        }

        val target = DeviceSetupTargetResolver.resolve(
            args = args
        )

        setupTarget = target

        _uiState.update {
            it.copy(
                initialized = true,
                setupSsid = target.setupSsid,
                displayName = target.displayName,
                familyName = target.familyName,
                serialText = target.serialText,
                expectedCategory = target.expectedCategory,
                expectedSetupCode = target.expectedSetupCode,
                setupShortId = target.setupShortId,
                setupContractValid = target.setupContractValid,
                statusText = if (target.setupContractValid) {
                    text(R.string.device_setup_choose_home_wifi)
                } else {
                    text(R.string.device_setup_invalid_setup_network)
                },
                activeStep = DeviceSetupStep.WIFI
            )
        }
    }

    fun scanHomeNetworks() {
        val target = setupTarget ?: return

        val currentState = _uiState.value

        if (currentState.isScanningNetworks || currentState.isSettingUp) {
            return
        }

        if (!ensureSetupContractReady(target)) {
            return
        }

        _uiState.update {
            it.copy(
                isScanningNetworks = true,
                isBusy = true,
                activeStep = DeviceSetupStep.WIFI,
                statusText = text(
                    resId = R.string.device_setup_connecting_to_setup,
                    target.setupSsid
                )
            )
        }

        viewModelScope.launch {
            try {
                val networks = setupUseCase.scanHomeWifiNetworks(
                    target = target,
                    onProgress = ::applyProgress
                )

                if (networks.isEmpty()) {
                    showError(
                        text(R.string.device_setup_no_networks_found)
                    )

                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusText = text(R.string.device_setup_no_networks_status)
                        )
                    }

                    return@launch
                }

                _events.send(
                    DeviceSetupEvent.ShowHomeWifiNetworks(
                        networks = networks.map { network ->
                            HomeWifiNetworkUi(
                                ssid = network.ssid,
                                rssi = network.rssi
                            )
                        }
                    )
                )

                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusText = text(R.string.device_setup_select_home_wifi_status)
                    )
                }
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }

                exception.printStackTrace()

                showError(
                    exception.message
                        ?: text(R.string.device_setup_scan_error)
                )

                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusText = text(R.string.device_setup_scan_failed)
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isScanningNetworks = false,
                        isBusy = if (it.isSettingUp) {
                            it.isBusy
                        } else {
                            false
                        }
                    )
                }
            }
        }
    }

    fun onHomeWifiSelected(
        ssid: String
    ) {
        if (ssid.isBlank()) {
            return
        }

        _uiState.update {
            it.copy(
                selectedHomeSsid = ssid,
                homeWifiSsidText = ssid,
                homeWifiSsidError = null,
                statusText = text(
                    resId = R.string.device_setup_selected_network,
                    ssid
                ),
                activeStep = DeviceSetupStep.WIFI
            )
        }
    }

    fun startSetup(
        enteredHomeSsid: String,
        enteredHomePassword: String
    ) {
        val target = setupTarget ?: return

        val currentState = _uiState.value

        if (currentState.isSettingUp) {
            return
        }

        if (!ensureSetupContractReady(target)) {
            return
        }

        val homeSsid = currentState.selectedHomeSsid.ifBlank {
            enteredHomeSsid.trim()
        }

        val homePassword = enteredHomePassword

        if (homeSsid.isBlank()) {
            _uiState.update {
                it.copy(
                    homeWifiSsidError = text(R.string.device_setup_validation_select_wifi)
                )
            }
            return
        }

        if (homePassword.isBlank()) {
            _uiState.update {
                it.copy(
                    homeWifiSsidError = null,
                    homeWifiPasswordError = text(R.string.device_setup_validation_password)
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                homeWifiSsidText = homeSsid,
                homeWifiSsidError = null,
                homeWifiPasswordError = null,
                isSettingUp = true,
                isBusy = true,
                activeStep = DeviceSetupStep.CONNECT,
                statusText = text(R.string.device_setup_preparing)
            )
        }

        viewModelScope.launch {
            try {
                val savedDeviceId = setupUseCase.runSetupFlow(
                    target = target,
                    credentials = HomeWifiCredentials(
                        ssid = homeSsid,
                        password = homePassword
                    ),
                    onProgress = ::applyProgress
                )

                _events.send(
                    DeviceSetupEvent.OpenDevice(
                        deviceId = savedDeviceId,
                        deviceTitle = target.displayName
                    )
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    throw exception
                }

                exception.printStackTrace()

                showError(
                    setupErrorMessage(
                        exception = exception
                    )
                )

                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusText = text(R.string.device_setup_failed)
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isSettingUp = false,
                        isBusy = false
                    )
                }
            }
        }
    }

    private fun applyProgress(
        progress: DeviceSetupProgress
    ) {
        _uiState.update { state ->
            when (progress) {
                DeviceSetupProgress.CONNECTING_TO_SETUP_NETWORK -> {
                    state.copy(
                        isBusy = true,
                        activeStep = DeviceSetupStep.WIFI,
                        statusText = text(
                            resId = R.string.device_setup_connecting_to_setup,
                            state.setupSsid
                        )
                    )
                }

                DeviceSetupProgress.SCANNING_HOME_NETWORKS -> {
                    state.copy(
                        isBusy = true,
                        activeStep = DeviceSetupStep.WIFI,
                        statusText = text(R.string.device_setup_scanning_home_networks)
                    )
                }

                DeviceSetupProgress.SENDING_HOME_WIFI_CREDENTIALS -> {
                    state.copy(
                        isBusy = true,
                        activeStep = DeviceSetupStep.CONNECT,
                        statusText = text(R.string.device_setup_sending_credentials)
                    )
                }

                DeviceSetupProgress.CHECKING_DEVICE_CONNECTION -> {
                    state.copy(
                        isBusy = true,
                        activeStep = DeviceSetupStep.CONNECT,
                        statusText = text(R.string.device_setup_checking_connection)
                    )
                }

                DeviceSetupProgress.JOINING_HOME_WIFI -> {
                    state.copy(
                        isBusy = true,
                        activeStep = DeviceSetupStep.CONNECT,
                        statusText = text(R.string.device_setup_joining_home_wifi)
                    )
                }

                DeviceSetupProgress.CLOSING_SETUP_NETWORK -> {
                    state.copy(
                        isBusy = true,
                        activeStep = DeviceSetupStep.CONNECT,
                        statusText = text(R.string.device_setup_closing_setup_network)
                    )
                }

                DeviceSetupProgress.WAITING_PHONE_HOME_WIFI -> {
                    state.copy(
                        isBusy = true,
                        activeStep = DeviceSetupStep.CONNECT,
                        statusText = text(R.string.device_setup_waiting_phone_home_wifi)
                    )
                }

                DeviceSetupProgress.FINDING_DEVICE_ON_HOME_NETWORK -> {
                    state.copy(
                        isBusy = true,
                        activeStep = DeviceSetupStep.CONNECT,
                        statusText = text(R.string.device_setup_finding_device)
                    )
                }

                DeviceSetupProgress.SUCCESS -> {
                    state.copy(
                        isBusy = true,
                        activeStep = DeviceSetupStep.DONE,
                        statusText = text(R.string.device_setup_success)
                    )
                }
            }
        }
    }

    private fun ensureSetupContractReady(
        target: DeviceSetupTarget
    ): Boolean {
        if (target.setupSsid.isBlank()) {
            showError(
                text(R.string.device_setup_missing_setup_network)
            )
            return false
        }

        if (!target.setupContractValid) {
            showError(
                text(R.string.device_setup_invalid_setup_network)
            )
            return false
        }

        return true
    }

    private fun setupErrorMessage(
        exception: Exception
    ): String {
        if (exception is DeviceSetupFlowException) {
            return when (exception.error) {
                DeviceSetupFlowError.NOT_ACCEPTED -> {
                    exception.message
                        ?: text(R.string.device_setup_not_accepted)
                }

                DeviceSetupFlowError.CONNECTION_FAILED -> {
                    text(R.string.device_setup_connection_failed)
                }

                DeviceSetupFlowError.CLOSE_SETUP_AP_FAILED -> {
                    exception.message
                        ?: text(R.string.device_setup_close_ap_failed)
                }

                DeviceSetupFlowError.PHONE_NOT_HOME_WIFI -> {
                    text(
                        resId = R.string.device_setup_phone_not_home_wifi,
                        exception.detailValue.orEmpty()
                    )
                }

                DeviceSetupFlowError.DEVICE_NOT_FOUND -> {
                    text(R.string.device_setup_device_not_found)
                }
            }
        }

        return exception.message
            ?: text(R.string.device_setup_generic_failed)
    }

    private fun showError(
        message: String
    ) {
        _events.trySend(
            DeviceSetupEvent.ShowError(
                message = message
            )
        )
    }

    private fun text(
        resId: Int,
        vararg args: Any
    ): String {
        return if (args.isEmpty()) {
            appContext.getString(resId)
        } else {
            appContext.getString(
                resId,
                *args
            )
        }
    }

    override fun onCleared() {
        setupUseCase.close()

        super.onCleared()
    }
}

data class DeviceSetupUiState(
    val initialized: Boolean = false,
    val setupSsid: String = "",
    val displayName: String = "Device",
    val familyName: String = "Aqua device",
    val serialText: String = "",
    val expectedCategory: AquaDeviceCategory = AquaDeviceCategory.UNKNOWN,
    val expectedSetupCode: String = "",
    val setupShortId: String = "",
    val setupContractValid: Boolean = false,
    val homeWifiSsidText: String = "",
    val selectedHomeSsid: String = "",
    val homeWifiSsidError: String? = null,
    val homeWifiPasswordError: String? = null,
    val statusText: String = "",
    val activeStep: DeviceSetupStep = DeviceSetupStep.WIFI,
    val isBusy: Boolean = false,
    val isSettingUp: Boolean = false,
    val isScanningNetworks: Boolean = false
) {
    val setupInputEnabled: Boolean
        get() = setupContractValid && !isBusy

    val backEnabled: Boolean
        get() = !isSettingUp && !isScanningNetworks
}

enum class DeviceSetupStep {
    WIFI,
    CONNECT,
    DONE
}

sealed interface DeviceSetupEvent {
    data class ShowError(
        val message: String
    ) : DeviceSetupEvent

    data class ShowHomeWifiNetworks(
        val networks: List<HomeWifiNetworkUi>
    ) : DeviceSetupEvent

    data class OpenDevice(
        val deviceId: Long,
        val deviceTitle: String
    ) : DeviceSetupEvent
}

data class HomeWifiNetworkUi(
    val ssid: String,
    val rssi: Int
)
