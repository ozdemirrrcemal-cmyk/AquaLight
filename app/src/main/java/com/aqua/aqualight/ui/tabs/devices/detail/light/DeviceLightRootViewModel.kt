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
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusParser
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class DeviceLightRootViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = DevicesRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(DeviceLightRootUiState())
    val uiState: StateFlow<DeviceLightRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: DeviceUid? = null
    private var observeJob: Job? = null
    private var runtimeStatusJob: Job? = null
    private var pendingStatusRequestId: String = ""
    private var pendingOtaStatusRequestId: String = ""
    private var pendingOtaStartRequestId: String = ""
    private var pendingOtaClearRequestId: String = ""

    private var runtimeChannelCountText: String? = null
    private var runtimeManualText: String? = null
    private var runtimeProgramsText: String? = null
    private var otaTestOverlayText: String? = null
    private var lastSnapshot: DeviceSnapshot? = null
    private var lastOtaPlan: DeviceFirmwareUpdatePlan? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
        if (deviceUidText.isBlank()) {
            observeJob?.cancel()
            runtimeStatusJob?.cancel()
            clearRuntimeOverlay()

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
        runtimeStatusJob?.cancel()
        clearRuntimeOverlay()
        clearOtaTestState()

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
                    ).withRuntimeOverlay()
            }
        }

        runtimeStatusJob = viewModelScope.launch {
            observeRuntimeStatus(deviceUid)
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

    private suspend fun observeRuntimeStatus(deviceUid: DeviceUid) {
        val events = repository.runtimeEvents()
        if (events == null) {
            updateRuntimeOverlay(
                manualText = "Light runtime WebSocket repository is not configured."
            )
            return
        }

        coroutineScope {
            launch {
                repository.connectRuntime(deviceUid)
                delay(800L)

                val result = repository.runtimeModules()
                    ?.light
                    ?.requestStatus(deviceUid)

                pendingStatusRequestId = result?.messageId.orEmpty()

                if (result?.isSuccess == true && pendingStatusRequestId.isNotBlank()) {
                    updateRuntimeOverlay(
                        manualText = "Light runtime status requested..."
                    )
                } else {
                    updateRuntimeOverlay(
                        manualText = "Light runtime status request could not be sent."
                    )
                }
            }

            events.collect { event ->
                handleRuntimeEvent(
                    deviceUid = deviceUid,
                    event = event
                )
            }
        }
    }

    private fun handleRuntimeEvent(
        deviceUid: DeviceUid,
        event: AqlWsEvent
    ) {
        if (event.deviceUid != deviceUid) return

        val message = (event as? AqlWsEvent.Message)
            ?.parsed
            ?: return

        if (handleOtaRuntimeMessage(message)) {
            return
        }

        val statusRequestId = pendingStatusRequestId
        if (statusRequestId.isBlank() || message.id != statusRequestId) {
            return
        }

        when (message) {
            is AqlWsIncomingMessage.Response -> {
                if (!message.ok) {
                    updateRuntimeStatusError(
                        statusCode = message.statusCode,
                        message = "Runtime status request failed."
                    )
                    return
                }

                val data = message.json.optJSONObject("data") ?: message.json
                val status = DeviceLightStatusParser.parse(data)

                updateRuntimeOverlay(
                    channelCountText = status.channelCount.toString(),
                    manualText = "Runtime channels: ${status.channelCount}, manual: ${status.manualSupported}, liveEdit: ${status.liveEditEnabled}",
                    programsText = "Programs: ${status.programCount}, presets: ${status.presetsSupported}, simulation: ${status.simulationSupported}, writable: ${!status.runtime.readOnly}"
                )
            }

            is AqlWsIncomingMessage.Error -> {
                updateRuntimeOverlay(manualText = "Light runtime status error: ${message.statusCode} ${message.message}".trim())
            }

            else -> Unit
        }
    }

    private fun handleOtaRuntimeMessage(message: AqlWsIncomingMessage): Boolean {
        when (message) {
            is AqlWsIncomingMessage.Event -> {
                if (
                    message.event == DeviceFirmwareRuntimeContract.Event.OTA_PROGRESS ||
                    message.event == DeviceFirmwareRuntimeContract.Event.OTA_COMPLETED
                ) {
                    val data = message.json.optJSONObject("data") ?: JSONObject()
                    val snapshot = DeviceFirmwareStatusParser.parseOtaProgressEvent(data)
                    updateOtaTestText(formatOtaSnapshot("OTA event: ${message.event}", snapshot))
                    return true
                }
            }

            is AqlWsIncomingMessage.Response -> {
                if (message.id == pendingOtaStartRequestId) {
                    pendingOtaStartRequestId = ""
                    if (!message.ok) {
                        updateOtaTestText("OTA start rejected: HTTP ${message.statusCode}")
                        return true
                    }
                    val data = message.json.optJSONObject("data") ?: JSONObject()
                    val accepted = DeviceFirmwareStatusParser.parseOtaStartAccepted(data)
                    updateOtaTestText(
                        "OTA start accepted: ${accepted.accepted}\n" +
                            formatOtaSnapshot("OTA snapshot", accepted.ota)
                    )
                    return true
                }

                if (message.id == pendingOtaStatusRequestId) {
                    pendingOtaStatusRequestId = ""
                    if (!message.ok) {
                        updateOtaTestText("OTA status rejected: HTTP ${message.statusCode}")
                        return true
                    }
                    val data = message.json.optJSONObject("data") ?: JSONObject()
                    val snapshot = DeviceFirmwareStatusParser.parseOtaStatusResponse(data)
                    updateOtaTestText(formatOtaSnapshot("OTA status", snapshot))
                    return true
                }

                if (message.id == pendingOtaClearRequestId) {
                    pendingOtaClearRequestId = ""
                    if (!message.ok) {
                        updateOtaTestText("OTA clear rejected: HTTP ${message.statusCode}")
                        return true
                    }
                    val data = message.json.optJSONObject("data") ?: JSONObject()
                    val clear = DeviceFirmwareStatusParser.parseOtaClearResult(data)
                    updateOtaTestText(
                        "OTA clear result: ${clear.cleared}\n" +
                            formatOtaSnapshot("After clear", clear.ota)
                    )
                    return true
                }
            }

            is AqlWsIncomingMessage.Error -> {
                if (message.id == pendingOtaStartRequestId) {
                    pendingOtaStartRequestId = ""
                    updateOtaTestText(formatOtaError("OTA start error", message))
                    return true
                }
                if (message.id == pendingOtaStatusRequestId) {
                    pendingOtaStatusRequestId = ""
                    updateOtaTestText(formatOtaError("OTA status error", message))
                    return true
                }
                if (message.id == pendingOtaClearRequestId) {
                    pendingOtaClearRequestId = ""
                    updateOtaTestText(formatOtaError("OTA clear error", message))
                    return true
                }
            }

            else -> Unit
        }

        return false
    }

    private fun updateRuntimeStatusError(
        statusCode: Int,
        message: String
    ) {
        val errorText = "$DEFAULT_TITLE runtime status error: $statusCode $message".trim()
        updateRuntimeOverlay(manualText = errorText)
    }

    private fun updateRuntimeOverlay(
        channelCountText: String? = null,
        manualText: String? = null,
        programsText: String? = null
    ) {
        if (channelCountText != null) runtimeChannelCountText = channelCountText
        if (manualText != null) runtimeManualText = manualText
        if (programsText != null) runtimeProgramsText = programsText

        _uiState.value = _uiState.value.withRuntimeOverlay()
    }

    private fun updateOtaTestText(text: String) {
        otaTestOverlayText = text
        _uiState.value = _uiState.value.withRuntimeOverlay()
    }

    private fun clearRuntimeOverlay() {
        pendingStatusRequestId = ""
        runtimeChannelCountText = null
        runtimeManualText = null
        runtimeProgramsText = null
    }

    private fun clearOtaTestState() {
        pendingOtaStatusRequestId = ""
        pendingOtaStartRequestId = ""
        pendingOtaClearRequestId = ""
        lastOtaPlan = null
        otaTestOverlayText = null
    }

    private fun DeviceLightRootUiState.withRuntimeOverlay(): DeviceLightRootUiState {
        return copy(
            channelCountText = runtimeChannelCountText ?: channelCountText,
            manualMenuText = runtimeManualText ?: manualMenuText,
            programsMenuText = runtimeProgramsText ?: programsMenuText,
            otaTestText = otaTestOverlayText ?: otaTestText
        )
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

    private fun formatOtaError(
        title: String,
        message: AqlWsIncomingMessage.Error
    ): String {
        return buildString {
            appendLine(title)
            appendLine("status: ${message.statusCode}")
            if (message.code.isNotBlank()) appendLine("code: ${message.code}")
            if (message.field.isNotBlank()) appendLine("field: ${message.field}")
            appendLine("message: ${message.message}")
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
