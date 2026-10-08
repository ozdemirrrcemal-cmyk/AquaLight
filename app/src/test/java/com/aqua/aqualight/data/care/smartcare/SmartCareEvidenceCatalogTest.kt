package com.aqua.aqualight.data.care.smartcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCareEvidenceCatalogTest {

  @Test
  fun `every evidence id has one secure auditable source`() {
    assertEquals(
      SmartCareEvidenceId.entries.toSet(),
      SmartCareEvidenceCatalog.sources.map { source -> source.id }.toSet()
    )
    assertTrue(
      SmartCareEvidenceCatalog.sources.all { source ->
        source.reviewedOn.isNotBlank()
      }
    )
  }

  @Test
  fun `every automatic care rule resolves only known evidence ids`() {
    val knownIds = SmartCareEvidenceCatalog.sources
      .map { source -> source.id.stableId }
      .toSet()

    assertTrue(SmartCareRuleCatalog.allRules.all { rule -> rule.sourceTags.isNotEmpty() })
    assertTrue(
      SmartCareRuleCatalog.allRules
        .flatMap { rule -> rule.sourceTags }
        .all(knownIds::contains)
    )
  }

  @Test
  fun `fish health evidence always requires professional diagnosis`() {
    val healthSources = SmartCareEvidenceCatalog.sources.filter { source ->
      SmartCareEvidenceTopic.FISH_HEALTH in source.topics
    }

    assertTrue(healthSources.isNotEmpty())
    assertTrue(healthSources.all { source -> source.requiresProfessionalDiagnosis })
  }

  @Test
  fun `freshwater method rules cannot leak into marine lighting or dosing`() {
    val scopedTypes = setOf(
      SmartCareTaskType.LIGHTING,
      SmartCareTaskType.FERTILIZER,
      SmartCareTaskType.WATER_CHANGE
    )
    val scopedRules = SmartCareRuleCatalog.allRules.filter { rule ->
      rule.taskType in scopedTypes
    }
    val natureRule = requireNotNull(
      SmartCareRuleCatalog.allRules.firstOrNull { rule ->
        rule.id == "startup_active_soil_water_change_first_week"
      }
    )

    assertTrue(
      scopedRules.all { rule -> SmartCareCondition.FRESHWATER in rule.conditions }
    )
    assertEquals(SmartCareRepeatMode.DAILY, natureRule.repeatMode)
    assertTrue(SmartCareCondition.NATURE_AQUARIUM in natureRule.conditions)
  }
}
