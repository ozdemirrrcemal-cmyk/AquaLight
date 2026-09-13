package com.aqua.aqualight.application.devices.light.library

import kotlinx.coroutines.flow.Flow

/** Owner-scoped boundary. Presentation never reads or mutates DataStore directly. */
interface DeviceLightLibraryOperations {
    fun observeLibrary(deviceUid: String): Flow<DeviceLightLibraryResult>

    suspend fun usedNames(kind: DeviceLightLibraryKind): List<String>

    suspend fun refreshInstalledCustom(deviceUid: String)

    suspend fun saveManual(
        deviceUid: String,
        name: String,
        scene: DeviceLightLibraryScene
    ): DeviceLightLibraryMutationResult

    suspend fun saveCustom(
        deviceUid: String,
        name: String,
        weekdaysMask: Int,
        points: List<DeviceLightLibraryCustomPoint>
    ): DeviceLightLibraryMutationResult

    suspend fun rename(
        entryId: String,
        name: String
    ): DeviceLightLibraryMutationResult

    suspend fun delete(entryId: String): DeviceLightLibraryMutationResult

    suspend fun load(
        deviceUid: String,
        entryId: String
    ): DeviceLightLibraryMutationResult
}
