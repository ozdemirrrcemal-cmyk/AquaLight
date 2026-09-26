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
        return if (AquariumLivestockIdentity.isCustom(livestock.catalogEntryId)) {
            unverifiedCustomAssessment(livestock)
        } else {
            assessCatalogLivestock(livestock, water)
        }
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

    private fun assessCatalogLivestock(
        livestock: AquariumLivestock,
        water: AquariumWaterSnapshot
    ): LivestockWaterAssessment {
        val entry = LivestockCatalog.findById(
            context = appContext,
            entryId = livestock.catalogEntryId
        )

        return if (entry == null) {
            missingCatalogAssessment(livestock)
        } else {
            val compatibility = LivestockWaterCompatibilityEvaluator.evaluate(
                requirements = entry.waterRequirements,
                water = water
            )
            LivestockWaterAssessment(
                livestockId = livestock.id,
                catalogEntryId = livestock.catalogEntryId,
                status = compatibility.toAssessmentStatus(),
                compatibility = compatibility
            )
        }
    }

    private fun unverifiedCustomAssessment(
        livestock: AquariumLivestock
    ): LivestockWaterAssessment {
        return LivestockWaterAssessment(
            livestockId = livestock.id,
            catalogEntryId = livestock.catalogEntryId,
            status = LivestockWaterAssessmentStatus.CUSTOM_UNVERIFIED
        )
    }

    private fun missingCatalogAssessment(
        livestock: AquariumLivestock
    ): LivestockWaterAssessment {
        return LivestockWaterAssessment(
            livestockId = livestock.id,
            catalogEntryId = livestock.catalogEntryId,
            status = LivestockWaterAssessmentStatus.CATALOG_ENTRY_MISSING
        )
    }
}

private fun com.aqua.aqualight.application.aquarium.LivestockWaterCompatibility.toAssessmentStatus():
    LivestockWaterAssessmentStatus {
    return when {
        checkedParameterCount == 0 ->
            LivestockWaterAssessmentStatus.NO_COMPARABLE_MEASUREMENTS

        issues.isNotEmpty() ->
            LivestockWaterAssessmentStatus.OUT_OF_RANGE

        isCompatible ->
            LivestockWaterAssessmentStatus.COMPATIBLE

        else ->
            LivestockWaterAssessmentStatus.PARTIAL_EVIDENCE
    }
}
