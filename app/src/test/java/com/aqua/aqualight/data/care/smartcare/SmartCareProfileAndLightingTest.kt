package com.aqua.aqualight.data.care.smartcare

import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.application.care.SmartCareLightingAdjustment
import com.aqua.aqualight.application.care.SmartCareLightingPhase
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCareProfileAndLightingTest {

  private val currentDate = LocalDate.of(2026, 9, 15)
  private val nowMillis = currentDate.atStartOfDay(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()

  @Test
  fun `Nature Aquarium profile uses stable taxonomy and material keys`() {
    val profile = profile(
      tankType = AquariumTankTaxonomy.TYPE_PLANTED,
      tankStyle = AquariumTankTaxonomy.STYLE_NATURE_AQUARIUM,
      setupDay = 1
    )

    assertTrue(profile.isFreshwater)
    assertTrue(profile.isNatureAquarium)
    assertTrue(profile.hasLight)
    assertTrue(profile.hasActiveSoil)
    assertTrue(SmartCareCondition.NATURE_AQUARIUM in profile.conditions)
  }

  @Test
  fun `first three weeks recommend six hours without invented intensity`() {
    val recommendation = requireNotNull(
      SmartCareLightingAdvisor.recommend(
        profile(
          tankType = AquariumTankTaxonomy.TYPE_PLANTED,
          tankStyle = AquariumTankTaxonomy.STYLE_NATURE_AQUARIUM,
          setupDay = 21
        )
      )
    )

    assertEquals(SmartCareLightingPhase.STARTUP, recommendation.phase)
    assertEquals(360, recommendation.photoperiodMinutes)
    assertEquals(SmartCareLightingAdjustment.HOLD, recommendation.adjustment)
    assertTrue(recommendation.requiresIntensityCalibration)
  }

  @Test
  fun `post startup recommendation is gradual and capped at eight hours`() {
    val recommendation = requireNotNull(
      SmartCareLightingAdvisor.recommend(
        profile(
          tankType = AquariumTankTaxonomy.TYPE_PLANTED,
          tankStyle = AquariumTankTaxonomy.STYLE_NATURE_AQUARIUM,
          setupDay = 22
        )
      )
    )

    assertEquals(SmartCareLightingPhase.ESTABLISHING, recommendation.phase)
    assertEquals(360, recommendation.photoperiodMinutes)
    assertEquals(480, recommendation.maximumPhotoperiodMinutes)
    assertEquals(SmartCareLightingAdjustment.INCREASE_GRADUALLY, recommendation.adjustment)
  }

  @Test
  fun `marine tank never inherits planted freshwater lighting policy`() {
    val profile = profile(
      tankType = AquariumTankTaxonomy.TYPE_MARINE,
      tankStyle = "Other",
      setupDay = 10
    )

    assertTrue(profile.isMarine)
    assertFalse(profile.isFreshwater)
    assertNull(SmartCareLightingAdvisor.recommend(profile))
  }

  private fun profile(
    tankType: String,
    tankStyle: String,
    setupDay: Int
  ): SmartCareTankProfile {
    val tank = SavedAquariumTank(
      id = 1L,
      name = "Test",
      description = "",
      photoUri = null,
      setupDateEpochDay = currentDate.minusDays((setupDay - 1).toLong()).toEpochDay(),
      widthCm = 60,
      lengthCm = 40,
      heightCm = 40,
      volumeUnit = "L",
      tankType = tankType,
      tankStyle = tankStyle,
      createdAtMillis = 1L,
      plants = listOf(
        SavedAquariumPlant(
          id = 2L,
          catalogId = "plant:micranthemum_tweediei_monte_carlo",
          plantName = "Monte Carlo",
          category = "Carpet",
          markerX = 0.5f,
          markerY = 0.5f
        )
      ),
      materials = listOf(
        material(3L, "light", "light-device"),
        material(4L, "substrate", "ADA Amazonia Soil")
      )
    )
    return SmartCareProfileBuilder.build(tank, nowMillis)
  }

  private fun material(
    id: Long,
    category: String,
    name: String
  ): SavedAquariumMaterial {
    return SavedAquariumMaterial(
      id = id,
      productId = name,
      categoryKey = category,
      categoryTitle = category,
      name = name,
      brand = "",
      note = ""
    )
  }
}
