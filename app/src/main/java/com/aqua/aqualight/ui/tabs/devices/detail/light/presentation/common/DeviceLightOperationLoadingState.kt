package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common

/** One Light presentation policy for owner-keyed global loading during reads and commands. */
internal interface DeviceLightOperationLoadingState {
    val initialLoading: Boolean
    val operationInProgress: Boolean

    val showGlobalLoading: Boolean
        get() = initialLoading || operationInProgress
}
