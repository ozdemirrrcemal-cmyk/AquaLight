package com.aqua.aqualight.data.devices.light.library

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibrarySnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.currentStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

/** Commercial adapter joining owner-scoped persistence to the single Light runtime authority. */
internal class DefaultDeviceLightLibraryOperations(
    private val ownerUid: String,
    private val store: DeviceLightLibraryStore,
    private val devicesRepository: DevicesRepository,
    private val controlOperations: DeviceLightControlOperations,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : DeviceLightLibraryOperations {

    private val persistence = DeviceLightLibraryPersistence(
        ownerUid = ownerUid,
        store = store,
        controlOperations = controlOperations,
        nowMillis = nowMillis,
        newId = newId
    )

    override fun observeLibrary(deviceUid: String): Flow<DeviceLightLibraryResult> {
        val uid = deviceUid.toDeviceUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        return if (uid == null) {
            flowOf(DeviceLightLibraryResult.Failed(DeviceLightLibraryFailure.UNAVAILABLE))
        } else if (runtime == null) {
            flowOf(DeviceLightLibraryResult.Failed(DeviceLightLibraryFailure.UNAVAILABLE))
        } else {
            observeAvailableLibrary(uid, runtime)
        }
    }

    private fun observeAvailableLibrary(
        uid: DeviceUid,
        runtime: DeviceLightRuntimeRepository
    ): Flow<DeviceLightLibraryResult> = combine(
        store.observeEntries(),
        runtime.stateRevision
    ) { storedEntries, _ ->
        val status = runtime.currentStatus(
            uid,
            DeviceLightStatusReadAuthority.PRESENTATION
        ) ?: return@combine DeviceLightLibraryResult.Failed(
                DeviceLightLibraryFailure.NOT_CONNECTED
            )
        val product = status.product
        val target = status.toLibraryTarget(uid)
        val entries = storedEntries
            .filter { entry ->
                entry.productKey == product.wireValue &&
                    entry.channelKeysList == product.sceneFields
            }
            .map(StoredDeviceLightLibraryEntry::toApplicationEntry)
        DeviceLightLibraryResult.Available(
            DeviceLightLibrarySnapshot(
                target = target,
                entries = entries
            )
        )
    }.catch {
        emit(DeviceLightLibraryResult.Failed(DeviceLightLibraryFailure.INVALID_DATA))
    }

    override suspend fun usedNames(kind: DeviceLightLibraryKind): List<String> =
        persistence.usedNames(kind)

    override suspend fun saveManual(
        deviceUid: String,
        name: String,
        scene: DeviceLightLibraryScene
    ): DeviceLightLibraryMutationResult = persistence.saveManual(deviceUid, name, scene)

    override suspend fun saveCustom(
        deviceUid: String,
        name: String,
        weekdaysMask: Int,
        points: List<DeviceLightLibraryCustomPoint>
    ): DeviceLightLibraryMutationResult =
        persistence.saveCustom(deviceUid, name, weekdaysMask, points)

    override suspend fun rename(
        entryId: String,
        name: String
    ): DeviceLightLibraryMutationResult = persistence.rename(entryId, name)

    override suspend fun delete(entryId: String): DeviceLightLibraryMutationResult =
        persistence.delete(entryId)


}
