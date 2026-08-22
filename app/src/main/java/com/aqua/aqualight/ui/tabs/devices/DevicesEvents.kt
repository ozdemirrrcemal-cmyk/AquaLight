package com.aqua.aqualight.ui.tabs.devices

sealed class DevicesEvent {
    data class ShowDeletePartialSuccess(
        val succeededCount: Int,
        val failedCount: Int
    ) : DevicesEvent()

    data class ShowDeleteFailed(
        val failedCount: Int
    ) : DevicesEvent()
}
