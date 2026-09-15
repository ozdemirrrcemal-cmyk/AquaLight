package com.aqua.aqualight.data.care.smartcare

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.application.care.SmartCareLightingPhase
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank

object SmartCareTaskPolicy {

  fun waterChangeMessage(
    context: Context,
    setupDay: Int?,
    percent: Int
  ): String {
    return if (setupDay != null) {
      context.getString(R.string.maintenance_smart_msg_water_change_day, setupDay, percent)
    } else {
      context.getString(R.string.maintenance_smart_msg_water_change, percent)
    }
  }

  fun standardMessage(
    context: Context,
    taskType: SmartCareTaskType,
    profile: SmartCareTankProfile
  ): String {
    return when (taskType) {
      SmartCareTaskType.CO2_CHECK -> context.getString(R.string.maintenance_smart_msg_co2_check)
      SmartCareTaskType.FEEDING -> context.getString(R.string.maintenance_smart_msg_feeding)
      SmartCareTaskType.LIGHTING -> lightingMessage(context, profile)
      SmartCareTaskType.WATER_TEST -> context.getString(R.string.maintenance_smart_msg_water_test)
      SmartCareTaskType.PLANT_CHECK -> context.getString(R.string.maintenance_smart_msg_plant_check)
      SmartCareTaskType.PLANT_TRIM -> context.getString(R.string.maintenance_smart_msg_plant_trim)
      SmartCareTaskType.FILTER_CHECK -> context.getString(R.string.maintenance_smart_msg_filter_check)
      SmartCareTaskType.GLASS_CLEANING -> {
        context.getString(R.string.maintenance_smart_msg_glass_cleaning)
      }
      SmartCareTaskType.LIVESTOCK_CHECK -> {
        context.getString(R.string.maintenance_smart_msg_livestock_check)
      }
      else -> context.getString(R.string.maintenance_smart_msg_general_check)
    }
  }

  fun lightingMessage(
    context: Context,
    profile: SmartCareTankProfile
  ): String {
    val recommendation = SmartCareLightingAdvisor.recommend(profile)
      ?: return context.getString(R.string.maintenance_smart_msg_lighting)

    return when (recommendation.phase) {
      SmartCareLightingPhase.STARTUP -> {
        context.getString(
          R.string.maintenance_smart_msg_lighting_startup,
          recommendation.photoperiodMinutes / MINUTES_PER_HOUR
        )
      }

      SmartCareLightingPhase.ESTABLISHING -> {
        context.getString(
          R.string.maintenance_smart_msg_lighting_establishing,
          recommendation.maximumPhotoperiodMinutes / MINUTES_PER_HOUR
        )
      }

      SmartCareLightingPhase.MATURE -> {
        context.getString(
          R.string.maintenance_smart_msg_lighting_mature,
          recommendation.photoperiodMinutes / MINUTES_PER_HOUR
        )
      }
    }
  }

  fun requiresWaterTest(
    tank: SavedAquariumTank,
    rule: SmartCareRule
  ): Boolean {
    return rule.requiresWaterTest || (
      rule.taskType == SmartCareTaskType.FERTILIZER &&
        SmartCareFertilizerRuleResolver.resolve(tank)?.requiresWaterTest == true
      )
  }

  private const val MINUTES_PER_HOUR = 60
}
