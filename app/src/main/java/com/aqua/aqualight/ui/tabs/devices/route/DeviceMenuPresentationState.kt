package com.aqua.aqualight.ui.tabs.devices.route

import androidx.annotation.StringRes
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.ui.common.devicepresence.DeviceMenuUnavailableMessageMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DeviceMenuPresentationState {

    val requestId: Long?
    val deviceUid: String?

    data object Idle : DeviceMenuPresentationState {
        override val requestId: Long? = null
        override val deviceUid: String? = null
    }

    data class Preparing(
        override val requestId: Long,
        override val deviceUid: String
    ) : DeviceMenuPresentationState

    data class Ready(
        override val requestId: Long,
        val route: DeviceRoute
    ) : DeviceMenuPresentationState {
        override val deviceUid: String = route.deviceUid
    }

    data class Failure(
        override val requestId: Long,
        override val deviceUid: String,
        val title: String,
        @StringRes val messageRes: Int
    ) : DeviceMenuPresentationState
}

/**
 * Screen-scoped transition state shared by every device-card entry point.
 * Terminal results remain observable until the presenting UI acknowledges the matching request.
 */
internal class DeviceMenuPresentationStateHolder(
    private val routeResolver: DeviceRouteResolver
) {
    private val mutableState = MutableStateFlow<DeviceMenuPresentationState>(
        DeviceMenuPresentationState.Idle
    )
    val state: StateFlow<DeviceMenuPresentationState> = mutableState.asStateFlow()

    val isIdle: Boolean
        get() = mutableState.value is DeviceMenuPresentationState.Idle

    private var nextRequestId = 0L

    fun begin(deviceUid: String): DeviceMenuPresentationState.Preparing? {
        if (deviceUid.isBlank() || !isIdle) return null

        val preparing = DeviceMenuPresentationState.Preparing(
            requestId = ++nextRequestId,
            deviceUid = deviceUid
        )
        mutableState.value = preparing
        return preparing
    }

    fun complete(
        request: DeviceMenuPresentationState.Preparing,
        result: DeviceMenuAccessResult
    ) {
        if (mutableState.value != request) return

        mutableState.value = result.toPresentationState(
            requestId = request.requestId,
            requestedDeviceUid = request.deviceUid,
            routeResolver = routeResolver
        )
    }

    fun acknowledge(requestId: Long): Boolean {
        val current = mutableState.value
        val isMatchingTerminalState = when (current) {
            is DeviceMenuPresentationState.Ready -> current.requestId == requestId
            is DeviceMenuPresentationState.Failure -> current.requestId == requestId
            DeviceMenuPresentationState.Idle,
            is DeviceMenuPresentationState.Preparing -> false
        }
        if (!isMatchingTerminalState) return false

        mutableState.value = DeviceMenuPresentationState.Idle
        return true
    }
}

private fun DeviceMenuAccessResult.toPresentationState(
    requestId: Long,
    requestedDeviceUid: String,
    routeResolver: DeviceRouteResolver
): DeviceMenuPresentationState =
    when (this) {
        is DeviceMenuAccessResult.Available -> {
            if (presentationPrepared) {
                DeviceMenuPresentationState.Ready(
                    requestId = requestId,
                    route = routeResolver.resolve(this)
                )
            } else {
                DeviceMenuPresentationState.Failure(
                    requestId = requestId,
                    deviceUid = requestedDeviceUid,
                    title = title,
                    messageRes = DeviceMenuUnavailableMessageMapper.messageRes(
                        DeviceMenuUnavailableReason.CURRENT_DATA_NOT_READY
                    )
                )
            }
        }

        is DeviceMenuAccessResult.Unavailable ->
            DeviceMenuPresentationState.Failure(
                requestId = requestId,
                deviceUid = requestedDeviceUid,
                title = title,
                messageRes = DeviceMenuUnavailableMessageMapper.messageRes(reason)
            )
    }
