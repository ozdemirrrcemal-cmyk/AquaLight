package com.aqua.aqualight.data.recovery

import com.aqua.aqualight.application.user.LocalDataRecoveryArea
import com.aqua.aqualight.application.user.LocalDataRecoveryOperations

/** Maps the persistence tracker into the application recovery contract. */
object DefaultLocalDataRecoveryOperations : LocalDataRecoveryOperations {
    override fun consumeRecoveredAreas(): Set<LocalDataRecoveryArea> =
        LocalDataRecoveryTracker.consumeRecoveredAreas().mapTo(linkedSetOf()) { area ->
            LocalDataRecoveryArea.valueOf(area.name)
        }
}
