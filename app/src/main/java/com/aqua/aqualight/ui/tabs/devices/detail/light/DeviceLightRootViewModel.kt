package com.aqua.aqualight.ui.tabs.devices.detail.light

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceLightRootViewModel(
    private val rootOperations: DeviceRootOperations,
    private val firmwareUpdateOperations: DeviceFirmwareUpdateOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceLightRootUiState())
    val uiState: StateFlow<DeviceLightRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var observeJob: Job? = null
    private var pendingOtaStatusRequestId: String = ""
    private var pendingOtaStartRequestId: String = ""
    private var pendingOtaClearRequestId: String = ""
    private var otaTestOverlayText: String? = null
    private var lastOtaPlan: PreparedDeviceFirmwareUpdate? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = emptyState(fallbackTitle, "")
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        clearOtaTestState()
        rootOperations.connect(deviceUid)
        _uiState.value = emptyState(fallbackTitle, deviceUid)
        observeJob = viewModelScope.launch {
            rootOperations.observe(deviceUid).collect { snapshot ->
                _uiState.value = snapshot?.toLightRootUiState(fallbackTitle)
                    ?: emptyState(fallbackTitle, deviceUid)
            }
        }
    }

    fun checkBetaOtaManifest() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) {
            updateOtaTestText("OTA test failed: deviceUid is missing.")
            return
        }

        viewModelScope.launch {
            lastOtaPlan = null
            updateOtaTestText(
                "Checking signed beta manifest...\n" +
                    "URL: $OTA_TEST_BETA_MANIFEST_URL"
            )
            firmwareUpdateOperations.prepareUpdate(
                deviceUid = deviceUid,
                manifestUrl = OTA_TEST_BETA_MANIFEST_URL,
                applyNow = true
            ).fold(
                onSuccess = { plan ->
                    lastOtaPlan = plan
                    updateOtaTestText(formatPlan(plan))
                },
                onFailure = { error ->
                    updateOtaTestText(
                        "OTA test plan failed:\n" +
                            (error.message ?: error::class.java.simpleName)
                    )
                }
            )
        }
    }

    fun startOtaTestUpdate() {
        if (boundDeviceUid.isBlank()) {
            updateOtaTestText("OTA start failed: deviceUid is missing.")
            return
        }
        val plan = lastOtaPlan
        if (plan == null) {
            updateOtaTestText("Run Check Beta Manifest first. No OTA plan is ready.")
            return
        }

        val result = firmwareUpdateOperations.startUpdate(plan)
        pendingOtaStartRequestId = result.messageId
        if (result.isSuccess && pendingOtaStartRequestId.isNotBlank()) {
            updateOtaTestText(
                "OTA start command sent.\n" +
                    "messageId: $pendingOtaStartRequestId\n" +
                    "target: ${plan.targetVersion}\n" +
                    "Watch firmware.ota.progress events below."
            )
        } else {
            updateOtaTestText(
                "OTA start command could not be sent.\n" +
                    (result.errorMessage.ifBlank { "Unknown WebSocket send error." })
            )
        }
    }

    fun requestOtaTestStatus() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) {
            updateOtaTestText("OTA status failed: deviceUid is missing.")
            return
        }

        val result = firmwareUpdateOperations.requestStatus(deviceUid)
        pendingOtaStatusRequestId = result.messageId
        if (result.isSuccess && pendingOtaStatusRequestId.isNotBlank()) {
            updateOtaTestText("OTA status command sent. messageId: $pendingOtaStatusRequestId")
        } else {
            updateOtaTestText(
                "OTA status command could not be sent.\n" +
                    (result.errorMessage.ifBlank { "Unknown WebSocket send error." })
            )
        }
    }

    fun clearOtaTestStatus() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) {
            updateOtaTestText("OTA clear failed: deviceUid is missing.")
            return
        }

        val result = firmwareUpdateOperations.clearStatus(deviceUid)
        pendingOtaClearRequestId = result.messageId
        if (result.isSuccess && pendingOtaClearRequestId.isNotBlank()) {
            updateOtaTestText("OTA clear command sent. messageId: $pendingOtaClearRequestId")
        } else {
            updateOtaTestText(
                "OTA clear command could not be sent.\n" +
                    (result.errorMessage.ifBlank { "Unknown WebSocket send error." })
            )
        }
    }

    private fun updateOtaTestText(text: String) {
        otaTestOverlayText = text
        _uiState.value = _uiState.value.copy(otaTestText = text)
    }

    private fun clearOtaTestState() {
        pendingOtaStatusRequestId = ""
        pendingOtaStartRequestId = ""
        pendingOtaClearRequestId = ""
        lastOtaPlan = null
        otaTestOverlayText = null
    }

    private fun emptyState(fallbackTitle: String, deviceUid: String) = DeviceLightRootUiState(
        title = fallbackTitle.ifBlank { DEFAULT_TITLE },
        deviceUid = deviceUid,
        connectionStatus = "Offline",
        otaTestText = otaTestOverlayText ?: DEFAULT_OTA_TEXT
    )

    private fun DeviceRootSnapshot.toLightRootUiState(
        fallbackTitle: String
    ): DeviceLightRootUiState {
        val menuSections = DeviceRootMenuMapper.light(this)
        return DeviceLightRootUiState(
            title = title.ifBlank { fallbackTitle }.ifBlank { DEFAULT_TITLE },
            deviceUid = deviceUid,
            connectionStatus = DeviceRootPresentationMapper.availabilityLabel(this),
            ipText = ipAddress.ifBlank { "Unknown" },
            firmwareText = firmwareLabel.ifBlank { "Unknown" },
            modelText = modelLabel.ifBlank { "Unknown" },
            channelCountText = lightChannelCount.takeIf { it > 0 }?.toString() ?: "Unknown",
            featuresText = DeviceRootPresentationMapper.lightFeatureLabel(this),
            manualMenuText = menuSections.primaryText(
                "Firmware has not exposed manual light controls yet."
            ),
            programsMenuText = menuSections.secondaryText(
                "Firmware has not exposed light programs, presets or settings yet."
            ),
            otaTestText = otaTestOverlayText ?: DEFAULT_OTA_TEXT
        )
    }

    private fun formatPlan(plan: PreparedDeviceFirmwareUpdate): String {
        return """
            OTA beta manifest verified.
            READY TO START
            current: ${plan.currentVersion}
            target: ${plan.targetVersion}
            channel: ${plan.channel}
            env: ${plan.environment}
            productKey: ${plan.productKey}
            productId: ${plan.productId}
            hw: ${plan.hardwareRevision}
            file: ${plan.filename}
            size: ${plan.sizeBytes}
            sha256: ${plan.sha256.take(16)}...
        """.trimIndent()
    }

    private companion object {
        const val DEFAULT_TITLE = "Light"
        const val DEFAULT_OTA_TEXT = "Hidden OTA test panel. Long press Firmware to unlock."
        const val OTA_TEST_BETA_MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/download/v1.0.1/manifest-beta.json"
    }
}

data class DeviceLightRootUiState(
    val title: String = "Light",
    val deviceUid: String = "",
    val connectionStatus: String = "Unknown",
    val ipText: String = "Unknown",
    val firmwareText: String = "Unknown",
    val modelText: String = "Unknown",
    val channelCountText: String = "Unknown",
    val featuresText: String = "Unknown",
    val manualMenuText: String = "Firmware has not exposed manual light controls yet.",
    val programsMenuText: String = "Firmware has not exposed light programs, presets or settings yet.",
    val otaTestText: String = "Hidden OTA test panel. Long press Firmware to unlock."
)
