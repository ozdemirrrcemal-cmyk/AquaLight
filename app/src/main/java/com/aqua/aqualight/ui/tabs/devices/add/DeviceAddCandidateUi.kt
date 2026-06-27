package com.aqua.aqualight.ui.tabs.devices.add

data class DeviceAddCandidateUi(
    val id: String,
    val title: String,
    val serial: String,
    val model: String,
    val status: String,
    val rssiLabel: String = ""
)
