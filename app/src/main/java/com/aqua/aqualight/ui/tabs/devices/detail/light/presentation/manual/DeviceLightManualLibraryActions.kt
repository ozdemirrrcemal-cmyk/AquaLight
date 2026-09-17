package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Coordinates saved-scene actions without coupling live device mutation to persistence. */
internal class DeviceLightManualLibraryActions(
    private val operations: DeviceLightLibraryOperations,
    private val scope: CoroutineScope,
    private val currentDeviceUid: () -> String,
    private val currentState: () -> DeviceLightManualControlUiState,
    private val emitEffect: (DeviceLightManualControlEffect) -> Unit
) {
    private var manualNames: List<String> = emptyList()

    fun bind(deviceUid: String): Job {
        manualNames = emptyList()
        return scope.launch {
            operations.observeLibrary(deviceUid).collect { result ->
                if (result is DeviceLightLibraryResult.Available) {
                    manualNames = result.snapshot.entries
                        .filter { entry -> entry.kind == DeviceLightLibraryKind.MANUAL }
                        .map { entry -> entry.name }
                }
            }
        }
    }

    fun requestLoad() {
        emitEffect(DeviceLightManualControlEffect.OpenLibrary)
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
        if (!state.controlsEnabled || state.channels.isEmpty()) return
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
                    DeviceLightManualControlEffect.ShowError(result.failure.messageRes())
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

@StringRes
private fun DeviceLightLibraryFailure.messageRes(): Int = when (this) {
    DeviceLightLibraryFailure.DUPLICATE_NAME ->
        R.string.device_light_library_name_duplicate_error
    DeviceLightLibraryFailure.INVALID_NAME ->
        R.string.device_light_library_name_invalid_error
    DeviceLightLibraryFailure.NOT_CONNECTED ->
        R.string.device_light_library_load_not_connected_error
    else -> R.string.device_light_library_operation_error
}
