package com.aqua.aqualight.data.devices.light.library

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryFailure
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryNamePolicy
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import java.util.concurrent.CancellationException

/** Owner-scoped persistence boundary, independent from device-runtime mutation concerns. */
internal class DeviceLightLibraryPersistence(
    private val ownerUid: String,
    private val store: DeviceLightLibraryStore,
    private val controlOperations: DeviceLightControlOperations,
    private val nowMillis: () -> Long,
    private val newId: () -> String
) {
    suspend fun usedNames(kind: DeviceLightLibraryKind): List<String> =
        store.snapshot()
            .filter { entry -> entry.kind == kind.toStoredKind() }
            .map { entry -> entry.displayName }

    suspend fun saveManual(
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

    suspend fun saveCustom(
        deviceUid: String,
        name: String,
        weekdaysMask: Int,
        points: List<DeviceLightLibraryCustomPoint>
    ): DeviceLightLibraryMutationResult = save(
        deviceUid = deviceUid,
        name = name,
        kind = DeviceLightLibraryKind.CUSTOM
    ) { target, canonicalName, timestamp ->
        validateCustomCurve(target, weekdaysMask, points)
        storedEntryBuilder(target, canonicalName, timestamp)
            .setKind(StoredDeviceLightLibraryKind.STORED_DEVICE_LIGHT_LIBRARY_KIND_CUSTOM)
            .setCustom(
                StoredDeviceLightCustomCurve.newBuilder()
                    .setWeekdaysMask(weekdaysMask)
                    .addAllPoints(points.map { point -> point.toStoredPoint(target) })
            )
            .build()
    }

    suspend fun rename(entryId: String, name: String): DeviceLightLibraryMutationResult {
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

    suspend fun delete(entryId: String): DeviceLightLibraryMutationResult = runStoreMutation {
        store.delete(entryId)
        DeviceLightLibraryMutationResult.Success(entryId)
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

private fun validateCustomCurve(
    target: DeviceLightLibraryTarget,
    weekdaysMask: Int,
    points: List<DeviceLightLibraryCustomPoint>
) {
    require(
        weekdaysMask in DeviceLightLibraryStoreRules.MIN_WEEKDAYS_MASK..
            DeviceLightLibraryStoreRules.MAX_WEEKDAYS_MASK
    )
    require(points.size in 1..DeviceLightLibraryStoreRules.MAX_CUSTOM_POINTS)
    require(points.zipWithNext().all { (left, right) -> left.timeMs < right.timeMs })
    points.forEach { point ->
        require(point.timeMs in 0..DeviceLightLibraryStoreRules.LAST_DAY_MILLISECOND)
        require(point.timeMs % DeviceLightLibraryStoreRules.SCHEDULE_TIME_STEP_MS == 0L)
        requireExactScene(target, point.scene)
    }
}

private fun DeviceLightLibraryCustomPoint.toStoredPoint(
    target: DeviceLightLibraryTarget
): StoredDeviceLightCustomPoint = StoredDeviceLightCustomPoint.newBuilder()
    .setTimeMs(timeMs)
    .addAllChannels(scene.toStoredChannelValues(target.channels))
    .build()
