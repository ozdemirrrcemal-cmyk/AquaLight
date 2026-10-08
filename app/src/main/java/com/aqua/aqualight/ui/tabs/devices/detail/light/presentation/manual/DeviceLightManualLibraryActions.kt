package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.toCommercialLightError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Coordinates saved-scene actions without coupling live device mutation to persistence. */
internal class DeviceLightManualLibraryActions(
    private val operations: DeviceLightLibraryOperations,
    private val scope: CoroutineScope,
    private val currentDeviceUid: () -> String,
    private val currentState: () -> DeviceLightManualControlUiState,
    private val applyScene: (DeviceLightManualScene) -> Unit,
    private val emitEffect: (DeviceLightManualControlEffect) -> Unit
) {
    private var manualNames: List<String> = emptyList()
    private var manualScenes: Map<String, DeviceLightManualScene> = emptyMap()

    fun bind(deviceUid: String): Job {
        manualNames = emptyList()
        manualScenes = emptyMap()
        return scope.launch {
            operations.observeLibrary(deviceUid).collect { result ->
                if (result is DeviceLightLibraryResult.Available) {
                    val manualEntries = result.snapshot.entries
                        .filter { entry -> entry.kind == DeviceLightLibraryKind.MANUAL }
                    manualNames = manualEntries.map { entry -> entry.name }
                    manualScenes = manualEntries.mapNotNull { entry ->
                        val payload = entry.payload as? DeviceLightLibraryPayload.Manual
                        payload?.let { entry.id to it.scene.toManualScene() }
                    }.toMap()
                }
            }
        }
    }

    fun requestLoad() {
        emitEffect(DeviceLightManualControlEffect.OpenLibrary)
    }

    fun loadSavedScene(entryId: String) {
        val scene = manualScenes[entryId]
        if (scene != null) {
            applyScene(scene)
        } else {
            emitEffect(
                DeviceLightManualControlEffect.ShowError(
                    DeviceLightLibraryFailure.NOT_FOUND.toCommercialLightError().messageRes
                )
            )
        }
    }

    fun requestSaveAs() {
        scope.launch {
            val usedNames = runCatching {
                operations.usedNames(DeviceLightLibraryKind.MANUAL)
            }.getOrDefault(manualNames)
            emitEffect(DeviceLightManualControlEffect.OpenSaveAs(usedNames))
        }
    }

    fun saveAs(name: String) {
        val deviceUid = currentDeviceUid().takeIf(String::isNotBlank) ?: return
        val state = currentState()
        if (!state.libraryActionsEnabled || state.channels.isEmpty()) return
        val scene = DeviceLightLibraryScene(
            state.channels.associate { channel ->
                channel.id.toLibraryChannel() to channel.percent
            }
        )
        scope.launch {
            when (val result = operations.saveManual(deviceUid, name, scene)) {
                is DeviceLightLibraryMutationResult.Success -> emitEffect(
                    DeviceLightManualControlEffect.ShowSuccess(
                        R.string.device_light_library_saved_success
                    )
                )
                is DeviceLightLibraryMutationResult.Failed -> emitEffect(
                    DeviceLightManualControlEffect.ShowError(
                        result.failure.toCommercialLightError().messageRes
                    )
                )
            }
        }
    }
}

private fun DeviceLightManualChannelId.toLibraryChannel(): DeviceLightLibraryChannel = when (this) {
    DeviceLightManualChannelId.RED -> DeviceLightLibraryChannel.RED
    DeviceLightManualChannelId.GREEN -> DeviceLightLibraryChannel.GREEN
    DeviceLightManualChannelId.BLUE -> DeviceLightLibraryChannel.BLUE
    DeviceLightManualChannelId.WHITE -> DeviceLightLibraryChannel.WHITE
}


private fun DeviceLightLibraryScene.toManualScene(): DeviceLightManualScene =
    DeviceLightManualScene(
        channels = channels.mapKeys { (channel, _) -> channel.toManualChannel() }
    )

private fun DeviceLightLibraryChannel.toManualChannel(): DeviceLightManualChannel = when (this) {
    DeviceLightLibraryChannel.RED -> DeviceLightManualChannel.RED
    DeviceLightLibraryChannel.GREEN -> DeviceLightManualChannel.GREEN
    DeviceLightLibraryChannel.BLUE -> DeviceLightManualChannel.BLUE
    DeviceLightLibraryChannel.WHITE -> DeviceLightManualChannel.WHITE
}
