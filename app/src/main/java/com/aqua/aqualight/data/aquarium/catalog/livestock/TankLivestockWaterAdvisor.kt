package com.aqua.aqualight.data.aquarium.catalog.livestock

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.aqua.aqualight.application.aquarium.AquariumWaterSnapshot
import com.aqua.aqualight.application.aquarium.LivestockWaterCompatibility
import com.aqua.aqualight.application.aquarium.LivestockWaterCompatibilityEvaluator

enum class TankLivestockWaterAssessmentStatus {
    COMPATIBLE,
    OUT_OF_RANGE,
    NO_COMPARABLE_MEASUREMENTS,
    CUSTOM_UNVERIFIED,
    CATALOG_ENTRY_MISSING
}

data class TankLivestockWaterAssessment(
    val livestockId: Long,
    val catalogEntryId: String,
    val status: TankLivestockWaterAssessmentStatus,
    val compatibility: LivestockWaterCompatibility? = null
)

class TankLivestockWaterAdvisor(
    context: Context
) {
    private val appContext = context.applicationContext

    fun assess(
        livestock: AquariumLivestock,
        water: AquariumWaterSnapshot
    ): TankLivestockWaterAssessment {
        val catalogEntryId = livestock.catalogEntryId

        if (AquariumLivestockIdentity.isCustom(catalogEntryId)) {
            return TankLivestockWaterAssessment(
                livestockId = livestock.id,
                catalogEntryId = catalogEntryId,
                status = TankLivestockWaterAssessmentStatus.CUSTOM_UNVERIFIED
            )
        }

        val entry = LivestockCatalog.findById(
            context = appContext,
            entryId = catalogEntryId
        ) ?: return TankLivestockWaterAssessment(
            livestockId = livestock.id,
            catalogEntryId = catalogEntryId,
            status = TankLivestockWaterAssessmentStatus.CATALOG_ENTRY_MISSING
        )

        val compatibility = LivestockWaterCompatibilityEvaluator.evaluate(
            requirements = entry.waterRequirements,
            water = water
        )

        val status = when {
            compatibility.checkedParameterCount == 0 ->
                TankLivestockWaterAssessmentStatus.NO_COMPARABLE_MEASUREMENTS

            compatibility.isCompatible ->
                TankLivestockWaterAssessmentStatus.COMPATIBLE

            else ->
                TankLivestockWaterAssessmentStatus.OUT_OF_RANGE
        }

        return TankLivestockWaterAssessment(
            livestockId = livestock.id,
            catalogEntryId = catalogEntryId,
            status = status,
            compatibility = compatibility
        )
    }

    fun assessTank(
        livestock: List<AquariumLivestock>,
        water: AquariumWaterSnapshot
    ): List<TankLivestockWaterAssessment> {
        return livestock.map { item ->
            assess(
                livestock = item,
                water = water
            )
        }
    }
}
