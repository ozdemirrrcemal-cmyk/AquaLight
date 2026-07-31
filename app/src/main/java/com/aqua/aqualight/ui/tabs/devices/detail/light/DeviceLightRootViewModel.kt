package com.aqua.aqualight.ui.tabs.devices.detail.light

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.ui.common.text.AquaUiText
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
    private var otaTestOverlayText: AquaUiText? = null
    private var lastOtaPlan: PreparedDeviceFirmwareUpdate? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
        if (deviceUidText.isBlank() || deviceUidText != deviceUidText.trim()) {
            observeJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = emptyState(fallbackTitle, "")
            return
        }
        if (boundDeviceUid == deviceUidText) return

        boundDeviceUid = deviceUidText
        observeJob?.cancel()
        clearOtaTestState()
        _uiState.value = rootOperations.current(deviceUidText)?.toLightRootUiState(fallbackTitle)
            ?: emptyState(fallbackTitle, deviceUidText)
        rootOperations.connect(deviceUidText)
        observeJob = viewModelScope.launch {
            rootOperations.observe(deviceUidText).collect { snapshot ->
                _uiState.value = snapshot?.toLightRootUiState(fallbackTitle)
                    ?: emptyState(fallbackTitle, deviceUidText)
            }
        }
    }

    fun checkBetaOtaManifest() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) {
            updateOtaTestText(R.string.device_ota_test_missing_uid)
            return
        }

        viewModelScope.launch {
            lastOtaPlan = null
            updateOtaTestText(
                AquaUiText.Resource(
                    R.string.device_ota_test_checking_manifest,
                    listOf(OTA_TEST_BETA_MANIFEST_URL)
                )
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
                        AquaUiText.Resource(
                            R.string.device_ota_test_plan_failed,
                            listOf(
                                AquaUiText.Dynamic(
                                    error.message ?: error::class.java.simpleName
                                )
                            )
                        )
                    )
                }
            )
        }
    }

    fun startOtaTestUpdate() {
        if (boundDeviceUid.isBlank()) {
            updateOtaTestText(R.string.device_ota_test_start_missing_uid)
            return
        }
        val plan = lastOtaPlan
        if (plan == null) {
            updateOtaTestText(R.string.device_ota_test_plan_not_ready)
            return
        }

        viewModelScope.launch {
            val result = firmwareUpdateOperations.startUpdate(plan)
            if (result.successful) {
                updateOtaTestText(
                    AquaUiText.Resource(
                        R.string.device_ota_test_start_sent,
                        listOf(result.correlationId, plan.targetVersion)
                    )
                )
            } else {
                updateOtaTestText(
                    errorText(
                        R.string.device_ota_test_start_failed,
                        result.errorMessage
                    )
                )
            }
        }
    }

    fun requestOtaTestStatus() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) {
            updateOtaTestText(R.string.device_ota_test_status_missing_uid)
            return
        }

        viewModelScope.launch {
            val result = firmwareUpdateOperations.requestStatus(deviceUid)
            if (result.successful) {
                updateOtaTestText(
                    AquaUiText.Resource(
                        R.string.device_ota_test_status_sent,
                        listOf(result.correlationId)
                    )
                )
            } else {
                updateOtaTestText(
                    errorText(
                        R.string.device_ota_test_status_failed,
                        result.errorMessage
                    )
                )
            }
        }
    }

    fun clearOtaTestStatus() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) {
            updateOtaTestText(R.string.device_ota_test_clear_missing_uid)
            return
        }

        viewModelScope.launch {
            val result = firmwareUpdateOperations.clearStatus(deviceUid)
            if (result.successful) {
                updateOtaTestText(
                    AquaUiText.Resource(
                        R.string.device_ota_test_clear_sent,
                        listOf(result.correlationId)
                    )
                )
            } else {
                updateOtaTestText(
                    errorText(
                        R.string.device_ota_test_clear_failed,
                        result.errorMessage
                    )
                )
            }
        }
    }

    private fun updateOtaTestText(@StringRes textRes: Int) {
        updateOtaTestText(AquaUiText.Resource(textRes))
    }

    private fun updateOtaTestText(text: AquaUiText) {
        otaTestOverlayText = text
        _uiState.value = _uiState.value.copy(otaTestText = text)
    }

    private fun errorText(@StringRes messageRes: Int, detail: String): AquaUiText {
        val detailText = detail.takeIf(String::isNotBlank)
            ?.let(AquaUiText::Dynamic)
            ?: AquaUiText.Resource(R.string.device_ota_test_unknown_websocket_error)
        return AquaUiText.Resource(messageRes, listOf(detailText))
    }

    private fun clearOtaTestState() {
        lastOtaPlan = null
        otaTestOverlayText = null
    }

    private fun emptyState(fallbackTitle: String, deviceUid: String) = DeviceLightRootUiState(
        title = fallbackTitle,
        deviceUid = deviceUid,
        connectionStatusRes = R.string.device_offline,
        otaTestText = otaTestOverlayText
            ?: AquaUiText.Resource(R.string.device_ota_test_locked_message)
    )

    private fun DeviceRootSnapshot.toLightRootUiState(
        fallbackTitle: String
    ): DeviceLightRootUiState {
        val menuSections = DeviceRootMenuMapper.light(this)
        return DeviceLightRootUiState(
            title = title.ifBlank { fallbackTitle },
            deviceUid = deviceUid,
            connectionStatusRes = DeviceRootPresentationMapper.availabilityLabelRes(this),
            ipText = ipAddress,
            firmwareText = firmwareLabel,
            modelText = modelLabel,
            channelCountText = lightChannelCount.takeIf { it > 0 }?.toString().orEmpty(),
            featuresText = DeviceRootPresentationMapper.lightFeatureText(this),
            manualMenuText = menuSections.primaryText(
                R.string.device_menu_light_manual_unavailable
            ),
            programsMenuText = menuSections.secondaryText(
                R.string.device_menu_light_programs_unavailable
            ),
            otaTestText = otaTestOverlayText
                ?: AquaUiText.Resource(R.string.device_ota_test_locked_message)
        )
    }

    private fun formatPlan(plan: PreparedDeviceFirmwareUpdate): AquaUiText =
        AquaUiText.Resource(
            R.string.device_ota_test_plan_summary,
            listOf(
                plan.currentVersion,
                plan.targetVersion,
                plan.channel,
                plan.environment,
                plan.productKey,
                plan.productId,
                plan.hardwareRevision,
                plan.filename,
                plan.sizeBytes,
                plan.sha256.take(16)
            )
        )

    private companion object {
        const val OTA_TEST_BETA_MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/download/v1.0.1/manifest-beta.json"
    }
}
