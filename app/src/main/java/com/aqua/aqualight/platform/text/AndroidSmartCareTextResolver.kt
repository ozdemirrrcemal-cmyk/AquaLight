package com.aqua.aqualight.platform.text

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.smartcare.SmartCareRule
import com.aqua.aqualight.data.care.smartcare.SmartCareRuleCatalog
import com.aqua.aqualight.data.care.smartcare.SmartCareTaskGenerator
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTextPresentation
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rebuilds automatic task copy from its stable Smart Care rule identity.
 *
 * Persisted title/description strings are deliberately not the presentation source. Cards, task
 * details and reminder notifications resolve application-owned copy from the currently active
 * locale. Persisted strings remain only as a defensive fallback when semantic identity cannot be
 * resolved; user-authored manual notes and custom titles never pass through this resolver.
 */
internal class AndroidSmartCareTextResolver(
    private val localizedContext: Context
) {

    fun resolve(
        task: CareTaskSnapshot,
        tank: AquariumTankSnapshot?
    ): CareTaskTextPresentation? {
        val ruleKey = task.generatedRuleKey.trim()
        if (ruleKey.isBlank()) return null

        val rule = findRule(
            ruleKey = ruleKey,
            tankId = task.tankId
        ) ?: return null

        val generated = tank?.let { snapshot ->
            val setupDay = parseSetupDay(
                ruleKey = ruleKey,
                tankId = task.tankId,
                rule = rule
            )
            SmartCareTaskGenerator.generateForTank(
                context = localizedContext,
                tank = snapshot.toSavedTank(),
                nowMillis = generationMillis(snapshot, setupDay)
            ).firstOrNull { candidate -> candidate.id == ruleKey }
        }

        return CareTaskTextPresentation(
            title = generated?.titleTr ?: localizedContext.getString(rule.titleRes),
            description = generated?.messageTr ?: localizedContext.getString(rule.messageRes)
        )
    }

    private fun findRule(
        ruleKey: String,
        tankId: Long
    ): SmartCareRule? {
        return SmartCareRuleCatalog.allRules.firstOrNull { rule ->
            ruleKey.startsWith(rulePrefix(tankId, rule.id))
        }
    }

    private fun parseSetupDay(
        ruleKey: String,
        tankId: Long,
        rule: SmartCareRule
    ): Int? {
        return ruleKey
            .removePrefix(rulePrefix(tankId, rule.id))
            .toIntOrNull()
            ?.takeIf { day -> day > 0 }
    }

    private fun rulePrefix(tankId: Long, ruleId: String): String =
        "smart_${tankId}_${ruleId}_"

    private fun generationMillis(
        tank: AquariumTankSnapshot,
        setupDay: Int?
    ): Long {
        val setupEpochDay = tank.setupDateEpochDay ?: return System.currentTimeMillis()
        val resolvedSetupDay = setupDay ?: return System.currentTimeMillis()
        return LocalDate.ofEpochDay(setupEpochDay)
            .plusDays((resolvedSetupDay - 1L).coerceAtLeast(0L))
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun AquariumTankSnapshot.toSavedTank(): SavedAquariumTank = SavedAquariumTank(
        id = id,
        ownerUid = "presentation",
        name = name,
        description = description,
        photoUri = photoUri,
        setupDateEpochDay = setupDateEpochDay,
        widthCm = widthCm,
        lengthCm = lengthCm,
        heightCm = heightCm,
        sizeUnit = sizeUnit,
        volumeUnit = volumeUnit,
        tankType = tankType,
        tankStyle = tankStyle,
        createdAtMillis = createdAtMillis,
        smartCareEnabled = smartCareEnabled,
        careRemindersEnabled = careRemindersEnabled,
        plants = plants.map { plant -> plant.toSavedPlant() },
        materials = materials.map { material -> material.toSavedMaterial() },
        livestock = livestock.map { item -> item.toSavedLivestock() }
    )

    private fun AquariumPlantTag.toSavedPlant(): SavedAquariumPlant = SavedAquariumPlant(
        id = id,
        plantName = plantName,
        category = category,
        markerX = markerX,
        markerY = markerY
    )

    private fun AquariumMaterialSelection.toSavedMaterial(): SavedAquariumMaterial =
        SavedAquariumMaterial(
            id = id,
            productId = productId,
            categoryKey = categoryKey,
            categoryTitle = categoryTitle,
            name = name,
            brand = brand,
            note = note
        )

    private fun AquariumLivestock.toSavedLivestock(): SavedAquariumLivestock =
        SavedAquariumLivestock(
            id = id,
            name = name,
            category = category,
            quantity = quantity,
            addedDateEpochDay = addedDateEpochDay,
            note = note
        )
}
