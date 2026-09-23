package com.aqua.aqualight.data.aquarium

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumTankDetailsOperations
import com.aqua.aqualight.application.aquarium.AquariumTankSize
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.platform.media.AppMediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class DefaultAquariumTankDetailsOperations(
    context: Context,
    private val tankStore: AquariumTankDataStoreManager,
    private val dispatcher: CoroutineDispatcher
) : AquariumTankDetailsOperations {

    private val appContext = context.applicationContext

    override suspend fun updateTankPhoto(
        tankId: Long,
        photoUri: String?
    ): Unit = withContext(NonCancellable) {
        withContext(dispatcher) {
            val ownerUid = UserDataScope.requireCurrentUid()
            val result = runCatching {
                tankStore.updateTankPhoto(tankId, photoUri)
            }
            val previousPhoto = result.getOrElse { error ->
                runCatching {
                    AppMediaStorage.rollbackPendingMedia(
                        appContext,
                        photoUri
                    )
                }
                throw error
            }

            runCatching {
                AppMediaStorage.commitPendingMedia(
                    appContext,
                    photoUri
                )
            }
            runCatching {
                AppMediaStorage.deleteAfterCommit(
                    context = appContext,
                    ownerUid = ownerUid,
                    uriString = previousPhoto
                )
            }
            Unit
        }
    }

    override suspend fun updateTankName(
        tankId: Long,
        name: String
    ) = tankStore.updateTankName(tankId, name)

    override suspend fun updateTankType(
        tankId: Long,
        tankType: String
    ) = tankStore.updateTankType(tankId, tankType)

    override suspend fun updateTankSize(
        tankId: Long,
        size: AquariumTankSize
    ) = tankStore.updateTankSize(
        tankId = tankId,
        widthCm = size.widthCm,
        lengthCm = size.lengthCm,
        heightCm = size.heightCm,
        sizeUnit = size.sizeUnit
    )

    override suspend fun updateTankVolumeUnit(
        tankId: Long,
        volumeUnit: String
    ) = tankStore.updateTankVolumeUnit(
        tankId,
        volumeUnit
    )

    override suspend fun updateTankSetupDate(
        tankId: Long,
        setupDateEpochDay: Long
    ) = tankStore.updateTankSetupDate(
        tankId,
        setupDateEpochDay
    )

    override suspend fun updateTankStyle(
        tankId: Long,
        tankStyle: String
    ) = tankStore.updateTankStyle(
        tankId,
        tankStyle
    )

    override suspend fun updateTankDescription(
        tankId: Long,
        description: String
    ) = tankStore.updateTankDescription(
        tankId,
        description
    )
}
