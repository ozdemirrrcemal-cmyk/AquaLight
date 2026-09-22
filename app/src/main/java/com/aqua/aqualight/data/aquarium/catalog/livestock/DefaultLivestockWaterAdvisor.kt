package com.aqua.aqualight.data.aquarium.catalog.livestock

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.aqua.aqualight.application.aquarium.AquariumWaterSnapshot
import com.aqua.aqualight.application.aquarium.LivestockWaterAdvisorOperations
import com.aqua.aqualight.application.aquarium.LivestockWaterAssessment
import com.aqua.aqualight.application.aquarium.LivestockWaterAssessmentStatus
import com.aqua.aqualight.application.aquarium.LivestockWaterCompatibilityEvaluator

class DefaultLivestockWaterAdvisor(
    context: Context
) : LivestockWaterAdvisorOperations {

    private val appContext = context.applicationContext

    override fun assess(
        livestock: AquariumLivestock,
        water: AquariumWaterSnapshot
    ): LivestockWaterAssessment {
        val catalogEntryId = livestock.catalogEntryId

        if (AquariumLivestockIdentity.isCustom(catalogEntryId)) {
            return LivestockWaterAssessment(
                livestockId = livestock.id,
                catalogEntryId = catalogEntryId,
                status = LivestockWaterAssessmentStatus.CUSTOM_UNVERIFIED
            )
        }

        val entry = LivestockCatalog.findById(
            context = appContext,
            entryId = catalogEntryId
        ) ?: return LivestockWaterAssessment(
            livestockId = livestock.id,
            catalogEntryId = catalogEntryId,
            status = LivestockWaterAssessmentStatus.CATALOG_ENTRY_MISSING
        )

        val compatibility = LivestockWaterCompatibilityEvaluator.evaluate(
            requirements = entry.waterRequirements,
            water = water
        )

        val status = when {
            compatibility.checkedParameterCount == 0 ->
                LivestockWaterAssessmentStatus.NO_COMPARABLE_MEASUREMENTS

            compatibility.isCompatible ->
                LivestockWaterAssessmentStatus.COMPATIBLE

            else ->
                LivestockWaterAssessmentStatus.OUT_OF_RANGE
        }

        return LivestockWaterAssessment(
            livestockId = livestock.id,
            catalogEntryId = catalogEntryId,
            status = status,
            compatibility = compatibility
        )
    }

    override fun assessTank(
        livestock: List<AquariumLivestock>,
        water: AquariumWaterSnapshot
    ): List<LivestockWaterAssessment> {
        return livestock.map { item ->
            assess(
                livestock = item,
                water = water
            )
        }
    }
}
