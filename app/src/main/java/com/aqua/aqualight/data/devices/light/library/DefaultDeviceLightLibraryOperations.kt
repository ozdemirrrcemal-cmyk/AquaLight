package com.aqua.aqualight.data.devices.light.library

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
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
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomInstallPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomPoint
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightControlSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightLibraryReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManualSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.currentLibrary
import com.aqua.aqualight.data.devices.runtime.modules.light.installCustom
import com.aqua.aqualight.data.devices.runtime.modules.light.requestCustom
import java.util.UUID
import java.util.concurrent.CancellationException
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
        val frame = runtime.currentLibrary(
            uid,
            DeviceLightLibraryReadAuthority.PRESENTATION
        )
            ?: return@combine DeviceLightLibraryResult.Failed(
                DeviceLightLibraryFailure.NOT_CONNECTED
            )
        val status = frame.status
        val product = status.product
        val target = status.toLibraryTarget(uid)
        val entries = storedEntries
            .filter { entry ->
                entry.productKey == product.wireValue &&
                    entry.channelKeysList == product.sceneFields
            }
            .map { entry ->
                entry.toApplicationEntry(
                    product = product,
                    status = status,
                    installedCustom = frame.custom
                )
            }
        DeviceLightLibraryResult.Available(
            DeviceLightLibrarySnapshot(
                target = target,
                entries = entries,
                firmwareWriteAuthoritative = runtime.currentLibrary(
                    uid,
                    DeviceLightLibraryReadAuthority.AUTHORITATIVE
                ) != null
            )
        )
    }.catch {
        emit(DeviceLightLibraryResult.Failed(DeviceLightLibraryFailure.INVALID_DATA))
    }

    override suspend fun refreshInstalledCustom(deviceUid: String) {
        val uid = deviceUid.toDeviceUidOrNull() ?: return
        val runtime = devicesRepository.runtimeModules()?.light ?: return
        try {
            val refreshedControl = controlOperations.refreshControl(uid.value)
            if (
                refreshedControl is DeviceLightControlResult.Available ||
                runtime.currentStatus(uid) != null
            ) {
                runtime.requestCustom(uid)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Unit
        }
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

    override suspend fun load(
        deviceUid: String,
        entryId: String
    ): DeviceLightLibraryMutationResult {
        val uid = deviceUid.toDeviceUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        val status = if (uid == null) null else runtime?.currentStatus(uid)
        val entry = runCatching { store.snapshot().singleOrNull { stored -> stored.id == entryId } }
            .getOrNull()
        return when {
            uid == null || runtime == null ->
                failed(DeviceLightLibraryFailure.UNAVAILABLE)
            status == null -> failed(DeviceLightLibraryFailure.NOT_CONNECTED)
            entry == null -> failed(DeviceLightLibraryFailure.NOT_FOUND)
            entry.productKey != status.product.wireValue ||
                entry.channelKeysList != status.product.sceneFields ->
                failed(DeviceLightLibraryFailure.INCOMPATIBLE)
            else -> try {
                loadEntry(uid, entry, status, runtime)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed(DeviceLightLibraryFailure.INVALID_DATA)
            }
        }
    }

    private suspend fun loadEntry(
        uid: DeviceUid,
        entry: StoredDeviceLightLibraryEntry,
        status: DeviceLightStatus,
        runtime: DeviceLightRuntimeRepository
    ): DeviceLightLibraryMutationResult = when (entry.kind) {
        StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_MANUAL -> {
            runtime.setManual(
                uid,
                DeviceLightManualSetPayload(entry.manual.toRuntimeScene(status.product))
            ).toLibraryMutationResult(entry.id)
        }
        StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_CUSTOM -> {
            val payload = DeviceLightCustomInstallPayload(
                expectedRevision = status.custom.revision,
                weekdaysMask = entry.custom.weekdaysMask,
                points = entry.custom.pointsList.map { point ->
                    DeviceLightCustomPoint(
                        timeMs = point.timeMs,
                        scene = point.channelsList.toRuntimeScene(status.product)
                    )
                }
            )
            val installOutcome = runtime.installCustom(uid, payload)
            if (
                installOutcome is DeviceRuntimeCommandOutcome.Success &&
                runtime.currentStatus(uid)?.mode != DeviceLightMode.CUSTOM
            ) {
                runtime.setControl(
                    uid,
                    DeviceLightControlSetPayload(DeviceLightMode.CUSTOM)
                ).toLibraryMutationResult(entry.id)
            } else {
                installOutcome.toLibraryMutationResult(entry.id)
            }
        }
        StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_UNSPECIFIED,
        StoredDeviceLightLibraryKind.UNRECOGNIZED ->
            failed(DeviceLightLibraryFailure.INVALID_DATA)
    }

}
