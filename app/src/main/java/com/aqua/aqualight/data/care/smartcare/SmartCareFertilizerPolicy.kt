package com.aqua.aqualight.data.care.smartcare

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import java.util.Locale

object SmartCareFertilizerRuleResolver {

  fun resolve(tank: SavedAquariumTank): FertilizerDoseRule? {
    val supportedRules = FertilizerDoseCatalog.rules.filter { rule ->
      rule.evidenceSourceIds.isNotEmpty()
    }
    val exactMatch = tank.materials.firstNotNullOfOrNull { material ->
      supportedRules.firstOrNull { rule ->
        material.productId in rule.catalogProductIds
      }
    }
    return exactMatch ?: tank.materials.firstNotNullOfOrNull { material ->
      val materialText = listOf(
        material.name,
        material.brand,
        material.note
      ).joinToString(" ").lowercase(Locale.ROOT)

      supportedRules.firstOrNull { rule ->
        val productName = rule.productName.lowercase(Locale.ROOT)
        materialText.contains(productName) || productName
          .split(" ")
          .filter { token -> token.length > 2 }
          .all { token -> materialText.contains(token) }
      }
    }
  }
}

object SmartCareFertilizerSchedule {

  fun isDue(
    rule: FertilizerDoseRule,
    setupDay: Int,
    daysFromStart: Int
  ): Boolean {
    if (daysFromStart < 0) return false

    val frequency = when (SmartFertilizerDoseCalculator.decision(rule, setupDay)) {
      FertilizerDoseDecision.DEFER,
      FertilizerDoseDecision.OBSERVATION_REQUIRED -> FertilizerFrequency.AS_NEEDED
      else -> rule.frequency
    }
    return when (frequency) {
      FertilizerFrequency.DAILY -> true
      FertilizerFrequency.WEEKLY -> daysFromStart % DAYS_PER_WEEK == FIRST_DAY
      FertilizerFrequency.ONCE_OR_TWICE_WEEKLY -> {
        daysFromStart % DAYS_PER_WEEK in ONCE_OR_TWICE_DAYS
      }
      FertilizerFrequency.TWICE_WEEKLY -> daysFromStart % DAYS_PER_WEEK in TWICE_DAYS
      FertilizerFrequency.TWO_TO_THREE_TIMES_WEEKLY -> {
        daysFromStart % DAYS_PER_WEEK in TWO_TO_THREE_DAYS
      }
      FertilizerFrequency.AS_NEEDED -> daysFromStart % DAYS_PER_WEEK == FIRST_DAY
    }
  }

  private const val FIRST_DAY = 0
  private const val SECOND_DAY = 2
  private const val MID_WEEK_DAY = 3
  private const val LATE_WEEK_DAY = 4
  private const val THIRD_DOSE_DAY = 5
  private const val DAYS_PER_WEEK = 7
  private val ONCE_OR_TWICE_DAYS = setOf(FIRST_DAY, LATE_WEEK_DAY)
  private val TWICE_DAYS = setOf(FIRST_DAY, MID_WEEK_DAY)
  private val TWO_TO_THREE_DAYS = setOf(FIRST_DAY, SECOND_DAY, THIRD_DOSE_DAY)
}
