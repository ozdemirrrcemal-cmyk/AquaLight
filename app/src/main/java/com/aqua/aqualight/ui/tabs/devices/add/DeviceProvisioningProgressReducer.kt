package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.provisioning.ProvisioningTransportEvent
import com.aqua.aqualight.application.text.AppTextResolver

internal class DeviceProvisioningProgressReducer(
    private val presenter: ProvisioningProgressPresenter,
    private val textResolver: AppTextResolver
) {
    fun reduce(
        current: DeviceProvisioningProgressUiState,
        event: ProvisioningTransportEvent,
        setupCompleted: Boolean
    ): DeviceProvisioningProgressUiState = when (event) {
        is ProvisioningTransportEvent.Connecting,
        is ProvisioningTransportEvent.Connected,
        ProvisioningTransportEvent.ServicesDiscovered,
        ProvisioningTransportEvent.StartSessionWritten,
        ProvisioningTransportEvent.WifiCredentialsWritten -> reduceConnectionPhase(current, event)
        is ProvisioningTransportEvent.StatusReceived -> reduceStatus(current, event)
        is ProvisioningTransportEvent.DeviceInfoVerified,
        is ProvisioningTransportEvent.RuntimeHandoffReceived,
        ProvisioningTransportEvent.Completed,
        ProvisioningTransportEvent.FinalizeSetupWritten -> current
        is ProvisioningTransportEvent.Failed -> reduceFailure(current, event)
        ProvisioningTransportEvent.Disconnected -> reduceDisconnected(current, setupCompleted)
    }

    private fun reduceConnectionPhase(
        current: DeviceProvisioningProgressUiState,
        event: ProvisioningTransportEvent
    ): DeviceProvisioningProgressUiState = when (event) {
        is ProvisioningTransportEvent.Connecting -> current.copy(
            title = string(R.string.device_provisioning_connecting_title),
            message = string(R.string.device_provisioning_connecting_message),
            stepThree = string(R.string.device_provisioning_connecting_step),
            canStart = false,
            buttonText = string(R.string.device_provisioning_running),
            showProgress = true,
            wifiCredentialFailure = null
        )
        is ProvisioningTransportEvent.Connected -> current.copy(
            title = string(R.string.device_provisioning_device_found_title),
            message = string(R.string.device_provisioning_prepare_service_message),
            stepThree = string(R.string.device_provisioning_bluetooth_connected_step),
            showProgress = true,
            wifiCredentialFailure = null
        )
        ProvisioningTransportEvent.ServicesDiscovered -> current.copy(
            title = string(R.string.device_provisioning_secure_session_title),
            message = string(R.string.device_provisioning_secure_session_message),
            stepThree = string(R.string.device_provisioning_secure_session_step),
            showProgress = true,
            wifiCredentialFailure = null
        )
        ProvisioningTransportEvent.StartSessionWritten -> current.copy(
            title = string(R.string.device_provisioning_session_started_title),
            message = string(R.string.device_provisioning_sending_wifi_message),
            stepThree = string(R.string.device_provisioning_sending_wifi_step),
            showProgress = true,
            wifiCredentialFailure = null
        )
        ProvisioningTransportEvent.WifiCredentialsWritten -> current.copy(
            title = string(R.string.device_provisioning_wifi_sent_title),
            message = string(R.string.device_provisioning_wifi_connecting_message),
            stepThree = string(R.string.device_provisioning_wifi_connecting_step),
            showProgress = true,
            wifiCredentialFailure = null
        )
        else -> current
    }

    private fun reduceStatus(
        current: DeviceProvisioningProgressUiState,
        event: ProvisioningTransportEvent.StatusReceived
    ): DeviceProvisioningProgressUiState {
        val presentation = presenter.status(event.statusMessage)
        return current.copy(
            title = presentation.title,
            message = presentation.message,
            stepThree = presentation.step,
            showProgress = presentation.showProgress,
            canStart = presentation.canRetry,
            buttonText = string(R.string.device_provisioning_try_again),
            wifiCredentialFailure = presentation.wifiCredentialFailure
        )
    }

    private fun reduceFailure(
        current: DeviceProvisioningProgressUiState,
        event: ProvisioningTransportEvent.Failed
    ): DeviceProvisioningProgressUiState = current.copy(
        title = string(R.string.device_provisioning_failed_title),
        message = presenter.friendlyTransportError(event.message),
        stepThree = string(R.string.device_provisioning_setup_stopped),
        canStart = true,
        buttonText = string(R.string.device_provisioning_start_again),
        showProgress = false,
        requiresFreshDeviceSelection = true,
        wifiCredentialFailure = null
    )

    private fun reduceDisconnected(
        current: DeviceProvisioningProgressUiState,
        setupCompleted: Boolean
    ): DeviceProvisioningProgressUiState {
        if (setupCompleted) return current.copy(wifiCredentialFailure = null)

        return current.copy(
            title = string(R.string.device_provisioning_connection_closed_title),
            message = string(R.string.device_provisioning_connection_closed_message),
            stepThree = string(R.string.device_provisioning_connection_closed_step),
            canStart = true,
            buttonText = string(R.string.device_provisioning_start_again),
            showProgress = false,
            requiresFreshDeviceSelection = true,
            wifiCredentialFailure = null
        )
    }

    private fun string(resId: Int, vararg args: Any): String =
        textResolver.get(resId, *args)
}
