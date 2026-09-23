package com.aqua.aqualight.data.aquarium

import com.aqua.aqualight.application.aquarium.AquariumTankCareSettingsOperations
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.user.withCurrentOwnerScope

internal class DefaultAquariumTankCareSettingsOperations(
    private val tankStore: AquariumTankDataStoreManager,
    private val notificationPreferences:
        NotificationPreferenceUseCase
) : AquariumTankCareSettingsOperations {

    override suspend fun updateSmartCareEnabled(
        tankId: Long,
        enabled: Boolean
    ) = tankStore.updateSmartCareEnabled(
        tankId,
        enabled
    )

    override suspend fun updateCareRemindersEnabled(
        tankId: Long,
        enabled: Boolean
    ) = withCurrentOwnerScope { ownerUid ->
        tankStore.updateCareRemindersEnabled(
            tankId,
            enabled
        )
        notificationPreferences.reconcileOwner(ownerUid)
    }
}
