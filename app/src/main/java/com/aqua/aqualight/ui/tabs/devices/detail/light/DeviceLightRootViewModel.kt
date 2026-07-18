package com.aqua.aqualight.ui.tabs.devices.detail.light

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.application.text.AppTextResolver
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceLightRootViewModel(
    private val rootOperations: DeviceRootOperations,
    private val firmwareUpdateOperations: DeviceFirmwareUpdateOperations,
    private val textResolver: AppTextResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(emptyState("", ""))
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
            updateOtaTestText(textResolver.get(R.string.device_ota_test_missing_uid_check))
            return
        }

        viewModelScope.launch {
            lastOtaPlan = null
            updateOtaTestText(
                textResolver.get(
                    R.string.device_ota_test_checking_manifest,
                    OTA_TEST_BETA_MANIFEST_URL
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
                        textResolver.get(
                            R.string.device_ota_test_plan_failed,
                            error.message ?: error::class.java.simpleName
                        )
                    )
                }
            )
        }
    }

    fun startOtaTestUpdate() {
        if (boundDeviceUid.isBlank()) {
            updateOtaTestText(textResolver.get(R.string.device_ota_test_missing_uid_start))
            return
        }
        val plan = lastOtaPlan
        if (plan == null) {
            updateOtaTestText(textResolver.get(R.string.device_ota_test_no_plan))
            return
        }

        val result = firmwareUpdateOperations.startUpdate(plan)
        pendingOtaStartRequestId = result.messageId
        if (result.isSuccess && pendingOtaStartRequestId.isNotBlank()) {
            updateOtaTestText(
                textResolver.get(
                    R.string.device_ota_test_start_sent,
                    pendingOtaStartRequestId,
                    plan.targetVersion
                )
            )
        } else {
            updateOtaTestText(
                textResolver.get(
                    R.string.device_ota_test_start_failed,
                    result.errorMessage.ifBlank {
                        textResolver.get(R.string.device_ota_test_unknown_websocket_error)
                    }
                )
            )
        }
    }

    fun requestOtaTestStatus() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) {
            updateOtaTestText(textResolver.get(R.string.device_ota_test_missing_uid_status))
            return
        }

        val result = firmwareUpdateOperations.requestStatus(deviceUid)
        pendingOtaStatusRequestId = result.messageId
        if (result.isSuccess && pendingOtaStatusRequestId.isNotBlank()) {
            updateOtaTestText(
                textResolver.get(
                    R.string.device_ota_test_status_sent,
                    pendingOtaStatusRequestId
                )
            )
        } else {
            updateOtaTestText(
                textResolver.get(
                    R.string.device_ota_test_status_failed,
                    result.errorMessage.ifBlank {
                        textResolver.get(R.string.device_ota_test_unknown_websocket_error)
                    }
                )
            )
        }
    }

    fun clearOtaTestStatus() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank()) {
            updateOtaTestText(textResolver.get(R.string.device_ota_test_missing_uid_clear))
            return
        }

        val result = firmwareUpdateOperations.clearStatus(deviceUid)
        pendingOtaClearRequestId = result.messageId
        if (result.isSuccess && pendingOtaClearRequestId.isNotBlank()) {
            updateOtaTestText(
                textResolver.get(
                    R.string.device_ota_test_clear_sent,
                    pendingOtaClearRequestId
                )
            )
        } else {
            updateOtaTestText(
                textResolver.get(
                    R.string.device_ota_test_clear_failed,
                    result.errorMessage.ifBlank {
                        textResolver.get(R.string.device_ota_test_unknown_websocket_error)
                    }
                )
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
        title = fallbackTitle.ifBlank { textResolver.get(R.string.device_root_light_title) },
        deviceUid = deviceUid,
        connectionStatus = textResolver.get(R.string.device_runtime_offline),
        ipText = textResolver.get(R.string.device_runtime_unknown),
        firmwareText = textResolver.get(R.string.device_runtime_unknown),
        modelText = textResolver.get(R.string.device_runtime_unknown),
        channelCountText = textResolver.get(R.string.device_runtime_unknown),
        featuresText = textResolver.get(R.string.device_runtime_unknown),
        manualMenuText = textResolver.get(R.string.device_root_light_manual_empty),
        programsMenuText = textResolver.get(R.string.device_root_light_programs_empty),
        otaTestText = otaTestOverlayText
            ?: textResolver.get(R.string.device_ota_test_default_text)
    )

    private fun DeviceRootSnapshot.toLightRootUiState(
        fallbackTitle: String
    ): DeviceLightRootUiState {
        val menuSections = DeviceRootMenuMapper.light(this)
        return DeviceLightRootUiState(
            title = title.ifBlank { fallbackTitle }
                .ifBlank { textResolver.get(R.string.device_root_light_title) },
            deviceUid = deviceUid,
            connectionStatus = DeviceRootPresentationMapper.availabilityLabel(this, textResolver),
            ipText = ipAddress.ifBlank { textResolver.get(R.string.device_runtime_unknown) },
            firmwareText = firmwareLabel.ifBlank { textResolver.get(R.string.device_runtime_unknown) },
            modelText = modelLabel.ifBlank { textResolver.get(R.string.device_runtime_unknown) },
            channelCountText = lightChannelCount.takeIf { it > 0 }?.toString()
                ?: textResolver.get(R.string.device_runtime_unknown),
            featuresText = DeviceRootPresentationMapper.lightFeatureLabel(this, textResolver),
            manualMenuText = menuSections.primaryText(
                textResolver,
                R.string.device_root_light_manual_empty
            ),
            programsMenuText = menuSections.secondaryText(
                textResolver,
                R.string.device_root_light_programs_empty
            ),
            otaTestText = otaTestOverlayText
                ?: textResolver.get(R.string.device_ota_test_default_text)
        )
    }

    private fun formatPlan(plan: PreparedDeviceFirmwareUpdate): String {
        return textResolver.get(
            R.string.device_ota_test_plan_summary,
            plan.currentVersion,
            plan.targetVersion,
            plan.channel,
            plan.environment,
            plan.productKey,
            plan.productId,
            plan.hardwareRevision,
            plan.filename,
            plan.sizeBytes.toString(),
            plan.sha256.take(16)
        )
    }

    private companion object {
        const val OTA_TEST_BETA_MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/download/v1.0.1/manifest-beta.json"
    }
}

data class DeviceLightRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionStatus: String = "",
    val ipText: String = "",
    val firmwareText: String = "",
    val modelText: String = "",
    val channelCountText: String = "",
    val featuresText: String = "",
    val manualMenuText: String = "",
    val programsMenuText: String = "",
    val otaTestText: String = ""
)
