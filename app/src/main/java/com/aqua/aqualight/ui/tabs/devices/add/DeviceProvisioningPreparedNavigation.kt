package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuOpenResult
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.provisioning.ProvisionedDevice
import com.aqua.aqualight.application.text.AppTextResolver
import com.aqua.aqualight.ui.common.devicepresence.DeviceMenuUnavailableMessageMapper
import java.util.concurrent.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Owns only the short-lived prepared menu-open handoff between provisioning completion and
 * navigation commit. Device state remains owned by the central device runtime repositories.
 */
internal class DeviceProvisioningPreparedNavigation(
    private val menuOpenUseCase: DeviceMenuOpenUseCase,
    private val textResolver: AppTextResolver,
    private val uiState: MutableStateFlow<DeviceProvisioningProgressUiState>,
    private val events: Channel<DeviceProvisioningProgressEvent>
) {
    private var pending: DeviceMenuOpenResult.Ready? = null

    suspend fun open(device: ProvisionedDevice) {
        val event = prepareEvent(device)
        if (event is DeviceProvisioningProgressEvent.ShowAddedDeviceUnavailable) {
            uiState.value = uiState.value.copy(
                stepThree = textResolver.get(R.string.device_provisioning_step_setup_complete),
                canStart = false,
                buttonText = textResolver.get(R.string.device_provisioning_unavailable),
                showProgress = false,
                isCancelling = false,
                wifiCredentialFailure = null
            )
        }
        events.send(event)
    }

    fun finish(deviceUid: String, committed: Boolean) {
        val ready = pending?.takeIf { candidate ->
            candidate.access.deviceUid == deviceUid
        }
        if (ready != null) {
            if (!committed) menuOpenUseCase.abandon(ready)
            pending = null
        }
    }

    fun abandonAll() {
        pending?.let(menuOpenUseCase::abandon)
        pending = null
    }

    private suspend fun prepareEvent(
        device: ProvisionedDevice
    ): DeviceProvisioningProgressEvent {
        abandonAll()
        val result = runCatching { menuOpenUseCase.resolve(device.deviceUid) }
        val failure = result.exceptionOrNull()
        if (failure is CancellationException) throw failure

        return result.fold(
            onSuccess = { resolved -> resolved.toProgressEvent(device) },
            onFailure = {
                unavailableEvent(
                    title = device.title,
                    reason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                )
            }
        )
    }

    private fun DeviceMenuOpenResult.toProgressEvent(
        device: ProvisionedDevice
    ): DeviceProvisioningProgressEvent = when (this) {
        is DeviceMenuOpenResult.Ready -> {
            pending = this
            DeviceProvisioningProgressEvent.OpenAddedDevice(
                device = device.copy(
                    title = access.title.ifBlank { device.title },
                    family = access.family
                )
            )
        }
        is DeviceMenuOpenResult.Unavailable -> unavailableEvent(
            title = title.ifBlank { device.title },
            reason = reason
        )
    }

    private fun unavailableEvent(
        title: String,
        reason: DeviceMenuUnavailableReason
    ): DeviceProvisioningProgressEvent.ShowAddedDeviceUnavailable =
        DeviceProvisioningProgressEvent.ShowAddedDeviceUnavailable(
            title = title,
            messageRes = DeviceMenuUnavailableMessageMapper.messageRes(reason)
        )
}

internal fun DeviceProvisioningProgressViewModel.onDeviceNavigationFinished(
    deviceUid: String,
    committed: Boolean
) {
    preparedNavigation.finish(deviceUid, committed)
}