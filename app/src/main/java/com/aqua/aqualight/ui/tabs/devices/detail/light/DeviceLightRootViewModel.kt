package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.ui.common.devicepresence.DevicePresencePresentationMapper
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareUpdatePlan
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceLightRootViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = DevicesRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(DeviceLightRootUiState())
    val uiState: StateFlow<DeviceLightRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: DeviceUid? = null
    private var observeJob: Job? = null
    private var pendingOtaStatusRequestId: String = ""
    private var pendingOtaStartRequestId: String = ""
    private var pendingOtaClearRequestId: String = ""
    private var otaTestOverlayText: String? = null
    private var lastSnapshot: DeviceSnapshot? = null
    private var lastOtaPlan: DeviceFirmwareUpdatePlan? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
        if (deviceUidText.isBlank()) {
            observeJob?.cancel()

            _uiState.value = DeviceLightRootUiState(
                title = fallbackTitle.ifBlank { DEFAULT_TITLE },
                deviceUid = "",
                connectionStatus = "Offline",
            )
            return
        }

        val deviceUid = DeviceUid(deviceUidText)
        if (boundDeviceUid == deviceUid) {
            return
        }

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        clearOtaTestState()
        repository.connectRuntime(deviceUid)

        _uiState.value = DeviceLightRootUiState(
            title = fallbackTitle.ifBlank { DEFAULT_TITLE },
            deviceUid = deviceUid.value,
            connectionStatus = "Offline",
        )

        observeJob = viewModelScope.launch {
            repository.observeDevice(deviceUid).collect { snapshot ->
                if (snapshot != null) {
                    lastSnapshot = snapshot
                }

                _uiState.value = (
                    snapshot
                        ?.toLightRootUiState(fallbackTitle = fallbackTitle)
                        ?: DeviceLightRootUiState(
                            title = fallbackTitle.ifBlank { DEFAULT_TITLE },
                            deviceUid = deviceUid.value,
                            connectionStatus = "Offline",
                        )
                    )
            }
        }
    }

    fun checkBetaOtaManifest() {
        val deviceUid = boundDeviceUid
        if (deviceUid == null) {
            updateOtaTestText("OTA test failed: deviceUid is missing.")
            return
        }

        val snapshot = repository.currentDevice(deviceUid) ?: lastSnapshot
        if (snapshot == null) {
            updateOtaTestText("OTA test failed: device snapshot is not available yet.")
            return
        }

        val firmwareUpdate = repository.runtimeModules()?.firmwareUpdate
        if (firmwareUpdate == null) {
            updateOtaTestText("OTA test failed: runtime module provider is not configured.")
            return
        }

        viewModelScope.launch {
            lastOtaPlan = null
            updateOtaTestText(
                "Checking signed beta manifest...\n" +
                    "URL: $OTA_TEST_BETA_MANIFEST_URL"
            )

            repository.connectRuntime(deviceUid)

            val result = firmwareUpdate.fetchAndPlanUpdate(
                snapshot = snapshot,
                manifestUrl = OTA_TEST_BETA_MANIFEST_URL,
                applyNow = true
            )

            result.fold(
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
        val deviceUid = boundDeviceUid
        if (deviceUid == null) {
            updateOtaTestText("OTA start failed: deviceUid is missing.")
            return
        }

        val plan = lastOtaPlan
        if (plan == null) {
            updateOtaTestText("Run Check Beta Manifest first. No OTA plan is ready.")
            return
        }

        repository.connectRuntime(deviceUid)

        val result = repository.runtimeModules()
            ?.firmwareUpdate
            ?.startUpdate(plan)

        pendingOtaStartRequestId = result?.messageId.orEmpty()

        if (result?.isSuccess == true && pendingOtaStartRequestId.isNotBlank()) {
            updateOtaTestText(
                "OTA start command sent.\n" +
                    "messageId: $pendingOtaStartRequestId\n" +
                    "target: ${plan.targetVersion}\n" +
                    "Watch firmware.ota.progress events below."
            )
        } else {
            updateOtaTestText(
                "OTA start command could not be sent.\n" +
                    (result?.errorMessage?.ifBlank { null } ?: "Unknown WebSocket send error.")
            )
        }
    }

    fun requestOtaTestStatus() {
        val deviceUid = boundDeviceUid
        if (deviceUid == null) {
            updateOtaTestText("OTA status failed: deviceUid is missing.")
            return
        }

        repository.connectRuntime(deviceUid)

        val result = repository.runtimeModules()
            ?.firmwareUpdate
            ?.requestOtaStatus(deviceUid)

        pendingOtaStatusRequestId = result?.messageId.orEmpty()

        if (result?.isSuccess == true && pendingOtaStatusRequestId.isNotBlank()) {
            updateOtaTestText("OTA status command sent. messageId: $pendingOtaStatusRequestId")
        } else {
            updateOtaTestText(
                "OTA status command could not be sent.\n" +
                    (result?.errorMessage?.ifBlank { null } ?: "Unknown WebSocket send error.")
            )
        }
    }

    fun clearOtaTestStatus() {
        val deviceUid = boundDeviceUid
        if (deviceUid == null) {
            updateOtaTestText("OTA clear failed: deviceUid is missing.")
            return
        }

        repository.connectRuntime(deviceUid)

        val result = repository.runtimeModules()
            ?.firmwareUpdate
            ?.clearOtaStatus(deviceUid)

        pendingOtaClearRequestId = result?.messageId.orEmpty()

        if (result?.isSuccess == true && pendingOtaClearRequestId.isNotBlank()) {
            updateOtaTestText("OTA clear command sent. messageId: $pendingOtaClearRequestId")
        } else {
            updateOtaTestText(
                "OTA clear command could not be sent.\n" +
                    (result?.errorMessage?.ifBlank { null } ?: "Unknown WebSocket send error.")
            )
        }
    }

    private fun DeviceSnapshot.toLightRootUiState(fallbackTitle: String): DeviceLightRootUiState {
        val productName = product.displayName
            .ifBlank { product.model }
            .ifBlank { fallbackTitle }
            .ifBlank { DEFAULT_TITLE }

        val menuSections = DeviceRootMenuMapper.light(this)

        return DeviceLightRootUiState(
            title = productName,
            deviceUid = deviceUid.value,
            connectionStatus = DevicePresencePresentationMapper.availabilityLabel(connectionState.onlineState),
            ipText = endpoint.ip.ifBlank { "Unknown" },
            firmwareText = firmwareLabel(),
            modelText = modelLabel(),
            channelCountText = if (limits.lightChannelCount > 0) {
                limits.lightChannelCount.toString()
            } else {
                "Unknown"
            },
            featuresText = featureLabel(),
            manualMenuText = menuSections.primaryText(
                emptyText = "Firmware has not exposed manual light controls yet."
            ),
            programsMenuText = menuSections.secondaryText(
                emptyText = "Firmware has not exposed light programs, presets or settings yet."
            )
        )
    }

    private fun DeviceSnapshot.firmwareLabel(): String {
        return listOf(
            firmwareVersion.ifBlank { null },
            firmwareBuild.ifBlank { null }
        )
            .filterNotNull()
            .joinToString(separator = " / ")
            .ifBlank { "Unknown" }
    }

    private fun DeviceSnapshot.modelLabel(): String {
        return listOf(
            product.model.ifBlank { null },
            product.hardwareRevision.ifBlank { null }
        )
            .filterNotNull()
            .joinToString(separator = " / ")
            .ifBlank { "Unknown" }
    }

    private fun DeviceSnapshot.featureLabel(): String {
        val labels = buildList {
            if (capabilities.manualLight) add("Manual light")
            if (capabilities.lightProgram) add("Program")
            if (capabilities.lightPresets) add("Presets")
            if (capabilities.lightSimulation) add("Simulation")
            if (capabilities.temperature) add("Temperature")
            if (capabilities.ota) add("OTA")
            supportedFeatures
                .filter { feature -> feature.isNotBlank() }
                .forEach { feature -> add(feature) }
            supportedScreens
                .filter { screen -> screen.isNotBlank() }
                .forEach { screen -> add(screen) }
        }

        return labels
            .distinct()
            .joinToString(separator = ", ")
            .ifBlank { "Unknown" }
    }

    private fun formatPlan(plan: DeviceFirmwareUpdatePlan): String {
        return """
            OTA beta manifest verified.
            READY TO START
            current: ${plan.currentVersion}
            target: ${plan.targetVersion}
            channel: ${plan.channel}
            env: ${plan.env}
            productKey: ${plan.productKey}
            productId: ${plan.productId}
            hw: ${plan.hardwareRevision}
            file: ${plan.firmware.filename}
            size: ${plan.firmware.size}
            sha256: ${plan.firmware.sha256.take(16)}...
        """.trimIndent()
    }

    private fun formatOtaSnapshot(
        title: String,
        snapshot: DeviceFirmwareOtaSnapshot
    ): String {
        return buildString {
            appendLine(title)
            appendLine("phase: ${snapshot.phaseRaw}")
            appendLine("active: ${snapshot.active}")
            appendLine("completed: ${snapshot.completed}")
            appendLine("success: ${snapshot.success}")
            appendLine("failed: ${snapshot.failed}")
            appendLine("progress: ${snapshot.progressPercent}%")
            appendLine("bytes: ${snapshot.bytesWritten}/${snapshot.contentLength}")
            if (snapshot.targetVersion.isNotBlank()) {
                appendLine("target: ${snapshot.targetVersion}")
            }
            if (snapshot.httpStatus != 0) {
                appendLine("http: ${snapshot.httpStatus}")
            }
            if (snapshot.lastError.isNotBlank()) {
                appendLine("error: ${snapshot.lastError}")
            }
            if (snapshot.lastErrorField.isNotBlank()) {
                appendLine("field: ${snapshot.lastErrorField}")
            }
            if (snapshot.restartRequired) {
                appendLine("restartRequired: true")
            }
            if (snapshot.restartScheduled) {
                appendLine("restartScheduled: true")
            }
        }.trim()
    }



    private companion object {
        const val DEFAULT_TITLE = "Light"
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
