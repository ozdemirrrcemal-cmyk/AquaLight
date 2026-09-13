@file:Suppress("TooManyFunctions")

package com.aqua.aqualight.data.devices.light.library

import com.aqua.aqualight.application.devices.light.control.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.control.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryNamePolicy
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibrarySnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomDocument
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomInstallPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomPoint
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManualSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.installCustom
import com.aqua.aqualight.data.devices.runtime.modules.light.requestCustom
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/** Commercial adapter joining owner-scoped persistence to the single Light runtime authority. */
internal class DefaultDeviceLightLibraryOperations(
    private val ownerUid: String,
    private val store: DeviceLightLibraryStore,
    private val devicesRepository: DevicesRepository,
    private val controlOperations: DeviceLightControlOperations,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : DeviceLightLibraryOperations {

    private val installedCustomDocuments =
        MutableStateFlow<Map<DeviceUid, DeviceLightCustomDocument>>(emptyMap())

    override fun observeLibrary(deviceUid: String): Flow<DeviceLightLibraryResult> {
        val uid = deviceUid.toDeviceUidOrNull()
        return if (uid == null) {
            flowOf(DeviceLightLibraryResult.Failed(DeviceLightLibraryFailure.UNAVAILABLE))
        } else {
            val statuses = devicesRepository.runtimeModules()?.light?.states
                ?: flowOf<Map<DeviceUid, DeviceLightStatus>>(emptyMap())
            observeAvailableLibrary(uid, statuses)
        }
    }

    private fun observeAvailableLibrary(
        uid: DeviceUid,
        statuses: Flow<Map<DeviceUid, DeviceLightStatus>>
    ): Flow<DeviceLightLibraryResult> = combine(
        controlOperations.observeControl(uid.value),
        store.observeEntries(),
        statuses,
        installedCustomDocuments
    ) { control, storedEntries, statuses, customDocuments ->
        val controlSnapshot = (control as? DeviceLightControlResult.Available)?.snapshot
            ?: return@combine DeviceLightLibraryResult.Failed(
                (control as DeviceLightControlResult.Failed).failure.toLibraryFailure()
            )
        val product = runCatching {
            DeviceLightProduct.fromWireExact(controlSnapshot.productKey)
        }.getOrNull() ?: return@combine DeviceLightLibraryResult.Failed(
            DeviceLightLibraryFailure.INVALID_DATA
        )
        val target = controlSnapshot.toTarget(product)
        val status = statuses[uid]
        val entries = storedEntries
            .filter { entry ->
                entry.productKey == product.wireValue &&
                    entry.channelKeysList == product.sceneFields
            }
            .map { entry ->
                entry.toApplicationEntry(
                    product = product,
                    status = status,
                    installedCustom = customDocuments[uid]
                )
            }
        DeviceLightLibraryResult.Available(
            DeviceLightLibrarySnapshot(target = target, entries = entries)
        )
    }.catch {
        emit(DeviceLightLibraryResult.Failed(DeviceLightLibraryFailure.INVALID_DATA))
    }

    override suspend fun refreshInstalledCustom(deviceUid: String) {
        val uid = deviceUid.toDeviceUidOrNull() ?: return
        val runtime = devicesRepository.runtimeModules()?.light ?: return
        try {
            val outcome = runtime.requestCustom(uid)
            if (outcome is DeviceRuntimeCommandOutcome.Success) {
                installedCustomDocuments.update { documents ->
                    documents + (uid to outcome.value)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Unit
        }
    }

    override suspend fun usedNames(kind: DeviceLightLibraryKind): List<String> =
        store.snapshot()
            .filter { entry -> entry.kind == kind.toStoredKind() }
            .map { entry -> entry.displayName }

    override suspend fun saveManual(
        deviceUid: String,
        name: String,
        scene: DeviceLightLibraryScene
    ): DeviceLightLibraryMutationResult = save(
        deviceUid = deviceUid,
        name = name,
        kind = DeviceLightLibraryKind.MANUAL
    ) { target, canonicalName, timestamp ->
        requireExactScene(target, scene)
        storedEntryBuilder(target, canonicalName, timestamp)
            .setKind(StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_MANUAL)
            .setManual(
                StoredDeviceLightManualScene.newBuilder()
                    .addAllChannels(scene.toStoredChannelValues(target.channels))
            )
            .build()
    }

    override suspend fun saveCustom(
        deviceUid: String,
        name: String,
        weekdaysMask: Int,
        points: List<DeviceLightLibraryCustomPoint>
    ): DeviceLightLibraryMutationResult = save(
        deviceUid = deviceUid,
        name = name,
        kind = DeviceLightLibraryKind.CUSTOM
    ) { target, canonicalName, timestamp ->
        require(weekdaysMask in DeviceLightLibraryStoreRules.MIN_WEEKDAYS_MASK..
            DeviceLightLibraryStoreRules.MAX_WEEKDAYS_MASK)
        require(points.size in 1..DeviceLightLibraryStoreRules.MAX_CUSTOM_POINTS)
        require(points.zipWithNext().all { (left, right) -> left.timeMs < right.timeMs })
        points.forEach { point ->
            require(point.timeMs in 0..DeviceLightLibraryStoreRules.LAST_DAY_MILLISECOND)
            requireExactScene(target, point.scene)
        }
        storedEntryBuilder(target, canonicalName, timestamp)
            .setKind(StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_CUSTOM)
            .setCustom(
                StoredDeviceLightCustomCurve.newBuilder()
                    .setWeekdaysMask(weekdaysMask)
                    .addAllPoints(
                        points.map { point ->
                            StoredDeviceLightCustomPoint.newBuilder()
                                .setTimeMs(point.timeMs)
                                .addAllChannels(
                                    point.scene.toStoredChannelValues(target.channels)
                                )
                                .build()
                        }
                    )
            )
            .build()
    }

    override suspend fun rename(
        entryId: String,
        name: String
    ): DeviceLightLibraryMutationResult {
        val canonicalName = name.validatedNameOrFailure()
            ?: return failed(DeviceLightLibraryFailure.INVALID_NAME)
        return runStoreMutation {
            store.rename(
                entryId = entryId,
                displayName = canonicalName.display,
                normalizedName = canonicalName.normalized,
                updatedAtMillis = nowMillis()
            )
            DeviceLightLibraryMutationResult.Success(entryId)
        }
    }

    override suspend fun delete(entryId: String): DeviceLightLibraryMutationResult =
        runStoreMutation {
            store.delete(entryId)
            DeviceLightLibraryMutationResult.Success(entryId)
        }

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
            uid == null || runtime == null || status == null ->
                failed(DeviceLightLibraryFailure.UNAVAILABLE)
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
            when (val outcome = runtime.installCustom(uid, payload)) {
                is DeviceRuntimeCommandOutcome.Success -> {
                    installedCustomDocuments.update { documents ->
                        documents + (uid to outcome.value)
                    }
                    DeviceLightLibraryMutationResult.Success(entry.id)
                }
                else -> outcome.toLibraryMutationResult(entry.id)
            }
        }
        StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_UNSPECIFIED,
        StoredDeviceLightLibraryKind.UNRECOGNIZED ->
            failed(DeviceLightLibraryFailure.INVALID_DATA)
    }

    private suspend fun save(
        deviceUid: String,
        name: String,
        kind: DeviceLightLibraryKind,
        build: (
            DeviceLightLibraryTarget,
            DeviceLightLibraryNamePolicy.CanonicalName,
            Long
        ) -> StoredDeviceLightLibraryEntry
    ): DeviceLightLibraryMutationResult {
        val canonicalName = name.validatedNameOrFailure()
        val target = currentTarget(deviceUid)
        val duplicateResult = if (canonicalName == null || target == null) {
            null
        } else {
            runCatching {
                store.snapshot().any { entry ->
                    entry.kind == kind.toStoredKind() &&
                        entry.normalizedName == canonicalName.normalized
                }
            }
        }
        return when {
            canonicalName == null -> failed(DeviceLightLibraryFailure.INVALID_NAME)
            target == null || duplicateResult == null || duplicateResult.isFailure ->
                failed(DeviceLightLibraryFailure.UNAVAILABLE)
            duplicateResult.getOrThrow() -> failed(DeviceLightLibraryFailure.DUPLICATE_NAME)
            else -> runStoreMutation {
                val timestamp = nowMillis()
                val entry = build(target, canonicalName, timestamp)
                store.insert(entry)
                DeviceLightLibraryMutationResult.Success(entry.id)
            }
        }
    }

    private fun currentTarget(deviceUid: String): DeviceLightLibraryTarget? {
        val control = controlOperations.currentControl(deviceUid)
            as? DeviceLightControlResult.Available
        return control?.let { available ->
            runCatching {
                DeviceLightProduct.fromWireExact(available.snapshot.productKey)
            }.map { product -> available.snapshot.toTarget(product) }.getOrNull()
        }
    }

    private fun storedEntryBuilder(
        target: DeviceLightLibraryTarget,
        canonicalName: DeviceLightLibraryNamePolicy.CanonicalName,
        timestamp: Long
    ): StoredDeviceLightLibraryEntry.Builder = StoredDeviceLightLibraryEntry.newBuilder()
        .setId(newId())
        .setOwnerUid(ownerUid)
        .setDisplayName(canonicalName.display)
        .setNormalizedName(canonicalName.normalized)
        .setProductKey(target.productKey)
        .addAllChannelKeys(target.channels.map(DeviceLightLibraryChannel::sceneKey))
        .setCreatedAtMillis(timestamp)
        .setUpdatedAtMillis(timestamp)

    private suspend fun runStoreMutation(
        mutation: suspend () -> DeviceLightLibraryMutationResult
    ): DeviceLightLibraryMutationResult = try {
        mutation()
    } catch (_: DeviceLightLibraryStoreConflict.Name) {
        failed(DeviceLightLibraryFailure.DUPLICATE_NAME)
    } catch (_: DeviceLightLibraryStoreConflict.NotFound) {
        failed(DeviceLightLibraryFailure.NOT_FOUND)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failed(DeviceLightLibraryFailure.INVALID_DATA)
    }
}
