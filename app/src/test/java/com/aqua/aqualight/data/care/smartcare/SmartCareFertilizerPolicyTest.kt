package com.aqua.aqualight.data.care.smartcare

import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCareFertilizerPolicyTest {

  @Test
  fun `Tropica startup never invents a fractional dose`() {
    val rule = requireNotNull(
      FertilizerDoseCatalog.findById("tropica_specialised_nutrition_weekly")
    )

    val startup = SmartFertilizerDoseCalculator.calculate(
      rule = rule,
      grossVolumeL = 100.0,
      setupDay = 10,
      useEstimatedWaterVolume = false
    )
    val established = SmartFertilizerDoseCalculator.calculate(
      rule = rule,
      grossVolumeL = 100.0,
      setupDay = 29,
      useEstimatedWaterVolume = false
    )

    assertEquals(FertilizerDoseDecision.WITHHOLD_OR_LIMIT, startup.decision)
    assertNull(startup.advisedDoseMl)
    assertEquals(FertilizerDoseDecision.LABEL_DOSE, established.decision)
    assertEquals(12.0, established.advisedDoseMl ?: 0.0, 0.0)
    assertEquals(
      FertilizerAlgaeResponse.HALVE_DOSE_AND_INCREASE_WATER_CHANGES,
      rule.algaeResponse
    )
  }

  @Test
  fun `ADA stage and observation gates precede label dose`() {
    val iron = requireNotNull(
      FertilizerDoseCatalog.findById("ada_green_brighty_iron_daily")
    )
    val nitrogen = requireNotNull(
      FertilizerDoseCatalog.findById("ada_green_brighty_nitrogen_daily")
    )

    assertEquals(
      FertilizerDoseDecision.DEFER,
      SmartFertilizerDoseCalculator.calculate(iron, 100.0, 60).decision
    )
    assertEquals(
      FertilizerDoseDecision.LABEL_DOSE,
      SmartFertilizerDoseCalculator.calculate(iron, 100.0, 61).decision
    )
    assertEquals(
      FertilizerDoseDecision.OBSERVATION_REQUIRED,
      SmartFertilizerDoseCalculator.calculate(nitrogen, 100.0, 100).decision
    )
  }

  @Test
  fun `catalog product id wins while brand alone stays ambiguous`() {
    val selected = SmartCareFertilizerRuleResolver.resolve(
      tankWithMaterial(
        productId = "fertilizer_tropica_premium_nutrition",
        brand = "Tropica"
      )
    )
    val ambiguous = SmartCareFertilizerRuleResolver.resolve(
      tankWithMaterial(productId = "custom", brand = "Tropica")
    )

    assertEquals("tropica_premium_nutrition_weekly", selected?.id)
    assertNull(ambiguous)
  }

  @Test
  fun `product cadence follows declared weekly frequency`() {
    val weeklyRule = requireNotNull(
      FertilizerDoseCatalog.findById("tropica_specialised_nutrition_weekly")
    )
    val observedOnlyRule = requireNotNull(
      FertilizerDoseCatalog.findById("ada_green_brighty_nitrogen_daily")
    )
    val dueDays = (0..13).filter { day ->
      SmartCareFertilizerSchedule.isDue(weeklyRule, setupDay = day + 1, daysFromStart = day)
    }

    assertEquals(listOf(0, 7), dueDays)
    assertTrue(
      SmartCareFertilizerSchedule.isDue(
        observedOnlyRule,
        setupDay = 8,
        daysFromStart = 7
      )
    )
    assertFalse(
      SmartCareFertilizerSchedule.isDue(
        observedOnlyRule,
        setupDay = 9,
        daysFromStart = 8
      )
    )
  }

  private fun tankWithMaterial(
    productId: String,
    brand: String
  ): SavedAquariumTank {
    return SavedAquariumTank(
      id = 1L,
      name = "Test",
      description = "",
      photoUri = null,
      setupDateEpochDay = null,
      widthCm = 60,
      lengthCm = 40,
      heightCm = 40,
      volumeUnit = "L",
      tankType = "Planted",
      tankStyle = "Nature Aquarium",
      createdAtMillis = 1L,
      plants = emptyList(),
      materials = listOf(
        SavedAquariumMaterial(
          id = 2L,
          productId = productId,
          categoryKey = "fertilizer",
          categoryTitle = "Fertilizer",
          name = "",
          brand = brand,
          note = ""
        )
      )
    )
  }
}
