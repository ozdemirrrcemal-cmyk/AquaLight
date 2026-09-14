package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryEntry
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryPayload
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibrarySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Adds fixture install state while preserving the owner-scoped production DataStore. */
internal class DebugFixtureLightLibraryOperations(
    private val delegate: DeviceLightLibraryOperations,
    private val runtime: DebugLightFixtureRuntime
) : DeviceLightLibraryOperations {

    override fun observeLibrary(deviceUid: String): Flow<DeviceLightLibraryResult> =
        if (runtime.contains(deviceUid)) {
            combine(delegate.observeLibrary(deviceUid), runtime.revisions) { result, _ ->
                result.withFixtureLoadedState(deviceUid, runtime)
            }
        } else {
            delegate.observeLibrary(deviceUid)
        }

    override suspend fun usedNames(kind: DeviceLightLibraryKind): List<String> =
        delegate.usedNames(kind)

    override suspend fun refreshInstalledCustom(deviceUid: String) {
        if (!runtime.contains(deviceUid)) delegate.refreshInstalledCustom(deviceUid)
    }

    override suspend fun saveManual(
        deviceUid: String,
        name: String,
        scene: DeviceLightLibraryScene
    ): DeviceLightLibraryMutationResult = delegate.saveManual(deviceUid, name, scene)

    override suspend fun saveCustom(
        deviceUid: String,
        name: String,
        weekdaysMask: Int,
        points: List<DeviceLightLibraryCustomPoint>
    ): DeviceLightLibraryMutationResult = delegate.saveCustom(
        deviceUid = deviceUid,
        name = name,
        weekdaysMask = weekdaysMask,
        points = points
    )

    override suspend fun rename(
        entryId: String,
        name: String
    ): DeviceLightLibraryMutationResult = delegate.rename(entryId, name)

    override suspend fun delete(entryId: String): DeviceLightLibraryMutationResult =
        delegate.delete(entryId)

    override suspend fun load(
        deviceUid: String,
        entryId: String
    ): DeviceLightLibraryMutationResult = if (runtime.contains(deviceUid)) {
        loadFixtureCustom(deviceUid, entryId)
    } else {
        delegate.load(deviceUid, entryId)
    }

    private suspend fun loadFixtureCustom(
        deviceUid: String,
        entryId: String
    ): DeviceLightLibraryMutationResult {
        val result = delegate.observeLibrary(deviceUid).first()
        val entry = (result as? DeviceLightLibraryResult.Available)
            ?.snapshot
            ?.entries
            ?.singleOrNull { candidate -> candidate.id == entryId }
        return when (val payload = entry?.payload) {
            is DeviceLightLibraryPayload.Custom -> if (runtime.install(deviceUid, payload)) {
                DeviceLightLibraryMutationResult.Success(entryId)
            } else {
                failed(DeviceLightLibraryFailure.UNAVAILABLE)
            }
            is DeviceLightLibraryPayload.Manual -> delegate.load(deviceUid, entryId)
            null -> failed(DeviceLightLibraryFailure.NOT_FOUND)
        }
    }
}

private fun DeviceLightLibraryResult.withFixtureLoadedState(
    deviceUid: String,
    runtime: DebugLightFixtureRuntime
): DeviceLightLibraryResult = when (this) {
    is DeviceLightLibraryResult.Available -> DeviceLightLibraryResult.Available(
        DeviceLightLibrarySnapshot(
            target = snapshot.target,
            entries = snapshot.entries.map { entry -> entry.withLoadedState(deviceUid, runtime) }
        )
    )
    is DeviceLightLibraryResult.Failed -> this
}

private fun DeviceLightLibraryEntry.withLoadedState(
    deviceUid: String,
    runtime: DebugLightFixtureRuntime
): DeviceLightLibraryEntry = when (val currentPayload = payload) {
    is DeviceLightLibraryPayload.Custom -> copy(
        isLoaded = runtime.isInstalled(deviceUid, currentPayload)
    )
    is DeviceLightLibraryPayload.Manual -> this
}

private fun failed(failure: DeviceLightLibraryFailure): DeviceLightLibraryMutationResult =
    DeviceLightLibraryMutationResult.Failed(failure)
